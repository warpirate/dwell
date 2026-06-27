# Claude Code Kickstart — Phase 0

How to use this: create your project folder, drop all the docs (01–08) into a `/docs` folder inside it, copy `08-CLAUDE.md` to the repo root as `CLAUDE.md`, then open Claude Code in that folder and paste the prompt below.

---

## Setup before you paste

1. Make a project folder, e.g. `dwell/`.
2. Put `01-PRD.md` … `07-Launch-Readiness.md` in `dwell/docs/`.
3. Copy `08-CLAUDE.md` to `dwell/CLAUDE.md` (root, so Claude Code reads it every session).
4. Open Claude Code in `dwell/`.
5. Paste the prompt.

---

## The prompt

```
Read CLAUDE.md and docs/02-TRD.md and docs/03-UIUX-Design.md before doing anything. They define the stack, architecture, and design language. Follow them exactly. Do not introduce libraries or patterns not listed in CLAUDE.md without flagging it first.

We are doing Phase 0 from docs/06-Implementation-Plan.md only. Do NOT build wallpapers, widgets, accounts, or the launcher yet. The goal of Phase 0 is a running, empty, themed app shell wired to Firebase.

Scaffold a new Android app with:

- Kotlin, Jetpack Compose, Material 3
- Min SDK 26, target SDK 36, compile SDK 36
- Single Gradle module, MVVM, Hilt for DI, Coroutines + Flow
- Version catalog (libs.versions.toml) for dependencies
- Package name: ask me for it, or use com.<yourhandle>.dwell and tell me to change it

Implement the design tokens as a Compose theme, both light and dark, using these exact values from the UI/UX doc:
- Light: bg #FAFAF8, surface #FFFFFF, text-primary #1A1A18, text-secondary #6B6B66, divider #EAEAE5
- Dark: bg #0E0E0D, surface #1A1A18, text-primary #F5F5F2, text-secondary #9A9A94, divider #2A2A27
- Accent (shared): #3A5A40
- Type: Fraunces for display/titles (sparing), a clean sans (Inter or system) for body/UI. Sentence case. 8dp corner radius.
- Theme follows system, with the structure to allow a manual override later.

Build a bottom navigation shell with three empty destinations: Wallpapers (default), Widgets, More. Use the navigation pattern and labels from the UI/UX doc. Each destination is just a placeholder screen for now showing its name. Accent appears only on the selected nav item.

Add Firebase, but do only the parts you can do in code:
- Add the Firebase BoM, the dependencies for Auth, Firestore, Storage, Analytics, Crashlytics, and Messaging, and the google-services + crashlytics Gradle plugins.
- Wire the google-services plugin so the app reads google-services.json from the app module.
- Do NOT invent a google-services.json. Instead, give me a short, exact checklist of the manual steps I must do in the Firebase console: create the project, register the Android app with the package name, enable Auth (email + Google) / Firestore / Storage / Analytics / Crashlytics / Messaging, download google-services.json, and where to place it. The app should compile once I drop that file in.

Quality floor from the start: 48dp touch targets, visible focus, content descriptions on icon-only controls, WCAG AA contrast on both themes.

When done:
- The app launches to an empty themed Wallpapers tab.
- Bottom nav switches between the three placeholder screens.
- Both light and dark themes render correctly.
- Tell me exactly what to run to verify it, and give me the manual Firebase checklist.

Work in small steps. After scaffolding, stop and show me the structure before going further. Do not start Phase 1.
```

---

## After Phase 0

Once the shell runs and Firebase connects, the next prompt is Phase 1 (wallpapers): Firestore catalog read, category chips, the staggered grid, and the full-bleed apply sheet. Feed Claude Code one phase at a time, each ending in something runnable, exactly as the implementation plan describes.

One thing to settle before Phase 1's content work and definitely before Phase 7: which AI tool generates the wallpapers, and confirm its license allows commercial distribution. That decision shapes the admin upload pipeline and the Play AI-content disclosure.
