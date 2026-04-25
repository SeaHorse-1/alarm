package com.example.intervalalarm

import java.time.DayOfWeek
import java.time.LocalDateTime

private val everyDaySet = DayOfWeek.entries.map { it.value }.toSet()
private val workdaySet = setOf(
    DayOfWeek.MONDAY.value,
    DayOfWeek.TUESDAY.value,
    DayOfWeek.WEDNESDAY.value,
    DayOfWeek.THURSDAY.value,
    DayOfWeek.FRIDAY.value,
)

data class AlarmItem(
    val id: String,
    val startHour: Int = 7,
    val startMinute: Int = 0,
    val endHour: Int = 8,
    val endMinute: Int = 0,
    val enabled: Boolean = false,
    val nextTriggerAt: LocalDateTime? = null,
    val activeDays: Set<Int> = everyDaySet,
)

fun AlarmItem.startTotalMinutes(): Int = startHour * 60 + startMinute

fun AlarmItem.endTotalMinutes(): Int = endHour * 60 + endMinute

fun defaultActiveDays(): Set<Int> = everyDaySet

fun defaultAlarmItem(id: String = System.currentTimeMillis().toString()): AlarmItem = AlarmItem(id = id)

fun workdays(): Set<Int> = workdaySet

fun weekends(): Set<Int> = setOf(DayOfWeek.SATURDAY.value, DayOfWeek.SUNDAY.value)

fun everyday(): Set<Int> = everyDaySet

fun encodeDays(days: Set<Int>): Int {
    return days.fold(0) { mask, day -> mask or (1 shl (day - 1)) }
}

fun decodeDays(mask: Int): Set<Int> {
    val decoded = DayOfWeek.entries
        .map { it.value }
        .filter { day -> mask and (1 shl (day - 1)) != 0 }
        .toSet()
    return decoded.ifEmpty { everyDaySet }
}

fun dayLabel(day: Int): String {
    return when (day) {
        DayOfWeek.MONDAY.value -> "\u5468\u4e00"
        DayOfWeek.TUESDAY.value -> "\u5468\u4e8c"
        DayOfWeek.WEDNESDAY.value -> "\u5468\u4e09"
        DayOfWeek.THURSDAY.value -> "\u5468\u56db"
        DayOfWeek.FRIDAY.value -> "\u5468\u4e94"
        DayOfWeek.SATURDAY.value -> "\u5468\u516d"
        DayOfWeek.SUNDAY.value -> "\u5468\u65e5"
        else -> ""
    }
}

fun AlarmItem.activeDaysSummary(): String {
    val normalized = activeDays.sorted()
    val workdays = workdaySet.sorted()
    val weekends = listOf(DayOfWeek.SATURDAY.value, DayOfWeek.SUNDAY.value)
    val everyday = DayOfWeek.entries.map { it.value }

    return when {
        normalized == everyday -> "\u6bcf\u5929"
        normalized == workdays -> "\u5de5\u4f5c\u65e5"
        normalized == weekends -> "\u5468\u672b"
        normalized.size == 1 -> dayLabel(normalized.first())
        else -> normalized.joinToString("\u3001") { dayLabel(it) }
    }
}
