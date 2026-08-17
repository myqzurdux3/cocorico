package com.cocorico.ui

import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocorico.alarm.AlarmState
import com.cocorico.challenge.photo.PhotoChallenge
import com.cocorico.challenge.pompes.PompesChallenge
import com.cocorico.data.ChallengeId
import com.cocorico.data.Difficulty
import com.cocorico.ui.theme.CocoricoTheme

private enum class Ecran { ACCUEIL, DEFI, SONNERIE, STATS, ESSAI_PHOTO, SELECTION_OBJETS, VICTOIRE }

/**
 * Sauvegarde l'écran courant par son nom : un `Bundle` ne sait pas ranger une
 * énumération sans aide, et l'index ordinal se déplacerait sous les pieds
 * d'une sauvegarde existante si on ajoutait un écran au milieu de la liste.
 */
private val sauveurEcran = Saver<Ecran, String>(
    save = { it.name },
    restore = { runCatching { Ecran.valueOf(it) }.getOrDefault(Ecran.ACCUEIL) },
)

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    /**
     * L'activité est en `singleTop` : si elle tourne déjà quand le défi est
     * résolu, Android appelle `onNewIntent` et non `onCreate`. Sans cet état
     * observable, l'écran de victoire ne s'afficherait jamais pour un
     * utilisateur qui a laissé l'application ouverte la veille — le cas courant.
     */
    private val victoire = mutableStateOf(false)

    /**
     * Une alarme peut être en train de sonner alors que l'utilisateur arrive
     * ici : plein écran dégradé en bandeau, écran d'alarme balayé, retour par
     * l'icône de lancement. `AlarmActivity` est le seul composant capable
     * d'arrêter la sonnerie ; il faut donc toujours un chemin visible vers lui.
     */
    private val alarmeEnCours = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        victoire.value = consommerVictoire(intent)

        // Le `targetSdk 35` impose le bord-à-bord : autant le déclarer nous-mêmes
        // pour obtenir des barres système transparentes et des icônes lisibles.
        // Chaque écran range ensuite son contenu dans la zone sûre (`zoneSure`).
        enableEdgeToEdge()

        setContent {
            CocoricoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Contenu()
                }
            }
        }
    }

    @Composable
    private fun Contenu() {
        // Lu une seule fois ici, pas dans chaque branche qui en a besoin :
        // la reprise en a besoin pour annoncer le bon défi, et l'accueil des
        // permissions pour ne réclamer la caméra que si la photo est choisie.
        val config by viewModel.config.collectAsState()

        if (alarmeEnCours.value) {
            // Le défi effectivement en train de sonner dépend du réglage :
            // sans lire `config`, cet écran promettrait toujours des calculs,
            // même quand c'est le défi pompes ou photo qui attend en dessous.
            EcranReprise(
                challengeId = config.challengeId,
                difficulty = config.difficulty,
                onReprendre = { demarrerAlarme() },
            )
            return
        }

        // Relu à chaque retour au premier plan : chacune de ces permissions se
        // règle dans un écran d'Android, et le retour se fait souvent par le
        // bouton retour, qui ne rend aucun résultat. Voir la KDoc du helper.
        val etat = etatPermissionsObserve()
        if (!etat.value.toutesAccordees) {
            OnboardingScreen(etat.value, config.challengeId) {
                etat.value = PermissionChecker.etat(this)
            }
            return
        }
        // `rememberSaveable` et non `remember` : une rotation détruit et
        // recrée l'activité, et l'état de navigation partait avec elle —
        // l'utilisateur revenait à l'accueil, ayant perdu les stats, la
        // sonnerie ou la sélection d'objets qu'il était en train de régler.
        // L'énumération est sauvegardée par son nom, seule forme qu'un
        // `Bundle` accepte sans sérialiseur dédié.
        var ecran by rememberSaveable(stateSaver = sauveurEcran) {
            mutableStateOf(if (victoire.value) Ecran.VICTOIRE else Ecran.ACCUEIL)
        }
        LaunchedEffect(victoire.value) {
            if (victoire.value) ecran = Ecran.VICTOIRE
        }
        when (ecran) {
            Ecran.ACCUEIL -> HomeScreen(
                viewModel = viewModel,
                onOuvrirDefi = { ecran = Ecran.DEFI },
                onOuvrirSonnerie = { ecran = Ecran.SONNERIE },
                onOuvrirStats = { ecran = Ecran.STATS },
                onChoisirHeure = { ouvrirSelecteurHeure() },
            )
            Ecran.DEFI -> ChallengeSettingsScreen(
                viewModel = viewModel,
                onEssayerPhoto = { ecran = Ecran.ESSAI_PHOTO },
                onOuvrirSelectionObjets = { ecran = Ecran.SELECTION_OBJETS },
                onRetour = { ecran = Ecran.ACCUEIL },
            )
            Ecran.ESSAI_PHOTO -> EssaiPhotoScreen(
                cleApi = config.cleApi,
                onRetour = { ecran = Ecran.DEFI },
            )
            Ecran.SELECTION_OBJETS -> SelectionObjetsScreen(
                viewModel = viewModel,
                onRetour = { ecran = Ecran.DEFI },
            )
            Ecran.SONNERIE -> RingtoneScreen(viewModel) { ecran = Ecran.ACCUEIL }
            Ecran.STATS -> StatsScreen { ecran = Ecran.ACCUEIL }
            Ecran.VICTOIRE -> VictoryScreen {
                victoire.value = false
                ecran = Ecran.ACCUEIL
            }
        }
    }

    /**
     * Relu à chaque retour au premier plan, jamais quand la victoire est en
     * cours d'affichage : `AlarmService` efface le drapeau de façon asynchrone
     * juste avant que cette activité ne soit relancée, et une lecture arrivée
     * trop tôt bloquerait l'utilisateur sur l'écran de reprise.
     */
    override fun onResume() {
        super.onResume()
        if (!victoire.value) alarmeEnCours.value = AlarmState.estEnCours(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        victoire.value = consommerVictoire(intent)
        if (victoire.value) alarmeEnCours.value = false
    }

    /**
     * Lit l'indicateur de victoire **et le retire de l'intent**. Sans cette
     * consommation, chaque recréation de l'activité — une rotation, un
     * changement de thème — relisait le même intent et ramenait l'écran de
     * victoire que l'utilisateur venait de fermer, indéfiniment.
     *
     * L'annonce n'est retenue que si elle vient de l'application elle-même.
     * Cette activité est exportée : sans vérification, n'importe quelle
     * application installée pouvait la lancer avec cet extra, afficher l'écran
     * de victoire et remettre [alarmeEnCours] à faux — masquant le seul chemin
     * visible vers `AlarmActivity` pendant que la sonnerie, elle, continuait.
     */
    private fun consommerVictoire(intent: Intent): Boolean {
        val victorieux = intent.getBooleanExtra(EXTRA_VICTOIRE, false)
        @Suppress("DEPRECATION")
        val jeton = intent.getParcelableExtra<PendingIntent>(EXTRA_JETON)
        intent.removeExtra(EXTRA_VICTOIRE)
        intent.removeExtra(EXTRA_JETON)
        return victoireLegitime(victorieux, jeton?.creatorPackage, packageName)
    }

    private fun demarrerAlarme() {
        startActivity(
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun ouvrirSelecteurHeure() {
        val config = viewModel.config.value
        TimePickerDialog(
            this,
            { _, heure, minute -> viewModel.majHeure(heure, minute) },
            config.hour,
            config.minute,
            true,
        ).show()
    }

    companion object {
        const val EXTRA_VICTOIRE = "com.cocorico.EXTRA_VICTOIRE"
        const val EXTRA_JETON = "com.cocorico.EXTRA_JETON"

        /**
         * Jeton d'origine, accompagnant [EXTRA_VICTOIRE].
         *
         * Un `PendingIntent` porte le paquet qui l'a fabriqué, et le système
         * seul le renseigne : une autre application ne peut pas en forger un
         * qui prétende venir de nous. C'est la seule preuve d'appelant qui
         * tienne ici — ni `referrer` (figé au lancement, donc faux quand
         * l'activité est déjà vivante et reçoit `onNewIntent`, le cas courant)
         * ni le paquet de l'intent ne prouvent quoi que ce soit.
         *
         * Il n'est jamais envoyé : c'est une pièce d'identité, pas une action.
         * D'où l'intent vide, et `FLAG_IMMUTABLE` — exigé depuis Android 12 et,
         * de toute façon, la seule forme acceptable pour un jeton qu'on
         * transmet.
         */
        fun jetonIdentite(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_JETON).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private const val ACTION_JETON = "com.cocorico.JETON_IDENTITE"
    }
}

/**
 * L'annonce de victoire est-elle recevable ?
 *
 * Décision isolée d'Android — donc vérifiable — parce qu'elle garde le seul
 * chemin visible vers l'écran qui arrête la sonnerie : la retenir sur un intent
 * forgé revient à cacher ce chemin pendant que l'alarme hurle.
 */
internal fun victoireLegitime(
    demandee: Boolean,
    paquetCreateurJeton: String?,
    paquetApplication: String,
): Boolean = demandee && paquetCreateurJeton == paquetApplication

/**
 * Passe avant tout le reste, onboarding compris : tant que la sonnerie tourne,
 * la seule chose qui compte est de pouvoir revenir au défi.
 */
@Composable
private fun EcranReprise(challengeId: ChallengeId, difficulty: Difficulty, onReprendre: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .zoneSure()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Le coq n'a pas fini.", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Ton alarme sonne toujours. " + texteDefiRestant(challengeId, difficulty),
            fontSize = 17.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Button(onClick = onReprendre, modifier = Modifier.fillMaxWidth()) {
            Text("Reprendre le défi", fontSize = 18.sp)
        }
    }
}

/**
 * Cet écran ne connaît pas le repli silencieux vers les calculs quand les
 * capteurs ou la caméra manquent (voir [PompesChallenge.capteurDisponible] et
 * `PhotoChallenge.camerasDisponibles`, testés côté `AlarmActivity`) : il ne
 * fait qu'annoncer le défi réglé, pas celui qui sonne effectivement. Rester
 * juste dans le cas courant — capteurs et caméra présents — vaut mieux que
 * mentir sur les trois.
 */
private fun texteDefiRestant(challengeId: ChallengeId, difficulty: Difficulty): String =
    when (challengeId) {
        ChallengeId.POMPES -> "${PompesChallenge.nombrePour(difficulty)} pompes et elle se tait."
        ChallengeId.PHOTO -> "${PhotoChallenge.nombrePour(difficulty)} photos et elle se tait."
        ChallengeId.MATHS -> "Trois calculs et elle se tait."
    }
