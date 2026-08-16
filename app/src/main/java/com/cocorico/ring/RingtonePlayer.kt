package com.cocorico.ring

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer

/**
 * Lecture en boucle sur STREAM_ALARM, qui ignore le mode silencieux.
 * Mémorise le volume système d'origine et le restaure à l'arrêt : sans ça,
 * l'utilisateur retrouve son téléphone à fond toute la journée.
 */
class RingtonePlayer(private val context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var volumeOrigine: Int? = null

    /**
     * Le volume d'origine est aussi persisté sur disque. Si le processus meurt
     * sans passer par [arreter] — arrêt forcé depuis les réglages, par exemple —
     * la valeur survit, et la session suivante restaure le bon volume au lieu de
     * prendre le maximum en cours pour l'état normal du téléphone.
     */
    fun demarrer(sonnerie: Sonneries.Sonnerie) {
        if (volumeOrigine == null) {
            volumeOrigine = VolumeOrigine.lire(context)
                ?: audio.getStreamVolume(AudioManager.STREAM_ALARM).also {
                    VolumeOrigine.ecrire(context, it)
                }
        }
        appliquer(VolumeState.PLEIN)

        val lecteur = MediaPlayer.create(context, sonnerie.resId)
        if (lecteur == null) {
            // Ressource illisible : plutôt que de rester muet, on se rabat sur la
            // sonnerie par défaut du système. Une alarme silencieuse est le seul
            // échec que cette application n'a pas le droit de produire.
            player = MediaPlayer.create(context, Sonneries.toutes.first().resId)
            player?.configurerEtLancer()
            return
        }
        player = lecteur.also { it.configurerEtLancer() }
    }

    private fun MediaPlayer.configurerEtLancer() {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        isLooping = true
        start()
    }

    /** PLEIN = maximum du flux alarme ; BAISSE = 30 % de ce maximum. */
    fun appliquer(state: VolumeState) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val cible = when (state) {
            VolumeState.PLEIN -> max
            VolumeState.BAISSE -> (max * 0.3f).toInt().coerceAtLeast(1)
        }
        audio.setStreamVolume(AudioManager.STREAM_ALARM, cible, 0)
    }

    /** Idempotent : appelable depuis la résolution du défi comme depuis `onDestroy`. */
    fun arreter() {
        player?.run {
            if (isPlaying) stop()
            release()
        }
        player = null
        val origine = volumeOrigine ?: VolumeOrigine.lire(context)
        origine?.let { audio.setStreamVolume(AudioManager.STREAM_ALARM, it, 0) }
        volumeOrigine = null
        VolumeOrigine.effacer(context)
    }
}

/**
 * Mémoire disque du volume d'alarme d'avant la sonnerie. Le volume système
 * n'est pas réinitialisé à la mort du processus : sans cette trace, un arrêt
 * forcé pendant l'alarme laisserait le téléphone à fond toute la journée.
 */
private object VolumeOrigine {

    private const val FICHIER = "cocorico_volume"
    private const val CLE = "volume_origine"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    fun lire(context: Context): Int? =
        prefs(context).getInt(CLE, -1).takeIf { it >= 0 }

    fun ecrire(context: Context, volume: Int) {
        prefs(context).edit().putInt(CLE, volume).commit()
    }

    fun effacer(context: Context) {
        prefs(context).edit().remove(CLE).commit()
    }
}
