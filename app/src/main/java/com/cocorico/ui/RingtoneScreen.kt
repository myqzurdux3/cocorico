package com.cocorico.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Slider
import androidx.compose.ui.text.font.FontFamily
import kotlin.math.roundToInt
import com.cocorico.ring.ApercuSonnerie
import com.cocorico.ring.NiveauxVolume
import com.cocorico.ring.SonneriePersonnaliseeLogique
import com.cocorico.ring.SonneriePersonnaliseeStore
import com.cocorico.ring.Sonneries
import com.cocorico.ring.SondeSonnerie

/**
 * Déplacé hors de `ChallengeSettingsScreen.kt` : cet écran a grandi avec
 * l'import d'une sonnerie personnelle, et n'a plus grand-chose à voir avec
 * les réglages de défi qui restent dans l'autre fichier.
 */
@Composable
fun RingtoneScreen(viewModel: HomeViewModel, onRetour: () -> Unit) {
    val config by viewModel.config.collectAsState()

    // L'extrait est coupé à la sortie de l'écran : sans ça, il continuerait de
    // jouer par-dessus l'accueil, sans plus personne pour l'arrêter.
    val context = LocalContext.current
    val apercu = remember { ApercuSonnerie(context) }
    DisposableEffect(Unit) { onDispose { apercu.arreter() } }

    // Lu depuis le magasin dédié plutôt que depuis `config` : la sonnerie
    // personnalisée n'est pas un champ d'`AlarmConfig`, voir la KDoc de
    // `SonneriePersonnaliseeStore` pour la raison.
    //
    // Chargé par un effet, jamais dans l'initialiseur d'un `remember` : ce sont
    // deux lectures SharedPreferences, donc deux accès disque, et l'initialiseur
    // s'exécute sur le fil principal au milieu de la composition. Le temps du
    // chargement la ligne annonce « Importer ma sonnerie… », ce qu'elle
    // annonçait déjà pour quelqu'un qui n'a jamais importé de fichier.
    var uriPersonnalisee by remember { mutableStateOf<String?>(null) }
    var nomPersonnalisee by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val (uri, nom) = withContext(Dispatchers.IO) {
            SonneriePersonnaliseeStore.lireUri(context) to SonneriePersonnaliseeStore.lireNom(context)
        }
        uriPersonnalisee = uri
        nomPersonnalisee = nom
    }
    var erreurImport by remember { mutableStateOf<String?>(null) }
    var verificationEnCours by remember { mutableStateOf(false) }
    val portee = rememberCoroutineScope()

    // ACTION_OPEN_DOCUMENT plutôt que GET_CONTENT : lui seul autorise
    // `takePersistableUriPermission`, indispensable pour que la sonnerie soit
    // encore lisible après un redémarrage du téléphone, à des heures de
    // l'alarme du matin.
    val lanceurImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        // Sans permission persistante, l'accès expire à la fin de cette
        // session : la sonnerie sonnerait aujourd'hui et se tairait au
        // prochain redémarrage. Un fournisseur qui refuse cette permission
        // rend le fichier inutilisable ici, pas seulement plus tard.
        val permissionAccordee = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess

        // La sonde prépare réellement le média et interroge le fournisseur :
        // sur un fichier servi par un fournisseur distant, la faire sur le fil
        // principal gèle l'écran jusqu'à l'ANR. Seules les mises à jour d'état
        // reviennent sur le fil principal, à la reprise de la coroutine.
        verificationEnCours = true
        portee.launch {
            val lisible = permissionAccordee && withContext(Dispatchers.IO) {
                SondeSonnerie.estLisible(context, uri)
            }
            if (lisible) {
                val nom = withContext(Dispatchers.IO) {
                    SonneriePersonnaliseeLogique.nomAffichable(
                        uri.toString(),
                        SondeSonnerie.nomInterroge(context, uri),
                    )
                }
                withContext(Dispatchers.IO) {
                    SonneriePersonnaliseeStore.ecrire(context, uri.toString(), nom)
                }
                uriPersonnalisee = uri.toString()
                nomPersonnalisee = nom
                erreurImport = null
                viewModel.majSonnerie(Sonneries.ID_PERSONNALISEE)
                if (!apercu.jouer(uri)) erreurImport = ECHEC_APERCU
            } else {
                erreurImport = "Ce fichier ne peut pas servir de sonnerie : format illisible, " +
                    "fichier corrompu ou accès refusé. Choisis-en un autre."
            }
            verificationEnCours = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .zoneSure()
            // Curseur de volume, quatre sonneries, ligne personnalisée, lien de
            // remplacement et message d'erreur d'import : le bas de l'écran,
            // message d'erreur compris, devenait inatteignable.
            // L'alignement en haut rend ce défilement invisible tant que le
            // contenu tient : rien ne bouge dans le cas courant.
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BoutonRetour(onRetour)
        Text("Sonnerie", style = MaterialTheme.typography.titleLarge)
        Text("De la moins violente à la pire.", fontSize = 15.sp)

        ReglageVolumeMax(
            pourcent = config.volumeMaxPourcent,
            onChange = viewModel::majVolumeMax,
        )

        Sonneries.toutes.forEach { sonnerie ->
            LigneSonnerie(
                nom = sonnerie.nom,
                choisie = sonnerie.id == config.ringtoneId,
                onClick = {
                    viewModel.majSonnerie(sonnerie.id)
                    if (!apercu.jouer(sonnerie)) erreurImport = ECHEC_APERCU
                },
            )
        }

        // La ligne personnalisée se comporte comme les autres dès qu'un
        // fichier a été importé : sélectionnable et rejouable pareil. Avant
        // le premier import, elle ouvre directement le sélecteur.
        LigneSonnerie(
            nom = nomPersonnalisee ?: "Importer ma sonnerie…",
            choisie = config.ringtoneId == Sonneries.ID_PERSONNALISEE,
            onClick = {
                val uriTexte = uriPersonnalisee
                if (uriTexte != null) {
                    viewModel.majSonnerie(Sonneries.ID_PERSONNALISEE)
                    if (!apercu.jouer(Uri.parse(uriTexte))) erreurImport = ECHEC_APERCU
                } else {
                    lanceurImport.launch(arrayOf("audio/*"))
                }
            },
        )
        if (uriPersonnalisee != null) {
            Text(
                text = "Remplacer le fichier importé",
                fontSize = 13.sp,
                modifier = Modifier.clickable { lanceurImport.launch(arrayOf("audio/*")) },
            )
        }
        // La vérification est passée hors du fil principal : sans ce repère,
        // l'écran resterait figé en apparence sur un fichier lent à sonder, et
        // l'utilisateur appuierait à nouveau.
        if (verificationEnCours) {
            Text("Vérification du fichier…", fontSize = 13.sp)
        }

        if (erreurImport != null) {
            Text(
                text = erreurImport ?: "",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LigneSonnerie(nom: String, choisie: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (choisie) 2.dp else 1.dp,
                color = if (choisie) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(nom, fontSize = 17.sp)
        Text("▶ aperçu", fontSize = 15.sp)
    }
}

/**
 * Plafond sonore de l'alarme, en pourcentage du maximum de l'appareil.
 *
 * Le maximum d'un téléphone peut être douloureux au réveil ; ce curseur permet
 * de le rabaisser. Il ne descend pas sous [NiveauxVolume.POURCENT_MINIMAL] :
 * en dessous, l'alarme cesserait d'être une alarme, et c'est la seule chose
 * que cette application promet. La borne est aussi appliquée au calcul et à
 * l'écriture, pas seulement ici — un curseur est une commodité, pas une
 * garantie.
 */
@Composable
private fun ReglageVolumeMax(pourcent: Int, onChange: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Puissance maximale", fontSize = 17.sp)
            Text(
                text = "$pourcent %",
                fontFamily = FontFamily.Monospace,
                fontSize = 17.sp,
            )
        }
        Slider(
            value = pourcent.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = NiveauxVolume.POURCENT_MINIMAL.toFloat()..NiveauxVolume.POURCENT_MAXIMAL.toFloat(),
            // Un cran par tranche de 5 % : assez fin pour trouver son confort,
            // assez grossier pour ne pas donner l'illusion d'une précision que
            // le flux d'alarme n'a pas — la plupart des téléphones n'ont que
            // sept à quinze crans en tout.
            steps = 9,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Le plancher est à ${NiveauxVolume.POURCENT_MINIMAL} % : en dessous, " +
                "ce ne serait plus un réveil.",
            fontSize = 15.sp,
        )
    }
}

/**
 * Un aperçu muet est la panne exacte que cet écran existe pour faire
 * découvrir **avant** la nuit : sortir en silence laissait l'utilisateur
 * croire qu'il venait de choisir une sonnerie qui marche.
 */
private const val ECHEC_APERCU =
    "Cette sonnerie n'a pas pu être jouée. Elle ne sonnera pas non plus au réveil : choisis-en une autre."
