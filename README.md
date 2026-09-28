# TTS Local Reader 🎧📖

A lightweight, 100% offline Text-to-Speech (TTS) web novel reader for Android. Designed for long-form reading with zero-gap audio playback, intelligent text sanitization, and seamless chapter prefetching.

---

## 🌟 Features

- **⚡ 100% Offline, Zero-Setup TTS**: Built directly on Android's native `TextToSpeech` engine. Works immediately on first launch with **0 MB downloads** and zero setup.
- **🔇 Intelligent Text Sanitizer**: Automatically detects and completely silences horizontal visual dividers (`-------`, `***`, `===`, `~*~*~`) and author/translator notes (`Author's Note`, `TL Note`, `A/N:`) during speech playback. The reading interface renders dividers as elegant glowing amber glyphs (`✦`).
- **⏩ Multi-Step Lookahead Prefetching**: Prefetches both Next ($N+1$) and Next-Next ($N+2$) chapters in the background with human-like cadence, ensuring instant, lag-free chapter transitions.
- **🔄 Continuous Zero-Gap Playback**: Continuous lookahead paragraph queuing eliminates the awkward 1–2 second silence between paragraphs found in typical TTS apps.
- **🎯 Synchronized Reading UI**: Sample-accurate sentence highlighting synced with the audio playhead and automatic auto-scrolling.
- **🛡️ Bot Protection Solver**: In-app Obsidian Amber styled challenge sheet to solve Cloudflare Turnstile / Bot challenges, automatically syncing clearance cookies to OkHttp.
- **📱 Background Playback**: Android Foreground Service with `MediaSessionCompat` lock screen controls and partial wake-locks for screen-off reading.
- **🎨 Obsidian Amber Theme**: Premium eye-friendly dark mode interface (`#121214` background, `#1E1E24` cards, and warm glowing `#F59E0B` amber accents).
- **⚙️ Voice & Reading Controls**: Quick speed presets (0.75x, 1.0x, 1.25x, 1.5x, 2.0x), fine-tuned pitch slider, and selector for all installed system voices.

---

## 📱 How to Build

### Using Android Studio
1. Clone the repository:
   ```bash
   git clone https://github.com/saijyothikumar/TTSLocalReader.git
   ```
2. Open the project folder in **Android Studio**.
3. Allow Gradle to sync.
4. Run on an Android device or emulator (API 28+ / Android 9.0+).

### Using the Command Line
```powershell
# Windows
.\gradlew.bat assembleDebug

# Linux / macOS
./gradlew assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📂 Architecture

- **`data/scraper`**: Universal web novel extractor (`NovelScraper`), divider/note speech filter (`TextSanitizer`), and challenge exceptions.
- **`data/local`**: Offline Room Database (`AppDatabase`, `ChapterEntity`, `ChapterDao`) for chapter storage and reading progress.
- **`tts`**: Double-buffered lookahead sentence queue (`AudioStreamPipeline`) and native Android TTS wrapper (`SystemTtsEngine`).
- **`service`**: Android Foreground Service (`PlaybackService`) with media notification and wake-lock management.
- **`ui`**: Jetpack Compose UI (`ReaderScreen`, `ReaderViewModel`, `CaptchaSolverSheet`) in the Obsidian Amber theme.

---

## 📄 License
This project is open-source and available under the [MIT License](LICENSE).
