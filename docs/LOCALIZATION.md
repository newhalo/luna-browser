# Localization

Luna's UI is translated into **English (default), Vietnamese, Spanish, French, German, Portuguese, Russian,
Japanese, Korean and Simplified Chinese**.

## How the language is chosen

1. **Automatic** (default): Luna follows the TV's system language. If that language isn't translated, English is used.
2. **Settings → General → Language** overrides it. The change is applied **immediately**, without restarting.
3. On Android 13+, the system's per-app language picker also works (`res/xml/locales_config.xml`).

Some defaults depend on the language too (see `data/Store.kt`, `data/AdBlocker.kt`):

| What | Behaviour |
|---|---|
| Home "Your sites" | YouTube first, then the region's main sites (e.g. VnExpress for Vietnamese, Yandex for Russian), then global sites and Wikipedia **in your language** |
| Search engines offered | Google, Bing, DuckDuckGo plus a regional one (Cốc Cốc, Yandex, Baidu, NAVER) |
| Voice search | Recognises speech in the UI language |
| Filter lists | ABPVN is on by default for Vietnamese only; EasyList for everyone else; all can be toggled in Settings |

## Where the strings live

| File | Language |
|---|---|
| `app/src/main/res/values/strings.xml` | English (default, and the reference for every other language) |
| `app/src/main/res/values-<code>/strings.xml` | `vi`, `es`, `fr`, `de`, `pt`, `ru`, `ja`, `ko`, `zh` |

Non-UI code reads strings with `app.s(R.string.…)` (so it honours the in-app language); Compose code uses
`stringResource(R.string.…)`. **Never hard-code user-visible text.**

## Improving or adding a translation

The translations were written carefully but **not all by native speakers — corrections are very welcome**.
Open a pull request that edits the language's `strings.xml`; no code changes are needed.

To add a **new language** (say Italian, `it`):

1. Copy `values/strings.xml` to `values-it/strings.xml` and translate every value.
   - Keep placeholders (`%1$s`, `%1$d`, `%d`) exactly as in English; you may reorder them.
   - Plurals need the forms your language uses (CLDR): `one`/`other`, and for example `few`/`many` for Russian.
   - Put a string in double quotes if it starts/ends with a space or contains runs of spaces (`"✓  On"`).
   - Use `’` instead of a bare `'`, or wrap the value in double quotes.
   - Keep toolbar labels short (≈ 10 characters): they sit in 84 dp wide buttons.
2. Add the code to `res/xml/locales_config.xml` and to `Languages` in `data/Store.kt` (the language's own name).
3. Optionally add regional sites / a search engine in `data/Store.kt` (`RegionalSites`, `searchEnginesFor`).
4. Run `./gradlew :app:testDebugUnitTest`. `LocalizationTest` fails if a key or placeholder is missing or wrong,
   or if the lists above disagree.
5. Try it on a TV or emulator: Settings → General → Language → your language. Check long labels aren't cut off.

## Not translated on purpose

Site and engine names (YouTube, VnExpress, DuckDuckGo…) and the native names in the language picker. The name **Luna**
stays as it is; only the word for "browser" around it is translated in the launcher name (`app_name`, e.g. *Trình duyệt
Luna*). The launcher shows it in the TV's system language, not the language chosen inside Luna.
