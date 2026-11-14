# Third-party notices

Luna Browser itself is released under the [MIT License](LICENSE). It builds on the following software and
data. Each remains under its own license.

## Libraries linked into the app

| Component | License |
|---|---|
| [Jetpack Compose](https://developer.android.com/jetpack/compose), AndroidX (core, activity, webkit, profileinstaller) | Apache-2.0 |
| [Kotlin](https://kotlinlang.org/) standard library and [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Apache-2.0 |
| [Coil](https://coil-kt.github.io/coil/) (image loading) | Apache-2.0 |
| Material Icons (Google) | Apache-2.0 |
| [ZXing](https://github.com/zxing/zxing) core (QR code encoder for phone pairing) | Apache-2.0 |

Test-only: [JUnit 4](https://junit.org/junit4/) (EPL-1.0) — not shipped in the APK.

Luna renders web pages with the device's **Android System WebView** (Chromium); it does not bundle a browser engine.

## Filter lists (downloaded at run time, not redistributed)

| List | License |
|---|---|
| [EasyList](https://easylist.to) | GPL-3.0 or CC BY-SA 3.0 (dual-licensed) |
| [ABPVN](https://abpvn.com) (`abpvn.txt` and the hosts list) | GPL-3.0 |

Luna **downloads** these lists from their maintainers' servers when the user enables them and refreshes them
daily. They are **not** part of this repository or of the APK, so Luna's MIT license is unaffected. If you
redistribute a build that bundles a copy of a list, you must comply with that list's license.

The bundled `app/src/main/assets/filters/luna.txt` is Luna's own work (MIT) and uses the common Adblock Plus
filter syntax, which is an open format.

## Network requests the app makes

Documented in the README under [Privacy](README.md#privacy).

## Sample content used in screenshots

The videos shown in `docs/screenshots/` are Blender Foundation open movies (*Big Buck Bunny*, © Blender
Foundation, <https://peach.blender.org>, licensed CC BY 3.0) and Wikipedia pages (CC BY-SA), used for
illustration only.
