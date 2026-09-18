package com.tts.reader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.tts.reader.MainActivity
import com.tts.reader.tts.AudioStreamPipeline
import com.tts.reader.tts.KokoroTtsEngine
import com.tts.reader.tts.ModelManager
import com.tts.reader.tts.SystemTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class PlaybackService : Service() {

    private val binder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null

    lateinit var modelManager: ModelManager
        private set
    lateinit var systemTts: SystemTtsEngine
        private set
    lateinit var kokoroTts: KokoroTtsEngine
        private set
    lateinit var audioPipeline: AudioStreamPipeline
        private set

    var currentChapterTitle: String = "Web Novel Reader"
    var currentNovelTitle: String = "Audio Playback"

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        initMediaSession()
        createNotificationChannel()

        modelManager = ModelManager(this)
        systemTts = SystemTtsEngine(this)
        kokoroTts = KokoroTtsEngine(this, modelManager)
        audioPipeline = AudioStreamPipeline(this, systemTts, kokoroTts, serviceScope)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NeuralNovelTTS::PlaybackWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(2 * 60 * 60 * 1000L /* 2 hours safety limit */)
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "TTSPlaybackMediaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    audioPipeline.play()
                    updateNotification(isPlaying = true)
                }

                override fun onPause() {
                    audioPipeline.pause()
                    updateNotification(isPlaying = false)
                }

                override fun onSkipToNext() {
                    val next = audioPipeline.activeSentenceIndex.value + 1
                    audioPipeline.seekToSentence(next)
                }

                override fun onSkipToPrevious() {
                    val prev = (audioPipeline.activeSentenceIndex.value - 1).coerceAtLeast(0)
                    audioPipeline.seekToSentence(prev)
                }

                override fun onStop() {
                    audioPipeline.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Novel Reading Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for offline background novel reading"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun startForegroundWithNotification(novelTitle: String, chapterTitle: String, isPlaying: Boolean) {
        currentNovelTitle = novelTitle
        currentChapterTitle = chapterTitle

        val notification = buildNotification(isPlaying)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun updateNotification(isPlaying: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(isPlaying))
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause, "Pause",
                createActionPendingIntent(ACTION_PAUSE)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play, "Play",
                createActionPendingIntent(ACTION_PLAY)
            ).build()
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentChapterTitle)
            .setContentText(currentNovelTitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_media_previous, "Prev",
                createActionPendingIntent(ACTION_PREV)
            )
            .addAction(playPauseAction)
            .addAction(
                android.R.drawable.ic_media_next, "Next",
                createActionPendingIntent(ACTION_NEXT)
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOngoing(isPlaying)
            .build()
    }

    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                audioPipeline.play()
                updateNotification(isPlaying = true)
            }
            ACTION_PAUSE -> {
                audioPipeline.pause()
                updateNotification(isPlaying = false)
            }
            ACTION_NEXT -> {
                val next = audioPipeline.activeSentenceIndex.value + 1
                audioPipeline.seekToSentence(next)
            }
            ACTION_PREV -> {
                val prev = (audioPipeline.activeSentenceIndex.value - 1).coerceAtLeast(0)
                audioPipeline.seekToSentence(prev)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        audioPipeline.release()
        serviceScope.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        mediaSession?.release()
    }

    companion object {
        const val CHANNEL_ID = "novel_tts_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.tts.reader.action.PLAY"
        const val ACTION_PAUSE = "com.tts.reader.action.PAUSE"
        const val ACTION_NEXT = "com.tts.reader.action.NEXT"
        const val ACTION_PREV = "com.tts.reader.action.PREV"
    }
}
