package com.example.intervalalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val alarmId = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: return

        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmPlayerService::class.java),
        )

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val preferences = AlarmPreferences(context)
                val scheduler = AlarmScheduler(context, preferences)
                scheduler.onAlarmTriggered(alarmId)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to handle triggered alarm: $alarmId", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}
