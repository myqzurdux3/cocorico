package com.cocorico.alarm

import java.time.LocalDateTime

/**
 * Ce qu'a donné une tentative de programmation.
 *
 * [AlarmScheduler.schedule] renvoyait `null` pour cinq situations très
 * différentes, dont deux — permission retirée, échec du système — signifient
 * « l'utilisateur a demandé un réveil et n'en aura pas ». Les trois appelants
 * jetaient ce `null` sans le regarder. L'alarme disparaissait donc en silence,
 * pendant que l'accueil continuait d'annoncer l'heure du prochain réveil.
 *
 * Type pur, sans import `android.*` : la distinction qui compte est une
 * décision, pas un appel système, et elle se teste sans téléphone.
 */
sealed interface ResultatPlanification {

    /** L'instant programmé, ou `null` si aucune alarme n'a été posée. */
    val prochaine: LocalDateTime?

    /** Vrai seulement si une alarme est réellement armée dans le système. */
    val alarmePosee: Boolean get() = prochaine != null

    /**
     * Faut-il prévenir l'utilisateur ?
     *
     * La ligne de partage n'est pas « une alarme a-t-elle été posée » mais
     * « l'utilisateur en attendait-il une ». Désarmer soi-même ou ne cocher
     * aucun jour sont des choix : les signaler serait du bruit, et le bruit
     * finit par faire ignorer les vraies alertes.
     */
    val doitAlerter: Boolean get() = false

    /** Une alarme est armée pour [prochaine]. */
    data class Programmee(override val prochaine: LocalDateTime) : ResultatPlanification

    /** L'utilisateur a désarmé l'alarme. Rien à signaler. */
    data object Desarmee : ResultatPlanification {
        override val prochaine: LocalDateTime? = null
    }

    /** Aucun jour n'est coché. Rien à signaler non plus. */
    data object AucunJourActif : ResultatPlanification {
        override val prochaine: LocalDateTime? = null
    }

    /**
     * Android 12 laisse retirer `SCHEDULE_EXACT_ALARM` après l'onboarding.
     * L'alarme reste armée à l'écran et ne sonnera jamais : c'est le seul cas
     * où l'application ment à l'utilisateur sans le savoir.
     */
    data object PermissionManquante : ResultatPlanification {
        override val prochaine: LocalDateTime? = null
        override val doitAlerter: Boolean = true
    }

    /** Le système a refusé la programmation pour une autre raison. */
    data object EchecSysteme : ResultatPlanification {
        override val prochaine: LocalDateTime? = null
        override val doitAlerter: Boolean = true
    }
}
