# Dwell Design System

**Status:** Locked v1 (from the design research pass).
**Audience:** anyone (human or agent) building any Dwell screen. Build to this. If a screen doesn't use these tokens/components, it's wrong.

---

## North-star invariant

On any screen, **the wallpaper is the single most visually complex and saturated element. Every piece of chrome is deliberately quieter than it.** Any component that out-shouts the wallpaper is wrong.

## Principles

1. **Accent is a mark, never a fill.** Deep green is exactly ONE solid fill per screen (the single primary action). Otherwise it's only a 1–2dp underline/border, a dot, or an icon tint for selected/active state. No green backgrounds on chips, selectors, bars, cards.
2. **Warm, never cold.** Near-blacks stay brown (`#12100E`), off-whites stay cream (`#ECE7DD` / `#FAFAF8`), neutrals are warm-gray. No pure `#000` scrims, no pure `#FFF` surfaces, no Material You dynamic color.
3. **Fraunces is rare and large.** Display serif appears at most once per viewport, only at ≥24sp (screen title, wallpaper name, or auth hero). Never on buttons, chips, or repeated rows.
4. **One 8dp radius everywhere.** Exactly one carve-out: bottom-sheet top corners at 24dp (`DwellSheetShape`).
5. **Depth = one soft warm shadow** over neutral surfaces + the warm radial ground. Not borders, not stacked shadows, never cast onto imagery. Over photos, depth comes only from the eased warm scrim.
6. **Text over imagery is always protected** by a long, eased, warm vertical scrim (transparent → warm near-black). Must clear WCAG AA (4.5:1 body, 3:1 large) against the darkest scrim value.
7. **Lead with value, defer the form.** Show product + one-tap options first; reveal fields only on opt-in. No login wall — Skip is always visible.
8. **States stay Dwell.** Warm ground, structure-preserving skeletons, dominant-color image placeholders. The only spinner allowed is inside the primary button.
9. **Motion is restrained.** <600ms, low-bounce spring, small fades + 8–12dp translates, always reduced-motion aware.

---

## Tokens

### Color (in `ui/theme/Color.kt`, wired to M3 ColorScheme)
| Token | Dark | Light | Role |
|---|---|---|---|
| Bg | `#12100E` | `#FAFAF8` | scaffold ground |
| Surface | `#221F1A` | `#FFFFFF` | cards, sheets, raised chrome |
| TextPrimary | `#ECE7DD` | `#1A1A18` | onSurface / onBackground |
| TextSecondary | `#C7C0B1` | `#6B6B66` | onSurfaceVariant, micro-labels |
| Divider | `#3E3A31` | `#EAEAE5` | hairline dividers only (outline) |
| Accent (fg) | `#6E9576` | `#3A5A40` | primary green for glyphs/marks/text (AA-safe on dark) |
| **AccentFill** | `#3A5A40` | `#3A5A40` | the one solid green button fill, identical both themes; `onAccentFill` = `#FAFAF8` |
| **DwellShadow** | `#1C1810` | `#1C1810` | warm ambient+spot shadow color |
| **ScrimWarm** | `#0E0B07` | `#0E0B07` | dark end of every scrim; never pure `#000` |

Do NOT revert dark accent text to `#3A5A40` (fails AA ~2.25:1). Keep `#6E9576` for foreground green on dark; `AccentFill #3A5A40` is only for the green button fill (white text passes on it).

### Type (`ui/theme/Type.kt`) — Fraunces display + system sans body
- `displayLarge` Fraunces SemiBold 28/36 — screen titles (one per viewport)
- **`displayMedium`** Fraunces SemiBold 40/46 — hero/auth headline only
- `titleLarge` Fraunces Medium 20/28 — wallpaper name, section headers
- `bodyLarge`/`bodyMedium` sans 15/22 — body, sublines, rows
- `labelLarge`/`labelMedium` sans Medium 13/18 — buttons, chips
- `labelSmall` sans 11/16 — captions, fine print
- **`SectionLabel`** composable — sans Medium 11sp, letterSpacing 1.5sp, uppercased, onSurfaceVariant — the only home of the tracked-caps eyebrow

