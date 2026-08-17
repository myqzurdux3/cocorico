package com.cocorico.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cocorico.alarm.AlarmService
import com.cocorico.alarm.AlarmState
import com.cocorico.challenge.Challenge
import com.cocorico.challenge.MathChallenge
import com.cocorico.challenge.MathChallengeEngine
import com.cocorico.challenge.MathProblemGenerator
import com.cocorico.challenge.photo.PhotoChallenge
import com.cocorico.challenge.pompes.PompesChallenge
import com.cocorico.data.AlarmConfig
import com.cocorico.data.AlarmConfigRepository
import com.cocorico.data.ChallengeId
import com.cocorico.data.CocoricoDatabase
import com.cocorico.data.WakeRecord
import com.cocorico.ring.HandDetector
import com.cocorico.ring.RingtonePlayer
import com.cocorico.ring.InactivityTracker
import com.cocorico.ring.NiveauxVolume
import com.cocorico.ring.VolumeState
import com.cocorico.ring.VolumeStateMachine
import com.cocorico.ui.theme.CocoricoTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * L'écran plein sur lequel l'utilisateur se réveille. Il pilote le volume mais
 * n'arrête jamais la sonnerie lui-même : seul AlarmService le fait, et seulement
 * quand le défi est résolu.
 */
class AlarmActivity : ComponentActivity() {

    private lateinit var player: RingtonePlayer
    private lateinit var machine: VolumeStateMachine
    private lateinit var detector: HandDetector
    private lateinit var retourNeutralise: OnBackPressedCallback
    private val inactivite = InactivityTracker(SECONDES_INACTIVITE * 1_000L)
    /**
     * Instant du déclenchement, lu chez le service et non horodaté ici.
     * L'activité peut mourir et être relancée en cours d'alarme ; horodater sa
     * propre création faisait repartir de zéro la durée enregistrée dans
     * l'historique, et faussait toutes les statistiques qui en découlent.
     *
     * Repli sur l'instant courant si le drapeau a disparu : une durée
     * approximative vaut mieux qu'un horodatage nul en base.
     */
    private val alarmeAt: Long by lazy {
        AlarmState.instantDeclenchement(this).takeIf { it > 0L } ?: System.currentTimeMillis()
    }

    /**
     * Reflets d'affichage, pas une seconde source de vérité : la machine à états
     * reste seule maîtresse du volume et [InactivityTracker] seul maître du
     * compte à rebours. On ne fait que recopier ce qu'ils décident déjà.
     */
    private val volumeAffiche = mutableStateOf(VolumeState.PLEIN)

    /**
     * Plafond sonore choisi par l'utilisateur, pour que la jauge annonce un
     * pourcentage qui corresponde à ce qu'il a réglé. Renseigné avec le reste
     * de la configuration ; jusque-là, le maximum, comme le lecteur.
     */
    private val plafondVolume = mutableStateOf(NiveauxVolume.POURCENT_MAXIMAL)
    private val secondesAvantRemontee = mutableStateOf(SECONDES_INACTIVITE)

    /**
     * État observable, pas un simple champ : le renoncement au défi pompes le
     * remplace en cours de route par les calculs, et l'écran doit s'en rendre
     * compte pour recomposer.
     */
    private val defi = mutableStateOf<Challenge?>(null)

    /** Vrai si l'utilisateur a renoncé au défi initial pour se rabattre sur les calculs. */
    private var abandon = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // Le défi pompes ne touche pas l'écran pendant une minute entière :
        // sans ce drapeau, l'affichage s'éteindrait en pleine série.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Bord-à-bord imposé par le `targetSdk 35`. On force le style « sombre »
        // pour les deux barres : cet écran est toujours peint en rouge vif, quel
        // que soit le thème du système, et il lui faut donc des icônes claires.
        // Le contenu, lui, est rangé dans la zone sûre par `EcranAlarme`.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        // `onBackPressed` n'est plus appelé quand l'application vise le SDK 35 :
        // le geste retour passe par l'OnBackPressedDispatcher. Sans ce rappel,
        // un retour arrière sur Android 15 fermerait l'écran et perdrait la
        // progression du défi, alarme toujours en train de sonner.
        retourNeutralise = onBackPressedDispatcher.addCallback(this) { }

