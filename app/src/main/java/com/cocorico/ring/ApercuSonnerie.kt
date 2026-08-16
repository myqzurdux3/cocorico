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
     */
    fun jouer(sonnerie: Sonneries.Sonnerie) {
        arreter()

        val nouveau = creer(sonnerie.resId) ?: return
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
