package com.ddas.app.medical

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ddas.app.databinding.ActivityVictimsMapBinding
import com.google.firebase.database.*

class VictimsMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVictimsMapBinding
    private var escalationListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVictimsMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        loadVictims()
    }

    private fun loadVictims() {
        binding.tvVictimCount.text = "Loading victims..."
        binding.progressBar.visibility = View.VISIBLE

        val ref = FirebaseDatabase.getInstance().reference.child("escalations")
        escalationListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                binding.progressBar.visibility = View.GONE
                binding.victimsContainer.removeAllViews()

                var pendingCount = 0

                for (child in snapshot.children) {
                    val status = child.child("status").getValue(String::class.java)
                    if (status != "pending") continue

                    pendingCount++
                    val uid       = child.child("uid").getValue(String::class.java) ?: "Unknown"
                    val lat       = child.child("latitude").getValue(Double::class.java)
                    val lng       = child.child("longitude").getValue(Double::class.java)
                    val magnitude = child.child("magnitude").getValue(String::class.java) ?: "?"
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val timeStr   = java.text.SimpleDateFormat(
                        "HH:mm:ss", java.util.Locale.getDefault()
                    ).format(java.util.Date(timestamp))

                    // Create a card for each victim
                    addVictimCard(uid, lat, lng, magnitude, timeStr, child.key ?: uid)
                }

                binding.tvVictimCount.text = if (pendingCount > 0)
                    "🆘 $pendingCount Victim(s) Need Help"
                else
                    "✅ No Active Victims"
            }

            override fun onCancelled(error: DatabaseError) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@VictimsMapActivity, "Failed to load", Toast.LENGTH_SHORT).show()
            }
        }
        ref.addValueEventListener(escalationListener!!)
    }

    private fun addVictimCard(
        uid: String, lat: Double?, lng: Double?,
        magnitude: String, timeStr: String, key: String
    ) {
        val card = layoutInflater.inflate(
            android.R.layout.simple_list_item_2,
            binding.victimsContainer,
            false
        )

        // Build victim card programmatically
        val cardView = androidx.cardview.widget.CardView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, 12) }
            radius = 16f
            setCardBackgroundColor(getColor(android.R.color.transparent))
        }

        val inner = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF1a1a1a.toInt())
        }

        // Victim info
        val tvTitle = android.widget.TextView(this).apply {
            text = "🆘 Victim: ...${uid.takeLast(6)}"
            textSize = 16f
            setTextColor(0xFFe63946.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val tvDetails = android.widget.TextView(this).apply {
            text = "Magnitude: $magnitude m/s²  |  Time: $timeStr"
            textSize = 13f
            setTextColor(0xFF888888.toInt())
            setPadding(0, 4, 0, 8)
        }

        inner.addView(tvTitle)
        inner.addView(tvDetails)

        if (lat != null && lng != null) {
            // Open in Google Maps button
            val btnMap = android.widget.Button(this).apply {
                text = "📍 Open Location in Maps"
                setBackgroundColor(0xFF2ecc71.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(8, 8, 8, 8)
                setOnClickListener {
                    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(Victim+Location)")
                    val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    if (mapIntent.resolveActivity(packageManager) != null) {
                        startActivity(mapIntent)
                    } else {
                        // Fallback to browser
                        val browserUri = Uri.parse("https://maps.google.com/?q=$lat,$lng")
                        startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                    }
                }
            }
            inner.addView(btnMap)
        } else {
            val tvNoLoc = android.widget.TextView(this).apply {
                text = "⚠️ Location not available"
                textSize = 13f
                setTextColor(0xFFf39c12.toInt())
            }
            inner.addView(tvNoLoc)
        }

        // Resolve button
        val btnResolve = android.widget.Button(this).apply {
            text = "✅ Mark as Resolved"
            setBackgroundColor(0xFF333333.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(8, 8, 8, 8)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 8 }
            setOnClickListener {
                FirebaseDatabase.getInstance().reference
                    .child("escalations").child(key).child("status")
                    .setValue("resolved")
                Toast.makeText(this@VictimsMapActivity, "Marked as resolved", Toast.LENGTH_SHORT).show()
            }
        }
        inner.addView(btnResolve)

        cardView.addView(inner)
        binding.victimsContainer.addView(cardView)
    }

    override fun onDestroy() {
        super.onDestroy()
        escalationListener?.let {
            FirebaseDatabase.getInstance().reference
                .child("escalations").removeEventListener(it)
        }
    }
}
