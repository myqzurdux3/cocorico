package com.cocorico.challenge.photo

import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.cocorico.challenge.Challenge
import com.cocorico.challenge.ChallengeProgress
import com.cocorico.data.ChallengeId
import com.cocorico.data.Difficulty
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Défi photo. Le catalogue tire les objets, [PhotoChallengeEtat] décide de la
 * progression et les juges rendent le verdict ; cette classe ne fait que
 * relier la caméra et l'écran à ces classes pures, exactement comme
 * [com.cocorico.challenge.pompes.PompesChallenge] relie le capteur de
 * proximité au compteur de pompes.
 *
 * [onRenoncer] bascule sur le défi maths, comme aux pompes, et le bouton exige
 * un appui long : la main tient le téléphone en visant l'objet, un simple
 * frôlement de l'écran ne doit pas effacer la progression.
 */
class PhotoChallenge(
    context: Context,
    difficulty: Difficulty,
    private val cleApi: String,
    private val onInteraction: () -> Unit,
    private val onRenoncer: () -> Unit,
) : Challenge {

    private val contexteApp = context.applicationContext

    private val juge: JugePhoto = JugeGemini(cleApi)

    private val total = nombrePour(difficulty)

    /**
     * Tirage figé à la construction, en excluant les objets du réveil
     * précédent. Les identifiants tirés sont aussitôt persistés — au moment du
     * tirage, pas de la résolution du défi — pour qu'un réveil abandonné en
     * cours de route change quand même les objets proposés le lendemain.
     */
    private val objets: List<ObjetPhoto> =
        CatalogueObjets.tirer(
            total,
            // Une exclusion illisible ne doit pas empêcher le tirage : ce
            // constructeur s'exécute avant que l'écran d'alarme ne s'affiche,
            // et une exception y laisserait un écran noir par-dessus le
            // verrouillage, sonnerie à fond, sans aucun moyen de l'arrêter.
            // Au pire, un objet du réveil précédent est proposé à nouveau.
            runCatching { ExclusionObjets.lire(contexteApp) }.getOrDefault(emptySet()),
            Random.Default,
        ).also {
            runCatching { ExclusionObjets.ecrire(contexteApp, it.map(ObjetPhoto::id).toSet()) }
        }

    private val etat = PhotoChallengeEtat(objets)

    private val _progress = MutableStateFlow(ChallengeProgress(done = 0, total = objets.size))
    override val progress: StateFlow<ChallengeProgress> = _progress.asStateFlow()

    override val id = ChallengeId.PHOTO
    override val isSolved: StateFlow<Boolean> = etat.isSolved

    /**
     * Essais ratés sur tout le réveil, pour l'historique. Exposé ici parce que
     * l'activité d'alarme ne connaît que l'interface [com.cocorico.challenge.Challenge] :
     * elle transtype pour lire ce compteur, comme elle le fait déjà pour les
     * fautes du défi de calcul mental.
     */
    val essaisTotal: StateFlow<Int> = etat.essaisTotal

    override fun onUserInteraction() = onInteraction()

    /**
     * Exposé pour que l'appelant refuse le défi photo sur un appareil sans
     * caméra exploitable — répondre vrai à tort laisserait l'utilisateur
     * devant une alarme qu'il ne pourrait plus arrêter.
     */
    val camerasDisponibles: Boolean =
        contexteApp.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) &&
            cleApi.isNotBlank()

    /**
     * Un seul juge depuis le retrait de la reconnaissance embarquée. Toute
     * exception y vaut déjà refus, imposé par le contrat de [JugePhoto] :
     * rien ici ne doit planter devant la sirène.
     */
    private suspend fun verdict(image: Bitmap, objet: ObjetPhoto): Boolean =
        juge.accepte(image, objet)

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content(modifier: Modifier) {
        val objetCourant by etat.objetCourant.collectAsState()
        val essais by etat.essais.collectAsState()
        val progression by etat.progression.collectAsState()
        _progress.value = ChallengeProgress(done = progression.first, total = progression.second)

        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()
        val imageCapture = remember { ImageCapture.Builder().build() }
        var fournisseurCamera by remember { mutableStateOf<ProcessCameraProvider?>(null) }

        // Empêche un second déclenchement pendant qu'un verdict est en
        // attente : sans cette garde, deux verdicts pourraient se croiser et
        // fausser la progression.
        var enAttenteVerdict by remember { mutableStateOf(false) }

        // Le compte à rebours d'inactivité fait remonter le volume au bout de
        // dix secondes sans geste. Or l'attente d'un verdict peut durer jusqu'à
        // huit secondes, capture comprise, pendant lesquelles le bouton est
        // désactivé : l'utilisateur ne *peut* rien faire. Sans ce réarmement,
        // la sirène repartait à fond au visage de quelqu'un qui venait
        // précisément d'obéir, et la marge tenait à deux secondes près.
        LaunchedEffect(enAttenteVerdict) {
            while (enAttenteVerdict) {
                onInteraction()
                delay(RYTHME_REARMEMENT_MS)
            }
        }

        // Faux tant que la caméra n'a pas pu être liée. Sans cet état, un échec
        // de liaison laissait un bouton actif dont chaque appui échouait en
        // silence : l'utilisateur appuyait, rien ne se passait, et rien ne lui
        // disait pourquoi ni comment s'en sortir.
        var cameraPrete by remember { mutableStateOf(false) }
        var echecCamera by remember { mutableStateOf(false) }

        // La composition peut être détruite avant que le fournisseur de caméra,
        // obtenu de façon asynchrone, ne soit arrivé — il suffit d'un appui long
        // « Je ne peux pas » dans la seconde qui suit l'ouverture, ce que ce
        // bouton est justement fait pour permettre. `onDispose` ne verrait alors
        // qu'une référence nulle, et la liaison se ferait juste après sur une
        // activité toujours vivante : la caméra resterait allumée pendant tout
        // le défi de calcul mental qui suit.
        val libere = remember { AtomicBoolean(false) }

        // Libère le juge embarqué et la caméra quand l'écran quitte la
        // composition — un client de reconnaissance ou une caméra non libérés
        // fuient à chaque réveil.
        DisposableEffect(Unit) {
            onDispose {
                libere.set(true)
                juge.fermer()
                runCatching { fournisseurCamera?.unbindAll() }
            }
        }

        Column(
            modifier = modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "${progression.first} / ${progression.second}",
                color = MaterialTheme.colorScheme.onError,
                fontSize = 22.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            LinearProgressIndicator(
                progress = { if (progression.second == 0) 1f else progression.first.toFloat() / progression.second },
                color = MaterialTheme.colorScheme.onError,
                trackColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
            )
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp)),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val futureFournisseur = ProcessCameraProvider.getInstance(ctx)
                    futureFournisseur.addListener(
                        {
                            // Toute exception ici vaut caméra indisponible :
                            // rien ne doit remonter jusqu'à l'écran d'alarme.
                            val resultat = runCatching {
                                val fournisseur = futureFournisseur.get()
                                fournisseurCamera = fournisseur
                                // L'écran a pu disparaître entre la demande et
                                // la réponse : lier maintenant allumerait la
                                // caméra pour personne, sans que rien ne vienne
                                // plus l'éteindre.
                                if (libere.get()) {
                                    fournisseur.unbindAll()
                                    return@runCatching false
                                }
                                val previsualisation = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                fournisseur.unbindAll()
                                fournisseur.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    previsualisation,
                                    imageCapture,
                                )
                                true
                            }
                            cameraPrete = resultat.getOrDefault(false)
                            echecCamera = resultat.isFailure
                        },
                        ContextCompat.getMainExecutor(ctx),
                    )
                    previewView
                },
            )
            Text(
                text = if (echecCamera) {
                    "Caméra indisponible. Appuie longuement sur le bouton " +
                        "ci-dessous pour passer aux calculs."
                } else {
                    consigne(objetCourant, essais, enAttenteVerdict)
                },
                color = MaterialTheme.colorScheme.onError,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Button(
                enabled = objetCourant != null && !enAttenteVerdict && cameraPrete,
                onClick = click@{
                    // Défense en profondeur en plus de `enabled` : un second
                    // appui pendant la recomposition ne doit pas non plus
                    // déclencher une seconde capture.
                    if (enAttenteVerdict) return@click
                    val objet = objetCourant ?: return@click
                    onInteraction()
                    enAttenteVerdict = true
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(contexteApp),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                // Conversion et redressement entièrement en
                                // mémoire, avant fermeture de l'ImageProxy :
                                // aucun fichier n'est créé, à aucun moment.
                                val bitmap = runCatching { image.versBitmapRedresse() }.getOrNull()
                                image.close()
                                if (bitmap == null) {
                                    enAttenteVerdict = false
                                    return
                                }
                                scope.launch {
                                    val accepte = runCatching { verdict(bitmap, objet) }.getOrDefault(false)
                                    if (etat.soumettre(accepte)) onInteraction()
                                    enAttenteVerdict = false
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                // Capture ratée : refus silencieux, l'écran
                                // reste et l'utilisateur réessaie.
                                enAttenteVerdict = false
                            }
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (enAttenteVerdict) "Vérification…" else "Prendre la photo",
                    fontSize = 18.sp,
                )
            }
            // Seuil relevé à 600 ms (contre ~500 ms par défaut), comme aux
            // pompes : un appui délibéré, pas un effleurement en cadrant la
            // photo.
            CompositionLocalProvider(
                LocalViewConfiguration provides object : ViewConfiguration by LocalViewConfiguration.current {
                    override val longPressTimeoutMillis: Long = SEUIL_APPUI_LONG_MS
                },
            ) {
                val renoncer = {
                    onInteraction()
                    onRenoncer()
                }
                Text(
                    text = if (echecCamera) "Passer aux calculs" else "Je ne peux pas — appui long",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .combinedClickable(
                            // L'appui simple ne fait rien tant que la caméra
                            // marche : c'est lui, pas l'appui long, que le doigt
                            // qui cadre la photo déclencherait par accident, et
                            // la progression serait perdue.
                            //
                            // Caméra en échec, l'arbitrage s'inverse : il n'y a
                            // plus de progression à protéger, plus rien à
                            // déclencher par accident, et laisser quelqu'un
                            // chercher un appui long devant une sirène pour
                            // sortir d'un écran qui ne fonctionne pas serait
                            // gratuitement hostile.
                            onClick = if (echecCamera) renoncer else ({}),
                            onLongClick = renoncer,
                        )
                        .padding(vertical = 14.dp),
                )
            }
        }
    }

    private fun consigne(objet: ObjetPhoto?, essais: Int, enAttenteVerdict: Boolean): String = when {
        enAttenteVerdict -> "Analyse de la photo…"
        objet == null -> "Défi terminé"
        essais == 0 -> "Photographie : ${objet.nom}"
        else -> "Pas encore reconnu. Réessaie : ${objet.nom}"
    }

    /**
     * Convertit cette capture en [Bitmap], redressé selon la rotation portée
     * par [ImageProxy.getImageInfo] : sur un téléphone tenu en portrait, cette
     * rotation vaut couramment 90°, et l'ignorer enverrait une image couchée
     * au juge — la reconnaissance tolère mal une image pivotée et le taux de
     * refus grimperait sans raison visible à l'écran. Tout se fait en
     * mémoire : aucun fichier n'est créé.
     */
    /**
     * Décode la capture, la redresse selon l'orientation du capteur, et la
     * réduit. Tout se passe en mémoire : aucune image n'atteint le disque.
     *
     * Le redressement n'est pas cosmétique. [JugeEmbarque] déclare une rotation
     * nulle à la reconnaissance — il reçoit un bitmap déjà constitué et ne peut
     * pas connaître l'orientation de la prise de vue. Sans ce redressement, le
     * modèle recevrait une image couchée de 90° en portrait et refuserait
     * beaucoup plus souvent, **sans qu'aucun seuil ne paraisse en cause**.
     *
     * La réduction sert le juge distant : la capture sort en pleine résolution
     * du capteur, l'encodage en base64 l'alourdit encore d'un tiers, et le tout
     * doit tenir dans les huit secondes du budget réseau, à six heures du matin
     * sur le réseau d'une chambre. Au-delà de [COTE_MAX_PX], la résolution
     * supplémentaire n'apporte plus rien à la reconnaissance. Cela réduit du
     * même coup le risque de saturation mémoire du redressement, qui alloue
     * l'image d'origine plus sa copie pivotée.
     */
    private fun ImageProxy.versBitmapRedresse(): Bitmap {
        val tampon = planes[0].buffer
        val octets = ByteArray(tampon.remaining())
        tampon.get(octets)
        val brut = BitmapFactory.decodeByteArray(octets, 0, octets.size)
        val reduit = reduire(brut)
        val rotation = imageInfo.rotationDegrees
        if (rotation == 0) return reduit
        val matrice = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(reduit, 0, 0, reduit.width, reduit.height, matrice, true)
    }

    /** Ramène le côté long à [COTE_MAX_PX], en conservant les proportions. */
    private fun reduire(image: Bitmap): Bitmap {
        val coteLong = maxOf(image.width, image.height)
        if (coteLong <= COTE_MAX_PX) return image
        val facteur = COTE_MAX_PX.toFloat() / coteLong
        return Bitmap.createScaledBitmap(
            image,
            (image.width * facteur).toInt().coerceAtLeast(1),
            (image.height * facteur).toInt().coerceAtLeast(1),
            true,
        )
    }

    companion object {
        fun nombrePour(difficulty: Difficulty): Int = when (difficulty) {
            Difficulty.FACILE -> 1
            Difficulty.MOYEN -> 2
            Difficulty.DIFFICILE -> 3
        }

        /**
         * Décide s'il faut payer un appel au juge distant après le verdict de
         * l'embarqué. Un accord de l'embarqué n'est jamais soumis au distant —
         * c'est gratuit et instantané, il n'y a aucune raison de le
         * contredire à prix d'appel réseau. Pure, sans import `android.*` :
         * testable sans caméra, sans réseau et sans modèle.
         */
        /** Durée d'appui exigée par le bouton de renoncement. Voir sa KDoc. */
        private const val SEUIL_APPUI_LONG_MS = 600L

        /**
         * Côté long maximal de l'image soumise aux juges. Au-delà, la
         * reconnaissance ne gagne rien et le téléversement du juge distant
         * s'allonge sans contrepartie.
         */
        private const val COTE_MAX_PX = 1568

        /**
         * Nettement plus court que les dix secondes du compte à rebours, pour
         * qu'un réarmement manqué ne suffise jamais à faire remonter le son.
         */
        private const val RYTHME_REARMEMENT_MS = 2_000L
    }
}

