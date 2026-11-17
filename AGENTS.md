# AGENTS.md

Context for AI coding assistants (and humans in a hurry) working on **Luna Browser**. Read this first, then the
document linked next to the area you touch. Humans: the friendly version is [CONTRIBUTING.md](CONTRIBUTING.md).

## What Luna is

A **TV-first web browser for Android TV / Google TV**: Kotlin, Jetpack Compose UI, the system **WebView** as engine.
Everything is driven by a **remote control** (D-pad, OK, Back, Menu, media keys); there is no touch input on the target
device. Goals, in order: usable from the sofa with a remote, smooth on weak TV hardware, private by design (no accounts,
analytics or telemetry), open source (MIT).

- Repository: `github.com/newhalo/luna-browser`. **Default branch is `marter`** (not `main`).
- Package `com.luna.browser` (debug builds: `com.luna.browser.debug`, shown as "Luna (debug)", installs next to release).
- `minSdk 26`, `targetSdk 34` (enough for Android TV on Google Play; **do not raise to 36 casually**: Android 16 removes
  the predictive-back opt-out and the remote's Back key handling, including long-press Back, depends on key events).
- Toolchain: AGP 8.13, Gradle 8.14, Kotlin 2.1 with the Compose compiler plugin, Compose BOM 2024.12.

## Commands

```bash
./gradlew :app:testDebugUnitTest          # unit tests (pure JVM, no device needed)
./gradlew :app:assembleDebug              # debug APK in app/build/outputs/apk/debug/
./gradlew :app:assembleRelease :app:bundleRelease   # needs the signing env vars, see docs/RELEASING.md
cd tools/phonepage-test && npm install && npm test  # the phone remote's web page, run in jsdom
```

Needs JDK 17 and the Android SDK. If you have neither locally, **push a branch and let CI build** (`.github/workflows/
build.yml`: tests + debug and release APKs as artifacts). A new push cancels the previous CI run of the same branch.

## Map of the code (`app/src/main/java/com/luna/browser`)

| Area | Where | Notes |
|---|---|---|
| Entry, key interception | `MainActivity`, `AppState` | `dispatchKeyEvent` hands **every key** to `AppState.handleKey` first. Navigation stack, Back / long-Back, phone-remote actions, update check |
| Page engine | `browser/BrowserController` | One per tab: WebView, virtual cursor, fullscreen video + JS bridge, error mapping, per-site settings, thumbnails |
| Tabs | `browser/TabManager`, `TabPolicy` | Max 8; only 3 (2 on low-end TVs) live, the rest frozen to address + title |
| Pop-ups / injected JS | `browser/PopupGuard` | Generic pop-up rules, overlay sweeper, cross-iframe video bridge |
| Reading mode, profiles | `browser/ReaderMode`, `browser/WebProfiles`, `ui/profiles` | The page only yields text blocks; the reading page is built from escaped text. One WebView profile per Luna profile |
| Data | `data/Store`, `Profiles`, `SiteSettings`, `Resume`, `TileSize`, `UpdateCheck`, `ReportLink`, `WebViewCheck`, `FavoriteInput` | `Store` is Compose-observable; settings keys live in object `K` |
| Ad blocking | `data/AdBlocker`, `data/filter/*` | Pure-Kotlin Adblock Plus engine (no Android types, easy to unit test). Lists: EasyList / ABPVN downloaded at run time, `assets/filters/luna.txt` is Luna's own and is also fetched from this repo daily |
| Phone remote | `remote/PhoneRemote`, `PadGestures` | LAN HTTP server + the phone web page (a Kotlin raw string with embedded JS) |
| UI | `ui/home`, `ui/browser`, `ui/tabs`, `ui/library`, `ui/settings`, `ui/components`, `ui/theme` | `CardRow` = horizontally sliding card row, `SettingRow`, `FocusSurface`, `TvButton` |

Deeper reading: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) (design), [docs/DESIGN.md](docs/DESIGN.md) (visual rules),
[docs/PERFORMANCE.md](docs/PERFORMANCE.md), [docs/ADBLOCK.md](docs/ADBLOCK.md), [docs/LOCALIZATION.md](docs/LOCALIZATION.md),
[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md), [docs/RELEASING.md](docs/RELEASING.md),
[docs/PUBLISHING.md](docs/PUBLISHING.md) (app stores), [SECURITY.md](SECURITY.md), [PRIVACY.md](PRIVACY.md).

