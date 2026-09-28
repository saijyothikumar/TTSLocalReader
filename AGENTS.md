# TTS App - Project Rules & Guidelines

## Project Overview
A lightweight, offline-first Text-to-Speech (TTS) application targeting Android (Android 9+ / API 28+) and local novel reading.
- Natural, smooth local neural voice (non-robotic, ~80MB–350MB model footprint).
- Web novel & Ranobes chapter scraper with intelligent content extractor (stripping site chrome, menus, and comments).
- Seamless, zero-gap paragraph playback via double-buffering lookahead audio synthesis.
- Background playback with Android Foreground Service, lock screen media controls, and screen-off wake locks.
- Synchronized reading UI with text highlighting, auto-scroll, dark mode, speed controls, and direct text paste.

## Locked Architectural Decisions
- **Framework**: Native Android (Kotlin + Jetpack Compose)
- **TTS Engine (100% Offline, Zero-Setup)**: Dedicated built-in Android `TextToSpeech` requiring **0 MB download**, functioning 100% offline immediately on first launch with zero setup. Heavy external neural voice models (Kokoro ONNX) and their runtime dependencies have been completely removed.
- **Audio Pipeline**: Continuous lookahead sentence queue ensuring zero audible gap between paragraphs and millisecond-accurate synchronized UI text highlighting with dynamic pitch, speed presets (0.75x–2.0x), and voice selection.
- **Text Sanitization**: `TextSanitizer` automatically detects visual horizontal dividers (`-------`, `***`, `===`, `~*~*~`) and author/translator notes (`Author's Note`, `TL Note`). Visual dividers are rendered as elegant amber glyphs (`✦`) in the reading UI and are completely silenced during TTS speech playback.
- **Background Architecture**: Android Foreground Service + `MediaSessionCompat` with lock-screen notification and partial wake-locks. Foreground notification and playback service are immediately terminated on task swipe (`onTaskRemoved`).
- **Content Extractor & Multi-Step Lookahead**: Universal Web Novel Extractor with early navigation link extraction (preserving Previous/Next links before DOM cleaning), 2-chapter lookahead background prefetching ($N+1, N+2$), cache-hit prefetch chaining, and title deduplication.
- **Interactive Cloudflare Solver**: In-app Obsidian Amber styled `CaptchaSolverSheet` with embedded WebView and cookie synchronization to OkHttp via `CookieManager`.

## Locked UI Theme (Obsidian Amber)
- **Background**: `#121214` (Deep Charcoal)
- **Surface / Cards**: `#1E1E24` (Subtle dark elevated surface)
- **Primary Accent**: `#F59E0B` (Warm glowing amber)
- **Text Primary**: `#EDEDF0` (Off-white high legibility)
- **Text Secondary / Muted**: `#9CA3AF` (Muted gray)
- **Highlight (Active Sentence/Word)**: `rgba(245, 158, 11, 0.25)` with `#F59E0B` indicator

## Development & Environment Rules
- **No Direct GitHub Pushes**: Never push directly to remote git repositories without explicit user instruction.
- **Root AGENTS.md**: Maintain and update this file with architectural decisions and guidelines.
- **Theme Lock**: Obsidian Amber is permanently locked. Never fall back to generic AI defaults.
- **Offline / Local First**: TTS inference and novel text must operate completely locally without external API latency.

## Build & Environment Invariants
- **Gradle & JVM Toolchain**: The Android Studio environment on this host uses JBR 25 (Java 25). However, Kotlin 1.9.24 / KSP requires Java 21 (`toolchainVersion=21` in `gradle/gradle-daemon-jvm.properties`). Gradle 9.3.1 automatically fetches Eclipse Adoptium JDK 21 to run the compiler daemon, preventing `IllegalArgumentException: 25.0.3` during `kspDebugKotlin`.
- **OneDrive & File Lock Workarounds**: Because the project is located in OneDrive (`OneDrive\Desktop\Projects\TTS app`), Microsoft OneDrive continuously attempts to sync build artifacts, causing file locks (`Access is denied`), `*(1)*` file collisions, and read-only cloud reparse points on generated folders. To prevent build errors:
  1. `org.gradle.vfs.watch=false` is set in `gradle.properties`.
  2. If clean or packaging fails with `Unable to delete directory`, strip read-only attributes with `attrib -r -s -h app\build\*.* /s /d` and run `Remove-Item app\build -Recurse -Force`.
  3. When running build commands, stop the daemon afterwards with `.\gradlew.bat --stop` to release all file handles and ensure `OpenJDK Platform binary` does not consume system memory in the background.
- **APK Optimization & R8 Shrinking**: Debug builds (`assembleDebug`) package unminified DEX bytecode (~54 MB) due to `material-icons-extended` containing thousands of vector icons. Production release builds (`assembleRelease`) have R8 code shrinking (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`) enabled, tree-shaking unused icons down to a ultra-compact **2.60 MB** APK. Release builds use `signingConfig = signingConfigs.getByName("debug")` for immediate sideloading.
