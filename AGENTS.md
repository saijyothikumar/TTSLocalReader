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
- **TTS Engine & Model Distribution**: Kokoro-82M ONNX via `sherpa-onnx` (~85MB model, ~200MB total runtime), running fully offline on Android 9+ (API 28+). Initial app APK is lightweight (~25MB) with a resilient one-tap first-run downloader for offline Kokoro model weights with SHA-256 verification.
- **Audio Pipeline**: Sentence-level lookahead buffer (2 sentences ahead) feeding continuous PCM audio into Android `AudioTrack`, eliminating all inter-paragraph gaps and providing millisecond-accurate synchronized UI text highlighting.
- **Background Architecture**: Android Foreground Service + `MediaSessionCompat` with lock-screen notification and partial wake-locks.
- **Content Extractor & Storage**: Custom HTML novel extractor (specialized for Ranobes, RoyalRoad, etc., stripping comments and navigation) with Local Room DB caching and automatic "Next Chapter" link pre-fetching.

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
