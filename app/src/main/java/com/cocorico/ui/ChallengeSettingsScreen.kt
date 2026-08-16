package com.cocorico.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocorico.data.ChallengeId
import com.cocorico.data.Difficulty

@Composable
fun ChallengeSettingsScreen(viewModel: HomeViewModel, onRetour: () -> Unit) {
    val config by viewModel.config.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .zoneSure()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("‹ Retour", fontSize = 16.sp, modifier = Modifier.clickable(onClick = onRetour))
        Text("Défi", style = MaterialTheme.typography.titleLarge)
        Text("Ce que tu devras faire pour la faire taire.", fontSize = 15.sp)

        Option(
            titre = "Calculs",
            detail = "3 opérations à résoudre",
            selectionne = config.challengeId == ChallengeId.MATHS,
            onClick = { viewModel.majDefi(ChallengeId.MATHS) },
        )
        Option(
            titre = "Pompes",
            detail = "10 répétitions comptées",
            selectionne = config.challengeId == ChallengeId.POMPES,
            onClick = { viewModel.majDefi(ChallengeId.POMPES) },
        )
        Option(titre = "Photo", detail = "Un objet précis, validé par l'IA", bientot = true)

        Text("Difficulté", fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { niveau ->
                Text(
                    text = niveau.name.lowercase().replaceFirstChar { it.uppercase() },
                    fontSize = 15.sp,
                    // Pastille sélectionnée : fond primaire, donc couleur de
                    // texte primaire. La couleur de contenu par défaut est
                    // calibrée pour le fond de l'écran, pas pour cette pastille.
                    color = if (niveau == config.difficulty) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (niveau == config.difficulty) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface,
                        )
                        .clickable { viewModel.majDifficulte(niveau) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun Option(
    titre: String,
    detail: String,
    selectionne: Boolean = false,
    bientot: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selectionne) 2.dp else 1.dp,
                color = if (selectionne) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        Text(if (bientot) "$titre — bientôt" else titre, fontSize = 17.sp)
        Text(detail, fontSize = 15.sp)
    }
}

@Composable
fun RingtoneScreen(viewModel: HomeViewModel, onRetour: () -> Unit) {
    val config by viewModel.config.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .zoneSure()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("‹ Retour", fontSize = 16.sp, modifier = Modifier.clickable(onClick = onRetour))
        Text("Sonnerie", style = MaterialTheme.typography.titleLarge)
        Text("De la moins violente à la pire.", fontSize = 15.sp)

        com.cocorico.ring.Sonneries.toutes.forEach { sonnerie ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (sonnerie.id == config.ringtoneId) 2.dp else 1.dp,
                        color = if (sonnerie.id == config.ringtoneId)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { viewModel.majSonnerie(sonnerie.id) }
                    .padding(14.dp),
            ) {
                Text(sonnerie.nom, fontSize = 17.sp)
            }
        }
    }
}
