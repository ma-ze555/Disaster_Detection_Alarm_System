package com.ddas.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ddas.app.auth.LoginActivity
import com.ddas.app.common.RoleSelectActivity
import com.ddas.app.citizen.CitizenHomeActivity
import com.ddas.app.medical.MedicalHomeActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val uid   = user.uid
        val prefs = getSharedPreferences("ddas_prefs", MODE_PRIVATE)

        // Check if role is already cached locally
        val cachedRole = prefs.getString("user_role", null)
        if (cachedRole != null) {
            routeByRole(cachedRole)
            return
        }

        // Fetch from Firebase and cache it
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("role")
            .get()
            .addOnSuccessListener { snapshot ->
                val role = snapshot.getValue(String::class.java) ?: ""
                // Always cache role locally
                prefs.edit().putString("user_role", role).apply()
                routeByRole(role)
            }
            .addOnFailureListener {
                startActivity(Intent(this, RoleSelectActivity::class.java))
                finish()
            }
    }

    private fun routeByRole(role: String) {
        when (role) {
            "citizen" -> startActivity(Intent(this, CitizenHomeActivity::class.java))
            "medical" -> startActivity(Intent(this, MedicalHomeActivity::class.java))
            else      -> startActivity(Intent(this, RoleSelectActivity::class.java))
        }
        finish()
    }
}
