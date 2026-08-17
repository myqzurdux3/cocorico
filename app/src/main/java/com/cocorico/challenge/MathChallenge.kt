package com.cocorico.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocorico.data.ChallengeId
import kotlinx.coroutines.flow.StateFlow

class MathChallenge(
    private val engine: MathChallengeEngine,
    private val onInteraction: () -> Unit,
) : Challenge {

    override val id = ChallengeId.MATHS
    override val isSolved: StateFlow<Boolean> = engine.isSolved

    /** Exposé pour l'enregistrement du réveil dans l'historique. */
    val erreurs: StateFlow<Int> = engine.erreurs

    @Composable
    override fun Content(modifier: Modifier) {
        val probleme by engine.current.collectAsState()
        val avancement by engine.progress.collectAsState()
        var saisie by remember { mutableStateOf("") }
        var faux by remember { mutableStateOf(false) }

        Column(
            modifier = modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Calcul ${avancement.done + 1} / ${avancement.total}",
                style = MaterialTheme.typography.titleLarge,
            )
            LinearProgressIndicator(
                progress = { avancement.done.toFloat() / avancement.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${probleme.prompt} = ?",
                fontFamily = FontFamily.Monospace,
                fontSize = 40.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = saisie.ifEmpty { "—" },
                fontFamily = FontFamily.Monospace,
                fontSize = 34.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            if (faux) {
                // Le défi s'affiche par-dessus l'écran d'alarme, qui est peint en
                // `error`. Un message en `error` y serait rouge sur rouge : on le
                // pose sur une pastille sombre, en couleur d'accent.
                Text(
                    text = "Non. Et le coq a entendu.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Pave(
                onChiffre = { chiffre ->
                    onInteraction()
                    faux = false
                    if (saisie.length < 6) saisie += chiffre
                },
                onEffacer = {
                    onInteraction()
                    faux = false
                    saisie = saisie.dropLast(1)
                },
                onValider = {
                    onInteraction()
                    val valeur = saisie.toIntOrNull()
                    if (valeur != null) {
                        // Lu avant la soumission : une fois le défi résolu, le moteur
                        // ignore les envois suivants et rend false — ce qui n'est pas
                        // une faute. Voir MathChallengeEngine.estUneFaute.
                        val dejaResolu = engine.isSolved.value
                        faux = MathChallengeEngine.estUneFaute(dejaResolu, engine.submit(valeur))
                        saisie = ""
                    }
                },
            )
        }
    }
}

@Composable
private fun Pave(
    onChiffre: (String) -> Unit,
    onEffacer: () -> Unit,
    onValider: () -> Unit,
) {
    val lignes = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("⌫", "0", "✓"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lignes.forEach { ligne ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ligne.forEach { touche ->
                    Touche(
                        libelle = touche,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (touche) {
                                "⌫" -> onEffacer()
                                "✓" -> onValider()
                                else -> onChiffre(touche)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Touche(libelle: String, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text = libelle,
        fontFamily = FontFamily.Monospace,
        fontSize = 26.sp,
        // Touche posée sur `surface` : sans couleur explicite, elle hériterait de
        // celle calibrée pour le fond de l'écran.
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}
