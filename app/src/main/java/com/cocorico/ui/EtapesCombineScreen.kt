package com.cocorico.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocorico.challenge.combine.EtapeCombine
import com.cocorico.challenge.combine.EtapesCombine
import com.cocorico.data.ChallengeId

/**
 * Composition du défi sur mesure : quelles épreuves, combien de chacune, et
 * dans quel ordre.
 *
 * L'ordre des lignes **est** l'ordre du matin — c'est le réglage principal de
 * cet écran, pas une commodité d'affichage. Il se change avec des flèches
 * plutôt qu'au glisser-déposer : le réordonnancement par glissement demande une
 * gestion d'état que rien ici ne pourrait vérifier sans appareil, et une flèche
 * se touche aussi bien à six heures du matin.
 *
 * Un compteur à zéro retire l'épreuve. Tout à zéro n'est pas empêché — on
 * n'empêche pas un geste — mais l'écran dit alors ce que le réveil demandera
 * réellement : [EtapesCombine.assainir] se replie sur des calculs, sans quoi
 * l'alarme s'arrêterait sans rien demander.
 */
@Composable
fun EtapesCombineScreen(viewModel: HomeViewModel, onRetour: () -> Unit) {
    val config by viewModel.config.collectAsState()
    val etapes = config.etapesCombine

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .zoneSure()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BoutonRetour(onRetour)
        Text("Sur mesure", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Les épreuves s'enchaînent dans cet ordre. À zéro, l'épreuve " +
                "est retirée.",
            fontSize = 15.sp,
        )

        // Les types absents de la liste sont affichés à zéro, à la suite : sans
        // ça, une épreuve retirée disparaîtrait de l'écran et deviendrait
        // impossible à remettre.
        val affichees = etapes + EtapesCombine.TYPES
            .filterNot { type -> etapes.any { it.type == type } }
            .map { EtapeCombine(it, 0) }

        affichees.forEachIndexed { index, etape ->
            LigneEtape(
                etape = etape,
                peutMonter = index > 0 && etape.nombre > 0,
                peutDescendre = index < etapes.size - 1 && etape.nombre > 0,
                onNombre = { nouveau ->
                    viewModel.majEtapesCombine(
                        affichees.toMutableList()
                            .also { it[index] = etape.copy(nombre = nouveau) },
                    )
                },
                onMonter = { viewModel.majEtapesCombine(EtapesCombine.monter(affichees, index)) },
                onDescendre = { viewModel.majEtapesCombine(EtapesCombine.descendre(affichees, index)) },
            )
        }

        if (etapes == EtapesCombine.REPLI && affichees.none { it.nombre > 0 }) {
            Text(
                text = "Aucune épreuve : le réveil demandera trois calculs. " +
                    "Une alarme qui ne demande rien n'en est plus une.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LigneEtape(
    etape: EtapeCombine,
    peutMonter: Boolean,
    peutDescendre: Boolean,
    onNombre: (Int) -> Unit,
    onMonter: () -> Unit,
    onDescendre: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = nom(etape.type),
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )

        Pastille("−", actif = etape.nombre > 0) {
            onNombre((etape.nombre - 1).coerceAtLeast(0))
        }
        Text(
            text = "${etape.nombre}",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.size(width = 34.dp, height = 24.dp),
        )
        Pastille("+", actif = etape.nombre < EtapesCombine.NOMBRE_MAX) {
            onNombre((etape.nombre + 1).coerceAtMost(EtapesCombine.NOMBRE_MAX))
        }

        Pastille("↑", actif = peutMonter, onClick = onMonter)
        Pastille("↓", actif = peutDescendre, onClick = onDescendre)
    }
}

/**
 * Cible tactile d'au moins 48 dp, comme partout ailleurs depuis la revue
 * d'accessibilité : ces boutons sont petits à l'œil, ils ne doivent pas l'être
 * au doigt.
 */
@Composable
private fun Pastille(symbole: String, actif: Boolean, onClick: () -> Unit) {
    Text(
        text = symbole,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = if (actif) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        },
        modifier = Modifier
            .heightIn(min = 48.dp)
            .size(48.dp)
            .then(if (actif) Modifier.clickable(onClick = onClick) else Modifier)
            .wrapContentSize(Alignment.Center),
    )
}

private fun nom(type: ChallengeId): String = when (type) {
    ChallengeId.MATHS -> "Calculs"
    ChallengeId.POMPES -> "Pompes"
    ChallengeId.PHOTO -> "Photos"
    // Jamais proposé : `EtapesCombine.TYPES` ne le contient pas.
    ChallengeId.COMBINE -> "Sur mesure"
}
