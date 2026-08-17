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

    @Test fun `l image passee se retrouve exactement dans les donnees`() {
        // Tout l'alphabet que `Base64.NO_WRAP` peut produire, remplissage
        // compris : c'est le seul contenu que ce champ verra jamais.
        val image = "ABCXYZabcxyz0189+/=="

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

    @Test fun `l image est recopiee telle quelle, sans passer par l echappement`() {
        // L'échappement de l'image parcourait les ~400 000 caractères du base64
        // d'une photo pour n'en changer aucun, et allouait environ un mégaoctet
        // à chaque essai, pendant que la sirène sonne. Ce test fige ce qui rend
        // ce parcours inutile : sur l'alphabet base64, le corps produit est
        // exactement le corps où l'image est recopiée telle quelle.
        val image = "ABCXYZabcxyz0189+/=".repeat(200)
        val corps = RequeteVision.corps("Tasse", image)
        assertTrue(corps.contains("\"data\":\"$image\""))
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
                """{"content":[{"type":"text","text":"Oui, cette photo montre bien une tasse."}]}""",
            ),
        )
        assertTrue(
            RequeteVision.lireVerdict(
                """{"content":[{"type":"text","text":"La réponse est OUI."}]}""",
            ),
        )
    }

    @Test fun `une phrase autour du mot NON reste un refus`() {
        assertFalse(
            RequeteVision.lireVerdict(
                """{"content":[{"type":"text","text":"Non, ce n'est pas une tasse."}]}""",
            ),
        )
    }

    @Test fun `un refus qui contient le mot oui n est pas lu comme un accord`() {
        // Le piège à éviter : chercher "oui" n'importe où dans le texte
        // ferait lire cette phrase de refus comme un accord.
        assertFalse(
            RequeteVision.lireVerdict(
                """{"content":[{"type":"text","text":"Je ne peux pas dire oui avec certitude."}]}""",
            ),
        )
        assertFalse(
            RequeteVision.lireVerdict(
                """{"content":[{"type":"text","text":"Je ne suis pas certain, donc je ne dirai pas oui."}]}""",
            ),
        )
    }

    // --- Forme réelle des réponses de `generateContent` ------------------
    //
    // Les tests ci-dessus emploient une enveloppe `content[].type` qui
    // n'existe pas dans l'API visée : ils prouvent que le verdict se lit dans
    // *un* JSON, pas dans celui que Google renvoie. Ceux qui suivent partent
    // de la forme réellement documentée,
    // `{"candidates":[{"content":{"parts":[{"text":"…"}],"role":"model"}}]}`,
    // et de ses cas de bord observables : erreur, réflexion, réponse vide.

    private fun reponseReelle(vararg parties: String): String =
        """{"candidates":[{"content":{"parts":[${parties.joinToString(",")}],"role":"model"},""" +
            """"finishReason":"STOP"}],"modelVersion":"gemini-3.5-flash-lite"}"""

    @Test fun `la forme reelle de generateContent est lue`() {
        assertTrue(RequeteVision.lireVerdict(reponseReelle("""{"text":"OUI"}""")))
        assertFalse(RequeteVision.lireVerdict(reponseReelle("""{"text":"NON"}""")))
    }

    @Test fun `une reponse d erreur de l API vaut refus`() {
        val quota = """{"error":{"code":429,"message":"Quota exceeded for quota metric""" +
            """ 'Generate requests'","status":"RESOURCE_EXHAUSTED"}}"""
        assertFalse(RequeteVision.lireVerdict(quota))
    }

    @Test fun `une partie de reflexion ne peut pas tenir lieu de verdict`() {
        // Les parties marquées `"thought": true` sont le raisonnement du
        // modèle, pas sa réponse. Les lire comme un verdict rendrait le défi
        // franchissable par une photo quelconque, dès que la réflexion
        // contient le mot « oui » — c'est-à-dire dès qu'elle hésite.
        assertFalse(
            RequeteVision.lireVerdict(
                reponseReelle("""{"text":"Je dirais oui, mais vérifions la forme","thought":true}"""),
            ),
        )
        // La réflexion précède la réponse : c'est la réponse qui décide.
        assertTrue(
            RequeteVision.lireVerdict(
                reponseReelle(
                    """{"text":"Ce n'est peut-être pas une tasse","thought":true}""",
                    """{"text":"OUI"}""",
                ),
            ),
        )
    }

    @Test fun `une reponse vide ou sans candidat vaut refus`() {
        assertFalse(RequeteVision.lireVerdict(""))
        assertFalse(RequeteVision.lireVerdict("{}"))
        assertFalse(RequeteVision.lireVerdict("""{"candidates":[]}"""))
        // Réponse bloquée en amont : aucun candidat, seulement le motif.
        assertFalse(
            RequeteVision.lireVerdict("""{"promptFeedback":{"blockReason":"SAFETY"}}"""),
        )
        // Candidat sans partie de texte (coupé sur le budget de jetons).
        assertFalse(
            RequeteVision.lireVerdict(
                """{"candidates":[{"content":{"role":"model"},"finishReason":"MAX_TOKENS"}]}""",
            ),
        )
    }

    @Test fun `un verdict en anglais est compris comme un verdict`() {
        // La consigne demande OUI ou NON, mais un modèle qui répond « Yes »
        // n'a pas refusé la photo. Ne reconnaître que le français faisait
        // rejeter *toutes* les photos sans qu'aucun message ne l'explique.
        assertTrue(RequeteVision.lireVerdict(reponseReelle("""{"text":"Yes"}""")))
        assertTrue(RequeteVision.lireVerdict(reponseReelle("""{"text":"Yes, the photo shows a mug."}""")))
        assertFalse(RequeteVision.lireVerdict(reponseReelle("""{"text":"No"}""")))
        assertFalse(RequeteVision.lireVerdict(reponseReelle("""{"text":"No, this is not a mug."}""")))
    }

    @Test fun `une negation anglaise n est pas lue comme un accord`() {
        assertFalse(RequeteVision.lireVerdict(reponseReelle("""{"text":"I cannot say yes with certainty."}""")))
        assertFalse(RequeteVision.lireVerdict(reponseReelle("""{"text":"I would not say yes."}""")))
    }

    @Test fun `un saut de ligne echappe separe bien les phrases`() {
        // Le texte arrive échappé dans le JSON : `\n` y est la suite des deux
        // caractères `\` et `n`. Sans dé-échappement, la fin de phrase n'est
        // pas vue, la négation de la ligne précédente est lue comme portant
        // sur le « Oui » final, et un accord franc devient un refus.
        val reponse = reponseReelle("""{"text":"Je ne vois aucun flou sur l'image\nOui"}""")
        assertTrue(RequeteVision.lireVerdict(reponse))
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
                '"' -> {
                    i++
                    break
                }
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
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString() to s.substring(i)
    }

    private fun analyserNombre(s: String): Pair<Double, String> {
        val m = Regex("^-?\\d+(\\.\\d+)?").find(s) ?: error("Nombre attendu près de '$s'")
        return m.value.toDouble() to s.substring(m.value.length)
    }
}
