# UI/UX Design

**Product:** Dwell — minimalist wallpapers + widgets + optional launcher
**Version:** v1.0
**Status:** Draft
**Companion to:** 01-PRD.md, 02-TRD.md

---

## 1. Design Thesis

The wallpapers are the product. The app's job is to get out of their way.

That single idea drives every choice below. In a wallpaper app, the imagery is the only color that matters. So the app's own chrome stays near-monochrome and lets the thumbnails carry all the saturation. The accent color is not decoration. It earns its place only on the one thing the user is currently acting on: a selected category, a filled favorite, the primary button in a sheet. Everywhere else, restraint.

This is deliberately not the "near-black background with a bright accent everywhere" look. Accent-everywhere fights the wallpapers for attention. We do the opposite: quiet frame, loud content.

---

## 2. Design Tokens

### Color

Two themes. The neutral ramp is warm-gray, not pure gray, so the app reads calm and premium rather than clinical.

**Light**
- `bg` `#FAFAF8` (warm off-white, not pure white)
- `surface` `#FFFFFF`
- `text-primary` `#1A1A18`
- `text-secondary` `#6B6B66`
- `divider` `#EAEAE5`

**Dark**
- `bg` `#0E0E0D` (near-black, warm)
- `surface` `#1A1A18`
- `text-primary` `#F5F5F2`
- `text-secondary` `#9A9A94`
- `divider` `#2A2A27`

**Accent (shared, one value)**
- `accent` `#3A5A40` (deep muted green)

Why this green: it reads calm and considered, it survives on both light and dark backgrounds, and it does not compete with wallpaper imagery the way a bright vermilion or acid green would. It is a frame color, not a hero color. This is the easiest token for you to own, so treat it as a proposal. Swap it for any single restrained hue and nothing else in the system breaks.

Accent is used only for: active/selected states, the filled favorite icon, the primary action in a bottom sheet, and the "applied" confirmation. Never for large fills or backgrounds.

### Typography

One characterful display face, one clean body/UI face. Not the same pairing reached for on every project.

- **Display** (screen titles, category headers): a quiet humanist serif or a distinctive grotesque. Proposal: **Fraunces** at a low optical size for titles, used sparingly. It gives the minimal frame a touch of editorial warmth that pure system fonts lack.
- **Body / UI** (labels, buttons, metadata): **Inter** or the platform default. Neutral, legible, gets out of the way.
- **Mono** (only if a widget or debug surface needs it): system mono.

Type scale, restrained: Display L 28 / Title 20 / Body 15 / Label 13 / Caption 11. Generous line height. Sentence case everywhere, never all-caps shouting.

If you'd rather an all-sans system for a more neutral feel, that's a one-line change. The serif is the one expressive risk in an otherwise quiet system.

### Spacing & shape

- 8pt base grid.
- Generous negative space is the point. Crowding kills the minimal feel.
- Corner radius: a single small radius (8dp) on cards and sheets. Consistent, not mixed.
- Hairline dividers, used sparingly. Whitespace separates more than lines do.

### Signature element

The **apply sheet**. When a user taps a wallpaper, it expands edge-to-edge as a full-bleed preview, and a single quiet sheet rises with just three choices: Home, Lock, Both. No clutter, no stats, one decision. That moment, full image plus one minimal decision, is the thing the app is remembered by. Every other screen stays calm so this one lands.

---

## 3. Navigation

Bottom navigation, three destinations. Minimal labels, minimal icons.

```
┌─────────────────────────────────────┐
│                                     │
│            (screen content)         │
│                                     │
│                                     │
├─────────────────────────────────────┤
│   Wallpapers     Widgets     More   │
└─────────────────────────────────────┘
```

- **Wallpapers** (default landing): categories + grid.
- **Widgets**: the four widgets, each tappable to preview/configure.
- **More**: favorites, settings, theme, account, remove-ads, about, launcher toggle.

The launcher, if shipped, is not a tab. It's an opt-in toggle under More. It is never forced.

---

## 4. Screen Specs

### 4.1 Wallpapers (landing)

