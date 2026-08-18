package com.cocorico.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocorico.alarm.AttenteSonnerie
import com.cocorico.challenge.pompes.PompesChallenge
import com.cocorico.data.ChallengeId
import com.cocorico.ring.CapteurPompes
import com.cocorico.ring.SonneriePersonnaliseeStore
import com.cocorico.ring.Sonneries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Le libellé s'exprime en minutes : le rebattre plus souvent ne changerait rien
 * à l'affichage et réveillerait le processeur pour rien. Assez court, en
 * revanche, pour que le passage à « Réveil imminent » suive la sonnerie de près.
 */
private const val INTERVALLE_RAFRAICHISSEMENT_MS = 20_000L

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOuvrirDefi: () -> Unit,
    onOuvrirSonnerie: () -> Unit,
    onOuvrirStats: () -> Unit,
    onChoisirHeure: () -> Unit,
) {
    val config by viewModel.config.collectAsState()
    val prochaine by viewModel.prochaine.collectAsState()
    val manquee by viewModel.manquee.collectAsState()

    // Le délai était calculé une seule fois, à la composition. Passé l'heure
    // prévue il devenait négatif et l'accueil annonçait « Réveil dans -2 min ».
    // On rebat l'instant courant régulièrement, et on redemande l'occurrence
    // dès qu'elle est périmée — c'est la replanification qui a la vérité, pas
    // cet écran.
    val contexte = LocalContext.current

    // Relu à chaque entrée sur l'accueil, et hors du fil principal.
    //
    // Deux défauts en un : la lecture (SharedPreferences, donc disque) se
    // faisait pendant la composition, et sa clé était `config.ringtoneId` —
    // or « Remplacer le fichier importé » garde le même identifiant. L'accueil
    // continuait donc d'annoncer le nom de l'ancien fichier, indéfiniment.
    // `LaunchedEffect(Unit)` relit à chaque entrée dans l'écran : revenir de
    // l'écran des sonneries en recompose la totalité, donc relance l'effet.
    var nomSonneriePersonnalisee by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        nomSonneriePersonnalisee = withContext(Dispatchers.IO) {
            SonneriePersonnaliseeStore.lireNom(contexte)
        }
    }

    // L'accueil annonçait le défi *choisi*, pas celui qui sonnera. Sans capteur,
    // sans permission caméra ou sans clé d'API, `AlarmActivity` se rabat sur les
    // calculs — et l'utilisateur ne l'apprenait qu'au réveil, devant la sirène.
    // Mêmes critères que `challengeEffectif`, qui décide pour de vrai : deux
    // listes de conditions séparées finiraient par diverger.
    val defiEffectif = remember(config.challengeId, config.cleApi) {
        challengeEffectif(
            challengeId = config.challengeId,
            capteurPompesDisponible = CapteurPompes(contexte) {}.capteurDisponible(),
            permissionCameraAccordee = PermissionChecker.etat(contexte).camera,
            camerasDisponibles = contexte.packageManager
                .hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) &&
                config.cleApi.isNotBlank(),
        )
    }

    // Le fuseau est relu à chaque composition plutôt que figé : un vol change
    // le fuseau du système, et un compte à rebours calculé sur l'ancien
    // afficherait un délai faux jusqu'au prochain redémarrage de l'écran.
    val fuseau: ZoneId = ZoneId.systemDefault()

    var maintenant by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            maintenant = LocalDateTime.now()
            delay(INTERVALLE_RAFRAICHISSEMENT_MS)
        }
    }
    LaunchedEffect(maintenant, prochaine) {
        if (CompteARebours.estPerimee(maintenant, prochaine, fuseau)) viewModel.rafraichirProchaine()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .zoneSure()
            // L'horloge occupe 68 sp. À grande taille de police, « Armer le
            // coq », dernier élément, sortait de l'écran : l'alarme ne pouvait
            // plus être armée du tout.
            // L'alignement en haut rend ce défilement invisible tant que le
            // contenu tient : rien ne bouge dans le cas courant.
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Cocorico", style = MaterialTheme.typography.titleLarge)

        // Au-dessus de tout le reste, et en couleur d'erreur : c'est la seule
        // chose de cet écran que l'utilisateur ne peut pas deviner autrement.
        manquee?.let { quand -> BandeauManquee(quand, viewModel::acquitterManquee) }

        Text(
            text = CompteARebours.libelle(maintenant, prochaine, fuseau, armee = config.armed),
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
            // Écart resserré : ce qu'il rend est repris par les cibles
            // tactiles, qui se partagent toute la largeur restante.
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
            // Une sonnerie importée porte le nom de son fichier, pas un
            // libellé générique : c'est la seule façon de savoir, depuis
            // l'accueil, laquelle est réellement armée pour demain matin.
            valeur = if (config.ringtoneId == Sonneries.ID_PERSONNALISEE) {
                nomSonneriePersonnalisee ?: Sonneries.parId(config.ringtoneId).nom
            } else {
                Sonneries.parId(config.ringtoneId).nom
            },
            onClick = onOuvrirSonnerie,
        )
        Ligne(
            titre = "Défi",
            // Même gabarit pour les trois variantes : le nom du défi, puis sa
            // difficulté. Pompes et photo ajoutent leur quantité, avec son
            // unité — un chiffre nu ne dit pas ce qu'il compte.
            valeur = when (config.challengeId) {
                ChallengeId.POMPES ->
                    "Pompes — ${PompesChallenge.nombrePour(config.difficulty)} répétitions, " +
                        config.difficulty.name.lowercase()
                // Pas de difficulté annoncée : elle ne s'applique pas à ce
                // défi, et l'afficher laisserait croire le contraire.
                ChallengeId.PHOTO -> "Photo — un objet à trouver"
                ChallengeId.MATHS -> "Maths — ${config.difficulty.name.lowercase()}"
                // Le détail des épreuves vit dans son propre écran : le résumer
                // ici tiendrait mal sur une ligne dès qu'il y en a trois.
                ChallengeId.COMBINE -> "Sur mesure — ${config.etapesCombine.size} épreuves"
            } + if (defiEffectif != config.challengeId) " · se rabattra sur les calculs" else "",
            onClick = onOuvrirDefi,
        )
        Ligne(
            titre = "Statistiques",
            valeur = "Le temps mis à faire taire le coq",
            onClick = onOuvrirStats,
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

/**
 * Un jour de la semaine, activable d'un appui.
 *
 * La cible tactile ne faisait que 38 dp de côté, sous le minimum utilisable :
 * armer le mardi coupait un doigt sur deux. Elle est ici découplée du disque
 * coloré — la cible occupe toute la cellule, haute d'au moins
 * [HAUTEUR_CIBLE_TACTILE], tandis que le disque garde sa taille de dessin.
 *
 * Sept cellules à 48 dp de large ne tiennent pas côte à côte sur un écran de
 * 360 dp : la largeur est donc partagée par poids (autour de 41 dp sur les plus
 * étroits, au-delà de 48 dp dès 400 dp d'écran) plutôt que fixée, ce qui aurait
 * poussé le dimanche hors de l'écran — un jour de la semaine impossible à
 * régler est pire qu'une cible un peu juste.
 *
 * Le disque, lui, grandit avec la police système au lieu de rogner la lettre :
 * sa taille est un minimum, pas une taille fixe.
 */
/**
 * Signale une sonnerie qui n'est jamais partie.
 *
 * Un réveil qui échoue en silence est le pire défaut de ce produit : sans ce
 * bandeau, l'utilisateur ne l'apprenait qu'en ne se réveillant pas. Il faut donc
 * l'acquitter à la main — le faire disparaître tout seul reviendrait à cacher
 * l'échec une deuxième fois.
 */
@Composable
private fun BandeauManquee(quand: LocalDateTime, onAcquitter: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = AttenteSonnerie.libelle(quand),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = "J'ai compris",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(onClick = onAcquitter)
                .wrapContentHeight(),
        )
    }
}

@Composable
private fun RowScope.PastilleJour(jour: DayOfWeek, actif: Boolean, onClick: () -> Unit) {
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
            .weight(1f)
            .heightIn(min = HAUTEUR_CIBLE_TACTILE)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = DIAMETRE_PASTILLE, minHeight = DIAMETRE_PASTILLE)
                .clip(CircleShape)
                .background(
                    if (actif) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = lettre,
                fontSize = 15.sp,
                maxLines = 1,
                color = if (actif) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                // Marge intérieure : c'est elle qui fait grandir le disque avec
                // la police plutôt que de laisser la lettre en déborder.
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Taille de dessin du disque d'un jour, hors cible tactile. */
private val DIAMETRE_PASTILLE = 38.dp

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