/**
 * Mémoire disque des identifiants tirés au réveil précédent, sur le modèle de
 * `ring.VolumeOrigine` : un petit magasin `SharedPreferences` dédié, distinct
 * d'[com.cocorico.data.AlarmConfig]. Ce n'est pas un réglage que
 * l'utilisateur choisit mais une trace d'exécution, et la mêler à la
 * configuration exposerait des identifiants d'objets dans un modèle que
 * plusieurs écrans observent.
 */
private object ExclusionObjets {

    private const val FICHIER = "cocorico_photo_exclusion"
    private const val CLE = "objets_precedents"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    /** Les identifiants tirés au réveil précédent, vide s'il n'y en a pas encore eu. */
    fun lire(context: Context): Set<String> = prefs(context).getStringSet(CLE, emptySet()).orEmpty()

    /**
     * Écrit les identifiants tirés — au moment du tirage, pas de la
     * résolution du défi. Voir la KDoc de [PhotoChallenge.objets].
     */
    fun ecrire(context: Context, ids: Set<String>) {
        // `apply()` et non `commit()` : cette écriture a lieu dans le
        // constructeur du défi, donc sur le thread principal, juste avant
        // d'afficher l'écran par-dessus une sonnerie à plein volume. Une
        // écriture disque synchrone y est un risque gratuit — perdre une
        // exclusion ne coûte qu'un objet répété le lendemain, contrairement au
        // volume d'origine, que `VolumeOrigine` persiste bien en `commit()`
        // parce que sa perte laisserait le téléphone à fond.
        prefs(context).edit().putStringSet(CLE, ids).apply()
    }
}
