package com.cocorico.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.cocorico.challenge.photo.CatalogueObjets
import com.cocorico.challenge.photo.EtiquetteReconnue
import com.cocorico.challenge.photo.JugeEmbarque
import com.cocorico.challenge.photo.JugementPhoto
import com.cocorico.challenge.photo.ObjetPhoto
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

/**
 * Banc d'essai de la reconnaissance embarquée, **sans alarme**.
 *
 * Il existe parce que la seule autre façon d'éprouver le défi photo était de
 * faire sonner le réveil à plein volume. Aucun seuil de ce défi n'ayant été
 * mesuré sur un appareil, il fallait pouvoir les confronter au réel sans
 * réveiller la maison.
 *
 * Il affiche ce que le défi, lui, cache volontairement : les étiquettes brutes
 * reconnues et leur confiance. Un refus a trois causes possibles — objet mal
 * reconnu, seuil trop haut, étiquette absente du catalogue — et elles
 * appellent trois correctifs différents. Le verdict seul ne permet pas de les
 * distinguer ; la liste brute, si.
 *
 * Rien n'est envoyé nulle part : le juge distant n'est pas sollicité ici, et
 * aucune image n'atteint le disque.
 */
@Composable
fun EssaiPhotoScreen(onRetour: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val juge = remember { JugeEmbarque() }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var fournisseur by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val libere = remember { AtomicBoolean(false) }

    var objet by remember { mutableStateOf(CatalogueObjets.tous.first()) }
    var etiquettes by remember { mutableStateOf<List<EtiquetteReconnue>?>(null) }
    var verdict by remember { mutableStateOf<Boolean?>(null) }
    var enCours by remember { mutableStateOf(false) }
    var cameraPrete by remember { mutableStateOf(false) }
    var echecCamera by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            libere.set(true)
            juge.fermer()
            runCatching { fournisseur?.unbindAll() }
        }
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
        Text("Essai de la reconnaissance", style = MaterialTheme.typography.titleLarge)
        Text(
            "Aucune alarme, aucun envoi : tout se passe sur le téléphone.",
            fontSize = 15.sp,
        )

        if (!juge.disponible()) {
            Text(
                "La reconnaissance embarquée n'est pas disponible sur cet appareil. " +
                    "Le défi photo se rabattrait sur les calculs.",
                fontSize = 15.sp,
            )
            return@Column
        }

        Text("Objet à viser", fontSize = 15.sp)
        Text(
            text = objet.nom,
            fontSize = 22.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable {
                    // Parcourt le catalogue : c'est ainsi qu'on repère les
                    // objets que le modèle ne sait pas nommer, ceux qu'il
                    // faudra retirer.
                    val suivant = (CatalogueObjets.tous.indexOf(objet) + 1) % CatalogueObjets.tous.size
                    objet = CatalogueObjets.tous[suivant]
                    etiquettes = null
                    verdict = null
                }
                .padding(14.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            "Appuie sur le nom pour passer à l'objet suivant du catalogue.",
            fontSize = 15.sp,
        )

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(12.dp)),
            factory = { ctx ->
                val vue = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener(
                    {
                        val resultat = runCatching {
                            val f = future.get()
                            fournisseur = f
                            if (libere.get()) {
                                f.unbindAll()
                                return@runCatching false
                            }
                            val apercu = Preview.Builder().build().also {
                                it.setSurfaceProvider(vue.surfaceProvider)
                            }
                            f.unbindAll()
                            f.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                apercu,
                                imageCapture,
                            )
                            true
                        }
                        cameraPrete = resultat.getOrDefault(false)
                        echecCamera = resultat.isFailure
                    },
                    ContextCompat.getMainExecutor(ctx),
                )
                vue
            },
        )

        if (echecCamera) {
            Text(
                "Caméra indisponible. Vérifie que la permission est accordée.",
                fontSize = 15.sp,
            )
        }

        Button(
            onClick = {
                if (enCours) return@Button
                enCours = true
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = runCatching { image.versBitmapEssai() }.getOrNull()
                            runCatching { image.close() }
                            if (bitmap == null) {
                                etiquettes = emptyList()
                                verdict = false
                                enCours = false
                                return
                            }
                            scope.launch {
                                val trouvees = juge.etiquettes(bitmap)
                                etiquettes = trouvees
                                verdict = JugementPhoto.accepte(objet, trouvees)
                                enCours = false
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            etiquettes = emptyList()
                            verdict = false
                            enCours = false
                        }
                    },
                )
            },
            enabled = cameraPrete && !enCours,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (enCours) "Analyse…" else "Analyser", fontSize = 18.sp)
        }

        verdict?.let { accepte ->
            Text(
                text = if (accepte) "ACCEPTÉ" else "REFUSÉ",
                fontFamily = FontFamily.Monospace,
                fontSize = 26.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Étiquettes attendues pour « ${objet.nom} » : " +
                    objet.etiquettes.joinToString(", "),
                fontSize = 15.sp,
            )
        }

        etiquettes?.let { liste ->
            Text("Ce que le modèle a reconnu", fontSize = 15.sp)
            if (liste.isEmpty()) {
                Text("Rien du tout.", fontSize = 15.sp)
            }
            liste.forEach { e ->
                val attendue = e.texte.lowercase() in objet.etiquettes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (attendue) "→ ${e.texte}" else e.texte,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = "%.2f".format(e.confiance),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                    )
                }
            }
            Text(
                text = "Seuil actuel : ${"%.2f".format(JugementPhoto.SEUIL_CONFIANCE)}. " +
                    "Une flèche marque une étiquette attendue pour cet objet.",
                fontSize = 15.sp,
            )
        }
    }
}

/**
 * Même traitement que dans le défi — redressement selon l'orientation du
 * capteur —, sans quoi l'essai mesurerait autre chose que ce que le défi fait
 * réellement, et ses chiffres ne vaudraient rien.
 */
private fun ImageProxy.versBitmapEssai(): Bitmap {
    val tampon = planes[0].buffer
    val octets = ByteArray(tampon.remaining())
    tampon.get(octets)
    val brut = BitmapFactory.decodeByteArray(octets, 0, octets.size)
    val coteLong = maxOf(brut.width, brut.height)
    val reduit = if (coteLong <= 1568) {
        brut
    } else {
        val facteur = 1568f / coteLong
        Bitmap.createScaledBitmap(
            brut,
            (brut.width * facteur).toInt().coerceAtLeast(1),
            (brut.height * facteur).toInt().coerceAtLeast(1),
            true,
        )
    }
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return reduit
    val matrice = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(reduit, 0, 0, reduit.width, reduit.height, matrice, true)
}
