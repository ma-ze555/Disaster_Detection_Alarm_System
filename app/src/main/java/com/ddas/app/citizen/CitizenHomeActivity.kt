package com.ddas.app.citizen

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ddas.app.auth.LoginActivity
import com.ddas.app.common.RoleSelectActivity
import com.ddas.app.databinding.ActivityCitizenHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging

class CitizenHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCitizenHomeBinding
    private lateinit var db: DatabaseReference
    private var alertListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCitizenHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid  = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db       = FirebaseDatabase.getInstance().reference

        // Request "Display over other apps" permission for full screen alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        // Subscribe to disaster alerts topic
        FirebaseMessaging.getInstance().subscribeToTopic("disaster_alerts")
            .addOnSuccessListener {
                android.util.Log.d("FCM", "Subscribed to disaster_alerts topic")
            }

        // Load user name
        db.child("users").child(uid).child("name").get()
            .addOnSuccessListener { binding.tvWelcome.text = "Hello, ${it.value} 👋" }

        // Listen for active alerts
        listenForAlerts()

        // Switch role
        binding.btnSwitchRole.setOnClickListener {
            startActivity(Intent(this, RoleSelectActivity::class.java))
        }

        // Logout
        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }
    }

    private fun listenForAlerts() {
        val ref = FirebaseDatabase.getInstance().reference.child("sensor_data")
        alertListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alertStatus = snapshot.child("alert_status").getValue(Boolean::class.java) ?: false
                val magnitude   = snapshot.child("vibration_magnitude").getValue(Double::class.java) ?: 0.0

                if (alertStatus) {
                    binding.tvStatus.text      = "⚠️ EARTHQUAKE DETECTED!"
                    binding.tvMagnitude.text   = "Magnitude: ${"%.2f".format(magnitude)} m/s²"
                    binding.cardStatus.setCardBackgroundColor(getColor(android.R.color.holo_red_dark))
                } else {
                    binding.tvStatus.text      = "✅ All Clear"
                    binding.tvMagnitude.text   = "No seismic activity detected"
                    binding.cardStatus.setCardBackgroundColor(getColor(android.R.color.holo_green_dark))
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@CitizenHomeActivity, "Failed to load data", Toast.LENGTH_SHORT).show()
            }
        }
        ref.addValueEventListener(alertListener!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        alertListener?.let {
            FirebaseDatabase.getInstance().reference.child("sensor_data").removeEventListener(it)
        }
    }
}
