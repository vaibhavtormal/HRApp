package com.example.hrapp.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.example.hrapp.MainActivity
import com.example.hrapp.R
import com.example.hrapp.data.db.Notification
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var firestoreListener: ListenerRegistration? = null
    
    companion object {
        private const val CHANNEL_ID = "hr_broadcast_channel"
        private const val CHANNEL_NAME = "HR App Notifications"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startListeningNotifications()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows real-time announcements, holiday alerts, and training videos"
                enableLights(true)
                enableVibration(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startListeningNotifications() {
        val firestore = FirebaseFirestore.getInstance()
        // Listen only to new notification documents added after the current service start time
        val startTime = System.currentTimeMillis() - 2000 // Small buffer to prevent duplicates on launch

        firestoreListener = firestore.collection("notifications")
            .whereGreaterThan("timestamp", startTime)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener

                for (dc in snapshots.documentChanges) {
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val notif = dc.document.toObject(Notification::class.java)
                        postSystemNotification(notif)
                    }
                }
            }
    }

    private fun postSystemNotification(notif: Notification) {
        scope.launch {
            val context = applicationContext
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                // Parse video URL if category is Learning
                if (notif.type == "Learning") {
                    val desc = notif.description
                    val marker = "Link: "
                    if (desc.contains(marker)) {
                        val linkPart = desc.substringAfter(marker).substringBefore("\n").trim()
                        putExtra("play_video_id", linkPart)
                    }
                }
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notif.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Try to extract thumbnail image url from desc if the type is learning
            var thumbnailUrl: String? = null
            if (notif.type == "Learning") {
                val allVideos = FirebaseFirestore.getInstance().collection("videos").get()
                try {
                    val task = com.google.android.gms.tasks.Tasks.await(allVideos)
                    for (doc in task.documents) {
                        val title = doc.getString("title") ?: ""
                        if (notif.title.contains(title, ignoreCase = true)) {
                            thumbnailUrl = doc.getString("thumbnailUrl")
                            break
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to extraction patterns if needed
                }
            }

            var bitmap: Bitmap? = null
            if (!thumbnailUrl.isNullOrEmpty()) {
                bitmap = fetchBitmap(thumbnailUrl)
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(notif.title)
                .setContentText(notif.description)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            if (bitmap != null) {
                // High premium style using BigPictureStyle with LargeIcon
                builder.setLargeIcon(bitmap)
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText(notif.description)
                )
            } else {
                builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(notif.description)
                )
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notif.id.toInt(), builder.build())
        }
    }

    private suspend fun fetchBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            Glide.with(applicationContext)
                .asBitmap()
                .load(url)
                .submit()
                .get()
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        firestoreListener?.remove()
        job.cancel()
    }
}
