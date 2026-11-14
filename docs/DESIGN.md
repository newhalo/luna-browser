# Luna Browser — Design System

TV-first, remote-first, dark. Designed on a **960 × 540 dp** canvas: Android TV reports this for both 1080p and 4K panels, so one layout is density-correct on both.

## Tokens (`ui/theme/Theme.kt`)
| Token | Value | Use |
|---|---|---|
| Bg | `#0B0D12` | App background |
| Surface / SurfaceHigh / SurfaceFocus | `#151922` / `#1F2530` / `#2E3748` | Cards, search bar, focused fill |
| Accent | `#8AA4FF` | Brand, progress, selection |
| TextPrimary / TextSecondary | `#F3F5F9` / `#A9B1C1` | Text (contrast ≥ 7:1 / 8:1 on Bg) |
| Danger / Success | `#FF8A8A` / `#6EE7A8` | Always paired with text or ✓ |
| Safe area | 48 dp horizontal, 27 dp vertical | 5 % overscan margin |

Type scale (sp): display 44 · title 28 · body 20 · label 18 · caption 16 (≈ 3 m viewing distance).

## Focus
Every interactive element is a `FocusSurface`: scale 1.06 (160 ms), elevation shadow, 3 dp white ring and a lighter fill — focus never depends on colour alone. Lists leave inner padding so a scaled item is never clipped. `rememberFocus` restores the last focused item when returning from another screen.

## Motion
100–250 ms fades/slides/scales only. No blur. Settings → General → *Reduce motion* sets every duration to 0.

## Components (`ui/components/Tv.kt`)
`FocusSurface`, `TvButton`, `IconLabelButton` (icon + text label), `SiteCard`, `LetterTile`, `EmptyState`, `ToastHost`, `MoonLogo`.

## Remote map
| Key | Page (cursor mode) | Fullscreen video |
|---|---|---|
| D-pad | Move pointer (accelerates); push at top/bottom edge to scroll; Up at very top opens toolbar | Left/Right seek, Up/Down show controls |
| OK | Click | 1st press shows controls, then Play/Pause |
| Hold OK | Switch cursor ⇄ focus mode | — |
| Menu | Toggle floating toolbar | Toggle toolbar |
| Back | Close toolbar → history back → Home → exit | Hide controls → leave fullscreen |
| Hold Back | Open toolbar | Leave fullscreen + toolbar |
| Play/Pause, FF, RW | Control the page's video | Same |
| CH+/CH− or Page Up/Down | Scroll a page | — |

Home, volume and power are never intercepted.

## States
Splash · page loading bar · offline / DNS / timeout / SSL error panels (Try again · Change address · Home) · video buffering and playback-failed overlays · empty Favorites / History · filter-list update failure.
