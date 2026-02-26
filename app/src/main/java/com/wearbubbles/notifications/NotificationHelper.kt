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
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.wearbubbles.MainActivity
import com.wearbubbles.data.SettingsDataStore

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

        val sender = Person.Builder()
            .setName(senderName)
            .build()

        // Get existing messaging style to append, or create new
        val existingNotification = manager.activeNotifications
            .firstOrNull { it.id == chatGuid.hashCode() }
        val style = existingNotification?.notification?.let {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it)
        } ?: NotificationCompat.MessagingStyle(
            Person.Builder().setName("Me").build()
        )

        style.addMessage(
            messageText ?: "New message",
            System.currentTimeMillis(),
            sender
        )

        // Tap to open conversation
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("chatGuid", chatGuid)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context, chatGuid.hashCode(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Inline reply action
        val remoteInput = RemoteInput.Builder("reply")
            .setLabel("Reply")
            .build()
        val replyIntent = Intent(context, ReplyReceiver::class.java).apply {
            putExtra("chatGuid", chatGuid)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context, chatGuid.hashCode(), replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapPendingIntent)
            .addAction(replyAction)
            .setAutoCancel(true)
            .build()

        manager.notify(chatGuid.hashCode(), notification)
    }

    fun updateNotificationAfterReply(context: Context, chatGuid: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(chatGuid.hashCode())
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