## Rules that are easy to break

**Privacy and network**
- No analytics, crash reporters, ad IDs, accounts or any new SDK that phones home. Do not add tracking dependencies.
- Every network request the app makes by itself is listed in the README *Privacy* table and in `PRIVACY.md`. If you add,
  change or remove one, **update both**. Today: filter lists, `api.github.com` (update notice; skipped for store
  installs), page thumbnails. Don't reintroduce a third-party icon/favicon service.
- Never put the user's browsing data (full URLs with query strings, search words) into a request, report or log. See
  `ReportLink.cleanPage` for how page addresses are cut.

**Profiles**
- Anything stored per person (history, favourites, positions, tabs, last session...) goes through `Store.pk()` /
  `ProfileRules.key`, so it follows the active profile and is erased with it. Add a new per-profile key to
  `ProfileRules.dataKeys`. Compute the key **before** handing a write to the IO executor (the profile may change before it runs).
- Profile ids are `0`..`9` or `guest` (`ProfileRules.isValidId`); never build storage keys or WebView profile names from
  anything else. The guest must leave nothing behind (storage and web data, also after a crash: see `Store` init).
- A profile PIN is `PinRules` (salted SHA-256, `pin.<id>` key). Everything that opens or changes a profile must go through
  `AppState.tryPin` (growing waits) and, while `AppState.locked`, nothing else may open pages (phone remote, `openUrl`, Back).
  Never store or log a PIN.
- Cookies are per profile only where the WebView supports multi-profile (`WebProfiles.supported()`); the UI says when not.

**Security of the phone remote** (`PhoneRemote`)
- Every API call needs the PIN; failures are counted per client address plus a global ceiling; only LAN addresses are served;
  request time and size are capped; keys are limited to a whitelist (`REMOTE_KEYS`); `open`/`search` only accept
  `http(s)`; it only runs while Luna is on screen. Don't weaken any of these, and add a test in `PhoneRemoteTest` for
  anything new.
- A link shared from the phone's browser (`#open=` in the phone page) is shown and sent only after a confirming tap, never
  automatically. The phone page JS is tested by `tools/phonepage-test`.
- `AndroidManifest` allows cleartext traffic because a browser must open `http://` pages and the remote is plain HTTP on
  the LAN. Keep it documented.

**Secrets and signing**
- Never commit keystores, passwords or tokens. `app/dev.keystore` is a **public development key** on purpose (debug and CI
  builds); official releases are signed with the maintainer's private key supplied by GitHub secrets. Don't print secrets
  in logs or PR text.
- A store build must keep the same signing identity as the GitHub APK (plan: import the key into Play App Signing,
  see `docs/PUBLISHING.md`). Don't change `applicationId`.

**Store policy**
- The update notice only informs; **never** download or install an APK from inside the app, and keep it off when installed
  from a store (`UpdateCheck.isStoreInstall`).
- Don't claim to play DRM services (Netflix, Disney+, …), don't market ad blocking of specific services, and keep
  wording about video sites neutral (no piracy hints in docs, store text, rules or code comments).
- The name "Luna" has an open trademark question (see `docs/PUBLISHING.md` section 0). Don't spread it into new places
  (package names, domains) without asking the maintainer.

## UI rules (TV)

- Everything reachable with the D-pad; focus is always visible; Back always leaves the current layer. Test with keys, not
  touch. Text at least ~18 sp; keep the outer 5 % of the screen free (`Dim.SafeH` / `Dim.SafeV`).
