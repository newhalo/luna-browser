<div align="center">

<img src="docs/logo.png" alt="Luna Browser logo: a crescent moon" width="120">

# Luna Browser

**A web browser built for the living-room TV — designed for the remote, readable from the sofa, smooth on weak TV hardware.**

[![CI](https://github.com/newhalo/luna-browser/actions/workflows/build.yml/badge.svg)](https://github.com/newhalo/luna-browser/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/newhalo/luna-browser?display_name=tag&color=8aa4ff)](https://github.com/newhalo/luna-browser/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Android TV](https://img.shields.io/badge/Android%20TV-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)

<img src="docs/screenshots/05-video-controls.jpg" alt="Luna Browser playing a video fullscreen with remote-friendly controls" width="720">

</div>

Most Android TV browsers are phone browsers stretched onto a big screen. Luna starts from the remote instead:
an on-screen pointer you steer with the D-pad, one-press access to everything, video controls made for
**Play / Pause / Left / Right**, a built-in ad and pop-up blocker, and a home screen that feels like a streaming app.

> **Status:** early (0.x) but daily-driven on a real TV. Latest release:
> [**v0.9.2**](https://github.com/newhalo/luna-browser/releases/latest) — snapping also works on sites such as vn2d that hide the window size; 0.9.1 added: the pointer *snaps to links* (a short arrow press jumps to the
> nearest link or button) and OK no longer misses far down a page; on top of 0.9's *private tabs*, and sites typed without `www`/`https` now open
> (Luna retries `www.`, plain http, or searches); on top of 0.8's pink *Sakura* theme, round background particles and an
> optional frosted *Glass look*; on top of 0.7's six themes, phone page that fits the screen, profile PINs, live tab thumbnails
> and reading mode with read aloud.

## Features

- **Streaming-style home screen** — animated background, a backdrop that follows the focused card, 16:9 cards with
  real thumbnails of the videos and pages you opened, "continue watching" with progress bars, your most-used sites,
  favourites, clock and voice search.
- **Virtual cursor that snaps to links** — a short arrow press jumps the pointer to the nearest link or button in that
  direction; hold an arrow to move it freely (it accelerates, scrolls at the screen edge and settles on a nearby link when
  you let go). **OK** clicks. Hold **OK** to switch to link-to-link focus navigation. Turn snapping off in Settings.
- **Video first** — fullscreen controls for any HTML5 video, **even inside a player iframe from another domain**
  (where many video sites put it). Hold ← / → to seek: key repeats accumulate, accelerate (10 s → 30 s → 60 s) and
  are previewed on the progress bar, then sent as **one** seek, so the stream doesn't reload at every step.
- **Continue where you left off** — videos of three minutes or more remember their position, whether you watch them
  fullscreen or inline on the page; open the page again (or pick it from Home) and Luna jumps back to where you
  stopped, and says so. Ads or another episode on the same page are never seeked.
- **Tabs** — up to 8 tabs with a switcher made of the same 16:9 cards as Home, each showing a fresh screenshot of its page. Only the most recent few stay alive
  (two on low-end TVs); the rest are frozen to save memory and reload when you return. Tabs survive restarts.
- **Your layout** — thumbnail size (large / medium / small) for Home, and a History page that is a list or a grid.
- **Reading mode** — the toolbar's *Read* button turns an article into a clean page with large type, a reading time and
  only the text and pictures that matter; Up / Down scroll, Left / Right change the text size (remembered), **OK reads it
  aloud** with the TV's speech engine and highlights the paragraph being read. Back returns to the page. Nothing from the
  page can run in it.
- **Themes** — seven looks, each with its own colours and background motion: *Luna*, *Dusk* (rising embers), *Forest*
  (fireflies), *Ocean* (slow waves), *Night sky* (twinkling stars), *Sakura* (pink, drifting petals) and *Graphite* (pure
  black, nothing moves — the lightest on weak TVs). *Settings → General → Theme*; the whole screen changes at once. Lights
  and waves are painted into one small bitmap and only the few dozen particles are stamped at full size, so a theme costs
  the GPU about the same as the original background.
- **Glass look** — optional frosted, see-through buttons, cards and rows (*Settings → General*). No blur, so it stays light.
- **Profiles** — up to five people (plus a guest) each get their own history, favourites, *continue watching* and open
  tabs, and, on a recent web engine, their own sign-ins. A profile can have a **4-digit PIN** (the picker then opens behind
  it). *Who's watching?* can appear when Luna opens; guest mode erases itself when it ends.
- **Private tabs** — *Private* next to the search bar (or *New private tab* in the tab switcher) opens a tab that leaves no
  history, *continue watching* entry, video position, thumbnail or saved tab, and keeps its own sign-ins (on a recent web
  engine) until the last private tab closes.
- **Per-site settings** — the toolbar's *Site* button sets desktop mode, text size, ad blocking and pointer mode
  for one site only, and has **Report a problem**: scan a QR code to open a pre-filled report on GitHub.
- **Your phone as a remote** — scan the QR code in *Settings → Phone remote* and your phone becomes a full remote
  (D-pad with hold-to-repeat, OK, Back, Home, Menu, media keys, volume), a **touchpad** (drag to move the pointer,
  two fingers to scroll, tap to click), your TV's **favourites** (open, save, remove) and a keyboard for addresses, searches and text fields — no app to install, it
  is a web page served by the TV on your Wi-Fi (details [below](#phone-remote)).
- **Ad, tracker and pop-up blocking** — a standards-based filter engine (Adblock Plus / uBlock Origin syntax)
  with the community [EasyList](https://easylist.to) and [ABPVN](https://abpvn.com) lists refreshed daily, a built-in list, generic pop-up and
  redirect protection, removal of invisible click-catching overlays and "disable your ad blocker" nags.
  Details in [docs/ADBLOCK.md](docs/ADBLOCK.md).
- **Made for TV hardware** — 0.6 s cold start and ~55 fps on a 32-bit MediaTek TV SoC; see
  [docs/PERFORMANCE.md](docs/PERFORMANCE.md).
- **Remote-only UX** — every control reachable with the D-pad, visible focus, no touch-only UI, safe-area margins,
  large type, *Reduce motion* setting.
- Floating two-row toolbar (address; Back, Forward, Refresh, Home, Tabs, Save, pointer mode, This site, Settings), History, Favourites,
  clear error screens (offline, DNS, timeout, TLS), settings in seven plain-language groups.
- **Speaks your language** — English, Tiếng Việt, Español, Français, Deutsch, Português, Русский, 日本語, 한국어, 中文.
  Follows the TV's system language automatically; switch any time in Settings → General → Language.
- **Private by design** — no accounts, analytics or telemetry. See [Privacy](#privacy).

## Screenshots

> Captured on a Xiaomi TV S Pro 65" (4K, Android 14). Sample content is the open-source Blender movie
> *Big Buck Bunny* and Wikipedia.

![Home screen with search and Continue watching](docs/screenshots/01-home.jpg)

| | |
|:--|:--|
| ![Fullscreen video with seek preview](docs/screenshots/04-video-seek-preview.jpg) | ![Virtual cursor on a web page](docs/screenshots/03-web-cursor.jpg) |
| **Hold → to seek:** target time and bar preview, one seek on release | The on-screen pointer; **OK** clicks |

More: tabs, reading mode, profiles, themes, settings and the phone page in the [full gallery](docs/SCREENSHOTS.md).

## Install

You need an **Android TV / Google TV** (Android 8.0+, API 26) and the ability to sideload an APK. One APK runs on
any CPU architecture and any TV resolution (720p to 4K); entry-level TVs get a lighter profile automatically.
Details and known limits: [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md). Tested so far on a Xiaomi TV S Pro 65"
— reports from other TVs are welcome.

1. Download `luna-browser-vX.Y.Z.apk` from the [latest release](https://github.com/newhalo/luna-browser/releases/latest)
   (the page lists its SHA-256 checksum).
2. On the TV enable **Developer options → USB debugging / network debugging**
   (Settings → Device Preferences → About → press *Build* seven times).
3. From a computer on the same network:

   ```bash
   adb connect <tv-ip>:5555
   adb install -r luna-browser-vX.Y.Z.apk
   ```

   Accept the *Allow USB debugging?* prompt on the TV the first time. Without a computer you can use a
   "send files to TV" app and a file manager instead. Or, with no computer at all: install the **Downloader** app on the
   TV, open `https://github.com/newhalo/luna-browser/releases/latest` in it and install the APK (you must allow
   Downloader to install unknown apps once). Store plans: [docs/PUBLISHING.md](docs/PUBLISHING.md).

Luna appears in the TV launcher as **Luna Browser**. Updating is the same `adb install -r` with the newer APK; your
history and favourites are kept.

> **Switching between release and source builds:** official releases are signed with the project's private key, builds
> from source with the public development key, and Android won't install one over the other — uninstall first
> (`adb uninstall com.luna.browser`; this clears Luna's data).

## Controls

| Key | On a web page | In fullscreen video |
|---|---|---|
| D-pad | short press: jump to the nearest link or button in that direction (scrolls when there is none); hold: move the pointer freely and settle on a nearby link on release; **Up** at the very top opens the toolbar | **← / →** seek (hold to accelerate) |
| **OK** | click | show controls, then play / pause |
| Hold **OK** | switch pointer ⇄ link-focus mode | — |
| **Menu** (or **Info**) | show / hide the toolbar | same |
| **Back** | close toolbar → previous page → Home → exit | hide controls → leave fullscreen |
| Hold **Back** | open the toolbar (use this if your remote has no Menu key) | leave fullscreen, open toolbar |
| Play/Pause, ⏪ ⏩ | control the page's video | same |

Home, volume and power keys are never intercepted. Full table: [docs/DESIGN.md](docs/DESIGN.md#remote-map).
No Menu key, or want to type faster? Use [your phone as the remote](#phone-remote).

## Build from source

Requirements: **JDK 17** and the **Android SDK** (platform 34) — Android Studio sets up both.

```bash
git clone https://github.com/newhalo/luna-browser.git
cd luna-browser
./gradlew :app:testDebugUnitTest :app:assembleDebug     # gradlew.bat on Windows
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Use `:app:assembleRelease` for the optimised build (R8 + baseline profiles) — that is the one to run on a TV.
Debug and release builds are signed with the same public development key, so they install over each other.
Official releases use a private key (so uninstall before switching between the two); see
[docs/RELEASING.md](docs/RELEASING.md).

## Project layout

```
app/src/main/java/com/luna/browser/
  MainActivity.kt, AppState.kt        entry point, navigation, global key handling, language
  browser/                            WebView controller (cursor, video, popups), PopupGuard
  data/                               Store (settings, history), AdBlocker, filter/ (list parser + engine), Resume (video positions)
  remote/                             PhoneRemote: the LAN web server + page behind "your phone as a remote"
  ui/                                 Compose screens, TV components, theme
app/src/main/res/values*/strings.xml  UI strings: English default + 9 translations
app/src/main/assets/filters/luna.txt  Luna's own filter rules (Adblock Plus syntax)
app/src/test/                         unit tests (filter engine, localisation, device compatibility, resume rules, phone remote)
tools/sitecheck/                      on-TV ad-blocking report (Chrome DevTools + adb)
tools/perf/                           on-TV frame timing
docs/                                 architecture, design, ad blocking, performance, localization, compatibility, releasing,
                                      phone remote, screenshots gallery, roadmap
```

Start with [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Phone remote

Typing with a TV remote is slow, and not every remote has a Menu key. With *Settings → Phone remote* on (the default),
scan the QR code shown there and your phone becomes a remote, a touchpad, a keyboard and a favourites list: a web page
served by the TV on your Wi-Fi, nothing to install, protected by a PIN, and only listening while Luna is on screen.
Pairing steps, sharing a page from your phone and the safety rules: [docs/PHONE_REMOTE.md](docs/PHONE_REMOTE.md).

## Privacy

Luna has no accounts, no analytics and no telemetry. The only network traffic besides the pages you open:

| Request | When | Why |
|---|---|---|
| Filter lists: EasyList (`easylist.to`) or, for Vietnamese, ABPVN (`raw.githubusercontent.com`, `abpvn.com`), plus Luna's own `luna.txt` from this repository (`raw.githubusercontent.com`) | first run, then daily | ad-blocking rules (each list can be switched off in Settings) |
| `api.github.com` (latest release) | once a day, only for builds **not** installed from a store | tells you a newer Luna exists; nothing is downloaded or installed (switch off in Settings → About) |
| Thumbnails (`i.ytimg.com` or the page's own `og:image`) | home / history cards | artwork for pages you visited |
| Incoming: your phone → the TV on port 8765 (LAN only, PIN-protected) | only while Luna is on screen and *Phone remote* is on | the phone remote |

History, favourites and thumbnails stay in the app's private storage on the TV; *Settings → Privacy* clears them.
Pages are rendered by the Android System WebView and get **no** JavaScript bridge to the app.

## Roadmap

**Next**
- [ ] Native-speaker review of the translations, then more languages ([how](docs/LOCALIZATION.md)).
- [ ] Compatibility reports from more TVs and Android versions ([how](docs/COMPATIBILITY.md#reporting-a-device)).
- [ ] Read aloud: choose the voice and speed, and skip to the next / previous paragraph.
- [ ] Per-profile settings (search engine, language, home layout).
- [ ] Live thumbnails for tabs that are frozen (they keep the picture taken when they were last left).

**Later**
- [ ] Emulator-based UI tests for D-pad navigation.
- [ ] Gradle 9 and the newest Compose, once the plugins catch up.
- [ ] Google Play listing: Play Console account, 12-tester closed test, then production (see [docs/PUBLISHING.md](docs/PUBLISHING.md)).

**Not planned**
- A download manager: a TV browser is for watching; use a phone or computer for files.
- DRM streaming services (Netflix, Disney+, …): a WebView can't provide the hardware DRM they need; use their own apps.

What each release added: [CHANGELOG.md](CHANGELOG.md) and the [full roadmap](docs/ROADMAP.md). Ideas and bug reports are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

## Contributing

Pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) and the
[Code of Conduct](CODE_OF_CONDUCT.md) first. Found a site that still shows ads? Use the *Ad / pop-up report*
issue template — it is usually a one-line fix. For vulnerabilities see [SECURITY.md](SECURITY.md).
Using an AI coding assistant? Point it at [AGENTS.md](AGENTS.md): the project context, rules and pitfalls it needs.

## Acknowledgements

[EasyList](https://easylist.to) and [ABPVN](https://abpvn.com) for the filter lists · [Jetpack Compose](https://developer.android.com/jetpack/compose) ·
[Coil](https://coil-kt.github.io/coil/) · the Chromium / Android WebView team · the Blender Foundation for open
movies that make great test footage. Full list: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Disclaimer

Luna is a general-purpose browser. It does not host, index or distribute any content, and the ad blocker exists to
protect viewers from intrusive ads, trackers and malicious pop-ups. You are responsible for complying with the laws
and terms of service that apply to the sites you visit.

## License

[MIT](LICENSE) © 2026 Nguyen Anh Luan