- A short row of category chips at top (Nature, Abstract, Dark, etc.). Selected chip uses accent.
- Below, a two-column staggered grid of wallpaper thumbnails. Images fill; no captions, no metadata clutter.
- Scroll is the primary interaction. Pull to refresh.
- Empty/offline: shows cached thumbnails with a quiet "You're offline, showing saved wallpapers" line. Not an error, a state.

### 4.2 Wallpaper preview + apply (the signature moment)

- Tap a thumbnail → full-bleed preview, image edge to edge.
- A minimal bottom sheet: **Set wallpaper** with three segmented choices: Home / Lock / Both. One primary "Apply" action in accent.
- A favorite icon (outline → filled accent on tap). Tapping favorite while logged out triggers the sign-in sheet.
- On apply: brief accent confirmation ("Applied to lock screen"), sheet dismisses. No celebration animation; restraint.

### 4.3 Widgets

- List of the four widgets, each a live preview card.
- Tap → configuration: pick color (a small swatch row) and font, see the preview update live, then "Add to home screen."
- Config copy is plain: "Color", "Font", "Add to home screen." No jargon.

### 4.4 More

- Favorites (prompts sign-in if logged out and none exist).
- Appearance: Light / Dark / System.
- Account: sign in / sign out / **Delete account**.
- Remove ads: shows price, or "Unlocked" if purchased.
- Optional launcher: a toggle with a one-line explanation of what changes.
- Privacy policy link, support, version.

### 4.5 Sign-in (sheet, not a wall)

- Appears only on demand (favorite, purchase, sync). Never on launch.
- Email/password and "Continue with Google."
- Copy frames the value: "Sign in to save your favorites across devices." Not "You must log in."

---

## 5. Light / Dark Mode

Both first-class, full token coverage, follows system by default with a manual override in More. Wallpaper thumbnails are unaffected by theme; only the chrome inverts.

---

## 6. Motion

Minimal and purposeful. Standard Material transitions for navigation. The apply-sheet rise is the one orchestrated moment and should feel smooth and weighted. No decorative animation elsewhere; extra motion would undercut the calm and read as overdesigned.

---

## 7. Quality Floor

- Responsive down to small phones; adaptive on tablets.
- Visible keyboard focus and proper touch target sizes (48dp min).
- Reduced-motion respected.
- Content descriptions on icon-only controls for screen readers.
- Color contrast meets WCAG AA on both themes (the warm-gray ramp is chosen with this in mind).

---

## 8. Copy Principles

Plain verbs, sentence case, no filler. An action keeps its name through the flow: the button says "Apply," the toast says "Applied." Errors say what happened and how to fix it, in the app's voice, never apologizing or going vague. Empty states invite action ("No favorites yet. Tap the heart on any wallpaper.").

---

## 9. Confirmed Decisions (was Open Questions)

Resolved during the Claude Design pass:

- **Accent hue:** deep muted green `#3A5A40`, used only on active states. Locked.
- **Display type:** Fraunces serif for titles, used sparingly, paired with a clean sans for UI. Locked. (Swap to Newsreader later only if it ever reads too characterful in practice.)
- **App icons (launcher):** outline monochrome, so the wallpaper stays the only color. Locked. (Solid monochrome is the fallback if outline ever reads too faint at small sizes.)
- **Ad placement:** a single native ad slot every ~12 items in the wallpaper grid, styled to match cards. No interstitial on the apply moment. Locked.
- **Launcher home style:** v1 ships one style (default: Zen). A home-style picker (Editorial / Zen / Structured) is a P1 fast-follow; the launcher is built so a style is swappable config. See section 10.

---

## 10. Launcher Home Styles

The launcher ships one home style in v1 and is architected so styles are swappable config (see TRD section 6). The three explored directions:

- **Zen (v1 default):** maximum whitespace, fewest elements, calmest. Best fit for the minimalist promise and least likely to tire at high daily frequency. A launcher's job is to disappear; Zen leans into that.
- **Editorial (P1):** leans into Fraunces and asymmetry, most distinctive and "designed."
- **Structured (P1):** grid-disciplined and predictable, closest to a conventional launcher done cleanly.

All three share the same parts: oversized thin Fraunces clock, battery + next-event widgets, monochrome outline app grid, dock, gesture pill. They differ in layout, spacing, and emphasis, which is exactly what the `HomeStyle` config controls.
