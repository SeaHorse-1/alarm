package com.example.intervalalarm.ui

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.intervalalarm.AlarmItem
import com.example.intervalalarm.AlarmPreferences
import com.example.intervalalarm.AlarmScheduler
import com.example.intervalalarm.activeDaysSummary
import com.example.intervalalarm.dayLabel
import com.example.intervalalarm.defaultActiveDays
import com.example.intervalalarm.defaultAlarmItem
import com.example.intervalalarm.endTotalMinutes
import com.example.intervalalarm.everyday
import com.example.intervalalarm.startTotalMinutes
import com.example.intervalalarm.weekends
import com.example.intervalalarm.workdays
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun IntervalAlarmApp(
    preferences: AlarmPreferences,
    scheduler: AlarmScheduler,
    exactAlarmReady: Boolean,
    notificationReady: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
) {
    val alarms by preferences.alarms.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingAlarm by remember { mutableStateOf<AlarmItem?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingAlarm = defaultAlarmItem() },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("新增闹钟") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HeaderCard(
                exactAlarmReady = exactAlarmReady,
                notificationReady = notificationReady,
                alarmCount = alarms.size,
            )

            if (alarms.isEmpty()) {
                EmptyStateCard()
            } else {
                alarms.sortedByDescending { it.enabled }.forEach { alarm ->
                    AlarmListCard(
                        alarm = alarm,
                        onEdit = { editingAlarm = alarm },
                        onDelete = {
                            scope.launch {
                                scheduler.delete(alarm.id)
                                snackbarHostState.showSnackbar("闹钟已删除。")
                            }
                        },
                        onToggleEnabled = { enabled ->
                            scope.launch {
                                if (enabled && !exactAlarmReady) {
                                    snackbarHostState.showSnackbar(
                                        "需要先允许精确闹钟权限。",
                                    )
                                    onRequestExactAlarmPermission()
                                    return@launch
                                }

                                if (enabled && !notificationReady && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    snackbarHostState.showSnackbar(
                                        "建议打开通知权限，否则响铃通知可能不可见。",
                                    )
                                    onRequestNotificationPermission()
                                }

                                if (enabled) {
                                    val next = scheduler.schedule(alarm.copy(enabled = true))
                                    if (next != null) {
                                        snackbarHostState.showSnackbar(
                                            "已开启，下次触发在 ${next.formatFriendly()}。",
                                        )
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            "未能设置精确闹钟，该闹钟已被关闭。",
                                        )
                                        onRequestExactAlarmPermission()
                                    }
                                } else {
                                    scheduler.cancel(alarm.id)
                                    snackbarHostState.showSnackbar("闹钟已关闭。")
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (editingAlarm != null) {
        AlarmEditorDialog(
            initialAlarm = editingAlarm!!,
            onDismiss = { editingAlarm = null },
            onSave = { edited ->
                scope.launch {
                    val existing = alarms.any { it.id == edited.id }
                    if (edited.enabled) {
                        if (!exactAlarmReady) {
                            snackbarHostState.showSnackbar(
                                "需要先允许精确闹钟权限。",
                            )
                            onRequestExactAlarmPermission()
                            return@launch
                        }
                        val next = scheduler.schedule(edited)
                        if (next != null) {
                            snackbarHostState.showSnackbar(
                                if (existing) {
                                    "闹钟已更新，下次触发在 ${next.formatFriendly()}。"
                                } else {
                                    "闹钟已新增，下次触发在 ${next.formatFriendly()}。"
                                },
                            )
                        } else {
                            snackbarHostState.showSnackbar(
                                "未能设置精确闹钟，该闹钟已被关闭。",
                            )
                            onRequestExactAlarmPermission()
                        }
                    } else {
                        scheduler.saveDisabled(edited)
                        snackbarHostState.showSnackbar(
                            if (existing) {
                                "闹钟已保存。"
                            } else {
                                "新闹钟已创建。"
                            },
                        )
                    }
                    editingAlarm = null
                }
            },
        )
    }
}

@Composable
private fun HeaderCard(
    exactAlarmReady: Boolean,
    notificationReady: Boolean,
    alarmCount: Int,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "随机区间闹钟",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "每个闹钟都可以单独设定时间区间和生效星期。新闹钟默认是每天生效。",
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            Text(
                text = "当前共 $alarmCount 个闹钟",
                style = MaterialTheme.typography.titleSmall,
            )
            PermissionChips(
                exactAlarmReady = exactAlarmReady,
                notificationReady = notificationReady,
            )
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("还没有闹钟", style = MaterialTheme.typography.titleLarge)
            Text(
                "点击右下角“新增闹钟”，创建第一个区间闹钟。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlarmListCard(
    alarm: AlarmItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${alarm.startLabel()} - ${alarm.endLabel()}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "生效日：${alarm.activeDaysSummary()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = onToggleEnabled,
                )
            }

            Text(
                text = if (alarm.enabled && alarm.nextTriggerAt != null) {
                    "下次触发：${alarm.nextTriggerAt.formatFriendly()}"
                } else {
                    "当前未开启"
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AlarmEditorDialog(
    initialAlarm: AlarmItem,
    onDismiss: () -> Unit,
    onSave: (AlarmItem) -> Unit,
) {
    var draft by remember(initialAlarm.id) { mutableStateOf(initialAlarm) }
    var pickerMode by remember { mutableStateOf<PickerMode?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialAlarm == draft && initialAlarm.nextTriggerAt == null && !initialAlarm.enabled) {
                    "新增闹钟"
                } else {
                    "编辑闹钟"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "左边界不能晚于右边界，新闹钟默认每天生效。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EditorTimeChip(
                        label = "开始",
                        value = draft.startLabel(),
                        onClick = { pickerMode = PickerMode.Start },
                    )
                    EditorTimeChip(
                        label = "结束",
                        value = draft.endLabel(),
                        onClick = { pickerMode = PickerMode.End },
                    )
                }
                Text(
                    text = "当前生效：${draft.activeDaysSummary()}",
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PresetChip("工作日", draft.activeDays == workdays()) { draft = draft.copy(activeDays = workdays()) }
                    PresetChip("周末", draft.activeDays == weekends()) { draft = draft.copy(activeDays = weekends()) }
                    PresetChip("每天", draft.activeDays == everyday()) { draft = draft.copy(activeDays = everyday()) }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = draft.activeDays.contains(day.value),
                            onClick = {
                                draft.activeDays.toggleDay(day.value)?.let { updated ->
                                    draft = draft.copy(activeDays = updated)
                                }
                            },
                            label = { Text(dayLabel(day.value)) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("保存后立即启用")
                    Switch(
                        checked = draft.enabled,
                        onCheckedChange = { draft = draft.copy(enabled = it) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )

    if (pickerMode != null) {
        TimePickerDialog(
            initialHour = if (pickerMode == PickerMode.Start) draft.startHour else draft.endHour,
            initialMinute = if (pickerMode == PickerMode.Start) draft.startMinute else draft.endMinute,
            title = if (pickerMode == PickerMode.Start) "开始时间" else "结束时间",
            onDismiss = { pickerMode = null },
            onConfirm = { hour, minute ->
                draft = when (pickerMode) {
                    PickerMode.Start -> updateStart(draft, hour, minute)
                    PickerMode.End -> updateEnd(draft, hour, minute)
                    null -> draft
                }
                pickerMode = null
            },
        )
    }
}

@Composable
private fun EditorTimeChip(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text("$label $value") },
    )
}

private fun updateStart(alarm: AlarmItem, hour: Int, minute: Int): AlarmItem {
    val newStart = hour * 60 + minute
    val end = alarm.endTotalMinutes()
    return if (newStart <= end) {
        alarm.copy(startHour = hour, startMinute = minute)
    } else {
        alarm.copy(startHour = alarm.endHour, startMinute = alarm.endMinute)
    }
}

private fun updateEnd(alarm: AlarmItem, hour: Int, minute: Int): AlarmItem {
    val start = alarm.startTotalMinutes()
    val newEnd = hour * 60 + minute
    return if (newEnd >= start) {
        alarm.copy(endHour = hour, endMinute = minute)
    } else {
        alarm.copy(endHour = alarm.startHour, endMinute = alarm.startMinute)
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionChips(
    exactAlarmReady: Boolean,
    notificationReady: Boolean,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AssistChip(
            onClick = {},
            label = {
                Text(
                    if (exactAlarmReady) {
                        "精确闹钟权限已就绪"
                    } else {
                        "缺少精确闹钟权限"
                    },
                )
            },
        )
        AssistChip(
            onClick = {},
            label = {
                Text(
                    if (notificationReady) {
                        "通知权限已就绪"
                    } else {
                        "缺少通知权限"
                    },
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TimeInput(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private fun Set<Int>.toggleDay(day: Int): Set<Int>? {
    return if (contains(day)) {
        val updated = this - day
        updated.takeIf { it.isNotEmpty() }
    } else {
        this + day
    }
}

private fun AlarmItem.startLabel(): String = "%02d:%02d".format(startHour, startMinute)

private fun AlarmItem.endLabel(): String = "%02d:%02d".format(endHour, endMinute)

@SuppressLint("NewApi")
private fun LocalDateTime.formatFriendly(): String {
    return format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
}

private enum class PickerMode {
    Start,
    End,
}
