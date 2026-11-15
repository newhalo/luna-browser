# Publishing Luna to app stores

Status: **not published anywhere yet**. Luna is distributed as a signed APK on
[GitHub Releases](https://github.com/newhalo/luna-browser/releases). This page is the plan for stores: what each store
asks for, what Luna already satisfies, what is missing, and what the maintainer has to do by hand.

> Store rules change often. Items marked **verify** come from third-party summaries or from memory and must be checked
> against the store's own current page before acting on them.

## 0. Before any store: the name

"Luna" collides with **Amazon Luna** (cloud gaming; several US trademark applications for LUNA since 2020). A browser is a
different kind of product, and adding "Browser" helps a little, but trademark offices and courts look at the dominant
word, and a generic word like "Browser" is usually disregarded, so **"Luna Browser" is not a safe harbour**. The risk is
highest on the Amazon Appstore and on any store where Amazon can file a complaint. Before publishing: search USPTO,
EUIPO, WIPO and the Vietnamese IP Office (NOIP) for LUNA in software classes (9, 42); ask an IP lawyer if anything
relevant appears; consider a more distinctive name while the app has no store users, because renaming later breaks
installs, reviews and links. This is not legal advice.

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
| Home-screen banner, 320 × 180 dp, containing the app name | Yes: `banner.png` (mdpi/hdpi/xhdpi) shows the moon and "Luna" |
| Everything reachable with a D-pad, visible focus | Yes, this is Luna's main design rule ([DESIGN.md](DESIGN.md)) |
| No mandatory sign-in, no crash on first run | Yes |
| Permissions | Only `INTERNET` and `ACCESS_NETWORK_STATE` |
| Target API level for TV | `targetSdk = 34`. Google's [target API page](https://support.google.com/googleplay/android-developer/answer/11926878) (read 15 Nov 2026) says Android TV apps must target **API 34 or higher**; the API 36 rule applies to phones and tablets. The page also contradicts itself in one paragraph (API 33 for existing TV apps), so re-check in Play Console. **Do not raise `targetSdk` to 36 casually**: Android 16 removes the opt-out from predictive back, which changes how the remote's Back key reaches the app, and Luna's long-press Back depends on it |

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

## 9. Updates: how users learn about a new version

| Install route | Who updates it | How the user finds out |
|---|---|---|
| **Google Play / Amazon / F-Droid** | The store, automatically (if the user allows auto-update) or from the store's page | Play shows "Update" and can update in the background. Nothing for Luna to build |
| **GitHub APK, Downloader, adb** | Nobody, unless the user does it | Luna checks once a day (`UpdateCheck`, GitHub's public "latest release" API) and shows *Luna X.Y.Z is available* on Home, plus a QR code to the download page in *Settings → About*. It never downloads or installs by itself |

The check is skipped when the installer is a store (`getInstallSourceInfo`), because store policies don't allow an app to
direct users somewhere else to get itself, and the store already handles it. Users can switch the check off in
*Settings → About*. Silent self-install of an APK is deliberately not offered: it needs the "install unknown apps"
permission, which Play forbids for browsers' self-updates and which would be a security risk.

## 10. Work for Luna (code / repo) before the first upload

- [x] `bundleRelease` in the Release workflow (the `.aab` is kept as a workflow artifact, 90 days).
- [ ] Decide and document the signing option; test installing the GitHub APK over a Play build.
- [x] `PRIVACY.md` (and a public URL: the GitHub file works).
- [x] `store/` folder: listing text in 10 languages, graphics (`store/README.md`).
- [x] Confirm `targetSdk` (34 is enough for TV, see section 2).
- [x] Banner shows the app name.
- [x] `dependenciesInfo { includeInApk = false }` (also required for F-Droid).
- [ ] Retake 1920 × 1080 screenshots from a release build.
- [ ] A short "TV app quality" self-test pass using the official checklist.
