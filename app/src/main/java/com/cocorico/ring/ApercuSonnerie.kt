package com.cocorico.ring

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Joue un court extrait d'une sonnerie, pour l'écouter avant de la choisir.
 *
 * Deux différences volontaires avec [RingtonePlayer], qui fait sonner l'alarme
 * pour de vrai :
 *
 * - **Le volume système n'est jamais touché.** L'alarme, elle, force le flux à
 *   fond et mémorise la valeur d'origine pour la restaurer. Un aperçu qui ferait
 *   la même chose laisserait le téléphone à fond si l'utilisateur quittait
 *   l'écran au mauvais moment. On se contente d'atténuer le lecteur lui-même.
 * - **La lecture s'arrête toute seule** au bout de [DUREE_MS], et ne boucle pas.
 *
 * Les attributs restent ceux d'une alarme : c'est le trajet audio réel, donc le
 * timbre entendu ici est celui qui réveillera. Conséquence assumée : si le
 * volume d'alarme du téléphone est à zéro, l'aperçu est muet — mais dans ce cas
 * l'alarme le serait aussi, et l'écouter ici est le meilleur moyen de s'en
 * apercevoir avant la nuit plutôt qu'après.
 */
class ApercuSonnerie(private val context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val minuteur = Handler(Looper.getMainLooper())
    private var lecteur: MediaPlayer? = null

    /**
     * L'identifiant de la sonnerie dont l'extrait joue, ou `null`.
     *
     * `mutableStateOf` et non un simple champ : l'écran s'en sert pour décider
     * ce que fera le prochain appui (voir [BasculeApercu]), et il doit se
     * recomposer quand l'extrait se termine tout seul — sinon un deuxième appui
     * après la fin tenterait d'arrêter un son déjà éteint et ne relancerait
     * rien.
     */
    var enCours: String? by mutableStateOf(null)
        private set

    /**
     * Coupe l'extrait en cours et lance le nouveau. Enchaîner les appuis sur
     * plusieurs sonneries ne doit jamais les superposer.
     *
     * [volumeMaxPourcent] est le plafond réglé par l'utilisateur : l'extrait le
     * suit, faute de quoi l'aperçu s'entendrait pareil quel que soit le
     * réglage — voir [NiveauxVolume.volumeApercu].
     *
     * Renvoie `false` si le lecteur n'a pas pu être créé — voir [jouer] sur URI
     * pour la raison de ce retour.
     */
    fun jouer(sonnerie: Sonneries.Sonnerie, volumeMaxPourcent: Int): Boolean {
        arreter()

        val nouveau = creer(sonnerie.resId) ?: return false
        demarrerExtrait(nouveau, sonnerie.id, volumeMaxPourcent)
        return true
    }

    /**
     * Même aperçu, pour un fichier importé plutôt qu'une ressource embarquée.
     * Utilisé à la fois pour rejouer une sonnerie personnalisée déjà choisie
     * et, à l'écran de sélection, pour donner à entendre un fichier tout
     * juste importé — au-delà de la simple vérification qu'il est lisible.
     *
     * Renvoie `false` si le lecteur n'a pas pu être créé, pour que l'appelant
     * l'affiche. Sortir en silence laissait l'utilisateur appuyer sur sa
     * sonnerie, ne rien entendre et ne rien apprendre — alors que la panne
     * qu'il vient de rencontrer est exactement celle qu'il doit découvrir
     * maintenant plutôt que le lendemain matin.
     */
    fun jouer(uri: Uri, volumeMaxPourcent: Int): Boolean {
        arreter()

        val nouveau = runCatching { creerDepuisUri(uri) }.getOrNull() ?: return false
        demarrerExtrait(nouveau, Sonneries.ID_PERSONNALISEE, volumeMaxPourcent)
        return true
    }

    private fun demarrerExtrait(nouveau: MediaPlayer, id: String, volumeMaxPourcent: Int) {
        val volume = NiveauxVolume.volumeApercu(volumeMaxPourcent)
        enCours = id
        lecteur = nouveau.also {
            it.isLooping = false
            it.setVolume(volume, volume)
            // Extrait plus court que le fichier : on coupe au bout du temps
            // imparti. Extrait plus court que le temps imparti : on libère dès
            // la fin plutôt que de garder un lecteur inutile.
            it.setOnCompletionListener { arreter() }
            it.start()
        }
        minuteur.postDelayed(::arreter, DUREE_MS)
    }

    /** Idempotent : appelable à la sortie de l'écran sans savoir si ça joue. */
    fun arreter() {
        minuteur.removeCallbacksAndMessages(null)
        // Remis avant le test de sortie anticipée : l'extrait qui se termine
        // seul passe aussi par ici, et laisser l'identifiant en place ferait
        // croire à l'écran qu'un son joue encore.
        enCours = null
        val courant = lecteur ?: return
        lecteur = null
        runCatching { courant.stop() }
        runCatching { courant.release() }
    }

    /**
     * Attributs posés avant la préparation, comme dans [RingtonePlayer] : les
     * poser après coup n'est pas garanti, et l'extrait partirait sur le flux
     * média, que le mode silencieux coupe.
     */
    private fun creer(resId: Int): MediaPlayer? = MediaPlayer.create(
        context,
        Uri.parse("android.resource://${context.packageName}/$resId"),
        null,
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build(),
        audio.generateAudioSessionId(),
    )

    /**
     * Comme [creer], pour une URI de contenu plutôt qu'une ressource. Peut
     * lever une `SecurityException` là où la variante ressource ne le fait
     * jamais : c'est l'appelant qui l'encapsule dans `runCatching`.
     */
    private fun creerDepuisUri(uri: Uri): MediaPlayer? = MediaPlayer.create(
        context,
        uri,
        null,
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build(),
        audio.generateAudioSessionId(),
    )

    private companion object {
        /** Assez pour reconnaître le caractère d'une sonnerie, trop court pour agacer. */
        const val DUREE_MS = 3_000L
    }
}
