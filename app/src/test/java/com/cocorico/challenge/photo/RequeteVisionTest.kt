package com.cocorico.challenge.photo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la partie pure du juge distant : la construction du corps de
 * requête et la lecture du verdict. Aucun réseau ici — ce texte se teste
 * sans appareil ni connexion, et c'est là que vivent les erreurs.
 */
class RequeteVisionTest {
    @Test fun `le corps nomme l objet attendu`() {
        assertTrue(RequeteVision.corps("Tasse", "AAAA").contains("Tasse"))
    }

    @Test fun `le corps porte l image encodee`() {
        assertTrue(RequeteVision.corps("Tasse", "AAAA").contains("AAAA"))
    }

    @Test fun `une reponse affirmative est lue comme un accord`() {
        assertTrue(RequeteVision.lireVerdict("""{"content":[{"type":"text","text":"OUI"}]}"""))
    }

    @Test fun `une reponse negative est lue comme un refus`() {
        assertFalse(RequeteVision.lireVerdict("""{"content":[{"type":"text","text":"NON"}]}"""))
    }

    @Test fun `une reponse inattendue vaut refus`() {
        // Un modèle bavard, une erreur d'API, une réponse tronquée : tout ce qui
        // n'est pas un oui franc est un non. Le défi doit pouvoir se rejouer,
        // jamais planter.
        assertFalse(RequeteVision.lireVerdict("""{"error":{"message":"overloaded"}}"""))
        assertFalse(RequeteVision.lireVerdict("pas du json"))
        assertFalse(RequeteVision.lireVerdict(""))
    }

    @Test fun `la casse et les espaces ne changent pas le verdict`() {
        assertTrue(RequeteVision.lireVerdict("""{"content":[{"type":"text","text":" oui "}]}"""))
    }
}
