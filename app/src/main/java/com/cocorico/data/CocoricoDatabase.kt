package com.cocorico.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [WakeRecord::class], version = 3)
abstract class CocoricoDatabase : RoomDatabase() {

    abstract fun wakeRecordDao(): WakeRecordDao

    companion object {
        /** Exposé pour que le test unitaire vérifie la cohérence avec l'entité. */
        val SQL_MIGRATION_1_2 = listOf(
            "ALTER TABLE wake_records ADD COLUMN defi TEXT NOT NULL DEFAULT 'MATHS'",
            "ALTER TABLE wake_records ADD COLUMN abandon INTEGER NOT NULL DEFAULT 0",
        )

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SQL_MIGRATION_1_2.forEach(db::execSQL)
            }
        }

        /**
         * Retire la colonne `triches`, jamais alimentée : elle était écrite en
         * dur à zéro et lue nulle part, si bien que `0` ne signifiait pas
         * « aucune triche » mais « rien mesuré ». Une colonne morte finit par
         * être prise pour une donnée.
         *
         * `ALTER TABLE … DROP COLUMN` n'existe qu'à partir de SQLite 3.35, donc
         * pas sur tous les appareils que couvre `minSdk 28` : on recrée la
         * table, on recopie, on remplace. C'est la procédure recommandée par
         * SQLite, et la seule qui marche partout ici.
         *
         * L'ordre des colonnes de la nouvelle table suit celui de l'entité :
         * Room valide le schéma au démarrage et un écart le fait échouer.
         */
        val SQL_MIGRATION_2_3 = listOf(
            "CREATE TABLE wake_records_nouveau (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "alarmeAt INTEGER NOT NULL, " +
                "resoluAt INTEGER NOT NULL, " +
                "erreurs INTEGER NOT NULL, " +
                "defi TEXT NOT NULL DEFAULT 'MATHS', " +
                "abandon INTEGER NOT NULL DEFAULT 0)",
            "INSERT INTO wake_records_nouveau (id, alarmeAt, resoluAt, erreurs, defi, abandon) " +
                "SELECT id, alarmeAt, resoluAt, erreurs, defi, abandon FROM wake_records",
            "DROP TABLE wake_records",
            "ALTER TABLE wake_records_nouveau RENAME TO wake_records",
        )

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SQL_MIGRATION_2_3.forEach(db::execSQL)
            }
        }

        @Volatile private var instance: CocoricoDatabase? = null

        fun get(context: Context): CocoricoDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CocoricoDatabase::class.java,
                "cocorico.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
