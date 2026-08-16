package com.cocorico.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [WakeRecord::class], version = 2)
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

        @Volatile private var instance: CocoricoDatabase? = null

        fun get(context: Context): CocoricoDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CocoricoDatabase::class.java,
                "cocorico.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
