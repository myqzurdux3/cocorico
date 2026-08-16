package com.cocorico.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Un réveil mené jusqu'à la résolution du défi. */
@Entity(tableName = "wake_records")
data class WakeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmeAt: Long,
    val resoluAt: Long,
    val erreurs: Int,
    val triches: Int,
)
