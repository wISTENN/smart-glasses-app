package com.smartglasses.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smartglasses.app.MainActivity
import com.smartglasses.app.network.GeminiApiClient
import com.smartglasses.app.receiver.MediaButtonReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale

class VoiceAssistantService : Service(), TextToSpeech.OnInitListener {

    private companion object {
        private const val TAG = "VoiceAssistantService"
        private const val CHANNEL_ID = "VoiceAssistantChannel"
        private const val NOTIFICATION_ID = 1
        private const val SAMPLE_RATE = 16000
    }

    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val audioDataStream = ByteArrayOutputStream()

    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var toneGenerator: ToneGenerator? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        startForegroundServiceWithNotification()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ToneGenerator", e)
        }

        setupMediaSession()
        requestAudioFocus()

        tts = TextToSpeech(this, this)
    }

    private fun startForegroundServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Glasses Assistant")
            .setContentText("Слушает кнопку гарнитуры...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setupMediaSession() {
        val mediaButtonReceiver = ComponentName(this, MediaButtonReceiver::class.java)
        mediaSession = MediaSessionCompat(this, "VoiceAssistantService", mediaButtonReceiver, null).apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                component = mediaButtonReceiver
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this@VoiceAssistantService,
                0,
                mediaButtonIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            setMediaButtonReceiver(pendingIntent)

            val state = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_STOP
                )
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()

            setPlaybackState(state)

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    Log.d(TAG, "MediaSessionCompat.Callback onMediaButtonEvent: $mediaButtonEvent")
                    return MediaButtonReceiver.handleMediaButtonIntent(this@VoiceAssistantService, mediaButtonEvent)
                }

                override fun onPlay() {
                    Log.d(TAG, "MediaSessionCompat onPlay")
                    toggleRecording()
                }

                override fun onPause() {
                    Log.d(TAG, "MediaSessionCompat onPause")
                    toggleRecording()
                }

                override fun onStop() {
                    Log.d(TAG, "MediaSessionCompat onStop")
                    if (isRecording) stopRecordingAndSend()
                }
            })

            isActive = true
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.d(TAG, "AudioFocus change: $focusChange")
                }
                .build()

            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                { focusChange -> Log.d(TAG, "AudioFocus change: $focusChange") },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand action: ${intent?.action}")

        mediaSession?.isActive = true
        requestAudioFocus()

        when (intent?.action) {
            "TOGGLE_RECORDING" -> toggleRecording()
            "START_RECORDING" -> if (!isRecording) startRecording()
            "STOP_RECORDING" -> if (isRecording) stopRecordingAndSend()
        }

        return START_STICKY
    }

    fun toggleRecording() {
        if (isRecording) {
            stopRecordingAndSend()
        } else {
            startRecording()
        }
    }

    private fun playBeep(toneType: Int) {
        try {
            toneGenerator?.startTone(toneType, 150)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing beep tone", e)
        }
    }

    private fun startRecording() {
        if (isRecording) return

        playBeep(ToneGenerator.TONE_PROP_BEEP)

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize.coerceAtLeast(2048)
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed, fallback to MIC")
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize.coerceAtLeast(2048)
                )
            }

            audioDataStream.reset()
            audioRecord?.startRecording()
            isRecording = true

            recordingThread = Thread {
                val buffer = ByteArray(1024)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        synchronized(audioDataStream) {
                            audioDataStream.write(buffer, 0, read)
                        }
                    }
                }
            }
            recordingThread?.start()
            Log.d(TAG, "Recording started")

        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for recording", e)
            speak("Нет разрешения на запись аудио")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
        }
    }

    private fun stopRecordingAndSend() {
        if (!isRecording) return

        playBeep(ToneGenerator.TONE_PROP_BEEP2)

        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingThread?.join(1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }

        val pcmData = synchronized(audioDataStream) {
            audioDataStream.toByteArray()
        }

        if (pcmData.isEmpty()) {
            Log.w(TAG, "Audio data is empty")
            return
        }

        val base64Pcm = Base64.encodeToString(pcmData, Base64.NO_WRAP)

        serviceScope.launch {
            Log.d(TAG, "Sending audio to Gemini...")
            val response = GeminiApiClient.sendAudioToGemini(base64Pcm, SAMPLE_RATE)
            Log.d(TAG, "Gemini response: $response")
            if (!response.isNull_or_empty_or_error()) {
                speak(response)
            } else {
                speak("Не удалось получить ответ от ассистента")
            }
        }
    }

    private fun String?.isNull_or_empty_or_error(): Boolean {
        return this.isNullOrBlank() || this.startsWith("Ошибка")
    }

    private fun speak(text: String) {
        Handler(Looper.getMainLooper()).post {
            if (isTtsReady) {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "GeminiTTS")
            } else {
                Log.w(TAG, "TTS is not ready yet")
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ru", "RU"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language RU is not supported")
                tts?.setLanguage(Locale.US)
            }
            isTtsReady = true
            Log.d(TAG, "TTS Initialized successfully")
        } else {
            Log.e(TAG, "TTS Initialization failed")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        serviceJob.cancel()

        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ToneGenerator", e)
        }

        mediaSession?.run {
            isActive = false
            release()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
        }

        tts?.stop()
        tts?.shutdown()

        Log.d(TAG, "Service onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
