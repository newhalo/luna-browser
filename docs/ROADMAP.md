# Roadmap

**Next**
- [ ] Native-speaker review of the translations, then more languages ([how](LOCALIZATION.md)).
- [ ] Compatibility reports from more TVs and Android versions ([how](COMPATIBILITY.md#reporting-a-device)).
- [ ] Read aloud: choose the voice and speed, and skip to the next / previous paragraph.
- [ ] Per-profile settings (search engine, language, home layout).
- [ ] Live thumbnails for tabs that are frozen (they keep the picture taken when they were last left).

**Later**
- [ ] Emulator-based UI tests for D-pad navigation.
- [ ] Gradle 9 and the newest Compose, once the plugins catch up.
- [ ] Google Play listing: Play Console account, 12-tester closed test, then production (see [docs/PUBLISHING.md](PUBLISHING.md)).

**Done in 0.9.1:** the pointer snaps to links and buttons (Settings: *Snap pointer to links*); a click far down a page lands where the pointer is.

**Done in 0.9.0:** *private tabs*, address fallbacks (`www.`, plain http, search) and *Open anyway* for certificate errors.

**Done in 0.8.0:** the *Sakura* theme, round particles, and the optional *Glass look*.

**Done in 0.7.1:** *themes* (Luna, Dusk, Forest, Ocean, Night sky, Graphite), a phone page that fits the screen, and D-pad fixes on Home and in Settings.
**Done in 0.7.0:** *profile PINs*, *live tab thumbnails*, *reading text size* (remembered) and *read aloud*.
**Done in 0.6.0:** *web engine check* (About warns when the TV's WebView is too old, and the Home screen says so), *reading mode*, *favourites from the phone* (a Favorites tab: open, save, remove), and *profiles* with a guest mode.
**Done in 0.5.0:** an update notice (once a day, builds outside a store only), store-ready packaging, *Report a problem* from the *Site* panel (a QR code opens a pre-filled GitHub report; the page address is cut at the "?" and nothing is sent by the TV), and links shared from the phone's browser.
**Done in 0.4.x:** tabs, per-site settings, phone touchpad, inline video positions, filter-rule updates without a release, history grid, thumbnail size, and the toolchain upgrade (Android Gradle Plugin 8.13, Gradle 8.14, Kotlin 2.1, Compose BOM 2024.12).

**Not planned**
- A download manager: a TV browser is for watching; use a phone or computer for files.
- DRM streaming services (Netflix, Disney+, …): a WebView can't provide the hardware DRM they need; use their own apps.

Ideas and bug reports are welcome — see [CONTRIBUTING.md](../CONTRIBUTING.md).