        player = RingtonePlayer(this)
        machine = VolumeStateMachine {
            player.appliquer(it)
            volumeAffiche.value = it
            // Le décompte n'est calculé que lorsqu'il est affiché : à l'instant
            // où il le devient, il faut donc le rafraîchir ici plutôt que de
            // laisser jusqu'à un demi-tour d'horloge une valeur périmée sous
            // les yeux de l'utilisateur.
            majCompteARebours(SystemClock.elapsedRealtime())
        }
        detector = HandDetector(
            context = this,
            onPrisEnMain = { machine.onPhonePrisEnMain() },
            // Bouger le téléphone doit réarmer le compte à rebours comme une
            // interaction avec le défi : même méthode, même chemin, pas de
            // second système de réarmement à maintenir en parallèle.
            onMouvement = { interaction() },
        )
        detector.demarrer()

        // Posé ici et non dans la coroutine : la composition observe `defi`,
        // qui est un état, et se met à jour toute seule dès qu'il arrive. Le
        // faire depuis la coroutine laissait la fenêtre vide par-dessus le
        // verrouillage le temps de la lecture disque, et permettait d'appeler
        // `setContent` après la destruction de l'activité.
        setContent {
            CocoricoTheme(darkTheme = true) {
                val challengeActuel by defi
                challengeActuel?.let { challenge ->
                    EcranAlarme(
                        plafondPourcent = plafondVolume.value,
                        detectionPriseEnMain = detector.capteurDisponible(),
                        challenge = challenge,
                        volume = volumeAffiche.value,
                        secondes = secondesAvantRemontee.value,
                    )
                }
            }
        }

