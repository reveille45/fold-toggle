# Fold Toggle

Switch a foldable phone between its inner and outer screen **on demand** — for phones
whose hinge sensor no longer works.

When the flex (ribbon) cable to a foldable's hinge/fold sensor comes loose or fails, the
phone can't tell it's been folded: the inner screen stays on and the cover screen never
takes over. Fold Toggle gives you a button for what the hinge used to do.

[Download APK](https://github.com/reveille45/fold-toggle/releases/latest) ·
[Privacy policy](https://reveille45.github.io/fold-toggle/privacy.html)

## Features

- **Always-on notification** — tap it from either screen to switch.
- **Quick Settings tile** — "Outer screen", on/off.
- **Launcher shortcut** — long-press the app icon → *Switch screen*.
- **Automation** — Tasker/Macrodroid etc. can launch `ToggleActivity` (see below).
- **No data collected**, no internet permission, no ads. ~60 KB.

## Device support

Fold Toggle asks the phone which display modes it supports and looks for the
**rear display** mode (inner screen off, outer screen on) — the same mode Android uses
for "rear camera selfie" features. Only phones that expose it to apps can work.

| Device | Status |
|---|---|
| Google Pixel 9 Pro Fold (Android 17) | ✅ Tested |
| Google Pixel 10 Pro Fold (Android 16) | ✅ Tested |
| Google Pixel 11 Pro Fold (Android 17) | ✅ Tested |
| Google Pixel Fold (original, Android 16) | 🟡 Expected to work — same configuration as Pixel 10 Pro Fold |
| Samsung Galaxy Z Fold 4 (Android 16) | ✅ Tested |
| Samsung Galaxy Z Fold 5 / Fold 6 (Android 16) | ✅ Tested (switching back after the app was closed by the system: fix pending verification) |
| Samsung Galaxy Z Flip 6 | ❌ Not supported — Android offers apps no cover-screen mode on flips |
| Other flip phones (Motorola Razr etc.) | ❌ Unlikely, for the same reason |

Tested on real devices via remote device streaming, September 2026. On Samsung the outer
screen is reached through Samsung's own "closed" display mode, so the phone behaves as if
folded.

**Help add your phone:** open the app, scroll to *Device info*, and tap
**Report this device on GitHub** (or copy the info into a
[device report](https://github.com/reveille45/fold-toggle/issues/new?template=device-report.yml)).
Reports from working *and* non-working phones are both useful.

Requires Android 14 or newer.

**"Switch screens?" prompt:** on Pixel, Android itself asks for confirmation each time an
app switches to the outer screen. Tap **Switch screens now**. (If you tap Cancel, nothing
changes.) Switching back to the inner screen doesn't ask.

**Upgrading from 1.0.0–1.0.2:** those versions only worked on phones with a developer
setting that disables Android's hidden-API checks. 1.0.3 works on stock phones.

## How it works

Android 14+ has a system service, `DeviceStateManager`, that tracks a foldable's
posture (closed, half-open, open, rear display…). It's a hidden API — not in the public
SDK — so the app calls it via reflection:

1. **Detect** the rear-display state ID. OEMs number states differently, so it checks, in
   order: the framework's `config_deviceStateRearDisplay` value, a state flagged
   `PROPERTY_FEATURE_REAR_DISPLAY` (Android 15+), and a state whose name contains
   `REAR_DISPLAY`.
2. **Request** it from an invisible activity — the platform only accepts requests from
   the focused, top-most app. (`CLOSED` itself can never be requested by apps.)
   Android blocks apps from building that request directly (`DeviceStateRequest` is a
   hidden API), so Fold Toggle asks the phone's own **WindowManager Extensions** library —
   the supported rear-display API Jetpack uses — to do it. That library defaults to the
   Android 17 "outer default" mode, which leaves the inner screen lit with a "Turn phone
   around" card (meant for rear-camera selfies), so the app points it at the classic
   rear-display state instead: inner screen **off**. On Pixel, Android asks
   "Switch screens?" each time; tap **Switch screens now**.
3. **Hold** it with a foreground service, because a request lives only as long as the
   requesting process.
4. **Release** by re-requesting (to own the request) then cancelling.

Because it relies on hidden APIs, a future Android or OEM update could break it.

### Automation

```
adb shell am start -n com.reveille.foldtoggle/.ToggleActivity                      # toggle
adb shell am start -n com.reveille.foldtoggle/.ToggleActivity --ez wantOuter true  # force outer
adb shell am start -n com.reveille.foldtoggle/.ToggleActivity --ez wantOuter false # back to inner
adb shell am start -n com.reveille.foldtoggle/.ToggleActivity --ei state 3         # manual state ID
adb shell am start -n com.reveille.foldtoggle/.ToggleActivity --ez useExtensions true  # test fallback path
```

`state` overrides detection for the life of the app process — useful for testing a
device where detection picks the wrong state (please file a report if so).

## Building

Open the project in Android Studio (Gradle 9.5, AGP 9.3.1, Java only, no dependencies),
or from the command line:

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew bundleRelease        # app/build/outputs/bundle/release/app-release.aab
```

Lint needs a **JDK 21** runtime (AGP 9.3 lint crashes on JDK 17 with
`NoSuchMethodError: List.removeLast()`); Android Studio's bundled JBR works. Release
steps are in [docs/RELEASE.md](docs/RELEASE.md).

## Support

Fold Toggle is free, ad-free, and always will be. If it saved you a repair bill (or
just some frustration), you can chip in:

- **Cash App:** [$Reveille45](https://cash.app/$Reveille45)
- **Venmo:** [@Charles-Warren-70](https://www.venmo.com/u/Charles-Warren-70)

Device reports help just as much.

## License

[MIT](LICENSE)
