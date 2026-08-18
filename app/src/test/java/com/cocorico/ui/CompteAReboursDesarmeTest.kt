package com.cocorico.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L'accueil annonçait « Aucun jour actif. Le coq dort. » alors que cinq jours
 * étaient bel et bien cochés : l'alarme était simplement désarmée. Les deux
 * situations rendent la même absence d'occurrence, et le message choisissait la
 * mauvaise explication — un utilisateur pouvait croire sa sélection de jours
 * perdue. Constaté sur l'appareil le 18 août 2026.
 */
class CompteAReboursDesarmeTest {

    @Test
    fun `desarme se dit desarme, pas sans jour actif`() {
        assertEquals(
            CompteARebours.DESARME,
            CompteARebours.libelle(MAINTENANT, cible = null, zone = ZONE, armee = false),
        )
    }

    @Test
    fun `arme sans occurrence reste sans jour actif`() {
        assertEquals(
            CompteARebours.SANS_OCCURRENCE,
            CompteARebours.libelle(MAINTENANT, cible = null, zone = ZONE, armee = true),
        )
    }

    /**
     * Désarmé mais avec une occurrence connue : le cas ne se produit pas
     * aujourd'hui, l'écran ne gardant pas d'occurrence à l'état désarmé. Fixé
     * quand même, sans quoi un futur appelant pourrait afficher un compte à
     * rebours pour une alarme qui ne sonnera pas.
     */
    @Test
    fun `desarme n'affiche jamais de compte a rebours`() {
        assertEquals(
            CompteARebours.DESARME,
            CompteARebours.libelle(MAINTENANT, cible = MAINTENANT.plusHours(2), zone = ZONE, armee = false),
        )
    }

    private companion object {
        val ZONE: java.time.ZoneId = java.time.ZoneId.of("Europe/Paris")
        val MAINTENANT: java.time.LocalDateTime = java.time.LocalDateTime.of(2026, 8, 18, 12, 0)
    }
}
