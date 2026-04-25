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
                text = { Text("\u65b0\u589e\u95f9\u949f") },
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
                                snackbarHostState.showSnackbar("\u95f9\u949f\u5df2\u5220\u9664\u3002")
                            }
                        },
                        onToggleEnabled = { enabled ->
                            scope.launch {
                                if (enabled && !exactAlarmReady) {
                                    snackbarHostState.showSnackbar(
                                        "\u9700\u8981\u5148\u5141\u8bb8\u7cbe\u786e\u95f9\u949f\u6743\u9650\u3002",
                                    )
                                    onRequestExactAlarmPermission()
                                    return@launch
                                }

                                if (enabled && !notificationReady && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    snackbarHostState.showSnackbar(
                                        "\u5efa\u8bae\u6253\u5f00\u901a\u77e5\u6743\u9650\uff0c\u5426\u5219\u54cd\u94c3\u901a\u77e5\u53ef\u80fd\u4e0d\u53ef\u89c1\u3002",
                                    )
                                    onRequestNotificationPermission()
                                }

                                if (enabled) {
                                    val next = scheduler.schedule(alarm.copy(enabled = true))
                                    if (next != null) {
                                        snackbarHostState.showSnackbar(
                                            "\u5df2\u5f00\u542f\uff0c\u4e0b\u6b21\u89e6\u53d1\u5728 ${next.formatFriendly()}\u3002",
                                        )
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            "\u672a\u80fd\u8bbe\u7f6e\u7cbe\u786e\u95f9\u949f\uff0c\u8be5\u95f9\u949f\u5df2\u88ab\u5173\u95ed\u3002",
                                        )
                                        onRequestExactAlarmPermission()
                                    }
                                } else {
                                    scheduler.cancel(alarm.id)
                                    snackbarHostState.showSnackbar("\u95f9\u949f\u5df2\u5173\u95ed\u3002")
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
                                "\u9700\u8981\u5148\u5141\u8bb8\u7cbe\u786e\u95f9\u949f\u6743\u9650\u3002",
                            )
                            onRequestExactAlarmPermission()
                            return@launch
                        }
                        val next = scheduler.schedule(edited)
                        if (next != null) {
                            snackbarHostState.showSnackbar(
                                if (existing) {
                                    "\u95f9\u949f\u5df2\u66f4\u65b0\uff0c\u4e0b\u6b21\u89e6\u53d1\u5728 ${next.formatFriendly()}\u3002"
                                } else {
                                    "\u95f9\u949f\u5df2\u65b0\u589e\uff0c\u4e0b\u6b21\u89e6\u53d1\u5728 ${next.formatFriendly()}\u3002"
                                },
                            )
                        } else {
                            snackbarHostState.showSnackbar(
                                "\u672a\u80fd\u8bbe\u7f6e\u7cbe\u786e\u95f9\u949f\uff0c\u8be5\u95f9\u949f\u5df2\u88ab\u5173\u95ed\u3002",
                            )
                            onRequestExactAlarmPermission()
                        }
                    } else {
                        scheduler.saveDisabled(edited)
                        snackbarHostState.showSnackbar(
                            if (existing) {
                                "\u95f9\u949f\u5df2\u4fdd\u5b58\u3002"
                            } else {
                                "\u65b0\u95f9\u949f\u5df2\u521b\u5efa\u3002"
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
                text = "\u968f\u673a\u533a\u95f4\u95f9\u949f",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "\u6bcf\u4e2a\u95f9\u949f\u90fd\u53ef\u4ee5\u5355\u72ec\u8bbe\u5b9a\u65f6\u95f4\u533a\u95f4\u548c\u751f\u6548\u661f\u671f\u3002\u65b0\u95f9\u949f\u9ed8\u8ba4\u662f\u6bcf\u5929\u751f\u6548\u3002",
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            Text(
                text = "\u5f53\u524d\u5171 $alarmCount \u4e2a\u95f9\u949f",
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
            Text("\u8fd8\u6ca1\u6709\u95f9\u949f", style = MaterialTheme.typography.titleLarge)
            Text(
                "\u70b9\u51fb\u53f3\u4e0b\u89d2\u201c\u65b0\u589e\u95f9\u949f\u201d\uff0c\u521b\u5efa\u7b2c\u4e00\u4e2a\u533a\u95f4\u95f9\u949f\u3002",
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
                        text = "\u751f\u6548\u65e5\uff1a${alarm.activeDaysSummary()}",
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
                    "\u4e0b\u6b21\u89e6\u53d1\uff1a${alarm.nextTriggerAt.formatFriendly()}"
                } else {
                    "\u5f53\u524d\u672a\u5f00\u542f"
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "\u7f16\u8f91")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "\u5220\u9664")
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
                    "\u65b0\u589e\u95f9\u949f"
                } else {
                    "\u7f16\u8f91\u95f9\u949f"
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
                    "\u5de6\u8fb9\u754c\u4e0d\u80fd\u665a\u4e8e\u53f3\u8fb9\u754c\uff0c\u65b0\u95f9\u949f\u9ed8\u8ba4\u6bcf\u5929\u751f\u6548\u3002",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EditorTimeChip(
                        label = "\u5f00\u59cb",
                        value = draft.startLabel(),
                        onClick = { pickerMode = PickerMode.Start },
                    )
                    EditorTimeChip(
                        label = "\u7ed3\u675f",
                        value = draft.endLabel(),
                        onClick = { pickerMode = PickerMode.End },
                    )
                }
                Text(
                    text = "\u5f53\u524d\u751f\u6548\uff1a${draft.activeDaysSummary()}",
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PresetChip("\u5de5\u4f5c\u65e5", draft.activeDays == workdays()) { draft = draft.copy(activeDays = workdays()) }
                    PresetChip("\u5468\u672b", draft.activeDays == weekends()) { draft = draft.copy(activeDays = weekends()) }
                    PresetChip("\u6bcf\u5929", draft.activeDays == everyday()) { draft = draft.copy(activeDays = everyday()) }
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
                    Text("\u4fdd\u5b58\u540e\u7acb\u5373\u542f\u7528")
                    Switch(
                        checked = draft.enabled,
                        onCheckedChange = { draft = draft.copy(enabled = it) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text("\u4fdd\u5b58")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("\u53d6\u6d88")
            }
        },
    )

    if (pickerMode != null) {
        TimePickerDialog(
            initialHour = if (pickerMode == PickerMode.Start) draft.startHour else draft.endHour,
            initialMinute = if (pickerMode == PickerMode.Start) draft.startMinute else draft.endMinute,
            title = if (pickerMode == PickerMode.Start) "\u5f00\u59cb\u65f6\u95f4" else "\u7ed3\u675f\u65f6\u95f4",
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
                        "\u7cbe\u786e\u95f9\u949f\u6743\u9650\u5df2\u5c31\u7eea"
                    } else {
                        "\u7f3a\u5c11\u7cbe\u786e\u95f9\u949f\u6743\u9650"
                    },
                )
            },
        )
        AssistChip(
            onClick = {},
            label = {
                Text(
                    if (notificationReady) {
                        "\u901a\u77e5\u6743\u9650\u5df2\u5c31\u7eea"
                    } else {
                        "\u7f3a\u5c11\u901a\u77e5\u6743\u9650"
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
                Text("\u786e\u5b9a")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("\u53d6\u6d88")
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
