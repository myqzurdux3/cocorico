package com.cocorico.ring

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager

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
        // Défense en profondeur : un second démarrage ne doit jamais abandonner
        // un lecteur en cours. Il continuerait de tourner en boucle sans que
        // personne ne détienne plus de référence pour l'arrêter.
        libererLecteur()

        if (volumeOrigine == null) {
            volumeOrigine = VolumeOrigine.lire(context)
                ?: audio.getStreamVolume(AudioManager.STREAM_ALARM).also {
                    VolumeOrigine.ecrire(context, it)
                }
        }
        appliquer(VolumeState.PLEIN)

        val lecteur = creerSource(sonnerie)
            // Ressource illisible : plutôt que de rester muet, on se rabat sur la
            // première sonnerie embarquée. Une alarme silencieuse est le seul
            // échec que cette application n'a pas le droit de produire. Couvre
            // aussi bien une ressource embarquée introuvable qu'une sonnerie
            // personnalisée disparue, corrompue ou dont la permission a expiré.
            ?: creer(Sonneries.toutes.first().resId)
            // Dernier recours : si `generateAudioSessionId()` est lui-même en
            // échec, les deux tentatives précédentes, qui partagent le même
            // overload de `MediaPlayer.create`, échouent à l'identique. La
            // voie historique sans session explicite en est indépendante.
            ?: creerDernierRecours(Sonneries.toutes.first().resId)
        player = lecteur?.also {
            it.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            it.isLooping = true
            it.start()
        }
    }

    /** Vrai tant qu'une sonnerie tourne réellement. */
    fun estEnLecture(): Boolean = runCatching { player?.isPlaying == true }.getOrDefault(false)

    /**
     * Les attributs d'alarme sont posés AVANT la préparation. `MediaPlayer.create`
     * sans attributs prépare le lecteur avec les attributs média par défaut, et
     * les poser après coup n'est pas garanti : la sonnerie partirait alors sur
     * STREAM_MUSIC, que le mode silencieux coupe et que tout le pilotage de
     * volume sur STREAM_ALARM ignore.
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
     * Choisit la source demandée — voir [SonneriePersonnaliseeLogique.sourceAJouer]
     * pour la décision — et tente de créer le lecteur correspondant. `null`
     * dans tous les cas d'échec, jamais d'exception : c'est [demarrer] qui
     * enchaîne alors sur le repli embarqué.
     */
    private fun creerSource(sonnerie: Sonneries.Sonnerie): MediaPlayer? {
        val source = SonneriePersonnaliseeLogique.sourceAJouer(
            personnalisee = sonnerie.personnalisee,
            uriPersistee = SonneriePersonnaliseeStore.lireUri(context),
        )
        return when (source) {
            is SonneriePersonnaliseeLogique.SourceAJouer.Personnalisee ->
                runCatching { creerDepuisUri(Uri.parse(source.uri)) }.getOrNull()
            SonneriePersonnaliseeLogique.SourceAJouer.Embarquee -> creer(sonnerie.resId)
        }
    }

    /**
     * Mêmes attributs d'alarme que [creer], posés avant la préparation pour
     * la même raison. `MediaPlayer.create` sur une URI de contenu peut lever
     * une `SecurityException` (permission perdue, fournisseur disparu) là où
     * la variante ressource ne le fait jamais : l'appelant l'encapsule dans
     * `runCatching`.
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

    /**
     * Chemin de dernier recours, indépendant de [creer] : l'ancien overload
     * `MediaPlayer.create(Context, Int)` ne dépend pas de
     * `generateAudioSessionId()`. Les attributs d'alarme sont posés après coup,
     * comme avant l'unification sur le nouvel overload.
     */
    private fun creerDernierRecours(resId: Int): MediaPlayer? =
        MediaPlayer.create(context, resId)?.apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }

    private fun libererLecteur() {
        player?.run {
            runCatching { if (isPlaying) stop() }
            release()
        }
        player = null
    }

    /** PLEIN = maximum du flux alarme ; BAISSE = 30 % de ce maximum. */
    /**
     * Plafond sonore choisi par l'utilisateur, en pourcentage du maximum de
     * l'appareil. Réglé par l'appelant dès qu'il a lu la configuration ;
     * jusque-là, le maximum, qui est le comportement historique — mieux vaut
     * une alarme trop forte pendant une fraction de seconde qu'une alarme trop
     * faible.
     */
    var volumeMaxPourcent: Int = NiveauxVolume.POURCENT_MAXIMAL

    fun appliquer(state: VolumeState) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val cible = when (state) {
            VolumeState.PLEIN -> NiveauxVolume.plein(max, volumeMaxPourcent)
            VolumeState.BAISSE -> NiveauxVolume.baisse(max, volumeMaxPourcent)
        }
        audio.setStreamVolume(AudioManager.STREAM_ALARM, cible, 0)
    }

    /** Idempotent : appelable depuis la résolution du défi comme depuis `onDestroy`. */
    fun arreter() {
        libererLecteur()
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
