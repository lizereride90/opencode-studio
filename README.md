# OpenCode Studio for Android

Native Jetpack Compose client for OpenCode with an app-private Debian Trixie ARM64 environment. The Android app includes a patched PRoot runtime, a real Debian installer, OpenCode server startup/API client, interactive PTY terminal, workspace file editing/ZIP sharing, an independent native WebView browser, and five preconfigured MCP servers.

## Android project

Open `android/` in Android Studio (JDK 17, Android SDK 36 and NDK 27.2). The PRoot engine is pinned as a git submodule at `android/runtime-upstream`; clone with `--recurse-submodules`.

The **Android app build** workflow builds a debug APK and uploads it as an artifact for pushes and pull requests to `main`.

## First run

1. Install the APK on an ARM64 Android device (Android 9/API 28 or newer).
2. Tap **Install** in Chat. The app downloads a Debian Trixie OCI rootfs into private app storage; allow several minutes and a few hundred MB of free space.
3. The setup then installs Node/npm, OpenCode, and MCP packages, writes the OpenCode MCP configuration, and starts `opencode serve` locally.
4. Add an OpenCode provider/API key in Debian (`opencode auth login` in Terminal) before sending coding prompts. The GitHub MCP additionally needs a GitHub token.

The root filesystem and project files remain in app-private storage. Uninstalling the app removes them. The Debian filesystem is a PRoot userland, not a privileged/chrooted OS; Android restrictions still apply. Only ARM64 is currently packaged.

## MCPs provisioned

Filesystem, Memory, GitHub, Sequential Thinking, and Playwright MCPs are installed as npm packages and added to the local OpenCode configuration. GitHub actions need a personal access token; Playwright browser automation may need browser setup in Debian.

## Notes

This app uses the OpenCode HTTP server API for native chat rather than loading the OpenCode web UI in a WebView. The separate Browser section is an Android WebView for browsing. OpenCode API runs bound to `127.0.0.1:4096` only.

The PRoot engine comes from [oonid/pr](https://github.com/oonid/pr) (MIT Android glue; upstream PRoot and included binaries retain their respective licenses). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
