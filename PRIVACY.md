# Privacy policy

*Luna Browser — last updated 15 November 2026*

Luna Browser ("Luna") is an open-source web browser for Android TV. **Luna does not collect, store on a server, sell or
share your personal data.** There are no accounts, no analytics, no advertising identifiers and no crash-reporting
services in the app.

## What stays on your TV

History, favourites, open tabs, thumbnails, per-site settings, video positions and settings are stored in the app's
private storage on your TV. They are never sent to the developer or to anyone else. *Settings → Privacy* clears history,
cookies, site data and the cache. Uninstalling Luna removes everything.

Web pages you open are rendered by the Android System WebView and may set their own cookies, as in any browser. Those
pages and their owners have their own privacy policies; Luna does not control them.

## Network requests Luna makes by itself

Besides the pages you open, Luna makes these requests. None of them carries an account, a device identifier or your
browsing history.

| Request | When | Why | Can be turned off |
|---|---|---|---|
| Filter lists from `easylist.to`, `raw.githubusercontent.com` (ABPVN and Luna's own list) and `abpvn.com` | First run, then daily | Ad and tracker blocking rules | Yes, per list (Settings → Ad blocking) |
| `api.github.com` (latest Luna release) | Once a day, **only for builds not installed from an app store** | To tell you a newer version exists | Yes (Settings → About → Check for updates) |
| Thumbnails from `i.ytimg.com` or the page's own preview image | History and Home cards | Artwork for pages you visited | Clear history to remove |

These servers see your TV's IP address, as any website does. Their own policies apply
(GitHub: <https://docs.github.com/en/site-policy/privacy-policies>).

## Profiles

Luna can keep separate history, favourites, open tabs and (on a recent web engine) sign-ins for up to five people and a
guest. This is only a separation on the TV, not an account: profile names and avatars are stored on the device and never sent
anywhere. Deleting a profile erases its data; a guest session erases itself when it ends.

## Private tabs

A private tab writes nothing about what it shows: no history entry, no *continue watching* card, no saved video position, no
thumbnail, no site icon, and it is never saved with the open tabs. On a recent web engine it has its own cookies and site
data, erased when the last private tab closes and again at every start of the app; on an older engine those are shared with
normal tabs (Luna says so when you open one). Favourites you save yourself are kept, as with any tab. Private tabs do not
hide anything from the sites you visit, your network or the TV's own system.

## Profile PINs

A profile PIN is stored on the TV only as a salted SHA-256 hash and never sent anywhere. It keeps family members out of each
other's profiles; it does not encrypt the data. Reading aloud uses the TV's own speech engine; Luna sends no text anywhere
for it.

## Phone remote

If *Settings → Phone remote* is on (it is by default), Luna listens on your home network, only while it is on screen, so a
phone on the same Wi-Fi can send addresses, searches, text and remote keys. Every request needs the 6-digit PIN shown on
the TV, nothing is sent to the internet, and nothing from the phone is stored by the developer. Turn it off in the same
settings page on networks you do not trust.

## Permissions

* `INTERNET`: to load web pages and the requests above.
* `ACCESS_NETWORK_STATE`: to show an offline message instead of a blank page.
* Microphone: Luna does not request it itself. *Voice search* uses the TV's own speech recogniser, if there is one.

## Children

Luna is a general-purpose web browser and is not directed at children. It shows whatever web content you open.

## Changes and contact

Changes to this policy are published in this file and noted in the [changelog](CHANGELOG.md). Questions: open an issue at
<https://github.com/newhalo/luna-browser/issues> or see [SECURITY.md](SECURITY.md) for vulnerabilities.
