# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/) (while below 1.0, minor versions may change behaviour).

## [Unreleased]

## [0.3.0] — 2026-11-14

### Added
- **Continue where you left off**: videos of three minutes or more remember their position (checked twice a second
  while fullscreen, saved every few seconds). Reopening the page seeks back and says so; Home cards show a progress
  bar. Videos that are almost finished are forgotten; a different video on the same page (an ad, a changed
  episode) is never seeked.
- **Phone remote**: a small LAN web server (only while Luna is on screen) lets a phone send an address, a search or
  text for the focused page field. PIN-paired, rate-limited, web pages only. *Settings → Phone remote* shows a QR
  code (scan it to open the page already paired), the address and the PIN, and can issue a new PIN. `ResumeRulesTest`, `PhoneRemoteTest`.
- **Localisation**: all UI text moved to string resources. Ten languages — English (default), Vietnamese,
  Spanish, French, German, Portuguese, Russian, Japanese, Korean, Simplified Chinese. The UI follows the TV's
  system language; *Settings → General → Language* overrides it live. Android 13+ per-app language picker supported.
- Regional defaults: quick-access sites, search engines, voice-search language and filter lists per language.
- **Works across TVs**: the UI is laid out on a 960 dp canvas derived from the screen's pixels (720p–4K and any
  density setting look the same), a low-memory profile for entry-level TVs, Info / Bookmark open the toolbar and the red
  key toggles pointer / link mode for remotes without a Menu key, fallback for older WebViews. See
  [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).
- **EasyList** as a filter list (on by default except for Vietnamese, where ABPVN is).
- `LocalizationTest` (same keys, placeholders and plural forms in every language), `docs/LOCALIZATION.md`.

### Changed
- Documentation, scripts and repository files are now in English.
- Signing: debug builds use the public `app/dev.keystore`; release builds read a private key from environment
  variables (`LUNA_KEYSTORE_FILE`, …) and fall back to the dev key.

### Repository
- Gradle wrapper, release workflow, Dependabot (monthly, grouped), issue / PR templates, contribution and
  security policies, code of conduct, third-party notices.

## [0.2.0] — 2026-11-14

### Added
- **Streaming-style home screen**: animated aurora background, full-screen backdrop that follows the focused
  card, 16:9 cards with real thumbnails (video thumbnails, `og:image`, page screenshots), "continue watching",
  your sites, favourites, clock and voice search.
- **Standard ad-blocking engine** (Adblock Plus / uBlock Origin syntax) with the community **ABPVN** lists,
  refreshed daily, plus Luna's own bundled list. Per-list toggles and rule counts in Settings.
- **Pop-up guard**: blocks cross-site pop-ups and unsolicited redirects, removes invisible click-catching
  overlays and "please disable your ad blocker" dialogs.
- **Cross-iframe video control**: play / pause / seek with the remote even when the player lives in an iframe
  on another domain.
- **Debounced, accelerating seek**: key repeats accumulate into one seek with a preview of the target time.
- History and Favourites screens with thumbnails; first-run tip; error panels for offline, DNS, timeout and TLS.
- Unit tests for the filter engine; on-TV tools for ad-blocking checks (`tools/sitecheck`) and frame timing
  (`tools/perf`).

### Changed
- Start-up 4.5 s → 0.6 s and ~9 → ~55 fps on the home screen (release build, baseline profiles, far fewer
  full-screen GPU layers). See [docs/PERFORMANCE.md](docs/PERFORMANCE.md).
- Clicks are delivered as touch taps (Chromium ignores synthetic mouse-button events).

## [0.1.0] — 2026-11-14

### Added
- First version: Jetpack Compose TV UI on the system WebView, virtual cursor, floating toolbar, fullscreen
  video controls, host-based ad blocking, settings, history and favourites.

[Unreleased]: https://github.com/newhalo/luna-browser/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/newhalo/luna-browser/releases/tag/v0.3.0
