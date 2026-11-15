# Publishing Luna to app stores

Status: **not published anywhere yet**. Luna is distributed as a signed APK on
[GitHub Releases](https://github.com/newhalo/luna-browser/releases). This page is the plan for stores: what each store
asks for, what Luna already satisfies, what is missing, and what the maintainer has to do by hand.

> Store rules change often. Items marked **verify** come from third-party summaries or from memory and must be checked
> against the store's own current page before acting on them.

## 1. Which stores matter

| Store | Who it reaches | Cost | Effort | Verdict |
|---|---|---|---|---|
| **Google Play** (Android TV / Google TV) | Almost every Android TV owner, including Xiaomi, Sony, TCL and Philips TVs outside China | US$25 once | High (account rules, AAB, review) | **Main target** |
| **Amazon Appstore** (Fire TV) | Fire TV sticks and TVs | Free developer account | Medium; must test on a real Fire TV | Second, only if someone has a Fire TV |
| **F-Droid** | Open-source users | Free | Medium (metadata in `fdroiddata`, builds from source) | Good fit for an open-source browser |
| **Xiaomi GetApps** | Mostly phones; TV-specific rules not found | Free | Unknown | Skip until there is demand |
| **Sideloading** (Downloader app + GitHub Releases) | Everyone willing to type a short code | Free | Low | **Do this now**, see section 7 |

## 2. Google Play: what Luna already satisfies

| Requirement | Luna today |
|---|---|
| Launchable on TV: an activity with the `LEANBACK_LAUNCHER` category | Yes (`AndroidManifest.xml`) |
| `android.software.leanback` declared with `required="false"` | Yes (also runs on non-TV Android if ever needed) |
| `android.hardware.touchscreen` declared `required="false"` | Yes |
| Home-screen banner, 320 × 180 px, containing the app name | `@drawable/banner` exists (vector); **verify** it shows the name and renders at that size |
| Everything reachable with a D-pad, visible focus | Yes, this is Luna's main design rule ([DESIGN.md](DESIGN.md)) |
| No mandatory sign-in, no crash on first run | Yes |
| Permissions | Only `INTERNET` and `ACCESS_NETWORK_STATE` |
| Target API level for TV | `targetSdk = 34`. Google's help page lists API 34 for Android TV, but secondary sources say new apps need API 36 from 31 Aug 2026. **Verify in Play Console before the first upload**, and raise `targetSdk` if in doubt (see "Work for Luna" below) |

Reference: Android's [TV app quality checklist](https://developer.android.com/training/tv/publishing/checklist) and
[Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878).

## 3. Google Play: what is missing in the project

1. **Android App Bundle.** Play wants an `.aab`, not an APK. Add `:app:bundleRelease` to the Release workflow and attach
   the bundle (the APK stays for GitHub / sideloading).
2. **Signing decision (important).** Play re-signs apps with its own key (*Play App Signing*). People who installed the
   GitHub APK have Luna signed with the private key in `luna-keys`; a Play-signed build would **not** install over it
   (signature mismatch). Choose one before the first upload:
   - **A. Upload the existing key to Play App Signing** (Play Console lets you import your current app-signing key via
     Google's PEPK tool). Both channels keep the same signature and update each other. *Recommended; cannot be changed
     later.*
   - **B. Use a different application id for the store build** (for example `com.luna.browser.play`). Two separate apps,
     no conflict, but users can't move their data between them.
3. **Privacy policy** at a public URL. Luna collects nothing, so the policy is short; the project already has the facts
   (README → Privacy). Write `PRIVACY.md` and publish it with GitHub Pages or link its raw GitHub view (Play accepts a
   plain page).
4. **Store listing assets and text** (see section 5). Keep them in the repo, for example `store/`.
5. **Raise `targetSdk`** if verification says so. Moving from 34 to 35/36 changes system behaviour (edge-to-edge
   insets, foreground-service and background rules); on a TV app with one activity the risk is small but it needs a
   pass on the real TV.
6. **Cleartext traffic.** `usesCleartextTraffic="true"` is needed for `http://` sites and the phone remote. It is allowed,
   but a reviewer may ask why; the answer is "a browser must open http sites".

## 4. Google Play: what the maintainer has to do

1. **Create a Play Console developer account**: a Google account, a one-time US$25 fee, and identity verification
   (government ID; personal accounts may also need to verify with an Android phone and the Play Console app).
   *Personal or organisation?* An organisation account (needs a D-U-N-S number) skips the tester rule below, a personal
   account is simpler for one person. Public contact details are shown on the listing for personal accounts.
2. **Closed-test requirement (personal accounts created after 13 Nov 2023):** run a **closed test with at least 12
   testers who stay opted in for 14 consecutive days**, then apply for production access. Testers need their own Google
   accounts and must install from the Play opt-in link (sideloaded APKs don't count). Start gathering 12–15 people early:
   family, friends, the r/AndroidTV community, GitHub issues. **Verify** the numbers in the Console.
3. **Prepare the declarations Play asks for in the Console:**
   - **Data safety form**: Luna collects and shares no personal data; data stays on the device. The phone remote talks
     over the home network only and is not sent to the developer.
   - **Content rating questionnaire** (IARC). A web browser shows unrestricted web content; answer honestly, expect a
     "users can access the web" style rating.
   - **Target audience**: not designed for children (choose 18+ / general audience, not "Families").
   - **Ads declaration**: Luna shows no ads. (Luna *blocks* web ads inside pages, which is allowed for browsers.)
   - **App access**: no login needed.
   - **Government / financial / health apps**: none.
4. **Upload the AAB** to *Internal testing* first, then *Closed testing*, then *Production*. Fill the TV-specific parts
   of the listing (banner, TV screenshots).
5. **Respond to review.** The first review of a new app can take days; the TV review checks that the app is usable with
   a remote only.

## 5. Store listing checklist

| Item | Notes |
|---|---|
| App name | "Luna Browser" |
| Short description (80 chars) | e.g. "A TV-first web browser: remote-friendly, ad blocking, phone as remote" |
| Full description (4000 chars) | Reuse the README feature list; no claims about DRM services |
| Translations | Luna's 10 UI languages: translate the listing the same way ([LOCALIZATION.md](LOCALIZATION.md)) |
| App icon 512 × 512 PNG | From `docs/logo.png` |
| Feature graphic 1024 × 500 | New asset |
| TV banner | 1280 × 720 for the listing and 320 × 180 in the manifest (**verify** sizes in the Console) |
| TV screenshots | At least a few 16:9 shots (1920 × 1080): `docs/screenshots/` already has them, retake from the release build |
| Privacy policy URL | See section 3 |
| Contact email / website | A public address (a project mailbox is better than a personal one) |
| Category | Tools or Communication; Play has no "Browser" category for TV (**verify**) |

## 6. Other stores

**Amazon Appstore (Fire TV).** Free developer account; upload an APK or AAB; the app must be tested on a physical
Fire TV before submission and must pass Amazon's test criteria (D-pad navigation, landscape, no features missing on Fire
TV). Listing needs a 1280 × 720 icon and 3–10 screenshots (**verify**). Fire TV uses its own WebView, so compatibility
needs a real device: add it to [COMPATIBILITY.md](COMPATIBILITY.md) first.

**F-Droid.** Everything Luna depends on is open source (Compose, Coil, ZXing, kotlinx), the browser engine is the
system WebView, and it has no tracking. F-Droid builds from source and signs with its own key, so the F-Droid build can
never update over the GitHub APK. Steps: add a metadata file to
[`fdroiddata`](https://gitlab.com/fdroid/fdroiddata), make sure the build doesn't embed Google's dependency-info block
(`android { dependenciesInfo { includeInApk = false } }`), and describe the daily filter-list download as a network
feature. Reviewers check for non-free dependencies and tracking; Luna has none
([policy](https://f-droid.org/en/docs/Inclusion_Policy/)).

**Xiaomi GetApps.** Needs a Mi Developer Console account with identity or business verification. No TV-specific rules were
found; do not spend time here unless users ask for it. (Xiaomi TVs sold outside China use Google TV, so Play already
covers them.)

## 7. Do this now: make sideloading easier

Most TV users sideload with the **Downloader** app (AFTVnews). It accepts a short code or a direct URL, so Luna can be
installed without a computer:

1. Install *Downloader* from the TV's store.
2. Open it and enter `https://github.com/newhalo/luna-browser/releases/latest` (or a short link).
3. Choose the APK, install.

Add this to the README's Install section, and put the APK's SHA-256 in each release's notes (already done).

## 8. Suggested order

1. Decide the signing option (section 3.2) and write `PRIVACY.md`.
2. Add `bundleRelease` to the Release workflow; check `targetSdk`; verify the banner.
3. Create the Play Console account, start the closed test, collect 12+ testers.
4. Fill the declarations and the listing in parallel with the 14-day wait.
5. Apply for production, fix review feedback.
6. Then F-Droid (open source, low cost) and Amazon (only with a real Fire TV).

## 9. Work for Luna (code / repo) before the first upload

- [ ] `bundleRelease` in the Release workflow, AAB attached to the release.
- [ ] Decide and document the signing option; test installing the GitHub APK over a Play build.
- [ ] `PRIVACY.md` and a public URL.
- [ ] `store/` folder: listing text in 10 languages, graphics, screenshots.
- [ ] Confirm `targetSdk`; raise to 35/36 and test on the TV if needed.
- [ ] Make the banner contain the app name at 320 × 180.
- [ ] `dependenciesInfo { includeInApk = false }` (also required for F-Droid).
- [ ] A short "TV app quality" self-test pass using the official checklist.
