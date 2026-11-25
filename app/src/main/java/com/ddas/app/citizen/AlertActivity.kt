package com.ddas.app.citizen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AppCompatActivity
import com.ddas.app.databinding.ActivityAlertBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class AlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mediaPlayer: MediaPlayer? = null
    private var countDownTimer: CountDownTimer? = null
    private val timeoutMs = 120_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show on lock screen and wake screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )

        binding = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val magnitude = intent.getStringExtra("magnitude") ?: "Unknown"
        binding.tvMagnitude.text = "Magnitude: $magnitude m/s²"

        startAlarm()
        startCountdown()

        // YES — I'm Safe
        binding.btnSafe.setOnClickListener {
            countDownTimer?.cancel()
            stopAlarm()
            saveResponse(safe = true, magnitude = magnitude, onDone = {
                // Go to Safety Precautions screen
                startActivity(Intent(this, SafetyActivity::class.java))
                finish()
            })
        }

        // NO — Need Help
        binding.btnNotSafe.setOnClickListener {
            countDownTimer?.cancel()
            stopAlarm()
            // Capture location then show HelpComingActivity
            captureLocationAndSave(magnitude)
        }
    }

    private fun startAlarm() {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setLegacyStreamType(AudioManager.STREAM_ALARM)
                    .build()
            )
            setDataSource(this@AlertActivity, alarmUri)
            isLooping = true
            prepare()
            start()
        }

        // Force max alarm volume — bypasses silent mode
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )
    }

    private fun startCountdown() {
        countDownTimer = object : CountDownTimer(timeoutMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.tvCountdown.text = "Auto-escalating in ${seconds}s"
            }
            override fun onFinish() {
                // Timeout — treat as unsafe
                stopAlarm()
                captureLocationAndSave(intent.getStringExtra("magnitude") ?: "0")
            }
        }.start()
    }

    private fun saveResponse(safe: Boolean, magnitude: String, onDone: () -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { onDone(); return }
        val responseMap = mapOf(
            "uid"       to uid,
            "safe"      to safe,
            "magnitude" to magnitude,
            "timestamp" to System.currentTimeMillis()
        )
        FirebaseDatabase.getInstance().reference
            .child("alert_responses").child(uid)
            .setValue(responseMap)
            .addOnCompleteListener { onDone() }
    }

    private fun captureLocationAndSave(magnitude: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            goToHelpScreen()
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            saveEscalation(uid, magnitude, lat = null, lng = null)
            goToHelpScreen()
            return
        }

        // Request fresh location update
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000
        ).setMaxUpdates(1).build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                    fusedLocationClient.removeLocationUpdates(this)
                    val location = result.lastLocation
                    saveEscalation(uid, magnitude, location?.latitude, location?.longitude)
                    goToHelpScreen()
                }
            },
            mainLooper
        )

        // Fallback after 5 seconds if location not received
        android.os.Handler(mainLooper).postDelayed({
            saveEscalation(uid, magnitude, null, null)
            goToHelpScreen()
        }, 5000)
    }

    private fun saveEscalation(uid: String, magnitude: String, lat: Double?, lng: Double?) {
        val escalationMap = mutableMapOf(
            "uid"       to uid,
            "status"    to "pending",
            "magnitude" to magnitude,
            "timestamp" to System.currentTimeMillis()
        )
        if (lat != null && lng != null) {
            escalationMap["latitude"]  = lat
            escalationMap["longitude"] = lng
        }

        FirebaseDatabase.getInstance().reference
            .child("escalations").child(uid)
            .setValue(escalationMap)

        // Also save response
        FirebaseDatabase.getInstance().reference
            .child("alert_responses").child(uid)
            .setValue(mapOf(
                "uid"       to uid,
                "safe"      to false,
                "magnitude" to magnitude,
                "timestamp" to System.currentTimeMillis()
            ))

        // Write dispatch_trigger to Firebase — MedicalHomeActivity listens to this
        FirebaseDatabase.getInstance().reference
            .child("dispatch_trigger")
            .setValue(mapOf(
                "uid"       to uid,
                "magnitude" to magnitude,
                "timestamp" to System.currentTimeMillis(),
                "active"    to true
            ))
    }

    private fun goToHelpScreen() {
        startActivity(Intent(this, HelpComingActivity::class.java))
        finish()
    }

    private fun stopAlarm() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        stopAlarm()
    }
}
