package com.example.intervalalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

class AlarmScheduler(
    private val context: Context,
    private val preferences: AlarmPreferences,
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun schedule(alarm: AlarmItem): LocalDateTime? {
        val triggerAt = nextRandomTrigger(alarm)
        if (!scheduleExact(alarm.id, triggerAt)) {
            preferences.upsert(alarm.copy(enabled = false, nextTriggerAt = null))
            return null
        }
        preferences.upsert(alarm.copy(enabled = true, nextTriggerAt = triggerAt))
        return triggerAt
    }

    suspend fun saveDisabled(alarm: AlarmItem) {
        alarmManager.cancel(alarmPendingIntent(alarm.id))
        preferences.upsert(alarm.copy(enabled = false, nextTriggerAt = null))
    }

    suspend fun cancel(id: String) {
        alarmManager.cancel(alarmPendingIntent(id))
        preferences.update(id) { it.copy(enabled = false, nextTriggerAt = null) }
    }

    suspend fun delete(id: String) {
        alarmManager.cancel(alarmPendingIntent(id))
        preferences.delete(id)
    }

    suspend fun rescheduleAll() {
        val alarms = preferences.alarms.first()
        alarms.filter { it.enabled }.forEach { alarm ->
            val target = alarm.nextTriggerAt?.takeIf { it.isAfter(LocalDateTime.now()) }
                ?: nextRandomTrigger(alarm)
            if (scheduleExact(alarm.id, target)) {
                preferences.upsert(alarm.copy(enabled = true, nextTriggerAt = target))
            } else {
                preferences.upsert(alarm.copy(enabled = false, nextTriggerAt = null))
            }
        }
    }

    suspend fun onAlarmTriggered(id: String) {
        val alarms = preferences.alarms.first()
        val alarm = alarms.firstOrNull { it.id == id } ?: return
        if (!alarm.enabled) return

        val next = nextRandomTrigger(alarm)
        if (scheduleExact(id, next)) {
            preferences.upsert(alarm.copy(nextTriggerAt = next))
        } else {
            preferences.upsert(alarm.copy(enabled = false, nextTriggerAt = null))
        }
    }

    fun nextRandomTrigger(alarm: AlarmItem, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val selectedDays = alarm.activeDays.ifEmpty { defaultActiveDays() }
        val start = LocalTime.of(alarm.startHour, alarm.startMinute)
        val end = LocalTime.of(alarm.endHour, alarm.endMinute)
        val earliestMoment = now.plusMinutes(1)

        repeat(14) { offset ->
            val candidateDate = now.toLocalDate().plusDays(offset.toLong())
            if (!selectedDays.contains(candidateDate.dayOfWeek.value)) {
                return@repeat
            }

            val candidateStart = LocalDateTime.of(candidateDate, start)
            val candidateEnd = LocalDateTime.of(candidateDate, end)

            if (candidateStart == candidateEnd) {
                if (!candidateStart.isBefore(earliestMoment)) {
                    return candidateStart
                }
                return@repeat
            }

            val earliest = maxOf(candidateStart, earliestMoment)
            if (earliest.isAfter(candidateEnd)) {
                return@repeat
            }

            val startEpoch = earliest.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endEpoch = candidateEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val randomEpoch = if (startEpoch == endEpoch) {
                startEpoch
            } else {
                Random.nextLong(startEpoch, endEpoch + 1)
            }

            return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(randomEpoch),
                ZoneId.systemDefault(),
            )
        }

        val fallbackDate = nextActiveDateAfter(now.toLocalDate(), selectedDays)
        return LocalDateTime.of(fallbackDate, start)
    }

    private fun nextActiveDateAfter(startDate: LocalDate, selectedDays: Set<Int>): LocalDate {
        var candidate = startDate.plusDays(1)
        while (!selectedDays.contains(candidate.dayOfWeek.value)) {
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    private fun scheduleExact(id: String, triggerAt: LocalDateTime): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false
        }

        val epochMillis = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                epochMillis,
                alarmPendingIntent(id),
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun alarmPendingIntent(id: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            data = Uri.parse("intervalalarm://alarm/${Uri.encode(id)}")
            putExtra(EXTRA_ALARM_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
