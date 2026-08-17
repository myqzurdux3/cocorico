package com.cocorico.ring

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper

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
     * Coupe l'extrait en cours et lance le nouveau. Enchaîner les appuis sur
     * plusieurs sonneries ne doit jamais les superposer.
     *
     * Renvoie `false` si le lecteur n'a pas pu être créé — voir [jouer] sur URI
     * pour la raison de ce retour.
     */
    fun jouer(sonnerie: Sonneries.Sonnerie): Boolean {
        arreter()

        val nouveau = creer(sonnerie.resId) ?: return false
        demarrerExtrait(nouveau)
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
    fun jouer(uri: Uri): Boolean {
        arreter()

        val nouveau = runCatching { creerDepuisUri(uri) }.getOrNull() ?: return false
        demarrerExtrait(nouveau)
        return true
    }

    private fun demarrerExtrait(nouveau: MediaPlayer) {
        lecteur = nouveau.also {
            it.isLooping = false
            it.setVolume(ATTENUATION, ATTENUATION)
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

        /**
         * Le volume d'alarme est souvent au maximum, et cet écran se consulte en
         * pleine journée, à côté d'autres gens. Un tiers d'amplitude laisse le
         * timbre reconnaissable sans faire sursauter.
         */
        const val ATTENUATION = 0.35f
    }
}
