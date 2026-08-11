package com.smartglasses.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.smartglasses.app.service.VoiceAssistantService

class MainActivity : AppCompatActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.all { it.value }
        if (granted) {
            startAssistant()
        } else {
            Toast.makeText(this, "Нужны разрешения на микрофон и уведомления", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.startServiceButton).setOnClickListener {
            requestPermissionsIfNeeded()
        }

        findViewById<Button>(R.id.stopServiceButton).setOnClickListener {
            stopService(Intent(this, VoiceAssistantService::class.java))
            Toast.makeText(this, "Сервис остановлен", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermissionsIfNeeded() {
        val required = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            required.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (required.isEmpty()) {
            startAssistant()
        } else {
            permissionLauncher.launch(required.toTypedArray())
        }
    }

    private fun startAssistant() {
        val intent = Intent(this, VoiceAssistantService::class.java)
        intent.action = VoiceAssistantService.ACTION_START
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "Голосовой ассистент запущен", Toast.LENGTH_SHORT).show()
    }
}
