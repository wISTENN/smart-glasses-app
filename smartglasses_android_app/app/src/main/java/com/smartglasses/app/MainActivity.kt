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
        val audioGranted = result[Manifest.permission.RECORD_AUDIO] 
            ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)

        if (audioGranted) {
            startAssistant()
        } else {
            Toast.makeText(this, "Для работы ассистента нужен доступ к микрофону!", Toast.LENGTH_LONG).show()
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

        if (required.isEmpty() || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startAssistant()
        } else {
            permissionLauncher.launch(required.toTypedArray())
        }
    }

    private fun startAssistant() {
        val intent = Intent(this, VoiceAssistantService::class.java)
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "Голосовой ассистент запущен", Toast.LENGTH_SHORT).show()
    }
}
