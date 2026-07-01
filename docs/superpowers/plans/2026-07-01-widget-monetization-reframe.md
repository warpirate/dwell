# Widget Monetization Reframe — Implementation Plan

> Execute with superpowers:executing-plans. Steps use checkbox syntax.

**Goal:** Reframe widgets to presets-free / engine-premium and ship an honest paywall.

**Architecture:** A `WidgetPreset` data layer feeds a reworked config picker; a new
`PaywallActivity` sells the style engine + presets at the real Play price. No new widget
rendering, no wallpaper sampling (P0).

**Tech:** Kotlin, Compose, Hilt, Play Billing 7 (existing), DataStore (existing).

---

### Task 1: Presets model
- Create `app/src/main/java/com/dwell/app/data/widget/WidgetPreset.kt`
- Test `app/src/test/java/com/dwell/app/data/widget/WidgetPresetTest.kt`
- [ ] enum EDITORIAL/SAGE/BOLD (free) + GOLD/QUIET/FOREST (premium); `free`/`premium`/`of(style)`
- [ ] test: `free` = 3, `premium` = 3, `of(preset.style) == preset` for all
- [ ] build + test green, commit

### Task 2: Surface the Play price
- Modify `BillingRepository.kt` (+ `BillingRepositoryImpl.kt`)
- [ ] add `suspend fun formattedPrice(): String?` → oneTimePurchaseOfferDetails.formattedPrice
- [ ] impl: connect, query product details, return formatted price (null on failure)
- [ ] build green, commit

### Task 3: Config ViewModel — presets + price
- Modify `WidgetConfigViewModel.kt`; test `WidgetConfigViewModelTest.kt`
- [ ] `selected: StateFlow<WidgetPreset>`, `selectPreset(p)` (sets draft = p.style)
- [ ] `needsUnlock(p): Boolean = !isPremium && !p.free`; `priceLabel: StateFlow<String>` (fallback "₹299")
- [ ] `load()` maps stored style → preset via `of()` (default EDITORIAL)
- [ ] tests: free preset ⇒ no unlock; premium preset while free ⇒ unlock; while premium ⇒ no
- [ ] build + test green, commit

### Task 4: Paywall screen + VM + Activity
- Create `ui/paywall/PaywallScreen.kt`, `PaywallViewModel.kt`, `PaywallActivity.kt`
- [ ] `PaywallViewModel`: `isPremium`, `priceLabel`, `unlock(activity)`
- [ ] `PaywallScreen`: headline, checklist, roadmap line, price CTA, Maybe later
- [ ] `PaywallActivity`: hosts screen; observes `isPremium` → finish() when true
- [ ] register in `AndroidManifest.xml` (exported=false, Theme.Dwell)
- [ ] build green, commit

### Task 5: Rework the config picker screen
- Modify `WidgetConfigScreen.kt`
- [ ] preset grid (free selectable, premium locked+tappable preview)
- [ ] premium engine controls inline for premium users; locked "Fine-tune" row → paywall for free
- [ ] dynamic CTA: free → Add widget (onSave); premium-selected while free → Unlock (open Paywall)
- [ ] wire `WidgetConfigActivity` to launch `PaywallActivity` on unlock intent
- [ ] build green, commit

### Task 6: Verify on device (verify skill)
- [ ] Sage (free green) → Add widget → renders green on home
- [ ] Gold (premium) while free → Unlock everything → Paywall shows price → Maybe later returns

### Task 7: Reconcile docs + price
- Modify `dwell-docs/01-PRD.md`, `02-TRD.md`, `05-Backend-Schema.md`, `08-CLAUDE.md`
- [ ] widget framing → presets-free / engine-premium; record ₹299 placeholder
- [ ] commit
