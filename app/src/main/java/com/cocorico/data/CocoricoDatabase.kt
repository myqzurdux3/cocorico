package com.cocorico.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WakeRecord::class], version = 1)
abstract class CocoricoDatabase : RoomDatabase() {

    abstract fun wakeRecordDao(): WakeRecordDao

    companion object {
        @Volatile private var instance: CocoricoDatabase? = null

        fun get(context: Context): CocoricoDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CocoricoDatabase::class.java,
                "cocorico.db",
            ).build().also { instance = it }
        }
    }
}
