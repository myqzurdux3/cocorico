package com.cocorico.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La migration ne peut pas s'exécuter sans appareil, mais son SQL doit rester
 * cohérent avec l'entité : ces deux colonnes sont exactement celles que
 * [WakeRecord] a gagnées, et un oubli — ou une simple divergence de type, de
 * nullabilité ou de valeur par défaut avec ce que Room attend réellement —
 * ferait planter Room au démarrage (IllegalStateException de validation, sur
 * la base de quelqu'un qui met à jour depuis la version précédente).
 *
 * Le schéma que Room attend est exporté et versionné dans
 * `app/schemas/com.cocorico.data.CocoricoDatabase/2.json` (voir
 * `ksp { arg("room.schemaLocation", ...) }` dans `app/build.gradle.kts`). Ce
 * test compare ce fichier, colonne par colonne, au SQL de
 * [CocoricoDatabase.SQL_MIGRATION_1_2] : c'est la seule façon de détecter,
 * par exemple, qu'un `defaultValue` a changé dans [WakeRecord] sans que le
 * SQL de la migration ait suivi — les seules sous-chaînes "ADD COLUMN" /
 * "NOT NULL" / "DEFAULT" resteraient présentes et laisseraient le test vert.
 */
class MigrationTest {

    /** Une colonne ajoutée par une instruction `ALTER TABLE ... ADD COLUMN`. */
    private data class ColonneAjoutee(
        val nom: String,
        val type: String,
        val nonNulle: Boolean,
        val defaut: String?,
    )

    /** La même colonne, telle que décrite par le schéma JSON exporté par Room. */
    private data class ColonneAttendue(
        val type: String,
        val nonNulle: Boolean,
        val defaut: String?,
    )

    @Test
    fun `la migration ajoute exactement les deux colonnes gagnees par l entite`() {
        val noms = CocoricoDatabase.SQL_MIGRATION_1_2.map(::parseAjoutColonne).map { it.nom }
        assertEquals(listOf("defi", "abandon"), noms)
    }

    @Test
    fun `chaque colonne ajoutee correspond au schema attendu par Room`() {
        val schema = JsonSimple.parseObjet(lireFichierSchema())
        val colonnesAttendues = colonnesAttendues(schema)

        CocoricoDatabase.SQL_MIGRATION_1_2.map(::parseAjoutColonne).forEach { colonne ->
            val attendue = colonnesAttendues[colonne.nom]
                ?: throw AssertionError(
                    "La colonne '${colonne.nom}' ajoutée par SQL_MIGRATION_1_2 n'existe pas " +
                        "dans le schéma exporté app/schemas/com.cocorico.data.CocoricoDatabase/2.json " +
                        "(colonnes connues : ${colonnesAttendues.keys})."
                )
            assertEquals("type de la colonne '${colonne.nom}'", attendue.type, colonne.type)
            assertEquals("nullabilité de la colonne '${colonne.nom}'", attendue.nonNulle, colonne.nonNulle)
            assertEquals("valeur par défaut de la colonne '${colonne.nom}'", attendue.defaut, colonne.defaut)
        }
    }

    /**
     * Découpe une instruction `ALTER TABLE wake_records ADD COLUMN <nom> <type>
     * [NOT NULL] [DEFAULT <valeur>]` par simples recherches de sous-chaînes :
     * le format de [CocoricoDatabase.SQL_MIGRATION_1_2] est entièrement sous
     * notre contrôle, une regex n'apporterait rien de plus fiable.
     */
    private fun parseAjoutColonne(instruction: String): ColonneAjoutee {
        assertTrue("instruction de migration inattendue : $instruction", instruction.contains("ADD COLUMN"))
        val apresAddColumn = instruction.substringAfter("ADD COLUMN ").trim()
        val mots = apresAddColumn.split(" ")
        val nom = mots[0]
        val type = mots[1]
        val nonNulle = apresAddColumn.contains("NOT NULL")
        val defaut = if (apresAddColumn.contains("DEFAULT ")) {
            apresAddColumn.substringAfter("DEFAULT ").trim()
        } else {
            null
        }
        return ColonneAjoutee(nom, type, nonNulle, defaut)
    }

    /**
     * Extrait, pour la table `wake_records`, les colonnes du schéma JSON exporté
     * par Room, sous la même forme que [parseAjoutColonne] pour rendre la
     * comparaison directe. Room encode déjà les valeurs par défaut avec leurs
     * quotes SQL (`"'MATHS'"` pour du texte, `"0"` pour un booléen) : on les
     * garde telles quelles, sans réinterprétation.
     */
    @Suppress("UNCHECKED_CAST")
    private fun colonnesAttendues(schema: Map<String, Any?>): Map<String, ColonneAttendue> {
        val database = schema["database"] as Map<String, Any?>
        val entites = database["entities"] as List<Map<String, Any?>>
        val table = entites.firstOrNull { it["tableName"] == "wake_records" }
            ?: throw AssertionError("Table 'wake_records' absente du schéma exporté.")
        val champs = table["fields"] as List<Map<String, Any?>>
        return champs.associate { champ ->
            val nom = champ["columnName"] as String
            nom to ColonneAttendue(
                type = champ["affinity"] as String,
                nonNulle = champ["notNull"] as Boolean,
                defaut = champ["defaultValue"] as String?,
            )
        }
    }

    /**
     * Localise `app/schemas/com.cocorico.data.CocoricoDatabase/2.json` en remontant
     * depuis le répertoire de travail du test JVM, qui varie selon que Gradle
     * lance la tâche depuis la racine du dépôt ou depuis le module `app`. Échoue
     * bruyamment — jamais silencieusement — si le fichier reste introuvable.
     */
    private fun lireFichierSchema(): String {
        val cheminsRelatifs = listOf(
            "app/schemas/com.cocorico.data.CocoricoDatabase/2.json",
            "schemas/com.cocorico.data.CocoricoDatabase/2.json",
        )
        val essais = mutableListOf<String>()
        var repertoire: File? = File(System.getProperty("user.dir")!!).absoluteFile
        while (repertoire != null) {
            for (chemin in cheminsRelatifs) {
                val candidat = File(repertoire, chemin)
                essais += candidat.path
                if (candidat.isFile) return candidat.readText()
            }
            repertoire = repertoire.parentFile
        }
        throw AssertionError(
            "Schéma Room introuvable. Essayé, depuis ${System.getProperty("user.dir")} et ses parents :\n" +
                essais.joinToString("\n") + "\n" +
                "Génère-le avec `./gradlew :app:assembleDebug` (il est ensuite versionné dans app/schemas/)."
        )
    }
}

/**
 * Analyseur JSON minimal : les fichiers sous `app/schemas` ne sont pas accessibles via une
 * bibliothèque JSON classique depuis ce test (org.json n'est qu'un stub sur la
 * JVM de test, sans Robolectric), et le fichier est trop imbriqué pour une
 * lecture par sous-chaînes fiable. Ne couvre que ce dont ce test a besoin :
 * objets, tableaux, chaînes, nombres, booléens, null.
 */
private object JsonSimple {

    @Suppress("UNCHECKED_CAST")
    fun parseObjet(texte: String): Map<String, Any?> = Lecteur(texte).let { lecteur ->
        lecteur.lireValeur() as Map<String, Any?>
    }

    private class Lecteur(private val texte: String) {
        private var pos = 0

        fun lireValeur(): Any? {
            sauterEspaces()
            return when (texte[pos]) {
                '{' -> lireObjet()
                '[' -> lireTableau()
                '"' -> lireChaine()
                't' -> { attendre("true"); true }
                'f' -> { attendre("false"); false }
                'n' -> { attendre("null"); null }
                else -> lireNombre()
            }
        }

        private fun attendre(mot: String) {
            require(texte.startsWith(mot, pos)) { "JSON invalide à la position $pos, attendu '$mot'" }
            pos += mot.length
        }

        private fun lireObjet(): Map<String, Any?> {
            val resultat = linkedMapOf<String, Any?>()
            pos++ // {
            sauterEspaces()
            if (texte[pos] == '}') {
                pos++
                return resultat
            }
            while (true) {
                sauterEspaces()
                val cle = lireChaine()
                sauterEspaces()
                require(texte[pos] == ':') { "JSON invalide à la position $pos, ':' attendu" }
                pos++
                resultat[cle] = lireValeur()
                sauterEspaces()
                when (texte[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; return resultat }
                    else -> error("JSON invalide à la position $pos, ',' ou '}' attendu")
                }
            }
        }

        private fun lireTableau(): List<Any?> {
            val resultat = mutableListOf<Any?>()
            pos++ // [
            sauterEspaces()
            if (texte[pos] == ']') {
                pos++
                return resultat
            }
            while (true) {
                resultat += lireValeur()
                sauterEspaces()
                when (texte[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; return resultat }
                    else -> error("JSON invalide à la position $pos, ',' ou ']' attendu")
                }
            }
        }

        private fun lireChaine(): String {
            require(texte[pos] == '"') { "JSON invalide à la position $pos, chaîne attendue" }
            pos++
            val sb = StringBuilder()
            while (texte[pos] != '"') {
                if (texte[pos] == '\\') {
                    pos++
                    when (texte[pos]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'b' -> sb.append('\b')
                        'u' -> {
                            val code = texte.substring(pos + 1, pos + 5).toInt(16)
                            sb.append(code.toChar())
                            pos += 4
                        }
                        else -> sb.append(texte[pos])
                    }
                } else {
                    sb.append(texte[pos])
                }
                pos++
            }
            pos++ // "
            return sb.toString()
        }

        private fun lireNombre(): Double {
            val debut = pos
            while (pos < texte.length && (texte[pos].isDigit() || texte[pos] in "-+.eE")) pos++
            return texte.substring(debut, pos).toDouble()
        }

        private fun sauterEspaces() {
            while (pos < texte.length && texte[pos].isWhitespace()) pos++
        }
    }
}
