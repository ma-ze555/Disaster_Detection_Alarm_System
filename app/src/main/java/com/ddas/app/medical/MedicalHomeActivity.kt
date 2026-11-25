package com.ddas.app.medical

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ddas.app.auth.LoginActivity
import com.ddas.app.common.RoleSelectActivity
import com.ddas.app.databinding.ActivityMedicalHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MedicalHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMedicalHomeBinding
    private var escalationListener: ChildEventListener? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMedicalHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Subscribe to medical alerts topic
        com.google.firebase.messaging.FirebaseMessaging.getInstance()
            .subscribeToTopic("medical_dispatch")

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        // Load name
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("name").get()
            .addOnSuccessListener { binding.tvWelcome.text = "Hello, ${it.value} 👋" }

        // If launched from FCM dispatch notification, open victims map immediately
        if (intent.getBooleanExtra("from_dispatch", false)) {
            startActivity(Intent(this, VictimsMapActivity::class.java))
        }

        // Listen for NEW escalations
        listenForNewEscalations()

        // Also load existing pending escalations for display
        loadExistingEscalations()

        binding.btnSwitchRole.setOnClickListener {
            startActivity(Intent(this, RoleSelectActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            stopAlarm()
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }
    }

    private fun listenForNewEscalations() {
        // ChildEventListener fires only for NEW children added
        val ref = FirebaseDatabase.getInstance().reference.child("escalations")
        escalationListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val status = snapshot.child("status").getValue(String::class.java)
                if (status == "pending") {
                    // New escalation — trigger alarm!
                    triggerMedicalAlarm(snapshot)
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val status = snapshot.child("status").getValue(String::class.java)
                if (status == "pending") {
                    triggerMedicalAlarm(snapshot)
                } else {
                    // Resolved — stop alarm
                    stopAlarm()
                    loadExistingEscalations()
                }
            }
            override fun onChildRemoved(snapshot: DataSnapshot) { stopAlarm() }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addChildEventListener(escalationListener!!)
    }

    private fun triggerMedicalAlarm(snapshot: DataSnapshot) {
        val victimUid = snapshot.child("uid").getValue(String::class.java) ?: "Unknown"
        val time      = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

        // Start alarm sound
        startAlarm()

        // Update UI
        binding.tvEscalationStatus.text = "🆘 EMERGENCY DISPATCH REQUIRED!"
        binding.tvEscalationList.text   = "Victim: ...${victimUid.takeLast(6)}\n" +
            "Time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(time))}\n\n" +
            "Tap RESPOND to accept dispatch."
        binding.cardEscalation.setCardBackgroundColor(getColor(android.R.color.holo_red_dark))

        // Show respond button
        binding.btnRespond.visibility = android.view.View.VISIBLE
        binding.btnRespond.setOnClickListener {
            stopAlarm()
            // Open map showing all victim locations
            startActivity(Intent(this, VictimsMapActivity::class.java))
        }
    }

    private fun loadExistingEscalations() {
        FirebaseDatabase.getInstance().reference.child("escalations")
            .get().addOnSuccessListener { snapshot ->
                var pendingCount = 0
                val sb = StringBuilder()
                for (child in snapshot.children) {
                    val status = child.child("status").getValue(String::class.java)
                    if (status == "pending") {
                        pendingCount++
                        val uid  = child.child("uid").getValue(String::class.java) ?: "Unknown"
                        val time = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        sb.appendLine("🆘 Victim: ...${uid.takeLast(6)}")
                        sb.appendLine("   Time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(time))}")
                        sb.appendLine()
                    }
                }
                if (pendingCount == 0) {
                    binding.tvEscalationStatus.text = "✅ No Active Dispatches"
                    binding.tvEscalationList.text   = "All clear. Monitoring for incidents."
                    binding.cardEscalation.setCardBackgroundColor(getColor(android.R.color.holo_green_dark))
                    binding.btnRespond.visibility = android.view.View.GONE
                }
            }
    }

    private fun startAlarm() {
        if (mediaPlayer?.isPlaying == true) return
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@MedicalHomeActivity, alarmUri)
            isLooping = true
            prepare()
            start()
        }
        // Vibrate
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
    }

    private fun stopAlarm() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        vibrator?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
        escalationListener?.let {
            FirebaseDatabase.getInstance().reference.child("escalations")
                .removeEventListener(it)
        }
    }
}
