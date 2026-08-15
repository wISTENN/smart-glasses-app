package com.smartglasses.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.smartglasses.app.service.VoiceAssistantService

class MediaButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_MEDIA_BUTTON) return

        val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        if (keyEvent.action == KeyEvent.ACTION_DOWN) {
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_HEADSETHOOK,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    context?.let { handleMediaButtonIntent(it, intent) }
                    if (isOrderedBroadcast) {
                        abortBroadcast()
                    }
                }
            }
        }
    }

    companion object {
        fun handleMediaButtonIntent(context: Context, intent: Intent?): Boolean {
            if (intent == null) return false
            val serviceIntent = Intent(context, VoiceAssistantService::class.java).apply {
                action = "TOGGLE_RECORDING"
            }
            context.startService(serviceIntent)
            return true
        }
    }
}
