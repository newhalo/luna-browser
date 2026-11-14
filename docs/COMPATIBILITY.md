# Device compatibility

Luna is a standard Android app (Kotlin, Jetpack Compose, the system WebView) with **no native code**, so one APK
runs on every CPU architecture (32/64-bit ARM, x86). It targets **Android 8.0+ (API 26)**, which covers Android TV and
Google TV, Fire TV (Fire OS 7 and later, which is Android 9+), Android-based set-top boxes and Android TV projectors.

> **Honest status:** it has been tested on one TV — a Xiaomi TV S Pro 65" (Android 14, MediaTek MT5879,
> 32-bit, Mali-G52, 2.4 GB RAM). The sections below explain what is adapted to other hardware and what is not yet
> verified. Reports from other TVs are very welcome (see [Reporting](#reporting-a-device)).

## What adapts automatically

| Difference between TVs | How Luna copes |
|---|---|
| **Resolution** — 720p, 1080p, 1440p, 4K, 8K | The UI is laid out on a 960 dp-wide canvas derived from the screen's *pixels* (`ui/theme/Scale.kt`), so everything has the same physical size whatever resolution or density the manufacturer configured |
| **CPU / GPU** | No native libraries. The GPU budget is tight on purpose (a Mali-G52 TV is the baseline): see [PERFORMANCE.md](PERFORMANCE.md) |
| **Little memory** (entry-level Android TV, Android Go TV, ≤ 1.5 GB) | A low-memory profile (`data/DeviceProfile.kt`) starts with the animated background off, *Reduce motion* on and the large EasyList filter off. Everything can be switched on in Settings |
| **No Menu key on the remote** (Google TV and Chromecast remotes) | The toolbar also opens with **Info**, **Bookmark**, **hold Back**, or **Up** when the pointer is at the very top of the page. The first-run tip mentions it. The **red** colour key toggles pointer / link mode where a remote has one. The [phone remote](../README.md#phone-remote) also has Menu, Back and Home buttons |
| **No microphone / no speech service** (AOSP boxes, no Google services) | The voice-search buttons are hidden |
| **Older WebView** (Android 8–9 TVs that rarely get updates) | Features check for support: without the document-start script API, pop-up and overlay protection still work but a video player inside a cross-origin iframe can't be bridged |
| **Language** | Follows the TV's system language (10 translations), see [LOCALIZATION.md](LOCALIZATION.md) |
| **Air-mouse / pointer remotes** | Mouse hover events are sent as usual; the on-screen pointer also works |

## Known limits

- **DRM streaming services** (Netflix, Disney+, Prime Video…) generally need hardware-backed DRM that a
  third-party browser's WebView does not offer, so they usually don't play (or only in low quality). Use their own apps.
- **Touch screens:** the layout is made for a remote; on a tablet or phone it runs but isn't designed for touch.
- **Phones/tablets:** installable (the `leanback` feature is optional) but unsupported.
- Remotes whose **OK key sends `ENTER`** are handled; remotes that send neither are not.

## Testing on other hardware

You don't need the TV to try layouts:

```bash
# Android Studio → Device Manager → "Android TV (1080p)" / "Android TV (4K)" / "Android TV (720p)" images
./gradlew :app:installDebug
```

On an emulator you can also force a different display to check the layout adapts. (Don't try this on a real TV:
the Xiaomi test TV produced a black picture after `wm size`, and the density rules are covered by unit tests
instead — `DeviceCompatTest`. If you do, note the TV's own display override first and restore *that*, not
`wm size reset`.)

```bash
adb shell wm size 1280x720 && adb shell wm density 213      # a 720p TV
adb shell wm size 3840x2160 && adb shell wm density 320     # a 4K panel left at 2x
adb shell wm size reset && adb shell wm density reset       # restore!
```

Measure frame timing with `tools/perf` and the ad-blocking behaviour with `tools/sitecheck`.

## Reporting a device

Open a **Bug report** (or a *compatibility report* in the title) with: TV model, Android/Google TV version, RAM, the
Web engine version (Settings → About) and what works or doesn't (start-up, remote keys, video, ad blocking).
Confirmed-working models will be listed here.

| Device | Android | RAM | Result |
|---|---|---|---|
| Xiaomi TV S Pro 65" (MiTV-MOEU0) | 14 | 2.4 GB | ✅ daily use; start-up 0.6 s, ~55 fps on Home |
