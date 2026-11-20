# AURA Music

Offline Android music player with purple glass-inspired UI. Built with Kotlin and Jetpack Compose.

## Features
- Read locally stored audio using Android MediaStore
- Play/pause, next/previous, seek, shuffle
- Dynamic purple gradient, translucent controls, artwork
- Android 11+ compatible; optional Android 12+ RenderEffect blur
- GitHub Actions debug APK artifact

## Build
Run the **Build Android APK** workflow from the Actions tab, or push to main. Download `aura-debug-apk` from the workflow run's Artifacts.

The debug APK is for personal testing. Grant audio access on first launch. Android 13+ uses READ_MEDIA_AUDIO; Android 11/12 uses READ_EXTERNAL_STORAGE.

## Notes
This is an initial test build. The first version focuses on a working local player and a glassy interface. Blur is applied to a decorative ambient layer rather than the whole interface so playback controls remain sharp. The full-glass mode can be switched off in the app. No music is bundled.
