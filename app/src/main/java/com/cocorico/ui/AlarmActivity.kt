package com.cocorico.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.cocorico.alarm.AlarmService
import com.cocorico.challenge.MathChallenge
import com.cocorico.challenge.MathChallengeEngine
import com.cocorico.challenge.MathProblemGenerator
import com.cocorico.data.AlarmConfig
import com.cocorico.data.AlarmConfigRepository
import com.cocorico.data.CocoricoDatabase
import com.cocorico.data.WakeRecord
import com.cocorico.ring.HandDetector
import com.cocorico.ring.RingtonePlayer
import com.cocorico.ring.InactivityTracker
import com.cocorico.ring.VolumeState
import com.cocorico.ring.VolumeStateMachine
import com.cocorico.ui.theme.CocoricoTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
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
    private val alarmeAt: Long = System.currentTimeMillis()

    /**
     * Reflets d'affichage, pas une seconde source de vérité : la machine à états
     * reste seule maîtresse du volume et [InactivityTracker] seul maître du
     * compte à rebours. On ne fait que recopier ce qu'ils décident déjà.
     */
    private val volumeAffiche = mutableStateOf(VolumeState.PLEIN)
    private val secondesAvantRemontee = mutableStateOf(SECONDES_INACTIVITE)

    private var defi: MathChallenge? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // `onBackPressed` n'est plus appelé quand l'application vise le SDK 35 :
        // le geste retour passe par l'OnBackPressedDispatcher. Sans ce rappel,
        // un retour arrière sur Android 15 fermerait l'écran et perdrait la
        // progression du défi, alarme toujours en train de sonner.
        retourNeutralise = onBackPressedDispatcher.addCallback(this) { }

        player = RingtonePlayer(this)
        machine = VolumeStateMachine {
            player.appliquer(it)
            volumeAffiche.value = it
        }
        detector = HandDetector(this) { machine.onPhonePrisEnMain() }
        detector.demarrer()

        lifecycleScope.launch {
            // Le défi doit s'afficher même si la persistance est cassée. Une
            // exception ici laissait un écran noir par-dessus le verrouillage,
            // sonnerie à fond et aucun moyen de l'arrêter.
            val config = runCatching { AlarmConfigRepository(applicationContext).current() }
                .getOrDefault(AlarmConfig.DEFAULT)
            val engine = MathChallengeEngine(
                generator = MathProblemGenerator(),
                difficulty = config.difficulty,
            )
            val challenge = MathChallenge(engine) {
                val maintenant = System.currentTimeMillis()
                inactivite.onInteraction(maintenant)
                machine.onInteraction()
                majCompteARebours(maintenant)
            }
            defi = challenge

            setContent {
                CocoricoTheme(darkTheme = true) {
                    EcranAlarme(
                        challenge = challenge,
                        volume = volumeAffiche.value,
                        secondes = secondesAvantRemontee.value,
                    )
                }
            }

            // Surveillance de l'inactivité : réveille le volume si l'utilisateur décroche.
            inactivite.onInteraction(System.currentTimeMillis())
            while (!challenge.isSolved.value) {
                delay(500)
                val maintenant = System.currentTimeMillis()
                if (inactivite.isExpired(maintenant)) {
                    machine.onInactiviteExpiree()
                }
                majCompteARebours(maintenant)
            }
            terminer()
        }
    }

    /** Secondes restantes avant la remontée du volume, arrondies au supérieur. */
    private fun majCompteARebours(maintenant: Long) {
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
        val erreurs = defi?.erreurs?.value ?: 0
        lifecycleScope.launch {
            withContext(NonCancellable) {
                runCatching {
                    CocoricoDatabase.get(applicationContext).wakeRecordDao().inserer(
                        WakeRecord(
                            alarmeAt = alarmeAt,
                            resoluAt = System.currentTimeMillis(),
                            erreurs = erreurs,
                            triches = 0,
                        ),
                    )
                }
                AlarmService.arreter(applicationContext)
            }
            startActivity(
                Intent(this@AlarmActivity, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_VICTOIRE, true),
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
 * L'écran est peint en `error` : sa `Surface` impose donc `onError` comme
 * couleur de contenu pour tout ce qu'il contient, défi compris. Sans elle, les
 * textes qui n'imposent pas leur couleur hériteraient de celle calibrée pour le
 * fond nuit — et le pavé numérique s'afficherait en noir.
 */
@Composable
private fun EcranAlarme(challenge: MathChallenge, volume: VolumeState, secondes: Int) {
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
            modifier = Modifier
                .fillMaxSize()
                .then(if (defiOuvert) Modifier.verticalScroll(defilement) else Modifier)
                .padding(24.dp),
            verticalArrangement = if (defiOuvert) {
                Arrangement.spacedBy(16.dp)
            } else {
                Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Jauge(volume = volume, secondes = secondes)

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
                    text = "Prends le téléphone en main : le volume baisse tout seul.",
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                challenge.Content(Modifier.fillMaxWidth())
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
private fun Jauge(volume: VolumeState, secondes: Int) {
    val urgent = volume == VolumeState.BAISSE && secondes <= SEUIL_URGENCE_S
    // Le décompte ne s'affiche qu'une fois le volume baissé : à fond, il n'y a
    // rien à décompter, seulement le contrat à rappeler.
    val avertissement = if (volume == VolumeState.PLEIN) {
        AVERTISSEMENT_REMONTEE
    } else {
        "Ça repart à fond dans %02d s.".format(secondes)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Volume — ${if (volume == VolumeState.PLEIN) 100 else 30} %",
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
