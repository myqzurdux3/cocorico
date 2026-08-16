package com.cocorico.challenge.pompes

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocorico.challenge.Challenge
import com.cocorico.challenge.ChallengeProgress
import com.cocorico.data.ChallengeId
import com.cocorico.data.Difficulty
import com.cocorico.ring.CapteurPompes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Défi pompes. Le compteur décide, cette classe ne fait que relier les capteurs
 * à l'écran et signaler les répétitions à l'activité.
 *
 * [onRenoncer] bascule sur le défi maths. Le bouton est immédiat et sans délai,
 * mais le renoncement est enregistré dans l'historique.
 */
class PompesChallenge(
    context: Context,
    difficulty: Difficulty,
    private val onInteraction: () -> Unit,
    private val onRenoncer: () -> Unit,
) : Challenge {

    private val total = nombrePour(difficulty)
    private val compteur = CompteurPompes(total)

    private val capteur = CapteurPompes(context) { echantillon ->
        // Une répétition comptée vaut une interaction : elle réarme le compte à
        // rebours, exactement comme une frappe sur le pavé numérique. Un simple
        // échantillon de capteur, lui, ne réarme rien.
        if (compteur.onEchantillon(echantillon)) onInteraction()
    }

    private val _progress = MutableStateFlow(ChallengeProgress(done = 0, total = total))
    override val progress: StateFlow<ChallengeProgress> = _progress.asStateFlow()

    override val id = ChallengeId.POMPES
    override val isSolved: StateFlow<Boolean> = compteur.isSolved

    override fun onUserInteraction() = onInteraction()

    /**
     * Exposé pour que l'appelant refuse le défi pompes sur un téléphone sans
     * capteur de proximité : sans lui, l'alarme deviendrait inarrêtable.
     */
    val capteurDisponible: Boolean get() = capteur.capteurDisponible()

    @Composable
    override fun Content(modifier: Modifier) {
        val comptees by compteur.comptees.collectAsState()
        val etat by compteur.etat.collectAsState()
        _progress.value = ChallengeProgress(done = comptees, total = total)

        // Libère les capteurs quand le composable quitte la composition : sans
        // ce ménage, ils continuent de tourner après la fin du défi et vident
        // la batterie.
        DisposableEffect(Unit) {
            capteur.demarrer()
            onDispose { capteur.arreter() }
        }

        Column(
            modifier = modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "$comptees / $total",
                color = MaterialTheme.colorScheme.onError,
                fontFamily = FontFamily.Monospace,
                fontSize = 64.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            LinearProgressIndicator(
                progress = { comptees.toFloat() / total },
                color = MaterialTheme.colorScheme.onError,
                trackColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = consigne(etat, comptees, total),
                color = MaterialTheme.colorScheme.onError,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Je ne peux pas",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        onInteraction()
                        onRenoncer()
                    }
                    .padding(vertical = 14.dp),
            )
        }
    }

    private fun consigne(etat: EtatPompes, comptees: Int, total: Int): String = when {
        comptees == total - 1 && etat != EtatPompes.ATTENTE_POSITION -> "Encore une"
        etat == EtatPompes.ATTENTE_POSITION -> "Pose le téléphone au sol, écran vers le haut"
        etat == EtatPompes.PRET -> "Descends"
        else -> "Remonte"
    }

    companion object {
        fun nombrePour(difficulty: Difficulty): Int = when (difficulty) {
            Difficulty.FACILE -> 5
            Difficulty.MOYEN -> 10
            Difficulty.DIFFICILE -> 20
        }
    }
}
