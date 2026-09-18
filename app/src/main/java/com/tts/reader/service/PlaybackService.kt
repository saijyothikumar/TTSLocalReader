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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tts.reader.MainActivity
import com.tts.reader.TTSApp
import com.tts.reader.tts.AudioStreamPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : Service() {

    private val tag = "PlaybackService"
    private val binder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null

    val audioPipeline: AudioStreamPipeline
        get() = TTSApp.instance.audioPipeline

    var currentChapterTitle: String = "Web Novel Reader"
    var currentNovelTitle: String = "Audio Playback"
    private var isForegroundActive = false

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        initMediaSession()
        createNotificationChannel()

        // Sync service notification and wake lock state reactively with pipeline
        serviceScope.launch {
            audioPipeline.isPlaying.collect { playing ->
                if (playing) {
                    acquireWakeLock()
                    if (isForegroundActive) {
                        updateNotification(isPlaying = true)
                    }
                } else {
                    releaseWakeLock()
                    if (isForegroundActive) {
                        updateNotification(isPlaying = false)
                    }
                }
            }
        }
    }

    /**
     * Acquires partial wake lock ONLY during active speech playback.
     * Prevents Android from putting CPU to sleep when the screen turns off or locks.
     */
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "NeuralNovelTTS::PlaybackWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(2 * 60 * 60 * 1000L /* 2 hours safety limit */)
            Log.d(tag, "WakeLock acquired for background playback (screen-off enabled)")
        }
    }

    /**
     * Releases wake lock immediately when paused or stopped.
     * Eliminates ANY idle battery drain when audio is not actively playing.
     */
    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(tag, "WakeLock released to save battery")
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "TTSPlaybackMediaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    audioPipeline.play()
                }

                override fun onPause() {
                    audioPipeline.pause()
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
                    stopServiceAndNotification()
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
        isForegroundActive = true
    }

    fun updateNotification(isPlaying: Boolean) {
        if (!isForegroundActive) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(isPlaying))
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification Action: Toggle Play/Pause with standard icons
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
            .setSmallIcon(com.tts.reader.R.drawable.ic_notification)
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

    private fun stopServiceAndNotification() {
        audioPipeline.stop()
        releaseWakeLock()
        isForegroundActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PLAYBACK -> {
                val novel = intent.getStringExtra(EXTRA_NOVEL_TITLE) ?: currentNovelTitle
                val chapter = intent.getStringExtra(EXTRA_CHAPTER_TITLE) ?: currentChapterTitle
                startForegroundWithNotification(novel, chapter, isPlaying = true)
                audioPipeline.play()
            }
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
            ACTION_STOP -> {
                stopServiceAndNotification()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
        mediaSession?.release()
    }

    companion object {
        const val CHANNEL_ID = "novel_tts_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_PLAYBACK = "com.tts.reader.action.START_PLAYBACK"
        const val ACTION_PLAY = "com.tts.reader.action.PLAY"
        const val ACTION_PAUSE = "com.tts.reader.action.PAUSE"
        const val ACTION_NEXT = "com.tts.reader.action.NEXT"
        const val ACTION_PREV = "com.tts.reader.action.PREV"
        const val ACTION_STOP = "com.tts.reader.action.STOP"

        const val EXTRA_NOVEL_TITLE = "extra_novel_title"
        const val EXTRA_CHAPTER_TITLE = "extra_chapter_title"

        fun startPlayback(context: Context, novelTitle: String, chapterTitle: String) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_START_PLAYBACK
                putExtra(EXTRA_NOVEL_TITLE, novelTitle)
                putExtra(EXTRA_CHAPTER_TITLE, chapterTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pausePlayback(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun stopPlayback(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
