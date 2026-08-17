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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Ce que la base a répondu, ou rien. Un seul état pour les trois mesures :
 * elles viennent de la même lecture et n'ont aucun sens séparément.
 */
private data class BilanVictoire(
    val serie: Int,
    val retardSecondes: Int,
    val defiLibelle: String?,
)

@Composable
fun VictoryScreen(onFermer: () -> Unit) {
    val context = LocalContext.current
    // Nullable, comme dans [StatsScreen] : à zéro, l'écran annonçait
    // « Réveils d'affilée 0 » et « Retard moyen 0 s » le temps du chargement.
    // Des mesures fausses présentées comme vraies, au moment exact où
    // l'utilisateur vient de gagner sa série — c'est le pire endroit du produit
    // pour mentir sur un chiffre.
    var bilan by remember { mutableStateOf<BilanVictoire?>(null) }
    var echecLecture by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Une exception qui sort d'ici remonte au scope de composition et fait
        // planter l'application. Sur cet écran, elle arriverait juste après
        // l'écriture du `WakeRecord` : le réveil est réussi, l'alarme est
        // coupée, et l'application se ferme sur un plantage. Une base illisible
        // n'est pas une raison de perdre l'écran.
        val resultat = runCatching {
            // WakeRecordDao.tous() trie par alarmeAt croissant : le dernier
            // élément de la liste est donc bien le réveil le plus récent.
            val records = CocoricoDatabase.get(context).wakeRecordDao().tous()
            val zone = ZoneId.systemDefault()
            BilanVictoire(
                serie = SerieCalculator.serie(records, zone, LocalDate.now(zone)),
                retardSecondes = SerieCalculator.retardMoyenSecondes(records),
                defiLibelle = records.lastOrNull()?.let { dernier ->
                    when {
                        dernier.abandon -> "Calculs (renoncé)"
                        dernier.defi == ChallengeId.POMPES.name -> "Pompes"
                        dernier.defi == ChallengeId.PHOTO.name -> "Photo"
                        else -> "Calculs"
                    }
                },
            )
        }
        // `runCatching` avale aussi l'annulation : sans ce contrôle, fermer
        // l'écran pendant la lecture afficherait une panne de base imaginaire.
        currentCoroutineContext().ensureActive()
        resultat
            .onSuccess { bilan = it }
            .onFailure { echecLecture = true }
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
        // Le tiret est la même convention que [StatsScreen] pour « on ne sait
        // pas » : ni chargé, ni lisible.
        Statistique(libelle = "Réveils d'affilée", valeur = bilan?.let { "${it.serie}" } ?: "—")
        Statistique(
            libelle = "Retard moyen",
            valeur = bilan?.let { "${it.retardSecondes} s" } ?: "—",
        )
        bilan?.defiLibelle?.let { Statistique(libelle = "Défi", valeur = it) }
        if (echecLecture) {
            Text(
                text = "Tes statistiques n'ont pas pu être lues. Le réveil, lui, est " +
                    "bien enregistré comme réussi.",
                fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(onClick = onFermer, modifier = Modifier.fillMaxWidth()) {
            Text("Fermer", fontSize = 17.sp)
        }
    }
}

/**
 * Une ligne « libellé / valeur » dans la charte des cartes de statistiques.
 * Réutilisée par [StatsScreen] plutôt que clonée : même forme, même charte.
 *
 * Les deux textes se partagent la largeur par des poids. Sans eux, le libellé
 * était mesuré le premier et prenait ce qu'il voulait : la valeur, en 26 sp
 * monospace, se retrouvait sur les miettes et se repliait caractère par
 * caractère — « Sur les dix derniers matins » avec « 1 min 12 s plus vite qu'à
 * tes débuts » donnait une colonne d'une lettre de large. Le libellé, plus
 * petit et bien plus long, reçoit la plus grosse part ; la valeur garde de quoi
 * tenir sur une ligne dans le cas courant.
 */
@Composable
fun Statistique(libelle: String, valeur: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(libelle, fontSize = 15.sp, modifier = Modifier.weight(3f))
        Text(
            text = valeur,
            fontFamily = FontFamily.Monospace,
            fontSize = 26.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(2f),
        )
    }
}
