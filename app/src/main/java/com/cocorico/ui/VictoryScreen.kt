package com.cocorico.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocorico.data.ChallengeId
import com.cocorico.data.CocoricoDatabase
import com.cocorico.data.SerieCalculator
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun VictoryScreen(onFermer: () -> Unit) {
    val context = LocalContext.current
    var serie by remember { mutableStateOf(0) }
    var retard by remember { mutableStateOf(0) }
    var defiLibelle by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // WakeRecordDao.tous() trie par alarmeAt croissant : le dernier élément
        // de la liste est donc bien le réveil le plus récent.
        val records = CocoricoDatabase.get(context).wakeRecordDao().tous()
        serie = SerieCalculator.serie(records, ZoneId.systemDefault())
        retard = SerieCalculator.retardMoyenSecondes(records)
        defiLibelle = records.lastOrNull()?.let { dernier ->
            when {
                dernier.abandon -> "Calculs (renoncé)"
                dernier.defi == ChallengeId.POMPES.name -> "Pompes"
                else -> "Calculs"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .zoneSure()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Debout à ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Bien joué.",
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        // SerieCalculator.serie compte des jours consécutifs avec un réveil
        // abouti, rien d'autre : aucune triche n'est comptabilisée à ce jour.
        // Le libellé dit donc ce qui est réellement calculé.
        Statistique(libelle = "Réveils d'affilée", valeur = "$serie")
        Statistique(libelle = "Retard moyen", valeur = "$retard s")
        defiLibelle?.let { Statistique(libelle = "Défi", valeur = it) }
        Button(onClick = onFermer, modifier = Modifier.fillMaxWidth()) {
            Text("Fermer", fontSize = 17.sp)
        }
    }
}

/**
 * Une ligne « libellé / valeur » dans la charte des cartes de statistiques.
 * Réutilisée par [StatsScreen] plutôt que clonée : même forme, même charte.
 */
@Composable
fun Statistique(libelle: String, valeur: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(libelle, fontSize = 15.sp)
        Text(valeur, fontFamily = FontFamily.Monospace, fontSize = 26.sp)
    }
}
