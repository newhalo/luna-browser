# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/) (while below 1.0, minor versions may change behaviour).

## [Unreleased]

## [0.7.1] - 2026-11-15

### Added
- **Themes**: *Luna* (default), *Dusk*, *Forest*, *Ocean*, *Night sky* and *Graphite*, chosen in *Settings → General → Theme*. Each has its own colours (background, surfaces, accent) and background motion: drifting lights, rising embers, fireflies, waves, twinkling stars, or nothing at all (Graphite, pure black). Reduce motion and *Animated background* still freeze them. Measured on the MiTV: idle GPU time per frame is the same as the original background in every theme (12.9 ms), and Graphite draws nothing while idle.

### Fixed
- Phone remote: the remote tab now fits the phone screen without scrolling (pad and buttons scale with the screen height); the Type and Favorites tabs scroll inside their own area while the tab bar stays at the bottom.
- Remote navigation: Right on the last card of a Home row no longer jumps into the row below, and Left from a Settings item always returns to the category being shown (it used to land a few categories further down).

## [0.7.0] - 2026-11-15

### Added
- **Profile PINs**: a profile can ask for a 4-digit PIN (number keys on the remote or an on-screen pad, entered twice when set). A profile with a PIN opens behind the *Who's watching?* picker at start-up; opening, editing or deleting it needs the PIN, Back at the picker leaves Luna, and the phone remote can't open pages while it is shown. Only a salted SHA-256 is stored; five wrong tries start growing waits (30 s up to 5 min). It keeps family members apart; it is not encryption.
- **Live tab thumbnails**: opening the tab switcher from a page first takes a fresh screenshot of it (never more than half a second), so each tab card shows what the page looks like now. Thumbnails are removed with the tab, the history and when the profile changes.
- **Reading mode text size**: Left / Right while reading make the text smaller or larger (5 steps, 89 % to 158 %); the choice is remembered and also in *Settings → Browsing*.
- **Read aloud**: OK in reading mode starts the article with the TV's speech engine (in the article's language when the engine has it), highlights the paragraph being read and scrolls to it; OK or Play/Pause pauses and resumes from that paragraph. Long paragraphs are cut at sentence ends for the engine.

## [0.6.0] - 2026-11-15

### Added
- **Web engine check**: *Settings → About* grades the TV's system WebView (up to date / outdated / too old) with how to update it; Home shows a line when it is outdated. (Chromium 110+ is "up to date".)
- **Reading mode**: a *Read* button on the toolbar rebuilds the page's article as a clean dark reading page (large type, reading time, headings, lists, quotes, pictures). Up / Down scroll, Back returns to the page. The reading page is built from escaped text in a separate document: nothing from the page runs in it.
- **Favourites from the phone**: a third *Saved* tab on the phone page lists the TV's favourites (tap to open, trash with a second tap to remove), and *Save to favorites* appears next to a shared link and in the Type tab. Up to 60 favourites.
- **Profiles**: up to five profiles and a guest. Each has its own history, favourites, continue-watching positions and open tabs; with a recent WebView also its own cookies and sign-ins. *Who's watching?* picker (optional at start-up), manage screen (name, colour, delete), a round profile button and greeting on Home. Guest mode keeps nothing and erases its web data when it ends. Existing data becomes the *Main* profile.

### Added
- `AGENTS.md` (project brief for AI coding assistants) and a `CLAUDE.md` that imports it.

## [0.5.1] - 2026-11-15

### Fixed
- Phone page: a link and the pairing PIN in the same address fragment no longer lose the PIN (found by the new jsdom test in `tools/phonepage-test`).

### Changed
- **No third-party icon service**: Home cards for built-in sites show a lettered tile in the site's colours until the site has been opened once; its own icon is then saved on the device. The app no longer contacts `google.com/s2/favicons`.
- Logo, banner and store graphics re-set in Roboto (open licence); notices for the screenshot artwork added; documentation wording about video sites made neutral.

## [0.5.0] - 2026-11-15

