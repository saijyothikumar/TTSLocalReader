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

## Resolved Issues & Hardened Invariants
- **Ranobes / DLE CMS Navigation Hijacking via Comments Sidebar**:
  - *Root Cause*: Ranobes uses `#next` and `#prev` IDs on chapter navigation buttons, which were missed by an older selector check (`#next_url`). The scraper fell back to scanning all document `<a>` tags before stripping the global comments sidebar (`#rightside`). Links in the sidebar to comments pages (e.g. `https://ranobes.net/.../comments/...`) containing user remarks like *"looking forward to next chapter"* were falsely identified as the next chapter URL. Loading that URL fetched a Ranobes 3-paragraph placeholder *"This page is for comments only..."*, from which subsequent sidebar scans jumped to completely unrelated novels.
  - *Fix & Verified Pattern*:
    1. `findNavigationLink` prioritizes exact `#next` / `#prev` IDs, class selectors, and scoped containers (`.chapter-nav`, `.navigation`, etc.).
    2. Document cloning with aggressive sidebar/comment stripping (`#rightside`, `.comments`, `aside`, etc.) before any fallback search.
    3. Strict keyword matching and length limit (`<= 25` chars) to reject user comments.
    4. Same-novel path prefix matching preference and same-domain requirement.
    5. `isValidChapterUrl` universally rejects `/comments/`, `/comment/`, `#comments`, `/user/`, `/catalog/`, etc.
    6. `parseDocument` actively detects and rejects *"This page is for comments only"* placeholders with `IllegalStateException`.
    7. `ReaderViewModel` automatically purges any previously corrupted comments chapter from the local Room database cache and protects prefetch chaining.
    8. `CaptchaSolverSheet` blocks navigation to `/comments/` and ignores comments URLs when checking challenge completion.
- **OriginOS 6 (iQOO Neo 7 Pro) Media Notification Widget Suppression**:
  - *Root Cause*: OriginOS 6 filters media playback widgets out of the Lock Screen and Control Center if the notification channel is initialized with `IMPORTANCE_LOW` (classified as silent), or if `MediaSessionCompat` fails to continuously report a valid `PlaybackStateCompat` (`STATE_PLAYING`/`STATE_PAUSED` with explicit actions `ACTION_PLAY_PAUSE`, `ACTION_SKIP_TO_NEXT`, `ACTION_SKIP_TO_PREVIOUS`, `ACTION_SEEK_TO`), or if `MediaMetadataCompat` lacks duration/metadata keys.
  - *Fix & Verified Pattern*:
    1. Channel configured with `NotificationManager.IMPORTANCE_DEFAULT` and `lockscreenVisibility = Notification.VISIBILITY_PUBLIC`.
    2. Continuous synchronization of `MediaSessionCompat.setPlaybackState()` whenever playback starts, pauses, or seeks.
    3. `NotificationCompat.MediaStyle` explicitly binds `setMediaSession(mediaSession.sessionToken)` with actions `[0, 1, 2]` and cancel button intent.
    4. Clean `PlaybackStateCompat.STATE_STOPPED` teardown before notification dismissal.
- **Inbuilt Voice Audio Clarity & Vocal Frequency Preservation**:
  - *Root Cause*: Device DSPs, Dolby Atmos, and sound equalizers on devices like iQOO/Vivo treat raw Android `TextToSpeech` output as generic media or alert tones, applying aggressive bass boost and spatial distortion that muffles vocal frequencies. Additionally, on Android 11+ (API 30+), apps cannot discover Google Speech Services (`com.google.android.tts`) without explicit `<queries>` declarations, causing fallback to low-quality OEM engines.
  - *Fix & Verified Pattern*:
    1. Added `<queries>` for `<package android:name="com.google.android.tts" />` and `<intent><action android:name="android.intent.action.TTS_SERVICE" /></intent></queries>` in `AndroidManifest.xml`.
    2. Implemented `speechClarityMode` routing speech with `AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY` + `CONTENT_TYPE_SPEECH` to explicitly instruct OS hardware DSPs to bypass music enhancement filters, stereo widening, and muffled bass boosts.
    3. Filtered out voices marked with `KEY_FEATURE_NOT_INSTALLED` or `isNetworkConnectionRequired`, automatically selecting high-definition/neural local voice packs.
    4. Provided an in-app engine picker and direct intent shortcut to Android System TTS settings in `VoiceSettingsSheet`.
