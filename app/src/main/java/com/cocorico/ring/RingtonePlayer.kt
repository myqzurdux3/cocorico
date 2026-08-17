package com.cocorico.ring

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import android.util.Log

/**
 * Lecture en boucle sur STREAM_ALARM, qui ignore le mode silencieux.
 * Mémorise le volume système d'origine et le restaure à l'arrêt : sans ça,
 * l'utilisateur retrouve son téléphone à fond toute la journée.
 */
class RingtonePlayer(private val context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var volumeOrigine: Int? = null
    private var relancesApresErreur = 0

    /**
     * Démarre la sonnerie ET pousse le volume au maximum autorisé : c'est
     * l'entrée normale du tout premier démarrage de l'alarme.
     *
     * La lecture part AVANT le volume, et pas l'inverse. `setStreamVolume` sur
     * STREAM_ALARM lève une `SecurityException` quand « Ne pas déranger » est
     * actif et que l'application n'a pas ACCESS_NOTIFICATION_POLICY : dans
     * l'ordre inverse, cette exception remontait hors de `demarrer`, à travers
     * le `launch` du service, et aucun lecteur n'avait encore été créé — alarme
     * parfaitement muette. Sonner au volume que l'utilisateur avait laissé est
     * un moindre mal ; ne pas sonner du tout n'en est pas un.
     */
    fun demarrer(sonnerie: Sonneries.Sonnerie) {
        demarrerLecture(sonnerie)
        appliquer(VolumeState.PLEIN)
    }

    /**
     * Lecture seule : ni volume ni état de la machine à états ne sont touchés.
     *
     * C'est l'entrée du filet de secours, qui ne cherche qu'à faire repartir un
     * son disparu. Repousser le volume à PLEIN depuis là serait un contresens :
     * si l'utilisateur a le téléphone en main, [VolumeStateMachine] est déjà en
     * BAISSE et ne renotifiera jamais le retour à PLEIN — le volume resterait
     * au maximum, en main, jusqu'à la fin de l'alarme.
     */
    fun demarrerLecture(sonnerie: Sonneries.Sonnerie) {
        // Défense en profondeur : un second démarrage ne doit jamais abandonner
        // un lecteur en cours. Il continuerait de tourner en boucle sans que
        // personne ne détienne plus de référence pour l'arrêter.
        libererLecteur()
        relancesApresErreur = 0
        installerLecteur(sonnerie)
    }

    /** Crée, configure et lance le lecteur. Le champ [player] est la seule référence. */
    private fun installerLecteur(sonnerie: Sonneries.Sonnerie) {
        player = creerLecteur(sonnerie)?.also {
            it.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            it.isLooping = true
            it.setOnErrorListener { _, _, _ -> onErreurLecteur(sonnerie) }
            it.start()
        }
    }

    /**
     * Chaîne de replis, du plus fidèle au choix de l'utilisateur au plus
     * grossier. Une alarme silencieuse est le seul échec que cette application
     * n'a pas le droit de produire.
     */
    private fun creerLecteur(sonnerie: Sonneries.Sonnerie): MediaPlayer? =
        creerSource(sonnerie)
            // Source illisible : ressource embarquée introuvable, ou sonnerie
            // personnalisée disparue, corrompue, permission expirée. Le repli
            // est la sonnerie la PLUS FORTE, pas la première de la liste :
            // quelqu'un qui a choisi la sirène l'a choisie parce que le coq ne
            // le réveille pas. Et la substitution est tracée, sinon personne
            // n'apprend jamais que sa sonnerie a été remplacée.
            ?: creerRepli(sonnerie, "source illisible")
            // Dernier recours : si `generateAudioSessionId()` est lui-même en
            // échec, les deux tentatives précédentes, qui partagent le même
            // overload de `MediaPlayer.create`, échouent à l'identique. La
            // voie historique sans session explicite en est indépendante.
            ?: creerDernierRecours(Sonneries.repliLaPlusForte.resId)
                .also { if (it != null) tracerRepli(sonnerie, "session audio indisponible") }

    private fun creerRepli(sonnerie: Sonneries.Sonnerie, cause: String): MediaPlayer? =
        creer(Sonneries.repliLaPlusForte.resId)?.also { tracerRepli(sonnerie, cause) }

    private fun tracerRepli(sonnerie: Sonneries.Sonnerie, cause: String) {
        Log.w(
            TAG,
            "Sonnerie « ${sonnerie.nom} » injouable ($cause) : repli sur " +
                "« ${Sonneries.repliLaPlusForte.nom} »",
        )
    }

    /**
     * Une mort du serveur média coupe le son sans rien lever : sans cet
     * écouteur, le silence durait jusqu'au prochain passage du filet de secours
     * du service, soit jusqu'à 30 s. On relance immédiatement.
     *
     * Le nombre de relances est plafonné : une source définitivement cassée
     * rejouerait l'erreur à chaque tentative, et une relance immédiate en
     * boucle occuperait le processeur sans jamais produire de son. Au-delà, on
     * laisse la main au filet de secours, qui réessaie à son rythme.
     *
     * `true` : l'erreur est traitée, le lecteur en défaut ne doit pas en plus
     * déclencher `onCompletion`.
     */
    private fun onErreurLecteur(sonnerie: Sonneries.Sonnerie): Boolean {
        if (relancesApresErreur >= RELANCES_MAX) {
            Log.w(TAG, "Lecteur en erreur, $RELANCES_MAX relances épuisées")
            return true
        }
        relancesApresErreur++
        Log.w(TAG, "Lecteur en erreur, relance $relancesApresErreur/$RELANCES_MAX")
        // Pas `demarrerLecture` : c'est la même session de sonnerie qui se
        // poursuit, le compteur de relances ne doit pas repartir de zéro.
        libererLecteur()
        installerLecteur(sonnerie)
        return true
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
            // L'entrée personnalisée n'a pas de ressource embarquée : son
            // `resId` vaut -1, valeur que la KDoc de [Sonneries.Sonnerie]
            // déclare inutilisable. On tombe ici quand l'URI a disparu du
            // magasin ; le passer à `MediaPlayer.create` ne « marchait » que
            // parce qu'il renvoie null sur une ressource introuvable. Le dire
            // explicitement : pas de source, la chaîne de replis prend le
            // relais.
            SonneriePersonnaliseeLogique.SourceAJouer.Embarquee ->
                if (sonnerie.personnalisee) null else creer(sonnerie.resId)
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
            // L'écouteur d'erreur est retiré d'abord : cette libération peut
            // être déclenchée depuis ce même écouteur, et un lecteur relâché ne
            // doit pas pouvoir y rentrer une seconde fois.
            runCatching { setOnErrorListener(null) }
            runCatching { if (isPlaying) stop() }
            release()
        }
        player = null
    }

    /**
     * Plafond sonore choisi par l'utilisateur, en pourcentage du maximum de
     * l'appareil. Réglé par l'appelant dès qu'il a lu la configuration ;
     * jusque-là, le maximum, qui est le comportement historique — mieux vaut
     * une alarme trop forte pendant une fraction de seconde qu'une alarme trop
     * faible.
     */
    var volumeMaxPourcent: Int = NiveauxVolume.POURCENT_MAXIMAL

    /**
     * `runCatching` : sous « Ne pas déranger », et sans ACCESS_NOTIFICATION_POLICY
     * au manifeste, `setStreamVolume` lève une `SecurityException`. Elle ne doit
     * jamais interrompre l'appelant — dans le service elle laissait l'alarme
     * muette, dans l'activité elle casserait le pilotage du volume à la prise en
     * main. Le volume reste alors celui que l'utilisateur avait laissé : c'est
     * dégradé, jamais silencieux.
     */
    fun appliquer(state: VolumeState) {
        memoriserVolumeOrigine()
        runCatching {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val cible = when (state) {
                VolumeState.PLEIN -> NiveauxVolume.plein(max, volumeMaxPourcent)
                VolumeState.BAISSE -> NiveauxVolume.baisse(max, volumeMaxPourcent)
            }
            audio.setStreamVolume(AudioManager.STREAM_ALARM, cible, 0)
        }.onFailure { Log.w(TAG, "Volume d'alarme non modifiable : $it") }
    }

    /**
     * Invariant : **la trace disque fait foi, et n'est jamais réécrite tant
     * qu'elle existe.** Le volume courant n'est capturé que si aucune trace
     * n'existe, parce qu'une fois PLEIN appliqué le volume courant est celui de
     * l'alarme : le recapturer reviendrait à enregistrer le maximum comme état
     * normal du téléphone, et la restauration finale laisserait l'appareil à
     * fond pour la journée. Corollaire : cette mémorisation doit précéder toute
     * écriture sur le volume, d'où sa place en tête d'[appliquer] plutôt que
     * dans le seul [demarrer] — l'activité d'alarme possède sa propre instance
     * et applique PLEIN sans jamais passer par [demarrer].
     *
     * La trace est persistée sur disque : si le processus meurt sans passer par
     * [arreter] — arrêt forcé depuis les réglages — la valeur survit, et la
     * session suivante restaure le bon volume.
     */
    private fun memoriserVolumeOrigine() {
        if (volumeOrigine != null) return
        volumeOrigine = VolumeOrigine.lire(context)
            ?: runCatching { audio.getStreamVolume(AudioManager.STREAM_ALARM) }
                .getOrNull()
                ?.also { VolumeOrigine.ecrire(context, it) }
    }

    /**
     * Idempotent : appelable depuis la résolution du défi comme depuis `onDestroy`.
     *
     * La trace disque n'est effacée que si la restauration a réellement eu lieu.
     * L'effacer après un `setStreamVolume` en échec — « Ne pas déranger » —
     * perdrait la seule mémoire du volume d'origine, et l'appareil resterait au
     * maximum sans que personne ne puisse plus le rétablir.
     */
    fun arreter() {
        libererLecteur()
        val origine = volumeOrigine ?: VolumeOrigine.lire(context)
        val restaure = origine == null || runCatching {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, origine, 0)
        }.onFailure { Log.w(TAG, "Volume d'origine non restauré : $it") }.isSuccess
        volumeOrigine = null
        if (restaure) VolumeOrigine.effacer(context)
    }

    private companion object {
        const val TAG = "CocoricoSonnerie"

        /** Au-delà, la source est cassée pour de bon : le filet de secours prend le relais. */
        const val RELANCES_MAX = 3
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
