package com.cocorico.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La migration ne peut pas s'exécuter sans appareil, mais son SQL doit rester
 * cohérent avec l'entité : ces deux colonnes sont exactement celles que
 * [WakeRecord] a gagnées, et un oubli ferait planter Room au démarrage.
 */
class MigrationTest {

    @Test
    fun `la migration ajoute les deux colonnes de l entite`() {
        val sql = CocoricoDatabase.SQL_MIGRATION_1_2.joinToString(" ")
        assertTrue(sql, sql.contains("ADD COLUMN defi"))
        assertTrue(sql, sql.contains("ADD COLUMN abandon"))
    }

    @Test
    fun `les colonnes ajoutees sont non nulles avec un defaut`() {
        CocoricoDatabase.SQL_MIGRATION_1_2.forEach { instruction ->
            assertTrue(instruction, instruction.contains("NOT NULL"))
            assertTrue(instruction, instruction.contains("DEFAULT"))
        }
    }
}
