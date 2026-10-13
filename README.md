# OpenCode Studio for Android

An Android-native Jetpack Compose project is in [`android/`](android/). Open that directory in Android Studio, or use the **Android app build** GitHub Actions workflow to produce a debug APK.

The earlier root-level Next.js UI was the wrong platform and is retained temporarily as a historical prototype; the Android app is the intended project going forward.

## Current Android milestone

The native app shell contains Chat, Terminal, Files, Browser, and five MCP setup panels. Debian Trixie / OpenCode runtime provisioning and live API integration are not yet implemented; tool screens report when their runtime is unavailable rather than pretending commands are executing. Runtime work needs an Android-compatible PRoot binary, Debian ARM64 root filesystem bootstrap, and a supervised local OpenCode server.

## Build

Open `android/` in Android Studio with JDK 17, or run `gradle --no-daemon assembleDebug` there with Gradle 8.11.1. The APK is generated at `android/app/build/outputs/apk/debug/app-debug.apk`.
