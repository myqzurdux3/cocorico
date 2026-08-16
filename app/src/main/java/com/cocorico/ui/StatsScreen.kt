package com.cocorico.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocorico.data.CocoricoDatabase
import com.cocorico.data.Statistiques
import com.cocorico.data.StatsCalculator
import java.time.DayOfWeek
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun StatsScreen(onRetour: () -> Unit) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<Statistiques?>(null) }

    LaunchedEffect(Unit) {
        val records = CocoricoDatabase.get(context).wakeRecordDao().tous()
        stats = StatsCalculator.calculer(records, ZoneId.systemDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .zoneSure()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("‹ Retour", fontSize = 16.sp, modifier = Modifier.clickable(onClick = onRetour))
        Text("Statistiques", style = MaterialTheme.typography.titleLarge)

        val valeurs = stats
        if (valeurs == null) {
            // Chargement en cours : rien à montrer de faux en attendant la base.
            return@Column
        }
        if (valeurs.nombreTotal == 0) {
            ContenuVide()
            return@Column
        }

        Text(
            text = "Le temps qu'il t'a fallu pour faire taire ce coq, matin après matin.",
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth(),
        )

        Statistique(libelle = "Ce matin", valeur = valeurs.dureeCeMatinSecondes?.let(::formatDuree) ?: "—")
        Statistique(libelle = "Temps moyen", valeur = valeurs.dureeMoyenneSecondes?.let(::formatDuree) ?: "—")
        Statistique(libelle = "Meilleur temps", valeur = valeurs.meilleureDureeSecondes?.let(::formatDuree) ?: "—")
        Statistique(libelle = "Pire temps", valeur = valeurs.pireDureeSecondes?.let(::formatDuree) ?: "—")
        Statistique(libelle = "Temps cumulé", valeur = formatDuree(valeurs.dureeCumuleeSecondes))
        Statistique(libelle = "Réveils affrontés", valeur = "${valeurs.nombreTotal}")

        valeurs.tauxAbandonPompes?.let { taux ->
            Statistique(libelle = "Pompes abandonnées", valeur = "${(taux * 100).roundToInt()} %")
        }
        if (valeurs.erreursCumulees > 0) {
            Statistique(libelle = "Fautes de calcul", valeur = "${valeurs.erreursCumulees}")
        }
        valeurs.jourLePlusLent?.let { jour ->
            Statistique(libelle = "Jour le plus lent", valeur = nomJour(jour))
        }
        valeurs.progressionSecondes?.let { progression ->
            Statistique(
                libelle = "Sur les dix derniers matins",
                valeur = texteProgression(progression),
            )
        }

        if (valeurs.dureesRecentesSecondes.size > 1) {
            Text("Derniers réveils", fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            RangeeBarres(valeurs.dureesRecentesSecondes)
        }
    }
}

@Composable
private fun ContenuVide() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Aucune statistique. Le coq n'a encore rien à raconter.",
            fontSize = 17.sp,
        )
        Text(
            text = "Arme l'alarme, affronte le défi, et reviens voir combien de temps ça t'a pris.",
            fontSize = 15.sp,
        )
    }
}

/**
 * Rangée de barres dessinées avec des `Box` de hauteur proportionnelle — le
 * projet s'interdit tout graphique compliqué, et une rangée de rectangles
 * suffit à montrer si les matins récents s'améliorent ou empirent.
 */
@Composable
private fun RangeeBarres(dureesSecondes: List<Long>) {
    val maxDuree = (dureesSecondes.maxOrNull() ?: 1L).coerceAtLeast(1L)
    val hauteurMax = 90.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(hauteurMax),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        dureesSecondes.forEach { duree ->
            val proportion = (duree.coerceAtLeast(0L).toFloat() / maxDuree.toFloat()).coerceIn(0.06f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(hauteurMax * proportion)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/**
 * Sur ce format `Xh`, `Xmin` ou `Xs`, le plus grand cas d'usage — le temps
 * cumulé — reste lisible : afficher un temps cumulé de plusieurs milliers de
 * secondes brutes serait illisible, quand personne ne songerait à afficher un
 * temps de résolution du matin autrement qu'en secondes. Une seule fonction
 * couvre les deux, sans jamais perdre le signe pour les écarts négatifs de
 * [texteProgression].
 */
private fun formatDuree(secondes: Long): String {
    val signe = if (secondes < 0) "-" else ""
    val valeurAbsolue = abs(secondes)
    val heures = valeurAbsolue / 3600
    val minutes = (valeurAbsolue % 3600) / 60
    val restantSecondes = valeurAbsolue % 60
    return when {
        heures > 0 -> "$signe${heures}h${"%02d".format(minutes)}"
        minutes > 0 -> "$signe${minutes}min${"%02d".format(restantSecondes)}"
        else -> "$signe${valeurAbsolue}s"
    }
}

private fun texteProgression(progressionSecondes: Long): String {
    val duree = formatDuree(abs(progressionSecondes))
    return when {
        progressionSecondes < 0 -> "$duree plus vite qu'à tes débuts"
        progressionSecondes > 0 -> "$duree plus lent qu'à tes débuts"
        else -> "Aucun changement"
    }
}

private fun nomJour(jour: DayOfWeek): String = when (jour) {
    DayOfWeek.MONDAY -> "Lundi"
    DayOfWeek.TUESDAY -> "Mardi"
    DayOfWeek.WEDNESDAY -> "Mercredi"
    DayOfWeek.THURSDAY -> "Jeudi"
    DayOfWeek.FRIDAY -> "Vendredi"
    DayOfWeek.SATURDAY -> "Samedi"
    DayOfWeek.SUNDAY -> "Dimanche"
}
