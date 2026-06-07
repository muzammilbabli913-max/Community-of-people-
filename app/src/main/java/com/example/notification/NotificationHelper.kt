package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import java.io.Serializable

data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: NotificationType,
    val timestamp: Long = System.currentTimeMillis(),
    val relatedId: Long? = null, // e.g., partnerId for Message, listingId for Listing
    var isRead: Boolean = false
) : Serializable

enum class NotificationType {
    DIRECT_MESSAGE,
    NEW_LISTING,
    SYSTEM
}

object NotificationHelper {
    private const val CHANNEL_MESSAGES_ID = "channel_neighborhood_messages"
    private const val CHANNEL_MESSAGES_NAME = "Direct Messages"
    private const val CHANNEL_LISTINGS_ID = "channel_neighborhood_listings"
    private const val CHANNEL_LISTINGS_NAME = "Nearby Listings"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                CHANNEL_MESSAGES_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for new direct messages from your neighbors."
                enableVibration(true)
            }

            val listingsChannel = NotificationChannel(
                CHANNEL_LISTINGS_ID,
                CHANNEL_LISTINGS_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for tools, services, and garden-fresh trade offerings posted nearby."
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(messagesChannel)
            notificationManager.createNotificationChannel(listingsChannel)
        }
    }

    fun showSystemNotification(
        context: Context,
        title: String,
        message: String,
        type: NotificationType,
        relatedId: Long? = null
    ) {
        val channelId = when (type) {
            NotificationType.DIRECT_MESSAGE -> CHANNEL_MESSAGES_ID
            else -> CHANNEL_LISTINGS_ID
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_NOTIFICATION_TYPE", type.name)
            if (relatedId != null) {
                putExtra("EXTRA_RELATED_ID", relatedId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            when (type) {
                NotificationType.DIRECT_MESSAGE -> 4001
                else -> 4002
            },
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Generate a pseudo-random notification integer ID
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val iconRes = android.R.drawable.ic_dialog_info

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(
                when (type) {
                    NotificationType.DIRECT_MESSAGE -> NotificationCompat.PRIORITY_HIGH
                    else -> NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            // Check POST_NOTIFICATIONS permission at runtime or catch security exception safely
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
