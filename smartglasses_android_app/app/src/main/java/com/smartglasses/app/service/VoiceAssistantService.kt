package com.smartglasses.app.service

import com.smartglasses.app.BuildConfig
import com.smartglasses.app.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.session.MediaSession
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smartglasses.app.network.GeminiApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class VoiceAssistantService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_START = "com.smartglasses.app.action.START"
        const val ACTION_MEDIA_BUTTON = "com.smartglasses.app.action.MEDIA_BUTTON"
        private const val CHANNEL_ID = "voice_assistant_channel"
        private const val CHANNEL_NAME = "Voice Assistant"
    }

    private var tts: TextToSpeech? = null
    private var captureJob: Job? = null
    private var isProcessing = AtomicBoolean(false)
    private lateinit var geminiClient: GeminiApiClient
    
    private var toneGenerator: ToneGenerator? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val apiKey = BuildConfig.GEMINI_API_KEY
        geminiClient = GeminiApiClient(apiKey)
        tts = TextToSpeech(this, this)

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        } catch (e: Exception) {
            Log.e("VoiceAssistantService", "Failed to init ToneGenerator", e)
        }

        mediaSession = MediaSession(this, "SmartGlassesMediaSession").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }

        startForeground(1, buildNotification("Голосовой ассистент готов"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(1, buildNotification("Сервис запущен"))
            }
            ACTION_MEDIA_BUTTON -> {
                handleMediaButtonPress()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleMediaButtonPress() {
        if (!isProcessing.compareAndSet(false, true)) {
            Log.d("VoiceAssistantService", "Ignoring duplicate media button press")
            return
        }

        captureJob?.cancel()
        captureJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Пик 1: Запись началась
                playBeepStart()

                val file = recordWhisperForSeconds(5)

                // Пик 2: Запись завершена
                playBeepEnd()

                val transcript = geminiClient.transcribeAudio(file)
                if (transcript.isEmpty() || transcript == "Ошибка распознавания речи" || transcript == "Не удалось распознать речь") {
                    speakAnswer("Не удалось распознать речь")
                    return@launch
                }
                speakAnswer(transcript)
                Log.d("VoiceAssistantService", "Recognized: $transcript")
            } catch (e: Exception) {
                Log.e("VoiceAssistantService", "Capture failed", e)
                speakAnswer("Ошибка доступа к микрофону")
            } finally {
                isProcessing.set(false)
            }
        }
    }

    private fun playBeepStart() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    private fun playBeepEnd() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
    }

    private fun recordWhisperForSeconds(seconds: Int): File {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        // Используем универсальный источник MIC, чтобы Samsung не блокировал доступ
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize.coerceAtLeast(2048)
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("Microphone hardware not ready")
        }

        val file = File(cacheDir, "whisper_command.wav")
        if (file.exists()) file.delete()

        val output = FileOutputStream(file)
        val tempBuffer = ByteArray(bufferSize.coerceAtLeast(2048))
        
        // Расчёт байт для 16-bit моно (16000 Гц * 2 байта на сэмпл * секунды)
        val targetBytes = sampleRate * 2 * seconds
        var totalBytesRead = 0

        audioRecord.startRecording()
        try {
            while (totalBytesRead < targetBytes) {
                val read = audioRecord.read(tempBuffer, 0, tempBuffer.size)
                if (read > 0) {
                    output.write(tempBuffer, 0, read)
                    totalBytesRead += read
                } else if (read < 0) {
                    // Страховка: если микрофон отдал ошибку, выходим из цикла, а не зависаем
                    Log.e("VoiceAssistantService", "AudioRecord error code: $read")
                    break
                }
            }
        } finally {
            output.flush()
            output.close()
            audioRecord.stop()
            audioRecord.release()
        }

        return file
    }

    private fun speakAnswer(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.language = Locale("ru")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_assistant")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("ru")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmartGlasses Assistant")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
        toneGenerator?.release()
        mediaSession?.release()
    }
}
