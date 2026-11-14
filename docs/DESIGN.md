# Design system

Luna is designed **TV-first, remote-first, dark**, in the spirit of streaming apps — content (especially video)
first, chrome second. The layout is drawn on a **960 × 540 dp** canvas. Most Android TVs report exactly this
(720p, 1080p and 4K panels alike), and the app enforces it: the density is derived from the screen's pixels
(`ui/theme/Scale.kt`), so the UI keeps the same physical size on any TV, whatever density setting its maker chose.

## Principles

1. **Everything works with a D-pad.** No touch-only control, no focus trap; Back always leaves the current layer.
2. **Focus is unmistakable.** Scale, elevation, a bright ring and a lighter fill together — never colour alone.
3. **Readable from ~3 m.** Large type, generous spacing, nothing important in the outer 5 % (overscan).
4. **Calm motion, cheap on the GPU.** Short fades and slides (100–250 ms); no blur; never more than a couple of
   full-screen layers (see [PERFORMANCE.md](PERFORMANCE.md)).
5. **Show, don't list.** Thumbnails and artwork over text rows wherever we have them.

## Tokens (`ui/theme/Theme.kt`)

| Token | Value | Use |
|---|---|---|
| `Bg` | `#0B0D12` | App background |
| `Surface` / `SurfaceHigh` / `SurfaceFocus` | `#151922` / `#1F2530` / `#2E3748` | Cards, search bar, focused fill |
| `Glass` | `#2EFFFFFF` | Controls on top of artwork |
| `Accent` | `#8AA4FF` | Brand, progress, selection |
| `TextPrimary` / `TextSecondary` | `#F3F5F9` / `#A9B1C1` | Text (contrast ≥ 7:1 / 8:1 on `Bg`) |
| `Danger` / `Success` | `#FF8A8A` / `#6EE7A8` | Always paired with text or a ✓ mark |
| Safe area | 48 dp horizontal, 27 dp vertical | ≈ 5 % overscan margin |

Type scale (sp): display 44 · title 28 · body 20 · label 18 · caption 16.

## Focus

Every interactive element is a `FocusSurface` (`ui/components/Tv.kt`): scale 1.06 in 160 ms, elevation shadow,
a 3 dp white ring and a lighter fill. Scroll containers keep inner padding so a scaled item is never clipped.
`rememberFocus` restores the last focused item when you come back to a screen.

## Motion

Fades, slides and scales of 100–250 ms. **Settings → General → Reduce motion** sets every duration to 0 and
freezes the aurora background.

## Components

`FocusSurface`, `TvButton`, `IconLabelButton` (icon + text label — never a bare icon), `MediaCard` (16:9,
artwork or brand gradient), `SiteIcon`, `EmptyState`, `ToastHost`, `Aurora`, `Backdrop`, `LightCrossfade`
(`ui/components/`).

## Remote map

| Key | Page (cursor mode) | Fullscreen video |
|---|---|---|
| D-pad | Move the pointer (accelerates); push at the top/bottom edge to scroll; Up at the very top opens the toolbar | Left / Right seek (hold to accelerate), Up / Down show controls |
| OK | Click | First press shows controls, then play / pause |
| Hold OK | Switch cursor ⇄ link-focus mode | — |
| Menu / Info / Bookmark | Toggle the floating toolbar | Toggle toolbar |
| Red colour key | Switch pointer ⇄ link mode | — |
| Back | Close toolbar → page history → Home → exit | Hide controls → leave fullscreen |
| Hold Back | Open the toolbar | Leave fullscreen and open the toolbar |
| Play/Pause, FF, RW | Control the page's video | Same |
| CH+/CH− or Page Up/Down | Scroll a page | — |

Home, volume and power keys are never intercepted.

## States

Page loading bar · offline / DNS / timeout / TLS error panels (Try again · Change address · Home) · video
buffering and playback-failed overlays · empty History / Favourites · filter-list update failure.

## Screenshots

See the gallery in the [README](../README.md#screenshots). Source images live in `docs/screenshots/`.
