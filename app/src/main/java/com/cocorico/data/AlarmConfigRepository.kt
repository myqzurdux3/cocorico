package com.cocorico.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cocorico.challenge.photo.CatalogueObjets
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

/**
 * Persists the unique alarm configuration to DataStore.
 *
 * @param context The application context (not an activity context).
 */
class AlarmConfigRepository(private val context: Context) {

    private object Keys {
        val HOUR = intPreferencesKey("hour")
        val MINUTE = intPreferencesKey("minute")
        val DAYS = stringPreferencesKey("days")
        val RINGTONE = stringPreferencesKey("ringtone")
        val CHALLENGE = stringPreferencesKey("challenge")
        val DIFFICULTY = stringPreferencesKey("difficulty")
        val ARMED = booleanPreferencesKey("armed")
        val CLE_API = stringPreferencesKey("cle_api")
        val OBJETS_SELECTIONNES = stringSetPreferencesKey("objets_selectionnes")
    }

    /** Lecture d'un instantané de préférences, partagée par le flux et l'écriture. */
    private fun lire(prefs: Preferences): AlarmConfig {
        val default = AlarmConfig.DEFAULT
        return AlarmConfig(
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
            cleApi = prefs[Keys.CLE_API] ?: default.cleApi,
            // Un identifiant persisté qui n'existe plus dans le catalogue —
            // objet retiré depuis une mise à jour — est ignoré par
            // `idsValides` plutôt que de fausser le tirage ou l'écran de
            // sélection avec une case qu'il n'affichera jamais.
            objetsSelectionnes = prefs[Keys.OBJETS_SELECTIONNES]
                ?.let(CatalogueObjets::idsValides)
                ?: default.objetsSelectionnes,
        )
    }

    val config: Flow<AlarmConfig> = context.dataStore.data.map(::lire)

    suspend fun current(): AlarmConfig = config.first()

    /**
     * Lecture et écriture dans la même transaction `edit` : deux mises à jour
     * concurrentes (deux jours cochés coup sur coup) ne doivent pas se perdre.
     */
    suspend fun update(transform: (AlarmConfig) -> AlarmConfig) {
        context.dataStore.edit { prefs ->
            val updated = transform(lire(prefs))
            prefs[Keys.HOUR] = updated.hour
            prefs[Keys.MINUTE] = updated.minute
            prefs[Keys.DAYS] = AlarmConfigCodec.encodeDays(updated.days)
            prefs[Keys.RINGTONE] = updated.ringtoneId
            prefs[Keys.CHALLENGE] = updated.challengeId.name
            prefs[Keys.DIFFICULTY] = updated.difficulty.name
            prefs[Keys.ARMED] = updated.armed
            prefs[Keys.CLE_API] = updated.cleApi
            prefs[Keys.OBJETS_SELECTIONNES] = updated.objetsSelectionnes
        }
    }
}
