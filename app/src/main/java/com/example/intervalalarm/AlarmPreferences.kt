package com.example.intervalalarm

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private val Context.dataStore by preferencesDataStore(name = "alarm_preferences")

class AlarmPreferences(private val context: Context) {

    val alarms: Flow<List<AlarmItem>> =
        context.dataStore.data.map { preferences ->
            decodeAlarms(preferences[Keys.ALARMS_JSON])
        }

    suspend fun replaceAll(alarms: List<AlarmItem>) {
        context.dataStore.edit { preferences ->
            preferences[Keys.ALARMS_JSON] = encodeAlarms(alarms)
        }
    }

    suspend fun upsert(alarm: AlarmItem) {
        context.dataStore.edit { preferences ->
            val current = decodeAlarms(preferences[Keys.ALARMS_JSON])
            val updated = current
                .filterNot { it.id == alarm.id }
                .plus(alarm)
                .sortedBy { it.id }
            preferences[Keys.ALARMS_JSON] = encodeAlarms(updated)
        }
    }

    suspend fun update(id: String, transform: (AlarmItem) -> AlarmItem) {
        context.dataStore.edit { preferences ->
            val current = decodeAlarms(preferences[Keys.ALARMS_JSON])
            val updated = current.map { alarm ->
                if (alarm.id == id) transform(alarm) else alarm
            }
            preferences[Keys.ALARMS_JSON] = encodeAlarms(updated)
        }
    }

    suspend fun delete(id: String) {
        context.dataStore.edit { preferences ->
            val current = decodeAlarms(preferences[Keys.ALARMS_JSON])
            preferences[Keys.ALARMS_JSON] = encodeAlarms(current.filterNot { it.id == id })
        }
    }

    private fun encodeAlarms(alarms: List<AlarmItem>): String {
        val array = JSONArray()
        alarms.forEach { alarm ->
            array.put(
                JSONObject().apply {
                    put("id", alarm.id)
                    put("startHour", alarm.startHour)
                    put("startMinute", alarm.startMinute)
                    put("endHour", alarm.endHour)
                    put("endMinute", alarm.endMinute)
                    put("enabled", alarm.enabled)
                    put("nextTriggerAt", alarm.nextTriggerAt?.let(::toEpochMillis))
                    put("activeDays", encodeDays(alarm.activeDays))
                },
            )
        }
        return array.toString()
    }

    private fun decodeAlarms(raw: String?): List<AlarmItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    runCatching {
                        val item = array.getJSONObject(index)
                        AlarmItem(
                            id = item.optString("id"),
                            startHour = item.optInt("startHour", 7),
                            startMinute = item.optInt("startMinute", 0),
                            endHour = item.optInt("endHour", 8),
                            endMinute = item.optInt("endMinute", 0),
                            enabled = item.optBoolean("enabled", false),
                            nextTriggerAt = item.optLong("nextTriggerAt")
                                .takeIf { it > 0L }
                                ?.let(::fromEpochMillis),
                            activeDays = decodeDays(item.optInt("activeDays", encodeDays(defaultActiveDays()))),
                        )
                    }.getOrNull()?.let(::add)
                }
            }.sortedBy { it.id }
        }.getOrDefault(emptyList())
    }

    private fun fromEpochMillis(epochMillis: Long): LocalDateTime {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
    }

    private fun toEpochMillis(dateTime: LocalDateTime): Long {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private object Keys {
        val ALARMS_JSON: Preferences.Key<String> = stringPreferencesKey("alarms_json")
    }
}
