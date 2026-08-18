package com.cocorico.alarm

import android.content.Context

/**
 * Mémorise si une alarme est en train de sonner. Survit au kill du processus et
 * au redémarrage : c'est ce qui permet à BootReceiver de relancer une alarme
 * interrompue par une extinction du téléphone.
 *
 * Le drapeau seul ne suffit pas : il n'est effacé que par une résolution du
 * défi. Un arrêt forcé depuis les réglages, un tueur de tâches constructeur ou
 * un crash le laissent à `true` pour toujours, et au redémarrage suivant
 * l'application ferait sonner une alarme fantôme. On horodate donc chaque signe
 * de vie et on considère le drapeau comme faux au-delà de [FENETRE_VALIDITE_MS].
 */
// `commit()` est délibéré, et lint a raison de le signaler par défaut : il
// écrit de façon synchrone. C'est précisément ce qu'on veut ici. La valeur doit
// être sur le disque **avant** que le processus puisse mourir — un `apply()`
// asynchrone perdrait le drapeau si le système tuait l'application entre
// l'écriture et le vidage, et l'alarme repartirait au démarrage suivant alors
// qu'elle a été résolue, ou l'inverse. Supprimé avec sa raison plutôt que laissé
// dans la liste : un avertissement qu'on sait faux finit par faire ignorer les
// autres.
@Suppress("ApplySharedPref")
object AlarmState {

    private const val FICHIER = "cocorico_alarm_state"
    private const val CLE_EN_COURS = "alarme_en_cours"
    private const val CLE_DERNIER_SIGNE = "alarme_dernier_signe"

    /**
     * Distincte de [CLE_DERNIER_SIGNE], et il faut que ça le reste : le signe
     * de vie est réécrit à chaque passage du filet de secours, donc toutes les
     * 30 secondes. Celle-ci n'est posée qu'une fois, au vrai déclenchement.
     */
    private const val CLE_DECLENCHEMENT = "alarme_declenchement"

    /**
     * L'instant de la sonnerie **attendue**, écrit à la programmation et effacé
     * par [marquerDemarree] quand elle part réellement. Une attente passée et
     * jamais effacée est la seule trace qu'une alarme a échoué en silence.
     */
    private const val CLE_ATTENDUE = "alarme_attendue"

    /**
     * L'instant d'une sonnerie constatée manquée, en attente d'être vue par
     * l'utilisateur. Séparée de [CLE_ATTENDUE] parce que reprogrammer écrase
     * l'attente : sans ce second emplacement, la preuve de l'échec serait
     * effacée par la reprogrammation qui suit immédiatement le constat.
     */
    private const val CLE_MANQUEE = "alarme_manquee"

    /**
     * Une alarme qui hurle depuis plus d'une heure sans être résolue est déjà
     * perdue : la relancer au démarrage suivant n'aiderait personne, alors
     * qu'une alarme fantôme au milieu de la journée est un bug visible.
     */
    const val FENETRE_VALIDITE_MS = 60 * 60 * 1000L

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    /**
     * Décision pure, testable sans Android : le drapeau est-il encore crédible ?
     *
     * Un horodatage absent (installation antérieure à cette version) ou un delta
     * négatif (horloge reculée, changement de fuseau) rendent le drapeau
     * inexploitable : on tranche du côté du silence, jamais de l'alarme
     * fantôme — l'alarme réelle, elle, reste programmée par ailleurs.
     */
    fun estEncoreFraiche(
        enCours: Boolean,
        dernierSigneMs: Long,
        maintenantMs: Long,
        fenetreMs: Long = FENETRE_VALIDITE_MS,
    ): Boolean {
        if (!enCours || dernierSigneMs <= 0L) return false
        val ecart = maintenantMs - dernierSigneMs
        return ecart in 0L until fenetreMs
    }

    /** Signe de vie : l'alarme sonne, et elle sonnait encore à cet instant. */
    fun marquerDemarree(context: Context) {
        prefs(context).edit()
            .putBoolean(CLE_EN_COURS, true)
            .putLong(CLE_DERNIER_SIGNE, System.currentTimeMillis())
            // L'attente est honorée : la sonnerie est partie. C'est le seul
            // endroit qui l'efface, et c'est voulu — toute autre sortie laisse
            // la trace, donc l'échec reste constatable.
            .remove(CLE_ATTENDUE)
            .commit()
    }