        lifecycleScope.launch {
            // Le défi doit s'afficher même si la persistance est cassée. Une
            // exception ici laissait un écran noir par-dessus le verrouillage,
            // sonnerie à fond et aucun moyen de l'arrêter.
            val config = runCatching { AlarmConfigRepository(applicationContext).current() }
                .getOrDefault(AlarmConfig.DEFAULT)
            // Ce lecteur ne joue rien : il ne sert qu'à piloter le volume
            // depuis la machine à états. Il lui faut le même plafond que celui
            // du service, sans quoi la remontée après inactivité repousserait
            // le son au maximum de l'appareil.
            player.volumeMaxPourcent = config.volumeMaxPourcent
            plafondVolume.value = config.volumeMaxPourcent
            defi.value = construireDefi(config)

            // Horloge monotone, exigée par `InactivityTracker` : l'horloge
            // murale peut sauter (resynchronisation au réveil), ce qui
            // figerait le compte à rebours ou ferait remonter le volume
            // aussitôt, téléphone en main.
            inactivite.onInteraction(SystemClock.elapsedRealtime())

            // Surveillance de l'inactivité : réveille le volume si l'utilisateur
            // décroche. Elle seule a besoin d'un tour d'horloge ; la résolution
            // du défi, elle, est désormais notifiée (voir ci-dessous).
            val surveillance = launch {
                while (true) {
                    delay(500)
                    val maintenant = SystemClock.elapsedRealtime()
                    if (inactivite.isExpired(maintenant)) {
                        machine.onInactiviteExpiree()
                    }
                    majCompteARebours(maintenant)
                }
            }

            attendreResolution()
            surveillance.cancel()
            terminer()
        }
    }

    /**
     * Rend la main à l'instant précis où le défi courant se déclare résolu.
     *
     * Scruter [Challenge.isSolved] toutes les 500 ms laissait jusqu'à une
     * demi-seconde de sirène après la victoire — la demi-seconde la plus longue
     * de la journée. On s'abonne donc au flux au lieu de le relire.
     *
     * `defi` reste relu à chaque changement : un renoncement **remplace** le
     * défi en cours de route, et `flatMapLatest` abandonne alors la surveillance
     * de l'ancien pour celle du nouveau. Une référence figée au démarrage
     * attendrait la résolution d'un défi que plus personne ne joue, et l'alarme
     * ne s'arrêterait jamais.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun attendreResolution() {
        snapshotFlow { defi.value }
            .filterNotNull()
            .flatMapLatest { challenge -> challenge.isSolved }
            .first { resolu -> resolu }
    }

    /**
     * Construit le défi demandé par la configuration, avec repli sur les
     * calculs si l'appareil ne peut pas le valider : sans capteur de
     * proximité, le défi pompes ne peut jamais se valider ; sans caméra, sans
     * permission caméra ou sans juge embarqué disponible, le défi photo non
     * plus. Le repli est décidé ici, avant tout affichage — jamais après.
     */
    private fun construireDefi(config: AlarmConfig): Challenge {
        val maths = {
            MathChallenge(
                MathChallengeEngine(MathProblemGenerator(), config.difficulty),
            ) { interaction() }
        }
        // Renoncement commun aux deux défis actifs : le nouvel écran remplace
        // l'ancien dans la composition, et ses capteurs (pompes) ou sa caméra
        // (photo) sont libérés par le `DisposableEffect` du `Content` sortant
        // quand celui-ci quitte la composition.
        val onRenoncer = {
            abandon = true
            defi.value = maths()
        }

        return when (config.challengeId) {
            ChallengeId.MATHS -> maths()

            ChallengeId.POMPES -> {
                val pompes = PompesChallenge(
                    context = this,
                    difficulty = config.difficulty,
                    onInteraction = { interaction() },
                    onRenoncer = onRenoncer,
                )
                // Un téléphone sans capteur de proximité ne doit pas piéger l'utilisateur.
                if (challengeEffectif(ChallengeId.POMPES, capteurPompesDisponible = pompes.capteurDisponible) ==
                    ChallengeId.POMPES
                ) {
                    pompes
                } else {
                    maths()
                }
            }

            ChallengeId.PHOTO -> {
                // La permission n'est vérifiée qu'ici, jamais réclamée par cette
                // activité : la demande vit dans l'onboarding, et seulement
                // quand la photo est le défi choisi (voir `OnboardingScreen`).
                // Un refus n'est pas bloquant : il vaut simple repli, comme un
                // capteur de proximité absent.
                val permissionCameraAccordee = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                if (!permissionCameraAccordee) {
                    maths()
                } else {
                    val photo = PhotoChallenge(
                        context = this,
                        difficulty = config.difficulty,
                        cleApi = config.cleApi,
                        // Sans ce passage, la sélection par pièce serait un
                        // réglage décoratif : l'écran la montrerait, la
                        // persistance la garderait, et le réveil piocherait
                        // quand même dans tout le catalogue — donc dans des
                        // objets que l'utilisateur a explicitement dit ne pas
                        // posséder. C'est exactement le blocage que cette
                        // fonctionnalité existe pour éviter.
                        objetsSelectionnes = config.objetsSelectionnes,
                        onInteraction = { interaction() },
                        onRenoncer = onRenoncer,
                    )
                    // Caméra absente ou juge embarqué indisponible : même repli.
                    if (challengeEffectif(
                            ChallengeId.PHOTO,
                            permissionCameraAccordee = true,
                            camerasDisponibles = photo.camerasDisponibles,
                        ) == ChallengeId.PHOTO
                    ) {
                        photo
                    } else {
                        maths()
                    }
                }
            }
        }
    }

    /** Regroupe les deux appels que chaque geste de l'utilisateur doit déclencher. */
    private fun interaction() {
        // Même horloge monotone que la boucle de surveillance : mélanger les
        // deux ferait comparer un instant mural à un instant depuis le
        // démarrage, et le compte à rebours n'aurait plus aucun sens.
        val maintenant = SystemClock.elapsedRealtime()
        inactivite.onInteraction(maintenant)
        machine.onInteraction()
        majCompteARebours(maintenant)
    }

    /**
     * Secondes restantes avant la remontée du volume, arrondies au supérieur.
     *
     * N'écrit **que** si la valeur est effectivement à l'écran. Chaque écriture
     * recompose tout `EcranAlarme`, dont `challenge.Content` — un récepteur
     * instable que Compose ne saute jamais : à plein volume, où le décompte
     * n'est pas affiché, on recomposait donc le pavé numérique ou l'aperçu
     * caméra deux fois par seconde pendant toute la durée de l'alarme, pour une
     * valeur que personne ne voyait.
     */
    private fun majCompteARebours(maintenant: Long) {
        if (!compteAReboursAffiche(volumeAffiche.value)) return
        val restant = inactivite.millisRestantes(maintenant)
        secondesAvantRemontee.value = ((restant + 999L) / 1000L).toInt()
    }

    /**
     * Neutralise les boutons de volume : c'est le premier réflexe d'un dormeur
     * et le contournement le plus évident.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        -> true
        else -> super.onKeyDown(keyCode, event)
    }

    /**
     * Le relâchement doit être consommé comme l'appui. Ne neutraliser que
     * `onKeyDown` laissait l'événement `KEYUP` remonter jusqu'à `PhoneWindow`,
     * qui peut alors afficher le panneau de volume du système par-dessus l'écran
     * d'alarme — un panneau qui, lui, obéit au doigt.
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        -> true
        else -> super.onKeyUp(keyCode, event)
    }

    /**
     * L'enregistrement du réveil et l'arrêt du service sont `NonCancellable` :
     * cette activité est en train de se terminer, et une annulation en vol
     * laisserait la sonnerie hurler alors que le défi est résolu. Un échec
     * d'écriture en base ne doit jamais empêcher l'arrêt de l'alarme, d'où le
     * `runCatching` autour de la seule insertion.
     */
    private fun terminer() {
        detector.arreter()
        // Défi résolu : le retour arrière redevient normal.
        retourNeutralise.isEnabled = false
        // Lu ici, pas au démarrage : c'est le défi qui vient effectivement de
        // se résoudre, celui d'après un éventuel renoncement.
        val challengeFinal = defi.value
        // Chaque défi compte ses ratés à sa façon : fautes de calcul d'un côté,
        // photos refusées de l'autre. Les pompes n'ont rien à compter — une
        // répétition mal faite n'est simplement pas comptée.
        val erreurs = when (challengeFinal) {
            is MathChallenge -> challengeFinal.erreurs.value
            is PhotoChallenge -> challengeFinal.essaisTotal.value
            else -> 0
        }
        lifecycleScope.launch {
            withContext(NonCancellable) {
                runCatching {
                    CocoricoDatabase.get(applicationContext).wakeRecordDao().inserer(
                        WakeRecord(
                            alarmeAt = alarmeAt,
                            resoluAt = System.currentTimeMillis(),
                            erreurs = erreurs,
                            defi = challengeFinal?.id?.name ?: ChallengeId.MATHS.name,
                            abandon = abandon,
                        ),
                    )
                }
                AlarmService.arreter(applicationContext)
            }
            startActivity(
                Intent(this@AlarmActivity, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_VICTOIRE, true)
                    // Preuve que la victoire vient bien de nous : `MainActivity`
                    // est exportée et refuse l'annonce sans ce jeton.
                    .putExtra(MainActivity.EXTRA_JETON, MainActivity.jetonIdentite(this@AlarmActivity)),
            )
            finish()
        }
    }

    override fun onDestroy() {
        detector.arreter()
        super.onDestroy()
    }

    private companion object {
        /** Doit refléter le délai d'[InactivityTracker] : c'est la valeur affichée. */
        const val SECONDES_INACTIVITE = 10
    }
}

