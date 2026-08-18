package com.cocorico.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.cocorico.challenge.photo.PhotoChallenge
import com.cocorico.challenge.photo.SelectionObjets
import com.cocorico.challenge.pompes.PompesChallenge
import com.cocorico.data.ChallengeId
import com.cocorico.data.Difficulty
import com.cocorico.ring.CapteurPompes

@Composable
fun ChallengeSettingsScreen(
    viewModel: HomeViewModel,
    onEssayerPhoto: () -> Unit,
    onOuvrirSelectionObjets: () -> Unit,
    onRetour: () -> Unit,
) {
    val config by viewModel.config.collectAsState()

    // Même critère que celui qui fait basculer l'alarme sur les calculs au
    // réveil ([CapteurPompes.capteurDisponible]) : sans lui, le réglage
    // « Pompes » pourrait rester sélectionnable ici alors qu'il sera ignoré
    // en silence chaque matin. L'instance ne sert qu'à lire ce booléen, elle
    // n'est jamais démarrée : aucun capteur n'est donc écouté.
    val context = LocalContext.current
    val capteursPompesDisponibles = remember {
        CapteurPompes(context) {}.capteurDisponible()
    }

    // Une caméra sur l'appareil. La clé d'API, elle, est vérifiée séparément :
    // ce sont deux manques distincts, avec deux messages distincts, et les
    // confondre dirait à quelqu'un sans clé que son téléphone n'a pas de
    // caméra.
    val cameraDisponibleAppareil = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    // Reflète l'état réel de la permission, pas seulement celui lu à
    // l'ouverture de l'écran : un refus dans la boîte de dialogue système doit
    // aussitôt faire apparaître l'avertissement, sans attendre un aller-retour
    // vers l'accueil. Relu aussi à chaque retour au premier plan, donc au
    // retour des réglages Android — accordée là-bas, la permission doit être
    // constatée ici sans avoir à ressortir de l'écran.
    val permissions = etatPermissionsObserve()
    val cameraAccordee = permissions.value.camera

    // Après un second refus, Android n'ouvre plus jamais la boîte de dialogue
    // et `launch` ne fait plus rien : chaque appui sur l'option Photo devenait
    // un échec parfaitement silencieux, alors même que l'avertissement parlait
    // des réglages du téléphone. On bascule donc vers la fiche de
    // l'application, seul chemin qui reste.
    var refusDefinitif by remember { mutableStateOf(false) }
    val activite = context as? Activity
    val demanderCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { accordee ->
        permissions.value = PermissionChecker.etat(context)
        refusDefinitif = refusCameraDefinitif(
            accordee = accordee,
            // Sans activité — cas qui ne se produit pas ici, mais que le type
            // autorise — on ne peut rien affirmer : on préfère laisser une
            // nouvelle demande possible plutôt que d'envoyer à tort dans les
            // réglages.
            justificationPossible = activite == null ||
                ActivityCompat
                    .shouldShowRequestPermissionRationale(activite, Manifest.permission.CAMERA),
        )
    }

    // Demander la caméra, ou ouvrir la fiche de l'application quand demander ne
    // sert plus à rien. Un seul chemin pour les deux appelants (l'option Photo
    // et le banc d'essai) : les séparer laisserait l'un des deux dans le vide.
    val reclamerCamera = {
        if (refusDefinitif) {
            ouvrirFicheApplication(context)
        } else {
            demanderCamera.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .zoneSure()
            // Défilement obligatoire : le bloc du juge photo, avec son texte
            // d'information et son champ de clé, pousse le sélecteur de
            // difficulté hors de l'écran. Sans lui, ce sélecteur disparaît
            // purement et simplement dès que le défi photo est choisi.
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BoutonRetour(onRetour)
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
            detail = if (capteursPompesDisponibles) {
                "${PompesChallenge.nombrePour(config.difficulty)} répétitions comptées"
            } else {
                "Capteur de proximité ou accéléromètre absent : indisponible sur ce téléphone"
            },
            selectionne = config.challengeId == ChallengeId.POMPES,
            indisponible = !capteursPompesDisponibles,
            onClick = if (capteursPompesDisponibles) {
                { viewModel.majDefi(ChallengeId.POMPES) }
            } else {
                null
            },
        )
        Option(
            titre = "Photo",
            // La difficulté ne rend jamais un objet plus dur à trouver — un
            // objet « difficile » n'aurait aucun sens à six heures du matin.
            // Elle change leur nombre, exactement comme les pompes changent de
            // nombre de répétitions. La carte doit le dire, sinon le réglage
            // paraît sans effet sur ce défi.
            detail = when (val nombre = PhotoChallenge.nombrePour(config.difficulty)) {
                1 -> "Un objet à photographier, validé par l'IA"
                else -> "$nombre objets à photographier, validés par l'IA"
            },
            selectionne = config.challengeId == ChallengeId.PHOTO,
            indisponible = !cameraDisponibleAppareil,
            // Choisir la photo sans avoir accordé la caméra ne doit plus
            // rester silencieux : sans ce mot, l'accueil promettrait une
            // photo que le réveil ne pourra jamais demander (voir la revue
            // qui a établi ce défaut). Le choix reste possible : la
            // permission peut toujours être accordée plus tard dans les
            // réglages Android.
            avertissement = when {
                !cameraDisponibleAppareil || cameraAccordee -> null
                // Le message doit dire ce que fera l'appui suivant : promettre
                // une boîte de dialogue qui ne s'ouvrira plus est la panne
                // qu'on corrige ici.
                refusDefinitif ->
                    "L'accès à l'appareil photo a été refusé définitivement : le réveil se " +
                        "rabattra sur les calculs. Appuie à nouveau pour ouvrir la fiche de " +
                        "Cocorico dans les réglages et l'y autoriser."
                else ->
                    "Sans l'accès à l'appareil photo, le réveil se rabattra sur les " +
                        "calculs. Tu peux l'accorder plus tard dans les réglages du téléphone."
            },
            onClick = if (cameraDisponibleAppareil) {
                {
                    viewModel.majDefi(ChallengeId.PHOTO)
                    // Demandée ici, au moment de la sélection : c'est le seul
                    // parcours normal qui atteint jamais cette permission
                    // (voir la KDoc d'[EtatPermissions.camera]).
                    if (!cameraAccordee) reclamerCamera()
                }
            } else {
                null
            },
        )
        if (config.challengeId == ChallengeId.PHOTO && cameraDisponibleAppareil) {
            // Éprouver la reconnaissance sans faire sonner le réveil. Sans cet
            // accès, la seule façon de la tester était de déclencher une alarme
            // à plein volume — et aucun de ses seuils n'a encore été confronté
            // à un vrai objet dans une vraie pièce.
            Text(
                text = "Essayer la reconnaissance",
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        if (!cameraAccordee) reclamerCamera() else onEssayerPhoto()
                    }
                    .padding(14.dp),
            )
            Text(
                "Sans alarme : la caméra, un objet, et le verdict du modèle.",
                fontSize = 15.sp,
            )
        }
        if (config.challengeId == ChallengeId.PHOTO) {
            val totalCoche = remember(config.objetsSelectionnes) {
                SelectionObjets.totalCoche(config.objetsSelectionnes)
            }
            Text(
                text = "Objets à photographier ($totalCoche cochés) ›",
                fontSize = 17.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onOuvrirSelectionObjets)
                    .padding(14.dp),
            )
            // Même avertissement qu'à l'écran de sélection, visible ici sans
            // avoir à l'ouvrir : voir sa KDoc pour le choix d'avertir sans
            // jamais bloquer.
            if (totalCoche < SelectionObjets.SEUIL_AVERTISSEMENT) {
                Text(
                    text = if (totalCoche == 0) {
                        "Aucun objet coché : le tirage se repliera sur tout le catalogue."
                    } else {
                        "Moins de ${SelectionObjets.SEUIL_AVERTISSEMENT} objets cochés : le " +
                            "tirage peut compléter avec des objets non cochés."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            ReglagesJugePhoto(
                cleApi = config.cleApi,
                onCleApiChange = viewModel::majCleApi,
            )
        }

        Text("Difficulté", fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { niveau ->
                Text(
                    text = niveau.name.lowercase().replaceFirstChar { it.uppercase() },
                    fontSize = 15.sp,
                    // Pastille sélectionnée : fond primaire, donc couleur de
                    // texte primaire. La couleur de contenu par défaut est
                    // calibrée pour le fond de l'écran, pas pour cette pastille.
                    color = if (niveau == config.difficulty) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (niveau == config.difficulty) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .clickable { viewModel.majDifficulte(niveau) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * Le refus de la caméra est-il définitif, c'est-à-dire hors de portée d'une
 * nouvelle demande ?
 *
 * Android n'annonce pas le « ne plus demander » : il se déduit d'un refus que
 * le système ne juge plus justifiable. Décision isolée d'Android — donc
 * vérifiable — parce que s'y tromper redonne exactement la panne qu'elle
 * corrige : un bouton qui n'ouvre plus rien.
 *
 * [justificationPossible] n'a de sens qu'**après** un refus effectif :
 * `shouldShowRequestPermissionRationale` répond faux aussi bien pour un refus
 * définitif que pour une permission jamais demandée. D'où le premier terme —
 * évalué sur le résultat d'une demande, jamais à froid, sans quoi on
 * enverrait dans les réglages Android quelqu'un à qui on n'a encore rien
 * demandé.
 */
internal fun refusCameraDefinitif(accordee: Boolean, justificationPossible: Boolean): Boolean =
    !accordee && !justificationPossible

/**
 * Ouvre la fiche « Informations sur l'application », d'où la permission
 * refusée définitivement peut encore être accordée. Sans garantie : quelques
 * surcouches n'exposent pas cet écran, et un échec ici ne doit pas faire
 * planter un écran de réglages.
 */
private fun ouvrirFicheApplication(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
    }
}

@Composable
private fun ReglagesJugePhoto(cleApi: String, onCleApiChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(width = 1.dp, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Ce défi fait juger la photo par un modèle de vision de Google. " +
                "L'image prise au réveil part vers ses serveurs, uniquement au " +
                "moment du verdict, et n'est jamais enregistrée : elle reste en " +
                "mémoire puis disparaît. La clé est la tienne, obtenue sur " +
                "Google AI Studio.",
            fontSize = 15.sp,
        )
        Text(
            if (cleApi.isBlank()) {
                "Sans clé, le défi photo n'est pas proposé : le réveil se rabat " +
                    "sur les calculs."
            } else {
                "Clé enregistrée. Sans réseau au moment du réveil, le défi se " +
                    "rabat sur les calculs."
            },
            fontSize = 15.sp,
        )
        OutlinedTextField(
            value = cleApi,
            onValueChange = onCleApiChange,
            label = { Text("Clé d'API Google", fontSize = 15.sp) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Option(
    titre: String,
    detail: String,
    selectionne: Boolean = false,
    indisponible: Boolean = false,
    avertissement: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selectionne) 2.dp else 1.dp,
                color = if (selectionne) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(12.dp),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        Text(
            text = when {
                indisponible -> "$titre — indisponible sur ce téléphone"
                else -> titre
            },
            fontSize = 17.sp,
        )
        Text(detail, fontSize = 15.sp)
        // Averti sans bloquer : contrairement à `indisponible`, ce cas laisse
        // l'option sélectionnable, puisque la permission peut encore être
        // accordée après coup.
        if (avertissement != null) {
            Text(
                text = avertissement,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
