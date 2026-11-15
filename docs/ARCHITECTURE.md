# Architecture

Luna is a single-activity app: **Jetpack Compose** draws every screen, and a **system `WebView`** renders
pages. A small set of plain-Kotlin classes owns state and behaviour; Compose only reads it.

```
MainActivity ── dispatchKeyEvent ──► AppState.handleKey ──► BrowserController.handleKey
     │                                    │
     └── setContent ──► LunaApp ──► Home / Browser / Tabs / Settings / History / Favorites screens
                                          │
                         ┌────────────────┼───────────────────────────────┐
                         ▼                ▼                               ▼
                       Store          AdBlocker                  BrowserController
              (settings, history,   (filter lists →               (WebView, cursor, video,
               favourites, artwork)  FilterEngine)                 pop-up guard, thumbnails)
```

## Key classes

| Class | Package | Responsibility |
|---|---|---|
| `MainActivity` | `com.luna.browser` | Hosts Compose, receives intents, **intercepts every key** before views see it (`dispatchKeyEvent`) |
| `AppState` | `com.luna.browser` | Navigation stack, shared services, global Back / long-Back handling, network state |
| `Store` | `data` | Settings, favourites, history and artwork paths. Compose-observable; disk writes happen on a background executor. `version` is bumped on any change so screens can memoise |
| `AdBlocker` | `data` | Owns the filter lists (bundled + downloaded), compiles them off the UI thread, answers `shouldBlock` for WebView threads |
| `FilterBuilder` / `FilterEngine` | `data.filter` | Pure-Kotlin Adblock Plus parser and matcher (no Android dependency → plain JUnit tests) |
| `PhoneRemote` | `remote` | LAN web server for the phone remote (keep-alive connections, closed when it stops): PIN check, per-phone lock-out, a time and size budget per request, actions (open / search / type / remote keys / volume / home / touchpad move, scroll, tap) handed to the UI thread through a callback — keys are injected as real `KeyEvent`s through the activity, limited to a whitelist; started and stopped by `AppState` from a `snapshotFlow` of "foreground and enabled" |
| `ResumeRules` / `Store.savePosition` | `data` | Pure rules for "continue where you left off" (what is worth saving, when a video is finished, when a saved position applies) and the throttled store of positions; `BrowserController` polls the video twice a second while fullscreen |
| `TabManager` / `Tab` / `TabPolicy` | `browser` | The open tabs. Each live tab owns a `BrowserController`; only 3 (2 on low-memory TVs) are live at once, the others are *frozen* to an address + title and loaded again when selected (`TabPolicy` decides what to freeze). Tabs are restored, frozen, after a restart. `AppState.browser` is the current tab's controller |
| `BrowserController` | `browser` | Owns one tab's `WebView` and its clients; page state; error mapping; the **virtual cursor**; fullscreen and in-page video handling (it remembers where long videos were left); per-site settings applied *before* a request goes out (user agent, text size, ad blocking, control mode); thumbnail capture |
| `SiteSettings` | `data` | Per-site overrides (desktop site, text size, ad blocking, control mode), stored by registrable domain; every field falls back to the global setting |
| `PopupGuard` | `browser` | Generic pop-up rules plus the JavaScript injected into every frame (overlay sweeper, cross-iframe video bridge) |
| `ui.components` | `ui` | `FocusSurface` and the TV widgets, `MediaCard`, `Aurora` / `Backdrop` |
| `ui.theme` | `ui` | Colour, type and spacing tokens |

## Input model

A TV has no touch screen, so input is a **key stream**:

1. `MainActivity.dispatchKeyEvent` gives `AppState` the first look at every key.
2. Back is handled centrally (`AppState.back`): address dialog → browser layers (fullscreen controls →
   fullscreen → toolbar → page history) → previous screen → exit. *Long* Back opens the toolbar.
3. On the Browser screen, `BrowserController.handleKey` decides, in order: Menu → fullscreen video keys →
   (toolbar or error panel visible: let Compose's focus system handle it) → media keys → page scrolling →
   OK / D-pad in **cursor mode** or **link-focus mode**.
4. **Cursor mode** moves an on-screen pointer with a 16 ms ticker (so motion doesn't depend on the remote's
   key-repeat rate) with acceleration; hover is sent as a mouse event, a click as a finger tap (Chromium ignores
   synthetic mouse-button events). Pushing against the top/bottom edge scrolls the page with a wheel event.
5. All other screens use Compose focus; `rememberFocus` restores the last focused item.

## Video

`BrowserController` keeps the page's fullscreen `View` (`onShowCustomView`) and draws its own controls on top.
Commands go through JavaScript. Because many video sites put the player in a **cross-origin iframe**, a script injected
into *every* frame (`VIDEO_BRIDGE_JS`) relays play / pause / seek down with `postMessage` and reports the largest
video's state back up. Seeking is debounced: key presses accumulate into one pending seek, previewed on the
progress bar, and sent when the viewer stops pressing.

## Ad blocking

See [ADBLOCK.md](ADBLOCK.md). In short: filter lists → `FilterEngine` → request blocking, element hiding and
navigation blocking, with `PopupGuard` covering what lists can't.

## Thumbnails

When a page finishes loading, `BrowserController` records the visit and, a few seconds later, grabs artwork for
Home: a YouTube thumbnail URL for watch pages, the page's `og:image`, and a small `PixelCopy` screenshot of the
visible page (saved as a ~480 px JPEG in app-private storage). The pointer is hidden while the screenshot is taken.

## Performance constraints

TV SoCs are slow. Read [PERFORMANCE.md](PERFORMANCE.md) before adding animation or full-screen layers.

## Testing

- **Unit tests** (`app/src/test`): the filter engine on real-world rule shapes. Run in CI.
- **On-device tools** (`tools/`): `sitecheck` (ad-blocking report through Chrome DevTools) and `perf` (frame timing).
