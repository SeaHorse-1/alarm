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
        DayOfWeek.MONDAY.value -> "周一"
        DayOfWeek.TUESDAY.value -> "周二"
        DayOfWeek.WEDNESDAY.value -> "周三"
        DayOfWeek.THURSDAY.value -> "周四"
        DayOfWeek.FRIDAY.value -> "周五"
        DayOfWeek.SATURDAY.value -> "周六"
        DayOfWeek.SUNDAY.value -> "周日"
        else -> ""
    }
}

fun AlarmItem.activeDaysSummary(): String {
    val normalized = activeDays.sorted()
    val workdays = workdaySet.sorted()
    val weekends = listOf(DayOfWeek.SATURDAY.value, DayOfWeek.SUNDAY.value)
    val everyday = DayOfWeek.entries.map { it.value }

    return when {
        normalized == everyday -> "每天"
        normalized == workdays -> "工作日"
        normalized == weekends -> "周末"
        normalized.size == 1 -> dayLabel(normalized.first())
        else -> normalized.joinToString("、") { dayLabel(it) }
    }
}
