package com.ddas.app.citizen

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ddas.app.databinding.ActivityHelpComingBinding

class HelpComingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpComingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpComingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener { finish() }
    }
}
