# Luna Browser

A TV-first web browser for Android TV (built for Xiaomi TV S Pro 65"). Kotlin, Jetpack Compose, system WebView.

- Premium dark UI with large D-pad friendly cards — see [docs/DESIGN.md](docs/DESIGN.md)
- Virtual cursor with acceleration, idle-hide and click feedback; switchable to focus navigation
- Video-first: fullscreen controls, seek, play/pause OSD, buffering/error overlays
- Ad / tracker / pop-up blocking with an updatable host list
- Home with quick sites, favorites, recent, continue browsing; History, Favorites, Settings

## Build
CI builds a debug APK on every push (Actions → *Build APK* → artifact `luna-browser-debug`).
Locally (JDK 17 + Android SDK 34):

```bash
gradle :app:assembleDebug     # or run `gradle wrapper` once, then ./gradlew
```

## Install on the TV
Enable Developer options → USB/Network debugging on the TV, then:

```bash
adb connect <tv-ip>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
