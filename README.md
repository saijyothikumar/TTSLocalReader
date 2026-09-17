# Neural Novel TTS 🎧📖

<div align="center">
  <img src="app_icon.jpg" width="140" height="140" alt="Neural Novel TTS Icon" style="border-radius: 28px;" />
  <h3>Offline, High-Fidelity Text-to-Speech Web Novel Reader for Android</h3>
  <p>Smooth, human-like neural voices with zero-gap paragraph playback.</p>
</div>

---

## 🌟 Key Highlights

- **Natural Neural Voice**: Powered by **Kokoro-82M ONNX** via `sherpa-onnx` running 100% locally on device CPU.
- **Zero-Gap Paragraph Audio**: Double-buffered lookahead synthesis streams continuous PCM into Android `AudioTrack`, eliminating the awkward 1-2 second pauses between paragraphs.
- **Sample-Accurate Text Highlighting**: Real-time sentence tracking synced with hardware playback head frames for accurate karaoke-style auto-scrolling.
- **Ranobes & Web Novel Scraper**: Automatically extracts story chapters (`#arrticle`), filters out reader comments (`#dle-comments-list`, `.comments`) and navigation chrome, and auto-detects "Next Chapter" links for offline pre-fetching.
- **Screen-Off Background Playback**: Android `ForegroundService` with `PARTIAL_WAKE_LOCK` and `MediaSessionCompat` lock screen controls.
- **Obsidian Amber Theme**: Eye-friendly dark mode interface (`#121214` background with glowing `#F59E0B` amber accents).
- **Android 9+ Compatibility**: Designed to run seamlessly on older and newer devices alike (API 28+).

---

## 📱 How to Build in Android Studio

1. **Open Project**: Launch Android Studio, select **Open**, and navigate to this repository folder.
2. **Gradle Sync**: Let Android Studio complete the initial Gradle sync.
3. **Run**: Connect your Android 9+ phone or emulator and click **Run 'app'** (`Shift + F10`).
4. **Generate APK**: In the top menu, click **Build** $\rightarrow$ **Build Bundle(s) / APK(s)** $\rightarrow$ **Build APK(s)**.

---

## 📂 Architecture Overview

- `com.tts.reader.data.scraper.NovelScraper`: Targeted Ranobes / web novel extractor with comment sanitization.
- `com.tts.reader.data.local`: Room Database for offline chapter caching and resume progress.
- `com.tts.reader.tts.ModelManager`: Resilient one-tap first-run downloader for Kokoro ONNX model weights.
- `com.tts.reader.tts.AudioStreamPipeline`: Streaming `AudioTrack` lookahead audio pipeline with sample-accurate highlight sync.
- `com.tts.reader.service.PlaybackService`: Background playback service with lock-screen notification and wake locks.
- `com.tts.reader.ui`: Jetpack Compose UI with locked Obsidian Amber theme.