### Spacing (`ui/theme/Spacing.kt`) — 8dp grid
`xs=4 · sm=8 · md=12 · lg=16 · xl=20 · xxl=24 · section=32` and `screenGutter=22dp` (page horizontal padding). Replace all raw dp literals with these.

### Radius (`ui/theme/Shape.kt`)
`DwellCorner = RoundedCornerShape(8.dp)` mapped to every M3 shape slot — use `MaterialTheme.shapes.small`, not literal `RoundedCornerShape(...)`. Only sanctioned second radius: `DwellSheetShape = RoundedCornerShape(topStart=24, topEnd=24)` for bottom sheets.

### Elevation (`ui/theme/Elevation.kt`)
`Modifier.dwellSoftShadow()` — the only elevation. Spec `0 30px 70px -24px rgba(28,24,16,0.34)` → `shadow(30.dp, shape, ambientColor=DwellShadow, spotColor=DwellShadow, clip=false)`. **API caveat:** spot/ambient color honored only on API 28+; on 26/27 fall back to a faint warm draw. Centralized in `DwellCard` so it's fixed once. Only on floating chrome over neutral surfaces — never over imagery.

### Gradient (`ui/theme/Gradients.kt`, `Scrim.kt`)
- `warmRadialBrush(dark, wPx, hPx)` — top-left light source ground (dark `#2C2822→#1B1815→#100E0C`, light `#F7F1E6→#EFE1CD→#E3CBAE`). Default ground of every neutral screen via `DwellScaffold`.
- `warmScrimVertical()` — long eased warm scrim for text-over-image. Multi-stop `0f Transparent, 0.4f ~0x33, 0.7f ~0x99, 1f 0xCC0E0B07` (midpoint ~30% from dark end, no visible band).
- `topScrim()` — shorter warm top-down scrim for a back button over imagery.

### Motion
- `LocalReducedMotion` CompositionLocal, set in MainActivity from `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`.
- `dwellEnterTransition(reduced)` = reduced ? `fadeIn(tween(0))` : `fadeIn(tween(280)) + slideInVertically { it/12 }`.
- Spring for translates: `spring(dampingRatio = 0.8f, low stiffness)`.
- Auth entry: hero/scrim fade ~300ms, then eyebrow→headline→subline→buttons stagger 80–120ms apart. Total <600ms.
- Auth hero: slow Ken Burns 1.0→1.05 over ~20s, infinite reverse.

---

## Component library (`ui/components/`)

Every screen composes from these. Each ships with `@Preview` light + dark.

| Component | Purpose | Signature |
|---|---|---|
| `DwellScaffold` | warm-radial canvas + insets; one-line warm ground | `(modifier, gradient=true, applyStatusBarPadding=true, applyNavBarPadding=true, content: @Composable BoxScope.()->Unit)` |
| `DwellCard` | layered warm surface + centralized soft shadow + 8dp radius (+API26/27 fallback) | `(modifier, shape=shapes.small, content: @Composable ColumnScope.()->Unit)` |
| `DwellPrimaryButton` | the single AccentFill CTA, 54dp, in-button spinner when loading | `(text, onClick, modifier, enabled=true, loading=false)` |
| `DwellSecondaryButton` | ghost/outline (providers, "Continue with email"); never green | `(text, onClick, modifier, leadingIcon: Painter?=null, enabled=true)` |
| `DwellTextField` | standardized field: shapes.small, password show/hide, isError+supportingText, correct IME/autofill | `(value, onValueChange, label, modifier, keyboardType, isPassword=false, imeAction=Next, error: String?=null)` |
| `DwellSegmentedToggle` | single-select; selected = green MARK (underline), not fill | `<T>(options: List<Pair<T,String>>, selected, onSelect, modifier)` |
| `SectionLabel` | tracked-caps micro-label/eyebrow | `(text, modifier, color=onSurfaceVariant)` |
| `DwellDisplayTitle` | Fraunces hero/title via the type scale | `(text, modifier, style=displayLarge)` |
| `WallpaperHero` | full-bleed AsyncImage (crop, dominantColor placeholder, crossfade) + eased warm scrim + content slot; guarantees AA | `(model: Any?, contentDescription, modifier, scrim=true, kenBurns=false, content: @Composable BoxScope.()->Unit)` |
| `FavoriteToggle` | shared heart (green tint only when active; no box) | `(favorite, onToggle, modifier)` |
| `SettingsRow` | shared list row (leading icon, title, optional trailing) | `(title, onClick, modifier, leadingIcon: Painter?=null, trailing: @Composable (()->Unit)?=null)` |
| `DwellSheet` | bottom sheet using DwellSheetShape + soft shadow + drag handle | `(modifier, content: @Composable ColumnScope.()->Unit)` |

