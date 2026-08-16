package com.cocorico.challenge.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la partie pure du juge distant : la construction du corps de
 * requête et la lecture du verdict. Aucun réseau ici — ce texte se teste
 * sans appareil ni connexion, et c'est là que vivent les erreurs.
 *
 * Le corps de requête n'a jamais été confronté à l'API réelle : on vérifie
 * donc sa *structure* (via [MiniJson], un petit analyseur écrit à la main —
 * `org.json` est un stub vide hors Android dans ce projet) et pas seulement
 * des sous-chaînes, qui laisseraient passer une image mal placée ou un
 * champ mal nommé sans qu'aucun test ne s'en aperçoive.
 */
class RequeteVisionTest {

    // --- Construction du corps ------------------------------------------

    @Test fun `la structure du corps est correcte`() {
        val image = "AAAA"
        @Suppress("UNCHECKED_CAST")
        val json = MiniJson.parse(RequeteVision.corps("Tasse", image)) as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val contents = json["contents"] as List<Map<String, Any?>>
        assertEquals(1, contents.size)
        assertEquals("user", contents[0]["role"])

        @Suppress("UNCHECKED_CAST")
        val parts = contents[0]["parts"] as List<Map<String, Any?>>
        assertEquals(2, parts.size)

        @Suppress("UNCHECKED_CAST")
        val inline = parts[0]["inline_data"] as Map<String, Any?>
        assertEquals("image/jpeg", inline["mime_type"])
        assertEquals(image, inline["data"])

        assertTrue((parts[1]["text"] as String).contains("Tasse"))

        @Suppress("UNCHECKED_CAST")
        val config = json["generationConfig"] as Map<String, Any?>
        // Budget large : un budget serré est un pari sur le comportement du
        // modèle, et s'il est faux le défi refuse toutes les photos.
        assertEquals(1024.0, config["maxOutputTokens"])
    }

    @Test fun `l url porte le modele et le point de terminaison de generation`() {
        val url = RequeteVision.url()
        assertTrue(url.contains(RequeteVision.MODELE))
        assertTrue(url.endsWith(":generateContent"))
        // La clé ne doit jamais voyager dans l'URL : une URL finit dans les
        // journaux des serveurs traversés, pas un en-tête.
        assertFalse(url.contains("key="))
    }

