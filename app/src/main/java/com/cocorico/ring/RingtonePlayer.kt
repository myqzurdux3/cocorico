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

    fun demarrer(sonnerie: Sonneries.Sonnerie) {
        if (volumeOrigine == null) {
            volumeOrigine = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        }
        appliquer(VolumeState.PLEIN)

        player = MediaPlayer.create(context, sonnerie.resId).apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            isLooping = true
            start()
        }
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

    fun arreter() {
        player?.run {
            if (isPlaying) stop()
            release()
        }
        player = null
        volumeOrigine?.let { audio.setStreamVolume(AudioManager.STREAM_ALARM, it, 0) }
        volumeOrigine = null
    }
}
