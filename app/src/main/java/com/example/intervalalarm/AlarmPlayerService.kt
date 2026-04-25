package com.example.intervalalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

class AlarmPlayerService : Service() {

    private var ringtone: Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        playAlarm()
        return START_STICKY
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, AlarmPlayerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            3001,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.notification_alarm_title))
            .setContentText(getString(R.string.notification_alarm_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .addAction(0, getString(R.string.notification_stop), stopPendingIntent)
            .build()
    }

    private fun playAlarm() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification())

        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLooping = true
            }
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            play()
        }

        val vibrator = getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 400, 300, 400), 0),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 400, 300, 400), 0)
        }
    }

    private fun stopAlarm() {
        ringtone?.stop()
        ringtone = null
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        val vibrator = getSystemService(Vibrator::class.java)
        vibrator.cancel()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            getString(R.string.channel_name_alarm),
            NotificationManager.IMPORTANCE_HIGH,
        )
        val scheduleChannel = NotificationChannel(
            CHANNEL_SCHEDULE,
            getString(R.string.channel_name_schedule),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(alarmChannel)
        manager.createNotificationChannel(scheduleChannel)
    }

    companion object {
        const val CHANNEL_ALARM = "alarm_alerts"
        const val CHANNEL_SCHEDULE = "alarm_schedule"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "com.example.intervalalarm.STOP_ALARM"
    }
}
