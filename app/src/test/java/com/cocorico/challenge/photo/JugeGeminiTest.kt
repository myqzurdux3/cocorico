package com.cocorico.challenge.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les parties du juge distant qui se testent sans réseau : le masquage de la
 * clé d'API et le classement des codes HTTP.
 *
 * **Toutes les clés employées ici sont inventées.** Aucun appel réseau n'est
 * fait, et la vraie clé de l'utilisateur n'a rien à faire dans un test.
 */
class JugeGeminiTest {

    // --- Masquage de la clé ---------------------------------------------
    //
    // C'est le seul garde-fou entre une clé d'API et l'écran : les messages
    // d'erreur du réseau ne sont pas sous notre contrôle, et une clé affichée
    // est une clé qui fuite. Il n'était couvert par aucun test.

    @Test fun `la cle est retiree du texte affiche`() {
        val juge = JugeGemini(cle = "AIzaFACTICE-0000")
        val masque = juge.masquer("Requête refusée pour la clé AIzaFACTICE-0000, réessayez")
        assertFalse(masque.contains("AIzaFACTICE-0000"))
        assertTrue(masque.contains(JugeGemini.MASQUE))
    }

    @Test fun `toutes les occurrences de la cle sont retirees`() {
        val juge = JugeGemini(cle = "cle-factice-42")
        val masque = juge.masquer("cle-factice-42 invalide (url=...key=cle-factice-42)")
        assertFalse(masque.contains("cle-factice-42"))
        assertEquals(2, JugeGemini.MASQUE.toRegex().findAll(masque).count())
    }

    @Test fun `une cle vide laisse le texte intact`() {
        // Sans cette garde, `replace("", …)` insérerait le masque entre chaque
        // caractère et rendrait tout message d'erreur illisible.
        val juge = JugeGemini(cle = "")
        val texte = "Aucune clé enregistrée"
        assertEquals(texte, juge.masquer(texte))
    }

    @Test fun `un texte sans la cle n est pas modifie`() {
        val juge = JugeGemini(cle = "AIzaFACTICE-0000")
        val texte = "java.net.UnknownHostException: generativelanguage.googleapis.com"
        assertEquals(texte, juge.masquer(texte))
    }

    // --- Classement des codes HTTP --------------------------------------

    @Test fun `les incidents passagers valent un second essai`() {
        // 429 et 503 étaient traités comme un « non » définitif : l'utilisateur
        // voyait un refus là où le juge n'avait simplement pas répondu.
        assertTrue(JugeGemini.estReessayable(429))
        assertTrue(JugeGemini.estReessayable(503))
        assertTrue(JugeGemini.estReessayable(500))
        assertTrue(JugeGemini.estReessayable(502))
        assertTrue(JugeGemini.estReessayable(408))
    }

    @Test fun `une requete fautive ne se retente pas`() {
        // Une clé invalide ou un modèle retiré ne guérit pas d'un essai de
        // plus : insister ne ferait que brûler le budget pendant que l'alarme
        // sonne.
        assertFalse(JugeGemini.estReessayable(400))
        assertFalse(JugeGemini.estReessayable(401))
        assertFalse(JugeGemini.estReessayable(403))
        assertFalse(JugeGemini.estReessayable(404))
        assertFalse(JugeGemini.estReessayable(200))
    }

    @Test fun `la cause affichee distingue les pannes des refus`() {
        assertTrue(JugeGemini.causeHttp(429).contains("uota"))
        assertTrue(JugeGemini.causeHttp(401).contains("clé"))
        assertTrue(JugeGemini.causeHttp(503).contains("panne"))
        // Le code reste visible : c'est lui qui rend un échec diagnosticable.
        assertTrue(JugeGemini.causeHttp(418).contains("418"))
    }

    @Test fun `un diagnostic sans issue explicite reste un verdict du modele`() {
        // Le banc d'essai construit des diagnostics locaux : ils ne doivent pas
        // se faire passer pour une panne du juge.
        assertEquals(IssueJuge.ACCEPTE, DiagnosticJuge(accepte = true, resume = "ok").issue)
        assertEquals(IssueJuge.REFUS_DU_MODELE, DiagnosticJuge(accepte = false, resume = "non").issue)
    }
}
