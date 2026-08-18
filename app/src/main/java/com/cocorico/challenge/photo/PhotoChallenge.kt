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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cocorico.challenge.Challenge
import com.cocorico.data.ChallengeId
import com.cocorico.data.Difficulty
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * Ce que le défi fait d'un essai que le juge n'a pas pu trancher. Voir
 * [PhotoChallenge.suiteApresPanne].
 */
internal enum class SuiteApresPanne {
    /** Incident isolé : l'utilisateur reprend une photo. */
    REESSAYER,

    /** Le repli calculs est affiché comme la sortie, à un seul appui. */
    PROPOSER_REPLI,

    /** Plus rien à attendre du juge : on bascule sur les calculs. */
    BASCULER,
}

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
    /**
     * Identifiants cochés à l'écran de sélection des pièces
     * (`AlarmConfig.objetsSelectionnes`). Valeur par défaut vide — pas de
     * restriction, tout le catalogue — pour que l'appelant qui ne la
     * fournirait pas encore continue de compiler et de sonner exactement
     * comme avant cette fonctionnalité.
     */
    private val objetsSelectionnes: Set<String> = emptySet(),
) : Challenge {

    private val contexteApp = context.applicationContext

    private val juge: JugePhoto = JugeGemini(cleApi)

    private val total = NOMBRE_OBJETS

    /**
     * Tirage figé au **premier usage** du défi, en excluant les objets du
     * réveil précédent. Les identifiants tirés sont aussitôt persistés — au
     * moment du tirage, pas de la résolution du défi — pour qu'un réveil
     * abandonné en cours de route change quand même les objets proposés le
     * lendemain.
     *
     * Paresseux, et non calculé dans le constructeur, pour deux raisons.
     * L'appelant construit ce défi *puis* consulte [camerasDisponibles] pour
     * décider de s'en servir : tirer d'emblée écrivait l'exclusion d'objets
     * que personne n'allait voir, et le lendemain les écartait pour rien. Et
     * ce constructeur s'exécute sur le thread principal, juste avant
     * l'affichage de l'écran d'alarme, alors que la sonnerie a déjà commencé.
     *
     * Le résultat ne peut pas être vide : [PhotoChallengeEtat] refuserait une
     * liste sans objet, et surtout un défi sans objet arrêterait l'alarme sans
     * qu'aucune photo n'ait été prise.
     */
    private val objets: List<ObjetPhoto> by lazy {
        // Ni le tirage ni la lecture de l'exclusion ne doivent lever : ce code
        // s'exécute pendant la composition de l'écran d'alarme, et une
        // exception y laisserait un écran noir par-dessus le verrouillage,
        // sonnerie à fond, sans aucun moyen de l'arrêter. Au pire, un objet du
        // réveil précédent est proposé à nouveau.
        val tirage = runCatching {
            CatalogueObjets.tirer(
                total,
                runCatching { ExclusionObjets.lire(contexteApp) }.getOrDefault(emptySet()),
                Random.Default,
                objetsSelectionnes,
            )
        }.getOrDefault(emptyList())
        val retenus = tirage.ifEmpty { CatalogueObjets.tous.take(total.coerceAtLeast(1)) }
        runCatching { ExclusionObjets.ecrire(contexteApp, retenus.map(ObjetPhoto::id).toSet()) }
        retenus
    }

    private val etat: PhotoChallengeEtat by lazy { PhotoChallengeEtat(objets) }

    override val id = ChallengeId.PHOTO

    // `get()` et non une valeur : lire ce flux avant le premier affichage
    // déclencherait le tirage, que cette classe repousse justement au premier
    // usage réel du défi.
    override val isSolved: StateFlow<Boolean> get() = etat.isSolved

    /**
     * Essais ratés sur tout le réveil, pour l'historique. Exposé ici parce que
     * l'activité d'alarme ne connaît que l'interface [com.cocorico.challenge.Challenge] :
     * elle transtype pour lire ce compteur, comme elle le fait déjà pour les
     * fautes du défi de calcul mental.
     */
    val essaisTotal: StateFlow<Int> get() = etat.essaisTotal

    /**
     * Exposé pour que l'appelant refuse le défi photo sur un appareil sans
     * caméra exploitable — répondre vrai à tort laisserait l'utilisateur
     * devant une alarme qu'il ne pourrait plus arrêter.
     */
    val camerasDisponibles: Boolean =
        contexteApp.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) &&
            cleApi.isNotBlank()

    /**
     * Un seul juge depuis le retrait de la reconnaissance embarquée. Le
     * contrat de [JugePhoto] interdit déjà toute exception ; le `runCatching`
     * est la ceinture qui va avec les bretelles, et il traite une exception
     * imprévue comme une panne du juge, jamais comme un refus de la photo —
     * l'utilisateur n'y est pour rien.
     */
    private suspend fun verdict(image: Bitmap, objet: ObjetPhoto): DiagnosticJuge =
        runCatching { juge.juger(image, objet) }.getOrElse { erreur ->
            DiagnosticJuge(
                accepte = false,
                resume = "erreur inattendue du juge (${erreur.javaClass.simpleName})",
                issue = IssueJuge.JUGE_INDISPONIBLE,
            )
        }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content(modifier: Modifier) {
        val objetCourant by etat.objetCourant.collectAsState()
        val essais by etat.essais.collectAsState()
        val progression by etat.progression.collectAsState()

        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()
        val imageCapture = remember { ImageCapture.Builder().build() }
        var fournisseurCamera by remember { mutableStateOf<ProcessCameraProvider?>(null) }

        // Empêche un second déclenchement pendant qu'un verdict est en
        // attente : sans cette garde, deux verdicts pourraient se croiser et
        // fausser la progression.
        var enAttenteVerdict by remember { mutableStateOf(false) }

        // Ce qui a empêché le dernier essai d'aboutir, quand ce n'est pas le
        // modèle qui a répondu « non ». Nul le reste du temps. Sans ce
        // message, une capture ratée, une photo illisible ou un juge en panne
        // se traduisaient tous par un bouton qui redevient actif sans un mot :
        // l'utilisateur appuyait, rien ne se passait, indéfiniment.
        var messageEchec by remember { mutableStateOf<String?>(null) }

        // Échecs d'affilée non imputables au modèle. Au-delà d'un seuil, rien
        // ne sert de continuer à photographier : c'est le repli calculs qu'il
        // faut proposer, puis prendre.
        var pannesJuge by remember { mutableIntStateOf(0) }

        // La capture et la conversion de l'image quittent le thread principal :
        // décoder, réduire et pivoter une photo de 12 Mpx gèle sinon l'écran
        // d'alarme plusieurs centaines de millisecondes à chaque essai.
        val executeurPhoto = remember { Executors.newSingleThreadExecutor() }

        // Filet autour de `takePicture` : si aucun rappel n'arrive, le bouton
        // resterait sur « Vérification… » pour toujours, et il n'y aurait plus
        // aucun moyen d'arrêter l'alarme par le défi.
        var veilleCapture by remember { mutableStateOf<Job?>(null) }

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
                // Le thread de conversion vivrait autrement aussi longtemps
                // que le processus, une fois par réveil.
                runCatching { executeurPhoto.shutdown() }
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
                text = when {
                    echecCamera ->
                        "Caméra indisponible. Appuie longuement sur le bouton " +
                            "ci-dessous pour passer aux calculs."
                    // La cause du dernier échec passe avant la consigne : sans
                    // elle, un échec qui n'est pas un refus s'affichait comme
                    // « Pas encore reconnu », et l'utilisateur recommençait.
                    messageEchec != null && !enAttenteVerdict -> messageEchec.orEmpty()
                    else -> consigne(objetCourant, essais, enAttenteVerdict)
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
                    messageEchec = null
                    enAttenteVerdict = true
                    veilleCapture = scope.launch {
                        delay(DELAI_MAX_CAPTURE_MS)
                        // Aucun rappel n'est arrivé : la caméra est muette.
                        // Rendre le bouton plutôt que laisser « Vérification… »
                        // indéfiniment, seul état dont l'utilisateur ne peut
                        // plus sortir.
                        enAttenteVerdict = false
                        messageEchec = "La caméra n'a pas répondu en " +
                            "${DELAI_MAX_CAPTURE_MS / 1000} s. Réessaie, ou appui long " +
                            "ci-dessous pour passer aux calculs."
                    }
                    imageCapture.takePicture(
                        // Hors thread principal, contrairement à l'ancien
                        // `getMainExecutor` : tout ce qui suit dans
                        // `onCaptureSuccess` est du décodage d'image.
                        executeurPhoto,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                // Conversion et redressement entièrement en
                                // mémoire, avant fermeture de l'ImageProxy :
                                // aucun fichier n'est créé, à aucun moment.
                                val bitmap = runCatching { image.versBitmapRedresse() }.getOrNull()
                                runCatching { image.close() }
                                // `scope` appartient à la composition : ce
                                // lancement ramène sur le thread principal,
                                // seul endroit où l'état d'écran se modifie.
                                scope.launch {
                                    veilleCapture?.cancel()
                                    if (bitmap == null) {
                                        enAttenteVerdict = false
                                        messageEchec = "Photo illisible. Réessaie, ou appui " +
                                            "long ci-dessous pour passer aux calculs."
                                        return@launch
                                    }
                                    val diagnostic = verdict(bitmap, objet)
                                    if (diagnostic.issue == IssueJuge.JUGE_INDISPONIBLE) {
                                        // Le modèle n'a pas vu la photo : ce
                                        // n'est pas un refus, et le compter
                                        // comme tel ferait rephotographier un
                                        // objet correct sans fin.
                                        pannesJuge += 1
                                        messageEchec = messagePanne(diagnostic.resume, pannesJuge)
                                        enAttenteVerdict = false
                                        if (suiteApresPanne(pannesJuge) == SuiteApresPanne.BASCULER) {
                                            onRenoncer()
                                        }
                                    } else {
                                        pannesJuge = 0
                                        if (etat.soumettre(diagnostic.accepte)) onInteraction()
                                        enAttenteVerdict = false
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                scope.launch {
                                    veilleCapture?.cancel()
                                    enAttenteVerdict = false
                                    // L'exception était avalée : l'utilisateur
                                    // appuyait et rien ne se passait, sans
                                    // qu'aucune cause ne soit affichée.
                                    messageEchec = "La photo n'a pas pu être prise " +
                                        "(erreur ${exception.imageCaptureError}). Réessaie, ou " +
                                        "appui long ci-dessous pour passer aux calculs."
                                }
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
                // Le repli n'est proposé d'un simple appui que lorsqu'il est
                // devenu la bonne réponse : caméra hors service, ou juge qui
                // ne répond plus. Voir le commentaire d'`onClick` ci-dessous.
                val replieAPortee = echecCamera ||
                    suiteApresPanne(pannesJuge) != SuiteApresPanne.REESSAYER
                Text(
                    text = if (replieAPortee) "Passer aux calculs" else "Je ne peux pas — appui long",
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
                            // Caméra en échec — ou juge muet plusieurs fois de
                            // suite — l'arbitrage s'inverse : la progression
                            // ne peut de toute façon plus avancer, et laisser
                            // quelqu'un chercher un appui long devant une
                            // sirène pour sortir d'un écran qui ne fonctionne
                            // pas serait gratuitement hostile.
                            onClick = if (replieAPortee) renoncer else ({}),
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
     * Décode la capture, la redresse selon l'orientation du capteur, et la
     * réduit. Tout se passe en mémoire : aucune image n'atteint le disque.
     *
     * Le redressement n'est pas cosmétique : le juge reçoit un bitmap déjà
     * constitué et ne peut pas connaître l'orientation de la prise de vue.
     * Sur un téléphone tenu en portrait, la rotation du capteur vaut
     * couramment 90° ; sans ce redressement le modèle recevrait une image
     * couchée et refuserait beaucoup plus souvent, **sans qu'aucun seuil ne
     * paraisse en cause**.
     *
     * La réduction sert l'appel réseau : la capture sort en pleine résolution
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

        // Les dimensions d'abord, sans allouer un seul pixel : décoder en
        // pleine résolution pour réduire ensuite demandait quelque 48 Mo pour
        // une photo de 12 Mpx, alors que l'image utile en fait moins de 10.
        // C'est le genre d'allocation qui fait tuer le processus par le
        // système — donc l'écran d'alarme — pendant que la sirène sonne.
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(octets, 0, octets.size, bornes)
        val options = BitmapFactory.Options().apply {
            inSampleSize = echantillonnage(bornes.outWidth, bornes.outHeight)
        }
        val brut = BitmapFactory.decodeByteArray(octets, 0, octets.size, options)
            ?: error("Photo indécodable")

        val reduit = reduire(brut)
        val rotation = imageInfo.rotationDegrees
        if (rotation == 0) return reduit
        val matrice = Matrix().apply { postRotate(rotation.toFloat()) }
        val pivote = Bitmap.createBitmap(reduit, 0, 0, reduit.width, reduit.height, matrice, true)
        // `createBitmap` peut rendre la source telle quelle si la matrice se
        // révèle sans effet : ne recycler qu'un intermédiaire réellement
        // distinct, jamais l'image rendue.
        if (pivote !== reduit) recycler(reduit)
        return pivote
    }

    /**
     * Ramène le côté long à [COTE_MAX_PX], en conservant les proportions, et
     * recycle la source dès qu'une copie réduite l'a remplacée : sans cela,
     * l'image de départ et sa copie cohabitaient jusqu'au prochain passage du
     * ramasse-miettes, à chaque photo.
     */
    private fun reduire(image: Bitmap): Bitmap {
        val coteLong = maxOf(image.width, image.height)
        if (coteLong <= COTE_MAX_PX) return image
        val facteur = COTE_MAX_PX.toFloat() / coteLong
        val reduit = Bitmap.createScaledBitmap(
            image,
            (image.width * facteur).toInt().coerceAtLeast(1),
            (image.height * facteur).toInt().coerceAtLeast(1),
            true,
        )
        if (reduit !== image) recycler(image)
        return reduit
    }

    /** Un recyclage raté ne vaut pas de faire échouer une photo. */
    private fun recycler(image: Bitmap) {
        runCatching { image.recycle() }
    }

    companion object {
        /**
         * Une photo, et une seule, quelle que soit la difficulté.
         *
         * Le réglage de difficulté ne s'applique pas à ce défi, et c'est
         * délibéré. Il ne pouvait de toute façon pas rendre un objet plus dur à
         * trouver — un objet « difficile » n'a aucun sens à six heures du matin
         * — donc il ne jouait que sur le nombre. Or se lever, traverser le
         * logement et cadrer un objet est déjà l'effort demandé : en exiger
         * deux ou trois n'ajoutait pas de la difficulté, seulement de la durée
         * devant une sirène.
         *
         * Constante et non fonction : un paramètre `difficulty` ignoré serait
         * un mensonge de signature, et le prochain lecteur y chercherait un
         * comportement qui n'existe pas.
         */
        const val NOMBRE_OBJETS = 1

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

        /**
         * Au-delà, on considère que `takePicture` ne rappellera jamais. Large
         * par rapport à une capture ordinaire (moins d'une seconde) : ce délai
         * n'est pas une exigence de rapidité, seulement le filet qui empêche
         * le bouton de rester bloqué sur « Vérification… » à vie.
         */
        private const val DELAI_MAX_CAPTURE_MS = 10_000L

        /** Échecs d'affilée du juge avant de proposer le repli calculs. */
        private const val PANNES_AVANT_PROPOSITION = 2

        /** Échecs d'affilée du juge avant d'y basculer sans plus attendre. */
        private const val PANNES_AVANT_BASCULE = 3

        /**
         * Ce qu'il faut faire après [pannesConsecutives] essais que le juge
         * n'a pas pu trancher. Rester bloqué devant une sirène est le pire
         * échec possible : passé un ou deux incidents, insister n'a plus de
         * sens, puisque aucune photo ne peut aboutir tant que le juge ne
         * répond pas.
         */
        internal fun suiteApresPanne(pannesConsecutives: Int): SuiteApresPanne = when {
            pannesConsecutives >= PANNES_AVANT_BASCULE -> SuiteApresPanne.BASCULER
            pannesConsecutives >= PANNES_AVANT_PROPOSITION -> SuiteApresPanne.PROPOSER_REPLI
            else -> SuiteApresPanne.REESSAYER
        }

        /**
         * Le texte affiché après un essai que le juge n'a pas pu trancher. Il
         * doit dire deux choses qu'un « Pas encore reconnu. Réessaie » ne
         * disait pas : que la photo n'est pas en cause, et par où sortir.
         */
        internal fun messagePanne(resume: String, pannesConsecutives: Int): String =
            if (suiteApresPanne(pannesConsecutives) == SuiteApresPanne.REESSAYER) {
                "Le juge n'a pas rendu de verdict — $resume. Ta photo n'est pas en cause : " +
                    "réessaie."
            } else {
                "Le juge ne répond toujours pas — $resume. Ta photo n'est pas en cause, et " +
                    "aucune photo ne pourra aboutir tant que c'est le cas : passe aux calculs " +
                    "avec le bouton ci-dessous."
            }

        /**
         * Facteur de sous-échantillonnage à demander au décodeur pour obtenir
         * une image encore au moins aussi grande que [coteMax] sur son côté
         * long — le redimensionnement exact reste à `reduire`, qui ne perdra
         * donc aucun détail utile.
         *
         * `BitmapFactory` n'accepte que des puissances de deux ; en donner une
         * autre le ferait arrondir en silence.
         */
        internal fun echantillonnage(largeur: Int, hauteur: Int, coteMax: Int = COTE_MAX_PX): Int {
            val coteLong = maxOf(largeur, hauteur)
            // Dimensions inconnues (décodage des bornes en échec) : on décode
            // en pleine résolution plutôt que de risquer une image inutilisable.
            if (coteLong <= 0 || coteMax <= 0) return 1
            var facteur = 1
            while (coteLong / (facteur * 2) >= coteMax) {
                facteur *= 2
            }
            return facteur
        }
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

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

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
