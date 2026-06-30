# Launch Readiness & Compliance

**Product:** Dwell — a calm home screen: wallpapers, widgets, and a minimalist launcher
**Version:** v1.0
**Status:** Draft
**Purpose:** The checklist that keeps a serious indie build from getting rejected at the finish line.

> This doc reflects Google Play policy as of mid-2026. Policies change. Re-verify each item against current Play Console Help before submitting.

---

## 1. Account Setup

- [ ] Standard personal Google Play developer account ($25 one-time).
- [ ] Identity verification completed (may require government ID).
- [ ] Aware that the **12-tester / 14-day closed test** applies to personal accounts created after Nov 13, 2023, before production access.

---

## 2. The Closed Test Gate

This is the step most solo devs underestimate. It is real elapsed time.

- [ ] App uploaded to the **closed testing** track.
- [ ] **12+ opted-in testers** (recruit 15+ as a buffer against opt-outs).
- [ ] Testers on **real devices**, genuinely using the app.
- [ ] **14 continuous days** of opted-in testing completed.
- [ ] Production access requested; questionnaire answered with **specific** feedback received and changes made (vague answers get rejected).

---

## 3. Target API & Build

- [ ] Targets **Android 16 (API 36)** or higher (required for new apps as of Aug 31, 2026).
- [ ] Min SDK 26.
- [ ] Signed with **Play App Signing** (let Google manage the signing key; keep your upload key safe).
- [ ] Release build is an **AAB** (Android App Bundle), not an APK.

---

## 4. Account Deletion (required, you have accounts)

- [ ] **In-app** account deletion (Settings → Delete account).
- [ ] **Public web** account-deletion page (no login required to find it).
- [ ] Both delete the account **and** associated user data (favorites, settings, etc.).
- [ ] Deletion URL referenced in the privacy policy and entered in the Data safety form (same URL in both).

---

## 5. Privacy Policy

- [ ] Hosted on a **public, non-geofenced URL** (a real web page, **not a PDF**).
- [ ] Linked in **Play Console** and **inside the app** (More → Privacy policy).
- [ ] Names the developer/publisher entity that appears on the store listing.
- [ ] Discloses what data is collected, how it's used, who it's shared with, retention, and the deletion mechanism.
- [ ] States that the Play purchase (the one-time unlock) is tied to the Google account and that deleting the app account does not refund it.

---

## 6. Data Safety Form

Declare honestly. Third-party SDKs count.

- [ ] **Firebase Analytics** — analytics, device IDs, app interactions, crash logs.
- [ ] **Crashlytics** — crash logs / diagnostics.
- [ ] **AdMob** — device IDs / advertising data (review AdMob's disclosure guidance).
- [ ] **Firebase Auth** — email, name (if Google sign-in).
- [ ] Encrypted in transit: **Yes** (all network is HTTPS).
- [ ] Users can request deletion: **Yes** (matches the deletion page).
- [ ] Form answers match what the privacy policy and app actually do (mismatches cause rejections).

---

## 7. AI-Generated Content

- [ ] Wallpapers are AI-generated; complete Play's **AI-generated content disclosure** where applicable.
- [ ] Generation tool's license permits **commercial** distribution of the output.
- [ ] No outputs that reproduce trademarked/copyrighted material or real people.
- [ ] A user-facing way to flag/report objectionable AI content if Play's UGC/AI guidance requires it (confirm scope for this app type).

---

## 8. Monetization Compliance

- [ ] The one-time unlock uses **Google Play Billing** (external payment for in-app digital goods is not allowed).
- [ ] Product configured as a one-time (non-consumable) purchase.
- [ ] Purchase verified **server-side** (Cloud Function), not trusted from the client.
- [ ] Restore-purchase path works (sign in on a new device → entitlement returns).

---

## 9. Permissions

- [ ] Only request what's used. No unused permissions in the manifest.
- [ ] Calendar permission requested **contextually** (when the calendar widget is added), not on launch.
- [ ] Notification permission (Android 13+) requested **contextually**.
- [ ] Each runtime permission has a clear in-context reason shown to the user.

---

## 10. Store Listing

- [ ] App name, short + full description.
- [ ] Feature graphic, icon, phone + tablet screenshots.
- [ ] Content rating questionnaire completed.
- [ ] Category and tags set.
- [ ] Support email and (if used) website.
- [ ] Target audience / content settings accurate.

---

## 11. Pre-Submission Self-Check

- [ ] No crashes on a clean install on a real mid-range device.
- [ ] Cold start under ~2s; grid scrolls at 60fps.
- [ ] Works fully logged-out (browse + apply).
- [ ] Login, favorite-sync, purchase, restore, and deletion all verified end to end.
- [ ] Both light and dark themes look right across all screens and states.
- [ ] Offline behavior graceful (cached grid, clear "needs connection" where true).

---

## 12. Items That Block Submission vs Items That Block Production

**Block submission to closed testing:** signed AAB, target API 36, privacy policy URL, basic store listing, content rating, Data safety form.

**Block production access (additionally):** completed 14-day / 12-tester closed test, account-deletion in-app + web, full compliance review, questionnaire.

Knowing the order avoids surprises: you can start the closed test before every store-listing asset is perfect, but you cannot reach production without the test and the deletion paths done.
