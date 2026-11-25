package com.ddas.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ddas.app.R
import com.ddas.app.citizen.AlertActivity
import com.ddas.app.medical.MedicalHomeActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_CITIZEN = "earthquake_alarm"
        const val CHANNEL_MEDICAL = "medical_dispatch_alarm"
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val type      = message.data["type"] ?: return
        val magnitude = message.data["magnitude"] ?: "0"

        android.util.Log.d("DDAS_FCM", "Received: type=$type")

        val role = getSharedPreferences("ddas_prefs", MODE_PRIVATE)
            .getString("user_role", "") ?: ""

        android.util.Log.d("DDAS_FCM", "Role=$role")

        when (type) {
            "EARTHQUAKE_ALERT", "MANUAL_ALERT" -> {
                when (role) {
                    "medical" -> showMedicalInfoNotification()
                    else      -> showCitizenAlarm(magnitude)
                }
            }
            "MEDICAL_DISPATCH" -> {
                // Only handle if this user is medical
                if (role == "medical") {
                    showMedicalDispatchAlarm(magnitude)
                }
            }
        }
    }

    // ── Citizen full screen alarm ─────────────────────────────────────────────
    private fun showCitizenAlarm(magnitude: String) {
        val nm        = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val alarmUri  = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.deleteNotificationChannel(CHANNEL_CITIZEN)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_CITIZEN, "Earthquake Alarm",
                    NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                    setSound(alarmUri, AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build())
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            )
        }

        val intent = Intent(this, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("magnitude", magnitude)
        }
        val pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        nm.notify(1001,
            NotificationCompat.Builder(this, CHANNEL_CITIZEN)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("⚠️ EARTHQUAKE ALERT")
                .setContentText("Magnitude: $magnitude m/s² — Are you safe?")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(alarmUri)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .setAutoCancel(false)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .build()
        )

        try { startActivity(intent) } catch (e: Exception) { }
    }

    // ── Medical dispatch alarm (when citizen clicks No) ───────────────────────
    private fun showMedicalDispatchAlarm(magnitude: String) {
        val nm       = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.deleteNotificationChannel(CHANNEL_MEDICAL)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_MEDICAL, "Medical Dispatch Alarm",
                    NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                    setSound(alarmUri, AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build())
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            )
        }

        val intent = Intent(this, MedicalHomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_dispatch", true)
        }
        val pi = PendingIntent.getActivity(this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        nm.notify(1002,
            NotificationCompat.Builder(this, CHANNEL_MEDICAL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("🚑 DISPATCH REQUIRED")
                .setContentText("Victim needs help! Magnitude: $magnitude — Tap to view location")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(alarmUri)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .build()
        )

        try { startActivity(intent) } catch (e: Exception) { }
    }

    // ── Medical info notification (earthquake detected, no dispatch yet) ───────
    private fun showMedicalInfoNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel("medical_info", "Medical Info",
                    NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        nm.notify(1003,
            NotificationCompat.Builder(this, "medical_info")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("⚠️ Earthquake Detected")
                .setContentText("Monitor citizen responses. Dispatch may be required.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("fcmToken")
            .setValue(token)
    }
}
