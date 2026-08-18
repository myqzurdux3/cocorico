package com.cocorico.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocorico.challenge.photo.CatalogueObjets
import com.cocorico.challenge.photo.Piece
import com.cocorico.challenge.photo.SelectionObjets

/**
 * Écran de sélection des objets du défi photo : une pièce par section,
 * repliée par défaut, chaque objet cochable individuellement et chaque pièce
 * cochable ou décochable d'un geste.
 *
 * Cet écran affiche la sélection **telle qu'elle est**, y compris entièrement
 * vide si l'utilisateur a tout décoché : voir la KDoc de
 * [SelectionObjets] sur la distinction entre ce que montre cet écran et le
 * repli d'exécution de [CatalogueObjets.tirer]. Le rôle de cet écran est donc
 * seulement d'avertir sans jamais bloquer — décocher est toujours permis,
 * même jusqu'au dernier objet.
 */
@Composable
fun SelectionObjetsScreen(viewModel: HomeViewModel, onRetour: () -> Unit) {
    val config by viewModel.config.collectAsState()
    val selection = config.objetsSelectionnes

    val comptage = remember(selection) { SelectionObjets.compterParPiece(selection) }
    val total = remember(selection) { SelectionObjets.totalCoche(selection) }

    // Repliées par défaut : le catalogue compte plusieurs dizaines d'objets,
    // et un écran qui les afficherait tous en même temps serait un mur de
    // texte impossible à parcourir.
    var pieceDepliees by remember { mutableStateOf(emptySet<Piece>()) }

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
        Text("Objets à photographier", style = MaterialTheme.typography.titleLarge)
        Text(
            "Le réveil ne tirera un objet que parmi ceux cochés ici. Déplie une " +
                "pièce pour voir ses objets, coche-la entière d'un geste ou objet par " +
                "objet.",
            fontSize = 15.sp,
        )

        // Averti sans jamais bloquer : voir la KDoc de la fonction. Un texte
        // rouge pour l'absence totale de sélection, plus grave qu'une
        // sélection simplement en dessous du seuil de la difficulté la plus
        // exigeante.
        when {
            total == 0 -> Text(
                text = "Aucun objet coché : le prochain réveil piochera dans tout le " +
                    "catalogue pour ne jamais te laisser bloqué devant l'alarme.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.error,
            )
            total < SelectionObjets.SEUIL_AVERTISSEMENT -> Text(
                text = "Moins de ${SelectionObjets.SEUIL_AVERTISSEMENT} objets cochés : à la " +
                    "difficulté la plus élevée, le tirage complétera avec des objets non " +
                    "cochés plutôt que d'en proposer moins que prévu.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }

        comptage.forEach { compte ->
            PieceSelectionnable(
                compte = compte,
                depliee = compte.piece in pieceDepliees,
                onDeplier = {
                    pieceDepliees = if (compte.piece in pieceDepliees) {
                        pieceDepliees - compte.piece
                    } else {
                        pieceDepliees + compte.piece
                    }
                },
                onBasculerPiece = { viewModel.basculerPiece(compte.piece) },
                selection = selection,
                onBasculerObjet = viewModel::basculerObjet,
            )
        }
    }
}

@Composable
private fun PieceSelectionnable(
    compte: SelectionObjets.ComptagePiece,
    depliee: Boolean,
    onDeplier: () -> Unit,
    onBasculerPiece: () -> Unit,
    selection: Set<String>,
    onBasculerObjet: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDeplier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = (if (depliee) "▾ " else "▸ ") + compte.piece.nom,
                    fontSize = 17.sp,
                )
                Text("${compte.coches} / ${compte.total} coché(s)", fontSize = 15.sp)
            }
            // Indéterminé quand la pièce est partiellement cochée : cocher ou
            // décocher tout d'un geste doit rester possible sans avoir à
            // deviner l'état exact de chacun de ses objets.
            TriStateCheckbox(
                state = when {
                    compte.coches == 0 -> ToggleableState.Off
                    compte.toutCoche -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                },
                onClick = onBasculerPiece,
            )
        }
        if (depliee) {
            CatalogueObjets.tous.filter { it.piece == compte.piece }.forEach { objet ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBasculerObjet(objet.id) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(objet.nom, fontSize = 15.sp)
                    Checkbox(
                        checked = objet.id in selection,
                        onCheckedChange = { onBasculerObjet(objet.id) },
                    )
                }
            }
        }
    }
}