### Added
- **Update notice**: for builds installed outside a store, Luna checks GitHub once a day and shows "Luna X is available" on Home, with a QR code to the download page in Settings → About. It never installs anything itself and can be switched off. Store installs skip it.
- **Store-ready**: a launcher banner that shows the name, `PRIVACY.md`, `store/` (listing text in 10 languages and Play graphics), an `.aab` built by the Release workflow, and no dependency-info block in the APK. See docs/PUBLISHING.md.
- **Report a problem** (Site panel): a QR code opens a GitHub report pre-filled with the page address (without query or fragment), Luna and WebView versions and the filter-list date. The TV sends nothing itself.
- **Share from the phone**: a bookmarklet on the phone page brings a page address from the phone browser to the TV, opened after one confirming tap.

## [0.4.1] - 2026-11-15

### Added
- **History as a grid**: a button switches the History page between the list and a grid of 16:9 cards (remembered).
- **Thumbnail size** (Settings → Home): large, medium or small tiles on Home and in the History grid, so more fit on screen.

### Fixed
- Tab switcher: the row of cards no longer stays scrolled after a tab is closed.
- Settings: the title no longer scrolls out of view; the category list fits without scrolling.

## [0.4.0] - 2026-11-15

### Added
- **Tabs**: up to 8 pages open at once. Each tab has its own WebView, but only 3 (2 on low-memory TVs) are live at a
  time; the others are frozen to an address and loaded again when selected, so a TV's memory is never exhausted.
  A *Tabs* screen (toolbar button, or on Home once something is open) shows them as the same 16:9 cards as Home:
  OK opens one, Down reaches *Close tab* / *Close other tabs* / *New tab*. Tabs come back after a restart.
- **Site settings**: a *Site* panel in the toolbar changes desktop site, text size, ad blocking and control mode for
  just the site you are on (every field falls back to the global setting). *Settings → Browsing* shows how many sites
  have overrides and can reset them. Useful when one site breaks with the blocker on, or needs bigger text.
- **Filter rules without a release**: Luna's own `luna.txt` is also downloaded daily from this repository; the copy
  with the higher `! Version:` wins. `BundledListTest` guards the file in CI.
- **Phone touchpad**: a *D-pad / Touchpad* switch in the phone's Remote tab. Drag to move the pointer (with
  acceleration), tap to click, two fingers to scroll; where there is no pointer (menus, Home, video) swipes become
  D-pad presses. The phone connection is now kept alive for a smooth pointer.
- Video position is also remembered for videos that play inside the page (not only fullscreen).
- Debug builds install next to the release app (`com.luna.browser.debug`, "Luna (debug)").

### Changed
- The browser toolbar has two rows (address on top, actions below) to make room for *Tabs* and *Site*.
- Toolchain: Android Gradle Plugin 8.13, Gradle 8.14, Kotlin 2.1 (with the Compose compiler plugin), Compose BOM
  2024.12, compileSdk 36 and newer AndroidX / Coil libraries.

## [0.3.1] — 2026-11-15

### Added
- **Remote pad on the phone page**: a *Remote* tab with D-pad (hold to repeat, so the pointer glides), OK, Back, Home,
  Menu, rewind / play-pause / fast-forward and volume; the typing tools moved to a *Type* tab.

### Changed
- The phone page was redesigned for thumbs: a round D-pad with a large OK, rounded icon buttons (inline SVG instead of
  text glyphs), a volume pill, bottom tabs, toast messages, haptic feedback, safe-area and small-screen layouts.
- Wrong-PIN lock-out is counted per phone (with a much higher shared ceiling), and the request queue is longer, so
  several phones can be used together.

### Fixed
- Home: *Up* from a row goes to the row above instead of jumping to the search bar; history merges the `m.` / `www.` /
  `#fragment` variants of one page into a single entry.
- Home: moving down to the *Your sites* (or *Favourites*) row no longer slides its first card to the screen edge,
  cuts the row off at the bottom or leaves the previous row peeking under the search bar. Compose's automatic
  "scroll the focused card into view" competed with the row-snap animation; rows now keep a 48 dp margin at both ends.

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

[Unreleased]: https://github.com/newhalo/luna-browser/compare/v0.7.0...HEAD
[0.7.0]: https://github.com/newhalo/luna-browser/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/newhalo/luna-browser/compare/v0.5.1...v0.6.0
[0.5.1]: https://github.com/newhalo/luna-browser/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/newhalo/luna-browser/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/newhalo/luna-browser/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/newhalo/luna-browser/compare/v0.3.1...v0.4.0
[0.3.1]: https://github.com/newhalo/luna-browser/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/newhalo/luna-browser/releases/tag/v0.3.0
