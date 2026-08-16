package com.cocorico.ring

/**
 * Compte à rebours d'inactivité. Sans horloge interne : l'appelant fournit
 * l'instant, ce qui rend la classe testable et indépendante d'Android.
 *
 * Avant tout appel à [onInteraction], le compte à rebours est considéré comme
 * expiré : `derniereInteraction` vaut 0 et l'appelant passe un horodatage
 * courant, très supérieur au délai. C'est délibéré — un appelant qui oublierait
 * d'amorcer le suivi obtient une alarme à plein volume, jamais une alarme
 * silencieuse. `AlarmActivity` amorce quand même explicitement avant de scruter.
 */
class InactivityTracker(private val timeoutMillis: Long = 10_000L) {

    private var derniereInteraction: Long = 0L

    fun onInteraction(nowMillis: Long) {
        derniereInteraction = nowMillis
    }

    fun isExpired(nowMillis: Long): Boolean =
        nowMillis - derniereInteraction >= timeoutMillis

    fun millisRestantes(nowMillis: Long): Long =
        (derniereInteraction + timeoutMillis - nowMillis).coerceAtLeast(0L)
}
