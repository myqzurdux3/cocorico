package com.cocorico.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface WakeRecordDao {

    @Insert
    suspend fun inserer(record: WakeRecord)

    @Query("SELECT * FROM wake_records ORDER BY alarmeAt ASC")
    suspend fun tous(): List<WakeRecord>
}
