package com.ddas.app.common

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ddas.app.citizen.CitizenHomeActivity
import com.ddas.app.databinding.ActivityRoleSelectBinding
import com.ddas.app.medical.MedicalHomeActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class RoleSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardCitizen.setOnClickListener  { selectRole("citizen") }
        binding.cardMedical.setOnClickListener  { selectRole("medical") }
    }

    private fun selectRole(role: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Save role locally in SharedPreferences for FCMService to read instantly
        getSharedPreferences("ddas_prefs", MODE_PRIVATE)
            .edit()
            .putString("user_role", role)
            .apply()

        FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("role")
            .setValue(role)
            .addOnSuccessListener {
                val intent = when (role) {
                    "citizen" -> Intent(this, CitizenHomeActivity::class.java)
                    else      -> Intent(this, MedicalHomeActivity::class.java)
                }
                startActivity(intent)
                finishAffinity()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to set role. Try again.", Toast.LENGTH_SHORT).show()
            }
    }
}