---

## Grand auth (the showcase screen)

A full-bleed, slowly drifting Dwell wallpaper IS the auth screen — the product demonstrates itself. No form on entry.

Layout (top → bottom):
1. `WallpaperHero(model = curated catalog wallpaper, kenBurns=true)` edge-to-edge; dominantColor placeholder + crossfade; `warmRadialBrush` fallback before load.
2. `warmScrimVertical()` sinks the bottom third to warm brown-black.
3. Top-right, status-bar-safe: quiet "Skip for now" text button (no login wall).
4. Bottom third, left-aligned, 22dp gutter, generous space above:
   - `SectionLabel` eyebrow: **DWELL**
   - `DwellDisplayTitle(displayMedium)`: value headline — **"A wallpaper worth living with."** (sentence case; sells the feeling, not the task)
   - subline (bodyLarge): "Set it once. Notice it every day."
   - `DwellPrimaryButton` (single green): **Continue with Google**
   - `DwellSecondaryButton` (ghost): **Continue with email** → reveals the form as a second state, not up front
   - footer (labelSmall): "By continuing you agree to our Terms and Privacy."
5. **Second state** (after Continue with email): hero+scrim stay; a `DwellCard` rises with the email/password form. (Email-first auto-detect that retires the Sign in/Create toggle is the target; deferred until a backend email-exists check exists — fetchSignInMethodsForEmail is empty under enumeration protection.)
6. Entry motion: scrim/hero fade ~300ms, then staggered eyebrow→headline→subline→buttons; Ken Burns drift; reduced-motion aware.

Copy bank: Eyebrow `DWELL` · Headline `A wallpaper worth living with.` (alt `Your screen, quietly beautiful.`) · Subline `Set it once. Notice it every day.` · `Continue with Google` · `Continue with email` · final actions `Sign in` / `Create account` · `Skip for now` · contextual pitch (shown in Favorites/More, not here) `Sign in to sync your favorites across devices.`

---

## Rollout (safe, additive-first)

0. Add token files (Spacing, Elevation, Scrim; extend Color/Type/Shape). Additive, zero visual diff.
1. Build `ui/components/` primitives with light+dark `@Preview`. Additive.
2. Wire `LocalReducedMotion` + `dwellEnterTransition`. No animations on yet.
3. Migrate grids (Wallpapers, Favorites) onto `DwellScaffold` + `DwellSpacing` (identical values → zero pixel change).
4. Swap PreviewScreen + MoreScreen to the components (Home/Lock/Both becomes a green underline mark; Apply stays the one green fill; back arrow gets a scrim).
5. Add `FavoriteToggle` as the grid-card overlay; add soft shadow to cards + apply sheet. Audit out stray green fills + stray radii.
6. Rebuild SignInScreen as the grand auth.
7. Move the account pitch into Favorites/More contextual moments; confirm auth is reachable AND skippable. Final a11y pass (48dp targets, contentDescription, scrimmed text).

---

## Open choices (decided defaults in parens)
- Fraunces italic for the auth headline (try it; upright fallback). 
- Magic-link/passkeys (defer to backend readiness; ship email-first later).
- Mono micro-labels for exact mock fidelity (default: system sans + standard tracking, zero font dep).
- Hero wallpaper: one curated still (default) vs cross-fade 2–3 (later).
