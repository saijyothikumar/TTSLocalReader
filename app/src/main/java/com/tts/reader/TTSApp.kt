package com.tts.reader

import android.app.Application
import com.tts.reader.data.local.AppDatabase
import com.tts.reader.tts.AudioStreamPipeline
import com.tts.reader.tts.KokoroTtsEngine
import com.tts.reader.tts.ModelManager
import com.tts.reader.tts.SystemTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TTSApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var database: AppDatabase
        private set
    lateinit var modelManager: ModelManager
        private set
    lateinit var systemTts: SystemTtsEngine
        private set
    lateinit var kokoroTts: KokoroTtsEngine
        private set
    lateinit var audioPipeline: AudioStreamPipeline
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        modelManager = ModelManager(this)
        systemTts = SystemTtsEngine(this)
        kokoroTts = KokoroTtsEngine(this, modelManager)
        audioPipeline = AudioStreamPipeline(this, systemTts, kokoroTts, appScope)
    }

    companion object {
        lateinit var instance: TTSApp
            private set
    }
}
