# My Player

A modern Android video player built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Media3 ExoPlayer**, featuring a Bengali-first interface.

## Features

- **Zero-Permission Video Selection**: Add single or multiple videos safely using the Android Photo Picker (`PickMultipleVisualMedia`) or Storage Access Framework (`OpenMultipleDocuments`) with persistent URI access where supported.
- **Media3 ExoPlayer Playback**: Full-screen hardware-accelerated video playback with built-in player controls, lifecycle pause handling, and safe-drawing top navigation.
- **External Video Intent Support**: Open `video/*` files directly from other apps via `ACTION_VIEW`.
- **Clean Video Library**: Displays video titles and formatted durations (`h:mm:ss` / `m:ss`), with options to remove items or clear the list.
- **Custom Adaptive Launcher Icon & Typography**: Bundled `Plus Jakarta Sans` and `JetBrains Mono` fonts with a custom adaptive icon.

## Building & APK

```bash
gradle :app:assembleDebug
```

The generated debug APK is located at `app/build/outputs/apk/debug/app-debug.apk`. In Google AI Studio, you can also generate and download APKs/AABs from the project settings menu.
