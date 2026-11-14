# Ad and pop-up blocking

Free streaming and news sites change domains and ad tricks all the time. Luna therefore has **no per-site
code**. Everything goes through a few generic layers, and everything that varies per site lives in **plain-text
filter lists** (Adblock Plus / uBlock Origin syntax) that can be updated without touching the app.

```
        ┌──────────────────────── Filter lists (text, ABP syntax) ────────────────────────┐
        │  filters/luna.txt (bundled)   EasyList · ABPVN · ABPVN hosts (downloaded daily)  │
        └───────────────────────────────────────┬─────────────────────────────────────────┘
                                                ▼
                                   FilterEngine  (data/filter/)
              ┌─────────────────────────┬───────┴────────────────┬─────────────────────────┐
              ▼                         ▼                        ▼                         ▼
     1. Block requests         2. Hide elements        3. Block navigation        PopupGuard
     shouldInterceptRequest    injectCosmetics         shouldOverrideUrlLoading   (generic rules,
                                                       onCreateWindow              no list needed)
```

| Layer | Where | Handles |
|---|---|---|
| Request blocking | `AdBlocker.shouldBlock` ← `BrowserController.shouldInterceptRequest` | ad / tracker scripts, images and iframes, pop-under networks |
| Element hiding | `FilterEngine.css` → `injectCosmetics` | sticky bottom banners ("catfish"), in-page ad boxes |
| Navigation blocking | `BrowserController` + `PopupGuard` | pages hijacked to an ad site, pop-up windows, unsolicited redirects |
| Invisible overlays | `PopupGuard.OVERLAY_JS` (injected at document start in every frame) | transparent iframes / layers that catch the first click on the player; "disable your ad blocker" dialogs |
| Video control | `PopupGuard.VIDEO_BRIDGE_JS` | players that live in a cross-origin iframe |

`PopupGuard` rules that need no list:

- A new window may open only for the **same site** or a sign-in provider (Google, Facebook, Apple, Microsoft, Zalo).
- A page that sends itself to another site without any key press (after 2.5 s of loading) is stopped.
- Navigation to a host that a filter list blocks is stopped.

## When a site shows ads again

1. **Update the lists:** Settings → Ad blocking → *Update filter lists*. The EasyList / ABPVN maintainers
   usually have the rule already. (EasyList is on by default except for Vietnamese, where ABPVN is.)
2. **Find the source:** put the page in `tools/sitecheck/sites.local.txt` (git-ignored; one URL per line) and run
   the check against a **debug** build installed on your TV:

   ```bash
   powershell -ExecutionPolicy Bypass -File tools/sitecheck/sitecheck.ps1 -Device 192.168.1.50:5555
   ```

   Needs `adb` on the PATH and Node.js 22+. `tools/sitecheck/out/report.md` lists, per page: which requests
   were blocked (and by which rule), ad slots still visible, third-party hosts the page called, whether
   pressing OK sent you to another site, plus a screenshot.
3. **Add a rule** to `app/src/main/assets/filters/luna.txt`, in the right section, with a date and the site:
   - block a whole ad domain: `||ads.example-network.com^`
   - block one path on one site: `||cdn.site.com/ads/$script,domain=site.com`
   - hide an element on one site: `site.com##.bottom-banner`
   - hide it on every TLD of a name: `sitename.*##.ads-top`
   - un-break a site that the blocker breaks: `@@||site.com^$document`
4. **Add a unit test** in `app/src/test/.../FilterEngineTest.kt` if the rule has a shape the tests don't cover yet.
5. Open a pull request. CI runs the tests and builds the APK.
6. If the rule helps everyone, also report it upstream: EasyList (<https://easylist.to>) or ABPVN
   (<https://abpvn.com>, for Vietnamese sites).

## When a site changes its tricks

| Symptom in the report | Usually | Fix |
|---|---|---|
| `[FAIL] redirected to …` after pressing OK | a new pop-under script | add its domain to section 1 of `luna.txt` |
| `[WARN] ads still visible` | a new banner | a `##` rule for that site (section 4) |
| Invisible overlays > 0 | a new kind of click-catcher | tune `OVERLAY_JS` (z-index, area thresholds) |
| Video in an iframe can't be controlled | player uses Shadow DOM / canvas | `VIDEO_BRIDGE_JS` |

## Adding another filter list

Add an entry to `FilterLists` in `data/AdBlocker.kt` (id, name, description, URL). It appears in Settings,
is downloaded, refreshed daily and can be switched on or off by the user. Supported syntax is documented at the
top of `data/filter/FilterEngine.kt`; scriptlet and procedural filters (`#?#`, `#$#`, `##+js`) are skipped.

## Known limits

- WebView does not report a request's resource type, so Luna infers it from the URL extension and the `Accept`
  header. A `$script` rule will not match a script served without a `.js` extension (such a request is treated as
  "unknown type" and matches only script / xhr / other rules).
- `luna.txt` is bundled in the app **and** downloaded from this repository (`raw.githubusercontent.com/.../HEAD/...`)
  once a day, so a fix reaches installed apps without a release. The copy with the higher `! Version: YYYY.MM.DD`
  line wins, which is why every edit must bump that line; `BundledListTest` (CI) checks the header, that the file
  parses into a sane number of rules, and that it has no script-injection syntax. Merging to the default branch
  publishes the change to everyone, so review rule edits like code.
- No filter list can stop an ad that is rendered *inside* the video stream.

## A note on use

Luna is a general-purpose browser: it does not host, index or link to any content, and the blocker is meant to
protect people from intrusive ads, trackers and malicious pop-ups. You are responsible for respecting the laws
and the terms of service that apply to the sites you visit.
