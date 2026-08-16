package com.cocorico.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocorico.ring.ApercuSonnerie
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

    // Lu à chaque recomposition depuis le magasin dédié plutôt que depuis
    // `config` : la sonnerie personnalisée n'est pas un champ d'`AlarmConfig`,
    // voir la KDoc de `SonneriePersonnaliseeStore` pour la raison.
    var uriPersonnalisee by remember { mutableStateOf(SonneriePersonnaliseeStore.lireUri(context)) }
    var nomPersonnalisee by remember { mutableStateOf(SonneriePersonnaliseeStore.lireNom(context)) }
    var erreurImport by remember { mutableStateOf<String?>(null) }

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

        if (permissionAccordee && SondeSonnerie.estLisible(context, uri)) {
            val nom = SonneriePersonnaliseeLogique.nomAffichable(
                uri.toString(),
                SondeSonnerie.nomInterroge(context, uri),
            )
            SonneriePersonnaliseeStore.ecrire(context, uri.toString(), nom)
            uriPersonnalisee = uri.toString()
            nomPersonnalisee = nom
            erreurImport = null
            viewModel.majSonnerie(Sonneries.ID_PERSONNALISEE)
            apercu.jouer(uri)
        } else {
            erreurImport = "Ce fichier ne peut pas servir de sonnerie : format illisible, " +
                "fichier corrompu ou accès refusé. Choisis-en un autre."
        }
    }

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

        Sonneries.toutes.forEach { sonnerie ->
            LigneSonnerie(
                nom = sonnerie.nom,
                choisie = sonnerie.id == config.ringtoneId,
                onClick = {
                    viewModel.majSonnerie(sonnerie.id)
                    apercu.jouer(sonnerie)
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
                    apercu.jouer(Uri.parse(uriTexte))
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
