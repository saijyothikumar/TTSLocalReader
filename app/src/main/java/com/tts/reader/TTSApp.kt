package com.tts.reader

import android.app.Application
import com.tts.reader.data.local.AppDatabase
import com.tts.reader.tts.AudioStreamPipeline
import com.tts.reader.tts.SystemTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TTSApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var database: AppDatabase
        private set
    lateinit var systemTts: SystemTtsEngine
        private set
    lateinit var audioPipeline: AudioStreamPipeline
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        systemTts = SystemTtsEngine(this)
        audioPipeline = AudioStreamPipeline(this, systemTts, appScope)
    }

    companion object {
        lateinit var instance: TTSApp
            private set
    }
}
