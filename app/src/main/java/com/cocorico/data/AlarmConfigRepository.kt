package com.cocorico.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "cocorico_alarm")

/** Encodage des jours en chaîne, isolé pour être testable sans appareil. */
object AlarmConfigCodec {

    fun encodeDays(days: Set<DayOfWeek>): String = days.joinToString(",") { it.name }

    fun decodeDays(raw: String): Set<DayOfWeek> = raw.split(",")
        .mapNotNull { token ->
            runCatching { DayOfWeek.valueOf(token.trim()) }.getOrNull()
        }
        .toSet()
}

class AlarmConfigRepository(private val context: Context) {

    private object Keys {
        val HOUR = intPreferencesKey("hour")
        val MINUTE = intPreferencesKey("minute")
        val DAYS = stringPreferencesKey("days")
        val RINGTONE = stringPreferencesKey("ringtone")
        val CHALLENGE = stringPreferencesKey("challenge")
        val DIFFICULTY = stringPreferencesKey("difficulty")
        val ARMED = booleanPreferencesKey("armed")
    }

    val config: Flow<AlarmConfig> = context.dataStore.data.map { prefs ->
        val default = AlarmConfig.DEFAULT
        AlarmConfig(
            hour = prefs[Keys.HOUR] ?: default.hour,
            minute = prefs[Keys.MINUTE] ?: default.minute,
            days = prefs[Keys.DAYS]?.let(AlarmConfigCodec::decodeDays) ?: default.days,
            ringtoneId = prefs[Keys.RINGTONE] ?: default.ringtoneId,
            challengeId = prefs[Keys.CHALLENGE]
                ?.let { runCatching { ChallengeId.valueOf(it) }.getOrNull() }
                ?: default.challengeId,
            difficulty = prefs[Keys.DIFFICULTY]
                ?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() }
                ?: default.difficulty,
            armed = prefs[Keys.ARMED] ?: default.armed,
        )
    }

    suspend fun current(): AlarmConfig = config.first()

    suspend fun update(transform: (AlarmConfig) -> AlarmConfig) {
        val updated = transform(current())
        context.dataStore.edit { prefs ->
            prefs[Keys.HOUR] = updated.hour
            prefs[Keys.MINUTE] = updated.minute
            prefs[Keys.DAYS] = AlarmConfigCodec.encodeDays(updated.days)
            prefs[Keys.RINGTONE] = updated.ringtoneId
            prefs[Keys.CHALLENGE] = updated.challengeId.name
            prefs[Keys.DIFFICULTY] = updated.difficulty.name
            prefs[Keys.ARMED] = updated.armed
        }
    }
}
