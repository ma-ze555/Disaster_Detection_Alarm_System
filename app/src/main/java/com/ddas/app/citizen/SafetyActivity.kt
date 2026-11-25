package com.ddas.app.citizen

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ddas.app.databinding.ActivitySafetyBinding

class SafetyActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySafetyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySafetyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener { finish() }
    }
}
