package com.cocorico.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocorico.data.ChallengeId
import com.cocorico.data.CocoricoDatabase
import com.cocorico.data.ReveilRecent
import com.cocorico.data.Statistiques
import com.cocorico.data.StatsCalculator
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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

        Statistique(libelle = "Ce matin", valeur = valeurs.dureeCeMatinSecondes?.let(StatsCalculator::formatDuree) ?: "—")
        Statistique(libelle = "Temps moyen", valeur = valeurs.dureeMoyenneSecondes?.let(StatsCalculator::formatDuree) ?: "—")
        Statistique(libelle = "Meilleur temps", valeur = valeurs.meilleureDureeSecondes?.let(StatsCalculator::formatDuree) ?: "—")
        Statistique(libelle = "Pire temps", valeur = valeurs.pireDureeSecondes?.let(StatsCalculator::formatDuree) ?: "—")
        Statistique(libelle = "Temps cumulé", valeur = StatsCalculator.formatDuree(valeurs.dureeCumuleeSecondes))
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

        // Un seul réveil valide donne un graphique à une barre plutôt que de
        // disparaître : l'utilisateur qui vient d'ajouter son premier réveil
        // doit voir la section apparaître, pas se demander où elle est passée.
        // Zéro réveil valide (aucun encore, ou tous aberrants) la masque
        // entièrement : il n'y a alors rien de cohérent à comparer.
        if (valeurs.reveilsRecents.isNotEmpty()) {
            Text("Derniers réveils", fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            GraphiqueReveils(valeurs.reveilsRecents, valeurs.dureeMoyenneSecondes)
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
 * Graphique des derniers réveils : des `Box` de hauteur proportionnelle — le
 * projet s'interdit tout graphique compliqué — avec deux repères de lecture
 * (voir [StatsCalculator.echelle]) et une sélection tactile qui révèle le
 * détail d'un réveil. La bascule de la sélection est calculée par
 * [StatsCalculator.basculerSelection], pure et testée ; ce composable ne fait
 * que garder l'état et dessiner.
 */
@Composable
private fun GraphiqueReveils(reveils: List<ReveilRecent>, moyenneSecondes: Long?) {
    var rangSelectionne by remember { mutableStateOf<Int?>(null) }
    val echelle = remember(reveils, moyenneSecondes) {
        StatsCalculator.echelle(reveils.map { it.dureeSecondes }, moyenneSecondes)
    }
    val hauteurMax = 90.dp

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Max ${StatsCalculator.formatDuree(echelle.maxSecondes)}", fontSize = 15.sp)
            if (echelle.positionMoyenne != null && moyenneSecondes != null) {
                Text("Moyenne ${StatsCalculator.formatDuree(moyenneSecondes)}", fontSize = 15.sp)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(hauteurMax),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                reveils.forEachIndexed { rang, reveil ->
                    val proportion = (reveil.dureeSecondes.toFloat() / echelle.maxSecondes.toFloat())
                        .coerceIn(0.06f, 1f)
                    val selectionnee = rangSelectionne == rang
                    Column(
                        // La colonne entière, pas la seule barre, porte le clic : sur
                        // une barre à 6 % de hauteur, ne cibler que le rectangle
                        // visible laisserait une zone de clic minuscule.
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                rangSelectionne = StatsCalculator.basculerSelection(rangSelectionne, rang)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(hauteurMax * proportion)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    if (selectionnee) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                ),
                        )
                    }
                }
            }
            // Ligne de moyenne, dessinée après la rangée de barres donc
            // au-dessus : elle doit rester visible même quand elle traverse une
            // barre, c'est justement cette intersection qui est instructive.
            echelle.positionMoyenne?.let { position ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.BottomStart)
                        .padding(bottom = hauteurMax * position)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                )
            }
        }

        rangSelectionne?.let { rang -> reveils.getOrNull(rang)?.let { DetailReveil(it) } }
    }
}

private val formateurDateDetail: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)

/**
 * Fiche de détail révélée par un appui sur une barre du graphique : la date,
 * le temps mis, le défi accompli et si l'utilisateur y a renoncé — exactement
 * ce qu'une barre seule, sans étiquette, ne peut pas dire.
 */
@Composable
private fun DetailReveil(reveil: ReveilRecent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${nomJour(reveil.date.dayOfWeek)} ${formateurDateDetail.format(reveil.date)}",
            fontSize = 17.sp,
        )
        LigneDetailReveil("Temps mis", StatsCalculator.formatDuree(reveil.dureeSecondes))
        LigneDetailReveil("Défi", libelleDefi(reveil.defi))
        LigneDetailReveil("Renoncement", if (reveil.abandon) "Oui" else "Non")
    }
}

@Composable
private fun LigneDetailReveil(libelle: String, valeur: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(libelle, fontSize = 15.sp)
        Text(valeur, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
    }
}

private fun libelleDefi(defi: String): String = when (defi) {
    ChallengeId.POMPES.name -> "Pompes"
    ChallengeId.PHOTO.name -> "Photo"
    else -> "Maths"
}

private fun texteProgression(progressionSecondes: Long): String {
    val duree = StatsCalculator.formatDuree(abs(progressionSecondes))
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
