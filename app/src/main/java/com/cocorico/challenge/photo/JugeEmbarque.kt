package com.cocorico.challenge.photo

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Le juge embarqué du défi photo : reconnaissance d'images de ML Kit, modèle
 * livré avec l'application, sans réseau ni service distant pour rendre un
 * verdict.
 *
 * Toute exception vaut refus. Un `Task` en échec, un modèle absent, une
 * image illisible : rien de tout cela ne doit remonter jusqu'à l'écran
 * d'alarme — l'exception y ferait planter l'application devant une sirène
 * que l'utilisateur ne pourrait alors plus arrêter qu'en forçant l'extinction
 * du téléphone.
 *
 * Le client ML Kit détient des ressources natives ; [fermer] doit être
 * appelé quand le défi disparaît, sans quoi elles fuient à chaque réveil.
 */
class JugeEmbarque : JugePhoto {

    /**
     * Le client ML Kit, construit une seule fois à la création du juge.
     * `null` si sa construction a échoué — c'est ce qui rend [disponible]
     * honnête et [accepte] toujours refusant plutôt que plantant.
     */
    private val etiqueteur: ImageLabeler? = runCatching {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }.getOrNull()

    /**
     * Vrai si la reconnaissance embarquée peut fonctionner sur cet appareil.
     * L'écran de défi s'en sert pour décider de se replier sur un autre défi
     * quand ce juge ne peut pas rendre de verdict — répondre vrai à tort
     * empêcherait ce repli de se déclencher et laisserait l'alarme
     * impossible à arrêter.
     */
    fun disponible(): Boolean = etiqueteur != null

    override suspend fun accepte(image: Bitmap, objet: ObjetPhoto): Boolean {
        val client = etiqueteur ?: return false
        return runCatching {
            JugementPhoto.accepte(objet, etiqueter(client, image))
        }.getOrDefault(false)
    }

    /**
     * Les étiquettes brutes reconnues dans [image], avec leur confiance, sans
     * jugement ni seuil. Sert à l'écran d'essai : un refus ne dit pas s'il
     * vient d'un objet mal reconnu, d'un seuil trop haut, ou d'une étiquette
     * absente du catalogue — ces trois causes appellent trois correctifs
     * différents, et seule la liste brute permet de les distinguer.
     *
     * Liste vide en cas d'échec, comme partout ailleurs dans cette classe.
     */
    suspend fun etiquettes(image: Bitmap): List<EtiquetteReconnue> {
        val client = etiqueteur ?: return emptyList()
        return runCatching { etiqueter(client, image) }.getOrDefault(emptyList())
    }

    /**
     * Ferme le client ML Kit. À appeler quand l'écran du défi disparaît, qu'un
     * verdict soit en cours ou non — voir [JugePhoto.fermer].
     */
    override fun fermer() {
        etiqueteur?.close()
    }

    /**
     * Fait parler [client] sur [image] et convertit son résultat en
     * [EtiquetteReconnue]. Le `Task` de ML Kit n'a pas de mécanisme
     * d'annulation propre : si la coroutine appelante est annulée pendant
     * l'attente, [suspendCancellableCoroutine] la marque comme inactive et
     * les écouteurs, une fois le `Task` terminé, vérifient cet état avant de
     * reprendre — pour ne jamais tenter de reprendre une coroutine déjà
     * morte.
     */
    private suspend fun etiqueter(client: ImageLabeler, image: Bitmap): List<EtiquetteReconnue> =
        suspendCancellableCoroutine { continuation ->
            client.process(InputImage.fromBitmap(image, 0))
                .addOnSuccessListener { etiquettes ->
                    if (continuation.isActive) continuation.resume(convertirEtiquettes(etiquettes))
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(emptyList())
                }
        }

    companion object {

        /**
         * Convertit les étiquettes rendues par ML Kit vers le type pur du
         * jugement. Extraite pour rester testable sans caméra ni Android : la
         * seule part de conversion qui a une décision à vérifier.
         */
        fun convertirEtiquettes(etiquettes: List<ImageLabel>): List<EtiquetteReconnue> =
            etiquettes.map { EtiquetteReconnue(it.text, it.confidence) }
    }
}
