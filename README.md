# My Player

An MX Player-inspired Android video and audio player built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Media3 ExoPlayer**, featuring a Bengali-first dark cinema interface.

## Features

- **Folder-Based Video & Audio Browser**: Add device folders via Storage Access Framework (`OpenDocumentTree`) or individual media files (`OpenMultipleDocuments` / `PickMultipleVisualMedia`) to view Videos and Audio grouped by folder, with All / Video / Audio filter chips, search, file sizes, and watched progress bars.
- **MX Player Touch Gestures**:
  - **Right-side vertical swipe**: Adjust media volume (`0%–100%`) with live HUD indicator.
  - **Left-side vertical swipe**: Adjust screen brightness (`0%–100%`) with live HUD indicator.
  - **Horizontal swipe**: Smoothly scrub playback position forward or backward with live target timestamp preview.
  - **Double-tap left / right**: Instant `-10s` rewind / `+10s` forward skip.
- **Full Player Controls**:
  - Interactive seekbar, Next / Previous track in folder, Play/Pause, `-10s` / `+10s`.
  - Playback speed selector (`0.5x` – `2.0x`), Repeat mode (`Off` / `One` / `All`), Mute & Volume slider.
  - Screen Lock mode, Aspect Ratio (`Fit` / `Zoom` / `Fill`), Orientation toggle (Portrait / Landscape), Audio playback backdrop, and automatic resume position saving.

## APK

The latest debug APK is available in `apk/apk.apk` (and `app/build/outputs/apk/debug/app-debug.apk`).