- **Android CLI Tooling & Environment Standards**:
  - Official Google `android` CLI is installed at `%USERPROFILE%\AppData\AndroidCLI\android.exe`.
  - When running Gradle commands or `android describe`, explicitly set `JAVA_HOME=C:\Users\saiku\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2` to ensure Kotlin 1.9.24 / KSP compiler compatibility.
  - Official Android developer documentation can be queried via `android docs search "<topic>"` and fetched via `android docs fetch "kb://..."`.
- **Reading UI Keyboard Auto-Dismissal & Clean Viewport**:
  - *Root Cause*: Permanent inline URL TextField retained focus and kept the software keyboard active, with no tap handlers on reading viewport containers or paragraphs to request `clearFocus()`.
  - *Fix & Verified Pattern*:
    1. Auto-hide URL bar on reading screen once chapter paragraphs are loaded (`paragraphs.isNotEmpty()`), migrating manual URL entry to a dedicated `AddLink` dialog.
    2. Universal `LocalFocusManager.clearFocus()` and `LocalSoftwareKeyboardController.hide()` wired to reading view background tap gestures, `ParagraphCard.onClick`, spoken title tap, and `listState.isScrollInProgress`.
    3. All TextFields enforce `ImeAction.Done` with auto-dismiss actions.
- **Sleep & Loop Mode State Machine (Zero-Battery Deep Sleep Guarantee)**:
  - Supports Continuous (Timer Off), Loop Current Chapter Once, and Play $N$ Chapters before sleep.
  - Dynamic mode switching: If set to Play 3 chapters, and switched to Loop on chapter 2, it loops chapter 2 once and enters sleep.
  - `enterSleep()` teardown: Completely stops audio pipeline, stops Foreground Service, dismisses notification, releases partial wake-locks, and abandons audio focus, allowing Android OS to enter deep sleep without battery drain.
- **Novel-First Architecture & Canonical Mirror Classification (`NovelEntity` & `NovelClassifier`)**:
  - *Root Cause & Prior Defect*: Chapters were previously saved into a flat Room table keyed solely by chapter URL with no first-class parent Novel concept. Grouping was attempted retroactively via in-memory string splitting on chapter titles, failing when mirror sites omitted novel names or used varying formats.
  - *Architecture & Verified Pattern*:
    1. First-class `NovelEntity` Room table (`id`, `title`, `coverUrl`, `lastReadUrl`, `lastReadChapterTitle`, `lastReadSentenceIndex`, `totalChapters`, `lastReadTimestamp`, `createdAt`).
    2. `ChapterEntity` foreign-linked with `novelId: String` (indexed) and `chapterNumber: Float` for chronological sorting.
    3. `NovelClassifier` normalizes novel titles across different mirror sites into canonical slugs (e.g., `"Shadow Slave - Ranobes"`, `"Shadow Slave | FreeWebNovel"` -> `"shadow-slave"`), stripping mirror chrome, site watermarks, and author tags.
    4. Numeric float parsing of chapter sequences (`"Chapter 105.5"` -> `105.5f`) ensures true chronological ordering in database queries (`ORDER BY chapterNumber ASC, cachedAtTimestamp ASC`).
    5. Replaced redundant dual dialogs (`SavedChaptersDialog` and `ChapterLibrarySheet`) with a unified Obsidian Amber `NovelBookshelfSheet`:
       - *Level 1 (Bookshelf)*: Shows all saved novels with total chapter counts, last read chapter, 1-tap "Resume Reading", "Chapters" drill-down, and novel deletion protected by confirmation dialogs.
       - *Level 2 (Chapters Directory)*: Shows all chapters of the selected novel sorted chronologically, active playback indicator, 1-tap play, chapter URL copy, and single chapter deletion.
       - Clean TopAppBar: Replaced redundant folder button with direct Bookshelf menu icon.

