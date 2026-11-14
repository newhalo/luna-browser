# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/) (while below 1.0, minor versions may change behaviour).

## [Unreleased]

### Changed
- Documentation, scripts and repository files are now in English; the in-app UI is still Vietnamese
  (localisation is on the [roadmap](README.md#roadmap)).
- Signing: debug builds use the public `app/dev.keystore`; release builds read a private key from environment
  variables (`LUNA_KEYSTORE_FILE`, …) and fall back to the dev key.

### Added
- Gradle wrapper, release workflow, Dependabot, issue / PR templates, contribution and security policies.

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

[Unreleased]: https://github.com/newhalo/luna-browser/compare/HEAD...HEAD
