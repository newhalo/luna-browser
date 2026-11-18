# Store assets

Everything a store listing needs, kept in the repository so it is reviewed and translated like code. How to publish is
in [docs/PUBLISHING.md](../docs/PUBLISHING.md).

```
store/
  graphics/
    icon-512.png                    Play app icon (512 x 512, square: Play rounds the corners)
    feature-graphic-1024x500.png    Play feature graphic
    tv-banner-1280x720.png          Play TV banner
  listings/<locale>/
    title.txt                       <= 30 characters
    short_description.txt           <= 80 characters
    full_description.txt            <= 4000 characters
```

Locales (Play codes): `en-US`, `vi`, `es-ES`, `fr-FR`, `de-DE`, `pt-BR`, `ru-RU`, `ja-JP`, `ko-KR`, `zh-CN`. The
launcher banner inside the app (320 x 180 dp, shows the name) is in `app/src/main/res/drawable-*/banner.png`.

Screenshots: `store/screenshots/` has 1920 x 1080 captures taken on a real TV (Xiaomi MiTV, Android 14) with the
open-movie sample pages. Play wants at least 2 and accepts up to 8 for TV. Retake them if the look changes.

## Play Console answers (draft, check against the console's current questions)

* **App category:** Tools (or Communication). **Contains ads:** No.
* **Data safety:** no data collected, no data shared. Data processed only on the device. Data is encrypted in transit
  (HTTPS) when the pages use it. No way to request data deletion is needed because nothing is held by the developer;
  uninstalling clears it.
* **Privacy policy URL:** `https://github.com/newhalo/luna-browser/blob/marter/PRIVACY.md` (or GitHub Pages).
* **App access:** all functionality available without login.
* **Content rating (IARC):** web browser with unrestricted web access; no user-generated content hosted by Luna.
* **Target audience:** 18+ / not designed for children.
* **Permissions:** `INTERNET`, `ACCESS_NETWORK_STATE`; cleartext traffic is used because a browser must open `http://` pages.
* **Foreground / background services, accessibility, sensitive permissions:** none.
