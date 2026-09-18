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
- **Hybrid TTS Engine**:
  1. *System TTS (Default / Instant)*: Built-in Android `TextToSpeech` requiring **0 MB download**, functioning 100% offline immediately on first launch with zero setup.
  2. *Kokoro-82M Neural Voice (Optional)*: High-fidelity studio-quality AI voice with a resumable (HTTP `Range`), auto-retrying chunked downloader that persists state across app restarts via local manifest.
- **Audio Pipeline**: Continuous lookahead sentence queue ensuring zero audible gap between paragraphs and millisecond-accurate synchronized UI text highlighting.
- **Background Architecture**: Android Foreground Service + `MediaSessionCompat` with lock-screen notification and partial wake-locks.
- **Content Extractor & Storage**: Universal Web Novel Extractor (supporting Ranobes, RoyalRoad, NovelFull, ScribbleHub, etc., with universal comment & ad stripping) with Local Room DB caching and automatic "Next Chapter" link pre-fetching.

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