    @Test fun `l image passee se retrouve exactement dans les donnees, meme avec des caracteres a echapper`() {
        val image = "AA\"BB\\CC"
        @Suppress("UNCHECKED_CAST")
        val json = MiniJson.parse(RequeteVision.corps("Tasse", image)) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val contents = json["contents"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val parts = contents[0]["parts"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val inline = parts[0]["inline_data"] as Map<String, Any?>
        assertEquals(image, inline["data"])
    }

    @Test fun `un nom d objet avec des guillemets ne casse pas la structure`() {
        @Suppress("UNCHECKED_CAST")
        val json = MiniJson.parse(RequeteVision.corps("Une \"tasse\"", "AAAA")) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val contents = json["contents"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val parts = contents[0]["parts"] as List<Map<String, Any?>>
        assertTrue((parts[1]["text"] as String).contains("Une \"tasse\""))
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

    @Test fun `un bloc de reflexion avant le texte n empeche pas de trouver le verdict`() {
        val reponse = """{"content":[
            {"type":"thinking","thinking":"Je regarde la photo..."},
            {"type":"text","text":"OUI"}
        ]}"""
        assertTrue(RequeteVision.lireVerdict(reponse))
    }

    @Test fun `un bloc de texte de preambule avant le verdict n empeche pas de le trouver`() {
        val reponse = """{"content":[
            {"type":"text","text":"Laissez-moi regarder cette photo attentivement."},
            {"type":"text","text":"OUI"}
        ]}"""
        assertTrue(RequeteVision.lireVerdict(reponse))
    }

    @Test fun `une phrase autour du mot OUI est lue comme un accord`() {
        assertTrue(
            RequeteVision.lireVerdict(
                """{"content":[{"type":"text","text":"Oui, cette photo montre bien une tasse."}]}"""
            )
        )
        assertTrue(
            RequeteVision.lireVerdict(
                """{"content":[{"type":"text","text":"La réponse est OUI."}]}"""
            )
        )
    }

    @Test fun `une phrase autour du mot NON reste un refus`() {
        assertFalse(
            RequeteVision.lireVerdict(
                """{"content":[{"type":"text","text":"Non, ce n'est pas une tasse."}]}"""
            )
        )
    }

    @Test fun `un refus qui contient le mot oui n est pas lu comme un accord`() {
        // Le piège à éviter : chercher "oui" n'importe où dans le texte
        // ferait lire cette phrase de refus comme un accord.
        assertFalse(
            RequeteVision.lireVerdict(
                """{"content":[{"type":"text","text":"Je ne peux pas dire oui avec certitude."}]}"""
            )
        )
        assertFalse(
            RequeteVision.lireVerdict(
                """{"content":[{"type":"text","text":"Je ne suis pas certain, donc je ne dirai pas oui."}]}"""
            )
        )
    }
}

/**
 * Petit analyseur JSON écrit à la main pour les tests : ce projet n'a pas de
 * bibliothèque JSON utilisable côté JVM pur (`org.json` y est un stub vide,
 * sans Robolectric). Suffisant pour les corps de requête produits par
 * [RequeteVision.corps] — objets, tableaux, chaînes échappées, nombres.
 */
private object MiniJson {

    fun parse(json: String): Any? {
        val (valeur, reste) = analyserValeur(json)
        check(reste.isBlank()) { "JSON restant non consommé : '$reste'" }
        return valeur
    }

    private fun analyserValeur(s: String): Pair<Any?, String> {
        val t = s.trimStart()
        return when {
            t.startsWith("{") -> analyserObjet(t)
            t.startsWith("[") -> analyserTableau(t)
            t.startsWith("\"") -> analyserChaine(t)
            else -> analyserNombre(t)
        }
    }

    private fun analyserObjet(s: String): Pair<Map<String, Any?>, String> {
        var reste = s.removePrefix("{").trimStart()
        val map = LinkedHashMap<String, Any?>()
        if (reste.startsWith("}")) return map to reste.removePrefix("}")
        while (true) {
            val (cle, r1) = analyserChaine(reste.trimStart())
            reste = r1.trimStart().removePrefix(":").trimStart()
            val (valeur, r2) = analyserValeur(reste)
            map[cle] = valeur
            reste = r2.trimStart()
            when {
                reste.startsWith(",") -> reste = reste.removePrefix(",").trimStart()
                reste.startsWith("}") -> return map to reste.removePrefix("}")
                else -> error("Objet JSON mal formé près de '$reste'")
            }
        }
    }

    private fun analyserTableau(s: String): Pair<List<Any?>, String> {
        var reste = s.removePrefix("[").trimStart()
        val liste = ArrayList<Any?>()
        if (reste.startsWith("]")) return liste to reste.removePrefix("]")
        while (true) {
            val (valeur, r1) = analyserValeur(reste)
            liste.add(valeur)
            reste = r1.trimStart()
            when {
                reste.startsWith(",") -> reste = reste.removePrefix(",").trimStart()
                reste.startsWith("]") -> return liste to reste.removePrefix("]")
                else -> error("Tableau JSON mal formé près de '$reste'")
            }
        }
    }

    private fun analyserChaine(s: String): Pair<String, String> {
        require(s.startsWith("\"")) { "Chaîne attendue près de '$s'" }
        val sb = StringBuilder()
        var i = 1
        while (true) {
            when (val c = s[i]) {
                '"' -> { i++; break }
                '\\' -> {
                    i++
                    when (val e = s[i]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        else -> sb.append(e)
                    }
                    i++
                }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString() to s.substring(i)
    }

    private fun analyserNombre(s: String): Pair<Double, String> {
        val m = Regex("^-?\\d+(\\.\\d+)?").find(s) ?: error("Nombre attendu près de '$s'")
        return m.value.toDouble() to s.substring(m.value.length)
    }
}
