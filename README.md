# TTS Local Reader

[![Android](https://img.shields.io/badge/Platform-Android%209.0%2B%20(API%2028%2B)-3DDC84?logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white)](#)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](#)
[![APK Size](https://img.shields.io/badge/APK%20Size-2.80%20MB-F59E0B)](#)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A lightweight, 100% offline Text-to-Speech (TTS) novel reader for Android. Built natively with Jetpack Compose and Android's built-in `TextToSpeech` engine, TTS Local Reader delivers zero-gap paragraph streaming, vocal clarity enhancement, smart web novel scraping, and sleep timers with zero idle battery drain.

---

## Key Features

### 100% Offline, Zero-Setup TTS
- Operates entirely on Android's built-in `TextToSpeech` engine.
- Functions immediately on first launch with **0 MB downloads**, zero network latency, and no heavy runtime dependencies.
- Discovers and prioritizes high-definition local neural voice packages (e.g., Google Speech Services).

### Speech Clarity Mode (DSP Bypass)
- Routes speech with `AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY` and `CONTENT_TYPE_SPEECH`.
- Explicitly signals device hardware DSPs (such as Vivo/iQOO DeepField, Dolby Atmos, and Super Audio) to bypass music equalization, artificial stereo widening, and muffled bass boosts that degrade speech legibility.
- In-app engine switcher and direct intent shortcut to Android System TTS settings.

### OriginOS 6 & System Lock Screen Media Controls
- Full Android Foreground Service integration with `MediaSessionCompat`.
- Lock-screen and control-center media playback widgets hardened for OriginOS 6, FuntouchOS, and standard Android devices.
- Continuous synchronization of `PlaybackStateCompat` supporting Play, Pause, Previous, Next, and Stop.
- Automatic service teardown and notification dismissal on swipe (`onTaskRemoved`).

### Sleep & Loop Timer (Zero-Battery Deep Sleep Guarantee)
- **Continuous**: Normal queue playback across chapters.
- **Loop Chapter Once**: Replays the current chapter a single time before sleeping.
- **Play $N$ Chapters**: Seamlessly advances through $N$ chapters before triggering sleep.
- **Dynamic Switching**: Change modes on the fly (e.g. switch to Loop on chapter 2 of 3 to loop that chapter once and stop).
- **Deep Sleep Guarantee**: When sleep triggers, the app stops the audio pipeline, releases partial wake-locks, dismisses the notification, stops the foreground service, and abandons audio focus, allowing Android OS to enter low-power deep sleep.

### Intelligent Text Sanitization
- **Divider Detection**: Visual horizontal rules (`-------`, `***`, `===`, `~*~*~`) are rendered as glowing amber glyphs (`✦`) in the reader UI and completely silenced during audio playback.
- **Author & Translator Notes**: Automatically identifies and silences boilerplate (`Author's Note`, `TL Note`, `A/N:`).
- **Web Artifact Cleaning**: Unescapes residual HTML entities, standardizes full-width Asian punctuation, cleans LitRPG system brackets (`[System: ...]`), converts dialogue em-dashes and tildes into natural speech pauses, and strips action asterisks/underscores (`*sigh*` &rarr; `sigh`).

### Novel-First Architecture & Obsidian Bookshelf
- **Canonical Mirror Normalization (`NovelClassifier.kt`)**: Automatically identifies novel identity across mirror sites (Ranobes, Light Novel Pub, FreeWebNovel, etc.) by extracting canonical keys (e.g., `"shadow-slave"`), ensuring chapters from different sources group into the exact same novel.
- **First-Class Novel Entity**: Backed by a dedicated `NovelEntity` Room table with last-read chapter title, last-read sentence index, total chapter tracking, and chronological ordering via float sequence parsing (`chapterNumber: Float`).
- **Obsidian Bookshelf Sheet (`NovelBookshelfSheet`)**:
  - *Level 1 (Bookshelf)*: Shows all saved novels with total chapter counts, last read chapter, 1-tap "Resume Reading", and "Chapters" drill-down.
  - *Level 2 (Chapters Directory)*: Chronological chapter listing with active playing indicator, 1-tap playback, chapter URL copying, and single-chapter deletion.
  - Safe deletion dialogs for individual chapters, entire novels, and full storage clearing.
- **Lookahead Prefetching**: Multi-step ($N+1, N+2$) background lookahead prefetching with automatic purge of corrupted comments placeholders.
- In-app Cloudflare solver sheet with synchronized WebView session cookies.

### Obsidian Amber Reading UI
- Clean, eye-friendly dark palette (`#121214` background, `#1E1E24` elevated surfaces, `#F59E0B` warm amber accent).
- Synchronized, millisecond-accurate sentence highlighting with auto-scroll.
- Auto-hiding URL bar and single-tap focus dismissal: tapping paragraphs, the viewport, or scrolling immediately dismisses the software keyboard.

---

## APK Downloads

Pre-built APKs are located in the project build outputs:

| Variant | File Location | Size | Description |
| :--- | :--- | :--- | :--- |
| **Release (Recommended)** | `app/build/outputs/apk/release/app-release.apk` | **2.80 MB** | R8 tree-shaken and resource-shrunk production build. Signed with debug keys for immediate sideloading. |
| **Debug** | `app/build/outputs/apk/debug/app-debug.apk` | 54.85 MB | Full unminified debug APK with detailed symbol tables and developer logging. |

> [!TIP]
> For testing on a physical device, transfer **`app-release.apk`** (2.80 MB) to your phone or install via ADB:
> ```powershell
> adb install -r app\build\outputs\apk\release\app-release.apk
> ```

---

## Building from Source

### Prerequisites
- **Android SDK**: API 34+ / Build-Tools 34.0.0+
- **JDK**: Eclipse Adoptium OpenJDK 21 LTS (`JAVA_HOME` pointing to JDK 21)
- **Gradle**: 9.3.1 (wrapper included)

### Build Commands

```powershell
# Build optimized release APK (2.80 MB)
.\gradlew.bat assembleRelease

# Build debug APK
.\gradlew.bat assembleDebug

# Run unit tests
.\gradlew.bat testDebugUnitTest
```

> [!NOTE]
> If building on Windows inside a synced folder (such as OneDrive), ensure the Gradle daemon is stopped after builds (`.\gradlew.bat --stop`) to prevent transient file lock collisions on build directories.

---

## Project Structure

```
app/src/main/java/com/tts/reader/
├── TTSApp.kt                     # Application class & global engine lifecycle
├── data/
│   ├── local/                    # Room Database (AppDatabase, NovelEntity, NovelDao, ChapterEntity, ChapterDao)
│   └── scraper/                  # NovelScraper, TextSanitizer, NovelClassifier
├── service/
│   └── PlaybackService.kt        # Foreground Media Playback Service & MediaSessionCompat
├── tts/
│   ├── AudioStreamPipeline.kt    # Lookahead sentence queue & audio focus management
│   └── SystemTtsEngine.kt        # Android TextToSpeech wrapper, clarity mode & engine switcher
└── ui/
    ├── screens/
    │   ├── ReaderScreen.kt       # Obsidian Amber reader view, Novel Bookshelf & chapter navigation
    │   ├── SleepModeSheet.kt     # Sleep & loop timer configuration sheet
    │   └── CaptchaSolverSheet.kt # Embedded Cloudflare challenge solver
    ├── theme/                    # Color, Typography, and Theme definitions
    └── viewmodel/
        └── ReaderViewModel.kt    # StateFlow presentation model & playback coordination
```
