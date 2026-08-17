package com.cocorico.ring

/**
 * Compte à rebours d'inactivité. Sans horloge interne : l'appelant fournit
 * l'instant, ce qui rend la classe testable et indépendante d'Android.
 *
 * **L'horloge fournie doit être monotone** — `SystemClock.elapsedRealtime()`,
 * jamais `System.currentTimeMillis()`. Tous les instants passés à cette classe
 * doivent venir de la même source, et cette source ne doit pas sauter : le
 * calcul est une soustraction entre deux relevés, et l'horloge murale se
 * resynchronise sur le réseau, typiquement quand la radio se rallume au réveil,
 * c'est-à-dire précisément pendant l'alarme. Un saut en avant fait expirer le
 * compte à rebours immédiatement et remonte le volume alors que le téléphone
 * est en main ; un saut en arrière le fige et le volume ne remonte plus jamais.
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

    fun isExpired(nowMillis: Long): Boolean = nowMillis - derniereInteraction >= timeoutMillis

    fun millisRestantes(nowMillis: Long): Long = (derniereInteraction + timeoutMillis - nowMillis).coerceAtLeast(0L)
}
