package com.cocorico.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocorico.challenge.pompes.PompesChallenge
import com.cocorico.data.ChallengeId
import com.cocorico.ring.Sonneries
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOuvrirDefi: () -> Unit,
    onOuvrirSonnerie: () -> Unit,
    onChoisirHeure: () -> Unit,
) {
    val config by viewModel.config.collectAsState()
    val prochaine by viewModel.prochaine.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .zoneSure()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Cocorico", style = MaterialTheme.typography.titleLarge)

        Text(
            text = prochaine?.let { "Réveil dans ${delaiLisible(it)}" }
                ?: "Aucun jour actif. Le coq dort.",
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Text(
            text = "%02d:%02d".format(config.hour, config.minute),
            fontFamily = FontFamily.Monospace,
            fontSize = 68.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onChoisirHeure),
            textAlign = TextAlign.Center,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            DayOfWeek.entries.forEach { jour ->
                PastilleJour(
                    jour = jour,
                    actif = jour in config.days,
                    onClick = { viewModel.basculerJour(jour) },
                )
            }
        }

        Ligne(
            titre = "Sonnerie",
            valeur = Sonneries.parId(config.ringtoneId).nom,
            onClick = onOuvrirSonnerie,
        )
        Ligne(
            titre = "Défi",
            valeur = if (config.challengeId == ChallengeId.POMPES) {
                "Pompes — ${PompesChallenge.nombrePour(config.difficulty)}"
            } else {
                "Maths — ${config.difficulty.name.lowercase()}"
            },
            onClick = onOuvrirDefi,
        )

        Button(
            onClick = { viewModel.armer(!config.armed) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (config.armed) "Désarmer" else "Armer le coq",
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun PastilleJour(jour: DayOfWeek, actif: Boolean, onClick: () -> Unit) {
    val lettre = when (jour) {
        DayOfWeek.MONDAY -> "L"
        DayOfWeek.TUESDAY -> "M"
        DayOfWeek.WEDNESDAY -> "M"
        DayOfWeek.THURSDAY -> "J"
        DayOfWeek.FRIDAY -> "V"
        DayOfWeek.SATURDAY -> "S"
        DayOfWeek.SUNDAY -> "D"
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(
                if (actif) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = lettre,
            fontSize = 15.sp,
            color = if (actif) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Ligne(titre: String, valeur: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(titre, fontSize = 15.sp)
        Text(valeur, fontSize = 17.sp)
    }
}

private fun delaiLisible(cible: LocalDateTime): String {
    val duree = Duration.between(LocalDateTime.now(), cible)
    val heures = duree.toHours()
    val minutes = duree.toMinutes() % 60
    return if (heures > 0) "$heures h $minutes min" else "$minutes min"
}
