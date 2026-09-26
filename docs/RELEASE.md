# Release checklist (Play Store + GitHub)

## 0. Version bump (first, every release)

`app/build.gradle.kts`: bump `versionName` and `versionCode`.
Scheme: `versionCode = major*1_000_000 + minor*1_000 + patch` (1.0.0 → 1000000,
1.2.3 → 1002003). Play rejects a code that isn't higher than every track's.

## 1. Build the signed AAB in Android Studio

1. Open the repo root in Android Studio.
2. **Build → Generate Signed App Bundle or APK → Android App Bundle**.
3. First release only: **Create new…** keystore → save as `keystore/upload.jks`
   (the `keystore/` folder is gitignored), alias `upload`. **Back it up** outside the repo
   (password manager + cloud). With Play App Signing this is only the *upload* key and
   Google can reset it if lost, but that takes days.
4. Variant `release` → output `app/release/app-release.aab`.

CLI alternative: copy `keystore.properties.example` → `keystore.properties`, then
`KEYSTORE_PASSWORD=... ./gradlew bundleRelease`.

Sanity checks before upload:
- [ ] AAB is ~20–30 KB. If it's MBs, `android.builtInKotlin=false` got lost from
      `gradle.properties` and AGP is bundling the Kotlin stdlib again.
- [ ] `./gradlew lintRelease` → 0 errors (run under JDK 21 / Android Studio's JBR).
- [ ] Installed the release build on the Pixel and toggled both directions, from the
      notification and the tile, and after a reboot.
- [ ] The old sideloaded build is debug-signed: **uninstall it first** or the Play/
      release install fails with a signature mismatch.

## 2. GitHub release (sideload APK for non-Play users)

```
./gradlew assembleRelease   # needs keystore.properties, or sign in Android Studio
gh release create v1.0.0 app/build/outputs/apk/release/app-release.apk --title "v1.0.0" --notes "..."
```

Sign the GitHub APK with the **same upload key** so sideload users can't mix up
builds; note that Play-installed copies are re-signed by Google, so users switching
between Play and GitHub builds must uninstall first. Say so in the release notes.

## 3. Play Console — first-time setup

Personal accounts created after Nov 2023 need a **closed test with ≥12 testers opted in
for 14 consecutive days, per app**, before production access. Recruit testers with
Samsung Folds; the closed test doubles as device validation.

### App content answers
| Section | Answer |
|---|---|
| Privacy policy | https://reveille45.github.io/fold-toggle/privacy.html |
| Ads | No ads |
| App access | All functionality available without special access |
| Content rating | Utility; no violence/UGC/etc. → Everyone |
| Target audience | 18+ (avoids Families policy scope; nothing child-directed) |
| Data safety | No data collected, no data shared. No internet permission. |
| Government / financial / health | No |

### Foreground service declaration (`specialUse`)
Play asks for a description and usually a short **video** (screen recording is fine).

> Fold Toggle is a workaround utility for foldable phones whose hinge/fold sensor
> has failed, so the device no longer switches to the outer screen when folded. When the
> user taps "switch screen", the app requests the platform's rear-display device state.
> Android ties that request to the requesting process, so a foreground service must keep
> the process alive for as long as the user wants the outer screen; stopping it would
> immediately switch the phone back to the inner screen. The service's persistent
> notification is also the user's one-tap control to switch back. It does no background
> work, holds no wakelocks, and uses no network, location, or sensors.

Video: show the inner screen → tap notification → outer screen turns on → tap
notification on outer screen → back to inner.

### Store listing (draft)
- **Name:** Fold Toggle
- **Short description (≤80):** Switch screens on a foldable with a broken hinge sensor.
- **Full description:**

  > Is your foldable stuck on the inner screen because the hinge sensor or its ribbon
  > cable failed? Fold Toggle gives you a button for what folding used to do.
  >
  > • One-tap switch from an always-on notification — works from either screen
  > • Quick Settings tile and app-icon shortcut
  > • Automation support (Tasker, MacroDroid)
  > • No ads, no data collection, no internet permission — open source (MIT)
  >
  > Supported: Google Pixel 9 Pro Fold (tested). Other Pixel Folds and Samsung Galaxy Z
  > Fold 5 and later are expected to work — the app checks your phone and tells you.
  > Flip phones (Galaxy Z Flip, Motorola Razr) are generally not supported.
  >
  > Fold Toggle uses an Android system feature that isn't officially documented for apps,
  > so an Android update could affect it. Source and device reports:
  > github.com/reveille45/fold-toggle
  >
  > Not affiliated with Google, Samsung, or Motorola.

- **Category:** Tools
- **Graphics needed:** 512×512 icon, 1024×500 feature graphic, ≥2 phone screenshots
  (setup screen + notification shade). Screenshots from the Pixel's inner screen are fine.

### Device targeting
Start restrictive, widen as reports come in: **Release → Device catalog → exclude**
everything except Pixel Fold models and Galaxy Z Fold 5+ (or use "Include only"
rules by device). Avoids 1-star reviews from phones that can't work.
