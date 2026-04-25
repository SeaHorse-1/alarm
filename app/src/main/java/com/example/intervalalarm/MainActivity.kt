package com.example.intervalalarm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.intervalalarm.ui.IntervalAlarmApp
import com.example.intervalalarm.ui.theme.IntervalAlarmTheme

class MainActivity : ComponentActivity() {

    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var alarmPreferences: AlarmPreferences
    private lateinit var alarmManager: AlarmManager

    private var exactAlarmReady by mutableStateOf(false)
    private var notificationReady by mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationReady = granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        alarmPreferences = AlarmPreferences(applicationContext)
        alarmScheduler = AlarmScheduler(applicationContext, alarmPreferences)
        alarmManager = getSystemService(AlarmManager::class.java)

        refreshPermissionState()

        setContent {
            IntervalAlarmTheme {
                IntervalAlarmApp(
                    preferences = alarmPreferences,
                    scheduler = alarmScheduler,
                    exactAlarmReady = exactAlarmReady,
                    notificationReady = notificationReady,
                    onRequestNotificationPermission = ::requestNotificationPermission,
                    onRequestExactAlarmPermission = ::requestExactAlarmPermission,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    private fun refreshPermissionState() {
        exactAlarmReady =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

        notificationReady =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }
}

fun Context.openAppNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        },
    )
}
