package com.wearbubbles.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.wearbubbles.MainActivity
import com.wearbubbles.data.SettingsDataStore
import kotlinx.coroutines.flow.first

object NotificationHelper {

    private const val CHANNEL_ID = "new_messages"
    const val SERVICE_CHANNEL_ID = "message_listener_service"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val messageChannel = NotificationChannel(
                CHANNEL_ID,
                "New Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new iMessages"
            }
            manager.createNotificationChannel(messageChannel)

            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Message Listener",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps message listener running"
                setShowBadge(false)
            }
            manager.createNotificationChannel(serviceChannel)
        }
    }

    fun showNewMessageNotification(
        context: Context,
        senderName: String,
        messageText: String?,
        chatGuid: String
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("chatGuid", chatGuid)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, chatGuid.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setContentTitle(senderName)
            .setContentText(messageText ?: "New message")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Use chatGuid hashCode so each chat gets its own notification
        manager.notify(chatGuid.hashCode(), notification)
    }

    suspend fun vibrateIfEnabled(context: Context) {
        val settings = SettingsDataStore(context)
        if (!settings.getHapticEnabled()) return

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Double-tap pattern (similar to iMessage)
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100), -1)
        vibrator.vibrate(effect)
    }
}
