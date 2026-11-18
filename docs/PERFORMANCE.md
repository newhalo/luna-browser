# Performance on a TV

Test device: Xiaomi TV S Pro 65" (MiTV-MOEU0), Android 14, MediaTek MT5879 with a **32-bit CPU (armeabi-v7a)**,
**Mali-G52** GPU, 2.4 GB RAM. 4K panel; the UI is rendered at 1920×1080 and scaled up.
Measured with [`tools/perf`](../tools/perf) (`measure.ps1` + `frames.py`, which read `dumpsys gfxinfo framestats`).

## Results (2026-11-14)

| Metric | Before (debug build) | After (release build) |
|---|---|---|
| Cold start (`am start -W`) | 4.2–4.8 s | **0.59–0.68 s** |
| Home, moving between cards — UI thread | 105 ms / frame | **10.7 ms** |
| Home, moving between cards — GPU | ~54 ms* | **25.5 ms** |
| Home, moving between cards — frame interval (median) | ~105 ms (≈ 9 fps) | **17.9 ms (≈ 55 fps)** |
| Home, idle — GPU | 32 ms* | **16.2 ms** (aurora at ~15 updates/s) |
| Home, idle — GPU, any of the six themes (debug build, v0.7) | — | 12.9 ms in each; *Graphite* draws no frames while idle |
| Home, idle — GPU, *Sakura* + Glass look (debug build) | — | 14.2 ms (particles stamped at full size, translucent cards) |
| Web page, moving the pointer / scrolling | — | 16.7 ms (60 fps) |
| Fullscreen video, held-arrow seek | one seek per key repeat | **one seek** after the key is released; GPU 2.5 ms |

\* measured on the first release build, before the GPU-layer reductions.

### Check of v0.10.0 (2026-11-18, same TV, release build)

| Metric | Result |
|---|---|
| Cold start, right after `adb install` (ART status `verify`, nothing compiled yet) | 1.13–1.15 s |
| Cold start after compiling with the shipped profiles (`cmd package compile -m speed-profile -f`) | **0.71–0.73 s** |
| Home, moving between cards: UI thread / GPU / frame-to-frame (median) | 14.8 ms / 28.1 ms / 27.5 ms (≈ 36 fps) |
| Home, idle: GPU | 16.1 ms (unchanged) |

A freshly sideloaded APK starts slower until Android's background optimisation (TV idle, usually overnight) compiles it
with the baseline profiles that `profileinstaller` installs on first start; that is the platform, not Luna's code. The
Home code did not change in 0.10.0; the card-moving numbers are higher than in the first table, measured with a
different theme, Home content and card sizes, so compare runs with the same settings. An app-level
`baseline-prof.txt` with wildcard rules was tried and did not change the shipped profile, so it is not used.
`tools/perf/frames.js` gives the same summary as `frames.py` for machines without Python (`node frames.js file.txt`).

## What was found and fixed

| Finding | Fix |
|---|---|
| The TV was running a *debug* build (no R8, no ahead-of-time compilation) | Use the **release** build day to day; `profileinstaller` ships the Compose / AndroidX baseline profiles |
| A fixed 900 ms in-app splash on top of the system splash | Removed; the Android 12+ system splash shows Luna's logo, home fades in |
| Every focus move recomposed the whole home screen; `File.exists()` during composition | `hero` is read only where needed; rows are memoised on a `Store.version` counter; artwork / host / favicon lookups are computed once |
| History (300-entry JSON) written on the UI thread | Written on a background single-thread executor |
| Compiling ~20,000 filter rules competed with the first frames | Background-priority thread; list download deferred 20 s after start |
| Aurora: three full-screen gradient layers redrawn at 60 Hz | Painted by the CPU into a 160×90 bitmap and stretched; ~15 updates/s; **pauses while the viewer is pressing keys** |
| The GPU can only afford ~5 full-screen blended layers per frame | Window and root backgrounds removed; backdrop image alpha applied at draw time (no offscreen buffer); the two scrims merged into one small bitmap; Settings dimming baked into the aurora |
| `Crossfade` renders each child into a full-screen offscreen buffer | `LightCrossfade` using `CompositingStrategy.ModulateAlpha` |
| History composed 300 rows at once | `LazyColumn` |
| Seeking: each key repeat reloaded the stream | Presses and repeats accumulate, the target is previewed, **one** seek is sent when the key is released; holding accelerates 10 s → 30 s → 60 s |
| First page load froze Home while Chromium initialised | The WebView provider is pre-loaded on a background thread shortly after start |

## Filter lists and memory

With EasyList (≈ 78,000 rules) on top of the other lists, the app's memory grows from ≈ 171 MB to ≈ 236 MB
(PSS) on the test TV; pointer movement and scrolling on a heavy news page stay at 60 fps. Lists are compiled on a
background-priority thread, and downloads wait 20 s after start. EasyList is therefore on by default for every
language except Vietnamese (which uses ABPVN) and can be switched off in Settings.

## Rules for new UI

- **Count full-screen layers.** Each translucent full-screen layer costs ~5 ms of GPU on this TV. Avoid
  `Modifier.alpha`, `Crossfade` and `graphicsLayer { alpha }` over large areas — pass `alpha` to the draw call or
  use `CompositingStrategy.ModulateAlpha`.
- **No I/O while composing** (file access, JSON parsing, `File.exists`).
- **Read state as low as possible** — pass `() -> T` instead of `T` so only the part that needs it recomposes.
- **Ambient animation must stop when the viewer is interacting.**
- Measure before and after with `tools/perf/measure.ps1` on a **release** build.

## Debug vs release

- **Release** — daily use (fast, not debuggable).
- **Debug** — needed for `tools/sitecheck` (DevTools, the filter log). Both are signed with the same key, so you
  can install one over the other without losing data.
