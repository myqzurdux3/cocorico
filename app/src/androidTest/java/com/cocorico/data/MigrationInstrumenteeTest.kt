package com.cocorico.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Joue la migration contre un **vrai** SQLite, sur un vrai appareil.
 *
 * `MigrationTest`, côté tests unitaires, ne compare que des chaînes : il lit le
 * SQL de la migration et le schéma exporté, et vérifie qu'ils se ressemblent.
 * C'est un bon garde-fou de revue, mais il ne peut pas voir un SQL
 * syntaxiquement cohérent qui échoue à l'exécution — exactement le défaut qui
 * plante l'application au démarrage chez quelqu'un qui met à jour.
 *
 * Ici, `MigrationTestHelper` crée une base à l'ancien schéma, y écrit des
 * lignes, applique la migration, puis **valide le résultat contre le schéma
 * exporté de la version d'arrivée**. Une colonne manquante, un type qui diffère,
 * une valeur par défaut absente : tout ressort ici et nulle part ailleurs.
 *
 * Se lance avec `./gradlew connectedDebugAndroidTest`, appareil branché.
 * Aucune alarme n'est programmée, aucun son n'est émis.
 */
@RunWith(AndroidJUnit4::class)
class MigrationInstrumenteeTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CocoricoDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun la_migration_1_vers_2_conserve_l_historique() {
        // Une base à l'ancien schéma, avec deux réveils déjà enregistrés :
        // c'est le cas qui compte, une migration sur base vide ne prouve rien.
        helper.createDatabase(BASE, 1).use { v1 ->
            v1.execSQL(
                "INSERT INTO wake_records (id, alarmeAt, resoluAt, erreurs, triches) " +
                    "VALUES (1, 1000, 61000, 2, 0), (2, 90000, 150000, 0, 0)",
            )
        }

        val v2 = helper.runMigrationsAndValidate(BASE, 2, true, CocoricoDatabase.MIGRATION_1_2)

        v2.query("SELECT id, alarmeAt, resoluAt, erreurs, defi, abandon FROM wake_records ORDER BY id").use { c ->
            assertEquals("les deux réveils doivent survivre à la migration", 2, c.count)

            assertTrue(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals(1_000L, c.getLong(1))
            assertEquals(61_000L, c.getLong(2))
            assertEquals(2, c.getInt(3))
            // Les deux colonnes ajoutées doivent porter la valeur par défaut du
            // SQL de migration, et non NULL : `WakeRecord.defi` et
            // `WakeRecord.abandon` sont non nullables côté Kotlin, et Room
            // n'infère jamais un défaut SQL depuis une valeur par défaut Kotlin.
            assertEquals(ChallengeId.MATHS.name, c.getString(4))
            assertEquals(0, c.getInt(5))

            assertTrue(c.moveToNext())
            assertEquals(2L, c.getLong(0))
            assertEquals(90_000L, c.getLong(1))
        }
        v2.close()
    }

    @Test
    fun la_migration_1_vers_2_accepte_une_base_vide() {
        // Une installation qui n'a jamais servi passe par le même chemin.
        helper.createDatabase(BASE, 1).close()
        helper.runMigrationsAndValidate(BASE, 2, true, CocoricoDatabase.MIGRATION_1_2).close()
    }

    @Test
    fun la_migration_2_vers_3_conserve_l_historique_en_retirant_la_colonne_morte() {
        // SQLite ne sait pas supprimer une colonne avant sa version 3.35 : la
        // migration recrée la table et recopie. C'est exactement le genre de
        // migration où l'on perd des lignes sans s'en apercevoir.
        helper.createDatabase(BASE, 2).use { v2 ->
            v2.execSQL(
                "INSERT INTO wake_records (id, alarmeAt, resoluAt, erreurs, triches, defi, abandon) " +
                    "VALUES (1, 1000, 61000, 2, 0, 'POMPES', 0), (2, 90000, 150000, 1, 0, 'PHOTO', 1)",
            )
        }

        val v3 = helper.runMigrationsAndValidate(BASE, 3, true, CocoricoDatabase.MIGRATION_2_3)

        v3.query("SELECT id, alarmeAt, resoluAt, erreurs, defi, abandon FROM wake_records ORDER BY id").use { c ->
            assertEquals("aucune ligne ne doit être perdue à la recopie", 2, c.count)

            assertTrue(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals(61_000L, c.getLong(2))
            assertEquals(2, c.getInt(3))
            assertEquals("POMPES", c.getString(4))
            assertEquals(0, c.getInt(5))

            assertTrue(c.moveToNext())
            assertEquals("PHOTO", c.getString(4))
            assertEquals("le renoncement doit survivre à la recopie", 1, c.getInt(5))
        }
        v3.close()
    }

    @Test
    fun une_installation_de_la_version_1_atteint_la_version_3() {
        // Le chemin le plus long, et celui que personne ne joue à la main :
        // quelqu'un qui n'a pas mis à jour depuis la toute première version.
        helper.createDatabase(BASE, 1).use { v1 ->
            v1.execSQL(
                "INSERT INTO wake_records (id, alarmeAt, resoluAt, erreurs, triches) " +
                    "VALUES (1, 1000, 61000, 3, 0)",
            )
        }

        val v3 = helper.runMigrationsAndValidate(
            BASE,
            3,
            true,
            CocoricoDatabase.MIGRATION_1_2,
            CocoricoDatabase.MIGRATION_2_3,
        )

        v3.query("SELECT erreurs, defi, abandon FROM wake_records").use { c ->
            assertEquals(1, c.count)
            assertTrue(c.moveToFirst())
            assertEquals(3, c.getInt(0))
            assertEquals(ChallengeId.MATHS.name, c.getString(1))
            assertEquals(0, c.getInt(2))
        }
        v3.close()
    }

    private companion object {
        const val BASE = "migration-test.db"
    }
}
