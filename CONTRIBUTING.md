# Contributing to Luna Browser

Thanks for your interest! Luna is a small project with a very specific goal — **a comfortable web browser
for the living-room TV** — so the best contributions keep that goal in sight: remote-control first,
readable from the sofa, smooth on weak TV hardware.

By participating you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to help

- **Report a site that still shows ads or pop-ups** — use the *Ad / pop-up report* issue template. This is
  the most useful report we get, and usually the fastest to fix (often a one-line filter rule).
- **Report a bug** with the remote or the video controls, ideally with the TV model and Android version.
  **Compatibility reports** from TVs other than the maintainer's are especially useful —
  see [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).
- **Translate or proofread the UI.** Ten languages exist, not all written by native speakers; fixing a
  translation or adding a language needs no code — see [docs/LOCALIZATION.md](docs/LOCALIZATION.md).
- **Fix a bug or build a feature** — open an issue first for anything larger than a small fix, so we can agree
  on the approach before you spend time on it.

## Development setup

You need **JDK 17** and the **Android SDK** (platform 34). Android Studio (Koala or newer) sets both up.

```bash
git clone https://github.com/newhalo/luna-browser.git
cd luna-browser
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

(`gradlew.bat` on Windows.) The APK is written to `app/build/outputs/apk/debug/`.

### Run on a TV or emulator

Create an **Android TV (1080p)** emulator, or enable *Developer options → USB / network debugging* on a real
TV, then:

```bash
adb connect <tv-ip>:5555        # real TV over Wi-Fi
./gradlew :app:installDebug
```

There is no touch input on a TV: test everything with the **D-pad only** (arrow keys, Enter, Back, Menu on an
emulator; the remote on a real TV).

## Before you open a pull request

1. `./gradlew :app:testDebugUnitTest` passes (CI runs it for every PR).
2. New behaviour has a test where one is practical — the filter engine is pure Kotlin and easy to test
   (`app/src/test/.../FilterEngineTest.kt`).
3. **User-visible text goes in `res/values/strings.xml`** (never hard-coded) and in every language's file —
   `LocalizationTest` checks it. For a new string you may add the English text and mark the others to be
   translated in the PR; maintainers or contributors will fill them in.
4. **TV checklist** for any UI change:
   - Every control is reachable and visibly focused with the D-pad; Back always leaves the current layer.
   - Text is at least ~18 sp; nothing important is in the outer 5 % of the screen (overscan).
   - No new full-screen translucent layer or `Modifier.alpha` over a large area — see
     [docs/PERFORMANCE.md](docs/PERFORMANCE.md). TV GPUs are weak.
   - No disk or network access while composing UI.
5. Keep changes focused; one concern per pull request. Update the docs and [CHANGELOG.md](CHANGELOG.md).
6. Describe *what* and *why* in the PR, and attach a screenshot for visual changes.

## Code style

Kotlin official style (`.editorconfig` is provided; Android Studio picks it up). Prefer small, readable
functions over comments that restate code; do comment *why* something non-obvious is done — especially
workarounds for WebView or TV-hardware quirks.

## Filter lists

Site-specific ad rules live in `app/src/main/assets/filters/luna.txt` (standard Adblock Plus syntax).
[docs/ADBLOCK.md](docs/ADBLOCK.md) explains how to find the offending request and write the rule. Please do
not add rules whose only purpose is to hide a site's own content or paywall.

## Security

Please do **not** open a public issue for a vulnerability — see [SECURITY.md](SECURITY.md).

## License

By contributing you agree that your contributions are licensed under the [MIT License](LICENSE).