- Weak GPUs: no full-screen translucent layers or `Modifier.alpha` over big areas, no disk or network work while composing.
- **Compose pitfall:** automatic "bring into view" fights custom scrolling and left rows half-scrolled. Rows (Home, Tabs,
  History cards) are translation-based (`CardRow`) instead of scroll containers; `LocalBringIntoViewSpec` does not exist in
  this Compose version. Focus moves between rows through explicit `FocusRequester`s.
- Settings rows are a small data model in `SettingsScreen` (`Toggle`, `Choice`, `Action`, `Info`, `Qr`); a new setting
  needs a key in `K`, a default in `Store`, a row, strings, and (if it changes layout) a look at the Home/History screens.
- The pointer (virtual cursor) vs link-focus mode, and fullscreen video controls, share the same keys: check both modes when
  you touch key handling in `AppState` or `BrowserController`.

## Strings and translations

- All user-visible text is in `res/values/strings.xml` (English) **and** the 9 other `values-xx/strings.xml`
  (`vi es fr de pt ru ja ko zh`). `LocalizationTest` fails if a language misses a key or a placeholder differs.
- Vietnamese is the maintainer's own language and the one used on the real TV; the others are carefully written but not
  all reviewed by native speakers. Keep placeholders (`%s`, `%d`) identical across languages.
- The phone remote page has its own labels (`ph_*` keys), injected into the page by `PhoneRemote.page()`; add new ones to
  `AppState`'s label map **and** the `PAGE_KEYS` list in `PhoneRemoteTest`.
- The English default is also what the Play listing is based on: `store/listings/<locale>/`.

## Tests and verification

- Prefer pure Kotlin for logic so it is unit-testable: filter engine, `ResumeRules`, `TabPolicy`, `SiteSettings`,
  `TileSize`, `UpdateCheck`, `ReportLink`, `PadGestures`. UI changes need a **real-TV or emulator check** (screenshots via
  `adb exec-out screencap -p` or `adb shell screencap` + `adb pull`; run adb from PowerShell on Windows, Git Bash mangles
  `/sdcard` paths).
- `BundledListTest` guards `assets/filters/luna.txt`: when you edit it, bump its `! Version:` line (YYYY.MM.DD); installed apps
  pick the newer of the downloaded and bundled copy.
- Video resume: a saved position is applied only when it is at least ~20 s ahead of where the page starts (pages autoplay
  for a while before fullscreen); don't "simplify" that rule without reading `ResumeRules` and its tests.
- Performance reference on the maintainer's TV (Xiaomi MiTV, Android 14): cold start about 0.6 s, ~55 fps. `tools/perf`
  measures frames; see `docs/PERFORMANCE.md` before adding animation.

## Git, commits, docs

- Repo line endings are LF (`.gitattributes`); don't commit CRLF. Docs are in **English**.
- Commit message: short imperative subject, a body that says *why*. One concern per commit/PR.
- Keep the docs in step: user-visible change → `README.md` (features / roadmap), `CHANGELOG.md` (*Unreleased*), the relevant
  `docs/*.md`. New screenshots go in `docs/screenshots/` (listed in `docs/SCREENSHOTS.md`; the README keeps only a few, 1280×720 JPEG) and, for store use, `store/screenshots/`
  (1920×1080 PNG) — keep sample pages neutral (Wikipedia pages about the Blender open movies), never personal data.
- Releases: bump `versionCode`/`versionName`, finish the changelog, tag `vX.Y.Z`, push the tag; the Release workflow builds
  the signed APK (and the `.aab` as an artifact). The tag push and anything outward-facing is the **maintainer's call**:
  don't publish releases or move/delete tags on your own initiative. Details: `docs/RELEASING.md`.

## Known open items

See the README *Roadmap* (what is next) and [docs/ROADMAP.md](docs/ROADMAP.md) (what each release added). Not planned on purpose: a download manager, DRM streaming. Not published on the Amazon Appstore by
decision of the maintainer. Things not yet tested on real hardware are listed in `docs/PUBLISHING.md` (TV quality
self-check) and in release notes.