/**
 * Décide quel défi sonnera effectivement, à partir du défi réglé et des
 * capacités déjà constatées de l'appareil — capteur, permission, caméra.
 * Aucun import `android.*` ici : c'est exactement cette décision qui garantit
 * qu'aucun réveil ne laisse l'utilisateur devant une alarme qu'il ne peut pas
 * arrêter, elle mérite donc d'être testable sans capteur ni caméra réels.
 *
 * Les calculs n'ont aucune capacité à vérifier : ils sonnent toujours tels
 * quels. Les pompes retombent sur les calculs sans capteur de proximité, la
 * photo sans permission caméra ou sans caméra/juge embarqué disponible —
 * [permissionCameraAccordee] et [camerasDisponibles] gardent leur valeur par
 * défaut quand l'appelant n'a pas besoin de les fournir.
 */
internal fun challengeEffectif(
    challengeId: ChallengeId,
    capteurPompesDisponible: Boolean = false,
    permissionCameraAccordee: Boolean = false,
    camerasDisponibles: Boolean = false,
): ChallengeId = when (challengeId) {
    ChallengeId.MATHS -> ChallengeId.MATHS
    ChallengeId.POMPES -> if (capteurPompesDisponible) ChallengeId.POMPES else ChallengeId.MATHS
    ChallengeId.PHOTO ->
        if (permissionCameraAccordee && camerasDisponibles) ChallengeId.PHOTO else ChallengeId.MATHS
}

/**
 * Le compte à rebours avant la remontée du volume est-il à l'écran ?
 *
 * À plein volume il n'y a rien à décompter : la jauge se contente de rappeler
 * le contrat. Ce prédicat est partagé entre l'affichage ([Jauge]) et le calcul
 * ([AlarmActivity.majCompteARebours]) parce que les deux doivent répondre la
 * même chose : c'est ce qui garantit qu'on ne recalcule jamais — donc qu'on ne
 * recompose jamais l'écran de défi — pour une valeur invisible, sans jamais
 * risquer d'afficher un décompte figé.
 */
internal fun compteAReboursAffiche(volume: VolumeState): Boolean = volume != VolumeState.PLEIN

/**
 * L'écran est peint en `error` : sa `Surface` impose donc `onError` comme
 * couleur de contenu pour tout ce qu'il contient, défi compris. Sans elle, les
 * textes qui n'imposent pas leur couleur hériteraient de celle calibrée pour le
 * fond nuit — et le pavé numérique s'afficherait en noir.
 */
