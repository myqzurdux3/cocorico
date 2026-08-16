package com.cocorico.ring

/**
 * Compte à rebours d'inactivité. Sans horloge interne : l'appelant fournit
 * l'instant, ce qui rend la classe testable et indépendante d'Android.
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
