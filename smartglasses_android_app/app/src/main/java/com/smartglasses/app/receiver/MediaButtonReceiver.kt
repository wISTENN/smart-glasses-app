package com.smartglasses.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import com.smartglasses.app.service.VoiceAssistantService

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return

        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
        if (event == null) return

        if (event.action != KeyEvent.ACTION_DOWN) return

        val keyCode = event.keyCode
        if (
            keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
        ) {
            Log.d("MediaButtonReceiver", "media button pressed: $keyCode")
            val serviceIntent = Intent(context, VoiceAssistantService::class.java)
            serviceIntent.action = VoiceAssistantService.ACTION_MEDIA_BUTTON
            context.startForegroundService(serviceIntent)
        }
    }
}