@Composable
private fun EcranAlarme(
    challenge: Challenge,
    volume: VolumeState,
    secondes: Int,
    plafondPourcent: Int,
    detectionPriseEnMain: Boolean,
) {
    var defiOuvert by remember { mutableStateOf(false) }
    val heure = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    val defilement = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    ) {
        Column(
            // Énoncé, saisie et pavé numérique dépassent la hauteur d'un petit
            // écran dès que la police système est agrandie. Sans défilement, la
            // touche de validation devient inatteignable : alarme inarrêtable.
            // Écart connu, non corrigé : défi fermé, il n'y a pas de
            // défilement, et à taille de police maximale l'horloge à 68 sp
            // pourrait rogner « Faire taire ce coq ». Rendre le défilement
            // inconditionnel supprime le centrage vertical de cet état — une
            // régression visuelle certaine contre un débordement supposé, que
            // seul un rendu sur appareil peut trancher. Voir AUDIT.md.
            // La zone sûre est appliquée **avant** le défilement : la fenêtre de
            // défilement s'arrête donc au-dessus de la barre de navigation au
            // lieu de passer dessous. La dernière rangée du pavé (dont la touche
            // de validation) reste atteignable, y compris avec la navigation
            // gestuelle. La `Surface` rouge, elle, garde toute la surface.
            modifier = Modifier
                .fillMaxSize()
                .zoneSure()
                .then(if (defiOuvert) Modifier.verticalScroll(defilement) else Modifier)
                .padding(24.dp),
            verticalArrangement = if (defiOuvert) {
                Arrangement.spacedBy(16.dp)
            } else {
                Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Jauge(volume = volume, secondes = secondes, plafondPourcent = plafondPourcent)

            if (!defiOuvert) {
                Text(
                    text = heure,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 68.sp,
                )
                Text(
                    text = "Debout. Y'a pas de bouton.",
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { defiOuvert = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Faire taire ce coq", fontSize = 18.sp)
                }
                Text(
                    // Promesse conditionnée au capteur : sur un téléphone sans
                    // accéléromètre, la baisse à la prise en main n'a jamais
                    // lieu, et l'annoncer quand même serait mentir à quelqu'un
                    // qui secoue son téléphone en attendant qu'il se taise.
                    text = if (detectionPriseEnMain) {
                        "Prends le téléphone en main : le volume baisse tout seul."
                    } else {
                        "Ce téléphone n'a pas le capteur qu'il faut : le volume ne baissera pas."
                    },
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                // Clé sur le défi lui-même : un renoncement change son identité,
                // ce qui force la sortie de composition de l'ancien (et donc la
                // libération de ses capteurs) plutôt qu'une simple mise à jour.
                key(challenge) {
                    challenge.Content(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * Le marché du produit, affiché en clair du début à la fin (spec § 6) : voici le
 * volume, et voici ce qui le fera remonter. Sans ça, la remontée au bout de dix
 * secondes n'est qu'une punition inexpliquée.
 *
 * Quand le compte à rebours s'épuise, l'avertissement passe sur une pastille
 * jaune : sur fond rouge, c'est le seul contraste qui saute aux yeux à 6 h.
 */
@Composable
private fun Jauge(volume: VolumeState, secondes: Int, plafondPourcent: Int) {
    val urgent = volume == VolumeState.BAISSE && secondes <= SEUIL_URGENCE_S
    // Le décompte ne s'affiche qu'une fois le volume baissé : à fond, il n'y a
    // rien à décompter, seulement le contrat à rappeler.
    val avertissement = if (compteAReboursAffiche(volume)) {
        "Ça repart à fond dans %02d s.".format(secondes)
    } else {
        AVERTISSEMENT_REMONTEE
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            // Le pourcentage se rapporte au plafond choisi par l'utilisateur,
            // pas au maximum de l'appareil : afficher « 100 % » en dur
            // contredisait ouvertement un plafond réglé plus bas.
            text = "Volume — ${
                if (volume == VolumeState.PLEIN) NiveauxVolume.pourcentAffichePlein(plafondPourcent)
                else NiveauxVolume.pourcentAfficheBaisse(plafondPourcent)
            } %",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
        )
        if (urgent) {
            Text(
                text = avertissement,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        } else {
            Text(
                text = avertissement,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Affiché tant que le volume est à fond : le décompte n'a pas encore commencé. */
private const val AVERTISSEMENT_REMONTEE = "Sans réponse pendant 10 s, ça repart à fond."

/** En dessous de ce reste, l'avertissement passe en mode alarmant. */
private const val SEUIL_URGENCE_S = 4
