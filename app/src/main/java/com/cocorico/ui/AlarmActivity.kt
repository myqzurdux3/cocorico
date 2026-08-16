package com.cocorico.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.cocorico.alarm.AlarmService
import com.cocorico.challenge.MathChallenge
import com.cocorico.challenge.MathChallengeEngine
import com.cocorico.challenge.MathProblemGenerator
import com.cocorico.data.AlarmConfigRepository
import com.cocorico.ring.HandDetector
import com.cocorico.ring.RingtonePlayer
import com.cocorico.ring.InactivityTracker
import com.cocorico.ring.VolumeStateMachine
import com.cocorico.ui.theme.CocoricoTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * L'écran plein sur lequel l'utilisateur se réveille. Il pilote le volume mais
 * n'arrête jamais la sonnerie lui-même : seul AlarmService le fait, et seulement
 * quand le défi est résolu.
 */
class AlarmActivity : ComponentActivity() {

    private lateinit var player: RingtonePlayer
    private lateinit var machine: VolumeStateMachine
    private lateinit var detector: HandDetector
    private val inactivite = InactivityTracker()

    private var defi: MathChallenge? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        player = RingtonePlayer(this)
        machine = VolumeStateMachine { player.appliquer(it) }
        detector = HandDetector(this) { machine.onPhonePrisEnMain() }
        detector.demarrer()

        lifecycleScope.launch {
            val config = AlarmConfigRepository(applicationContext).current()
            val engine = MathChallengeEngine(
                generator = MathProblemGenerator(),
                difficulty = config.difficulty,
            )
            val challenge = MathChallenge(engine) {
                inactivite.onInteraction(System.currentTimeMillis())
                machine.onInteraction()
            }
            defi = challenge

            setContent { CocoricoTheme(darkTheme = true) { Ecran(challenge) } }

            // Surveillance de l'inactivité : réveille le volume si l'utilisateur décroche.
            inactivite.onInteraction(System.currentTimeMillis())
            while (!challenge.isSolved.value) {
                delay(500)
                if (inactivite.isExpired(System.currentTimeMillis())) {
                    machine.onInactiviteExpiree()
                }
            }
            terminer()
        }
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

    /** Retour arrière inopérant tant que le défi n'est pas résolu. */
    override fun onBackPressed() {
        if (defi?.isSolved?.value == true) super.onBackPressed()
    }

    private fun terminer() {
        detector.arreter()
        AlarmService.arreter(this)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_VICTOIRE, true),
        )
        finish()
    }

    override fun onDestroy() {
        detector.arreter()
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
private fun Ecran(challenge: MathChallenge) {
    var defiOuvert by androidx.compose.runtime.remember { mutableStateOf(false) }
    val heure = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.error)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!defiOuvert) {
            Text(
                text = heure,
                fontFamily = FontFamily.Monospace,
                fontSize = 68.sp,
                color = MaterialTheme.colorScheme.onError,
            )
            Text(
                text = "Debout. Y'a pas de bouton.",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onError,
                textAlign = TextAlign.Center,
            )
            Button(onClick = { defiOuvert = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Faire taire ce coq", fontSize = 18.sp)
            }
            Text(
                text = "Prends le téléphone en main : le volume baisse tout seul.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onError,
                textAlign = TextAlign.Center,
            )
        } else {
            challenge.Content(Modifier.fillMaxWidth())
        }
    }
}
