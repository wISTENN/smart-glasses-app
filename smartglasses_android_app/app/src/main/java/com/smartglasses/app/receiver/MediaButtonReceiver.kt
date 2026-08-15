package com.smartglasses.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.smartglasses.app.service.VoiceAssistantService

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return

        // Безопасное извлечение нажатия для любых версий Android (включая Android 13+)
        val event: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }

        // Игнорируем зажатия (repeatCount > 0) и отпускания кнопки
        if (event == null || event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return

        val keyCode = event.keyCode
        if (
            keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
        ) {
            Log.d("MediaButtonReceiver", "Media button pressed: $keyCode")

            // Блокируем передачу кнопки дальше в систему и другим плеерам
            if (isOrderedBroadcast) {
                abortBroadcast()
            }

            val serviceIntent = Intent(context, VoiceAssistantService::class.java).apply {
                action = VoiceAssistantService.ACTION_MEDIA_BUTTON
            }

            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