    /**
     * Retient l'instant du **vrai** déclenchement, une seule fois par alarme.
     *
     * L'écran d'alarme horodatait son propre `onCreate`. Si le service doit le
     * relancer après une mort de l'activité, la durée enregistrée dans
     * l'historique repartait de zéro et faussait toutes les statistiques. Écrit
     * ici plutôt que dans l'activité : le service, lui, survit à l'écran.
     */
    fun marquerDeclenchement(context: Context) {
        val prefs = prefs(context)
        val retenu = instantARetenir(
            existant = prefs.getLong(CLE_DECLENCHEMENT, 0L),
            maintenant = System.currentTimeMillis(),
        )
        prefs.edit().putLong(CLE_DECLENCHEMENT, retenu).commit()
    }

    /** L'instant du déclenchement en cours, ou 0 si aucune alarme ne sonne. */
    fun instantDeclenchement(context: Context): Long = prefs(context).getLong(CLE_DECLENCHEMENT, 0L)

    /**
     * Décision pure derrière [marquerDeclenchement] : on garde le premier
     * horodatage plausible. Le service repasse par ce chemin à chaque relance
     * (`START_STICKY`, secours, processus tué) et réécrire ferait repartir la
     * durée de zéro à chaque fois ; à l'inverse, une valeur nulle ou négative
     * ne peut venir que d'un fichier abîmé et donnerait des durées de plusieurs
     * décennies dans les statistiques.
     */
    fun instantARetenir(existant: Long, maintenant: Long): Long = if (existant > 0L) existant else maintenant

    /** Arrêt délibéré : défi résolu. Le drapeau et ses horodatages disparaissent. */
    fun marquerTerminee(context: Context) {
        prefs(context).edit()
            .putBoolean(CLE_EN_COURS, false)
            .remove(CLE_DERNIER_SIGNE)
            .remove(CLE_DECLENCHEMENT)
            .commit()
    }

    /**
     * Enregistre l'instant de la prochaine sonnerie attendue.
     *
     * Promeut d'abord une attente précédente restée sans réponse : c'est le
     * seul moment où on peut la constater, juste avant de l'écraser.
     */
    fun noterAttente(context: Context, instantMs: Long) {
        promouvoirSiManquee(context)
        prefs(context).edit().putLong(CLE_ATTENDUE, instantMs).commit()
    }

    /**
     * Plus rien n'est attendu : désarmement, ou aucun jour actif. Le constat de
     * manquement est fait avant l'oubli — désarmer après une alarme ratée ne
     * doit pas effacer le fait qu'elle a raté.
     */
    fun oublierAttente(context: Context) {
        promouvoirSiManquee(context)
        prefs(context).edit().remove(CLE_ATTENDUE).commit()
    }

    /** L'instant de la sonnerie manquée à signaler, ou `0`. */
    fun sonnerieManquee(context: Context): Long = prefs(context).getLong(CLE_MANQUEE, 0L)

    /** L'utilisateur a vu le message : on ne le lui remontre pas. */
    fun acquitterManquee(context: Context) {
        prefs(context).edit().remove(CLE_MANQUEE).commit()
    }

    /**
     * Une seule sonnerie manquée est retenue, la plus récente. Empiler les
     * échecs d'un téléphone resté éteint une semaine noierait le message qui
     * compte — celui de ce matin.
     */
    private fun promouvoirSiManquee(context: Context) {
        val prefs = prefs(context)
        val attendue = prefs.getLong(CLE_ATTENDUE, 0L)
        if (!AttenteSonnerie.estManquee(attendue, System.currentTimeMillis())) return
        prefs.edit().putLong(CLE_MANQUEE, attendue).remove(CLE_ATTENDUE).commit()
    }

    fun estEnCours(context: Context): Boolean {
        val prefs = prefs(context)
        return estEncoreFraiche(
            enCours = prefs.getBoolean(CLE_EN_COURS, false),
            dernierSigneMs = prefs.getLong(CLE_DERNIER_SIGNE, 0L),
            maintenantMs = System.currentTimeMillis(),
        )
    }
}
