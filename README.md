# Snib

**Physical-key app blocking for Android.** A phone is *snibbed* by tapping it against an NFC token.
While snibbed, the apps on your blocklist won't open. The only way to unsnib is to tap a token.

Kotlin · Jetpack Compose · Material 3 · minSdk 26. No account, no server, nothing leaves the phone.

> Status: pre-release, in dogfooding. The website presents it honestly as "in development".

---

## What changed in this rework

The enforcement engine was rebuilt on the **Digital Detox model** — the approach we know works in a
shipping app with a million installs — rather than trusting a single accessibility event stream.
The teardown behind this is in [`docs/digital-detox-teardown.md`](docs/digital-detox-teardown.md).

**Track the app in front. Re-decide whenever anything changes. Don't trust a single signal.**

- **Foreground is an *activity*, not "whatever window fired last."** Once the block screen is up,
  events keep arriving from the keyboard, the notification shade, the volume panel and heads-up
  notifications. Those are not activities, so they can *raise* a block but never *clear* one. Only
  an activity coming to the front moves the foreground, and the whole decision re-runs on every
  session change and when a break ends. This is the fix for the Chrome bypass.
  — `service/BlockingService.kt`
- **A second, independent enforcer on Usage Access.** `SessionService` watches which app is in
  front via `UsageStatsManager` while snibbed and the screen is on. When accessibility is connected
  it feeds this as a second source (catching missed events); when accessibility is *off* it draws
  the block screen itself. Core blocking no longer depends solely on the accessibility service —
  exactly how Digital Detox survives with accessibility as optional cheat-protection.
  — `service/SessionService.kt`
- **A loud degraded state.** Snibbed but the accessibility service isn't bound → a rust
  "Snibbed, but not blocking" notification that opens Accessibility settings. Driven by the live
  binding (`BlockingService.isConnected`), not the settings string, which can lie.
- **The block screen can't be hidden.** When hosted by the accessibility service it uses
  `TYPE_ACCESSIBILITY_OVERLAY`, which `setHideOverlayWindows(true)` (used by banking apps) cannot
  make invisible. — `block/BlockOverlay.kt`
- **A clock the user can't wind.** Breaks, the weekly allowance and the stranded unlock run on
  `elapsedRealtime()` + `BOOT_COUNT`, so winding the wall clock can't extend a break or mint fresh
  ones. — `recovery/TrustedClock.kt`
- **Launcher & recents are never blockable**, the default browser is flagged in the picker, and the
  foreground-service property uses the name Play actually reads
  (`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`). Debug and release share one application id so they can't be
  installed side by side and mistaken for a cross-process bug.

---

## Build it

```bash
./gradlew assembleDebug        # Pixel plugged in, USB debugging on, then:
./gradlew installDebug
./gradlew testDebugUnitTest     # the JVM unit tests below
```

Requires JDK 17 and the Android SDK (platform 35). The Gradle wrapper is committed, so CI and a
fresh clone need nothing else. You need a blank NTAG213 or better for the token step; a 30p sticker
is fine for development.

Swapping between debug and release needs an uninstall (shared application id, and a signature
change clears the Keystore-backed household secret) — do it before writing any real token.

### Unit tests

Pure-JVM, no device, run on every push:

- `BlockDecisionTest` — the blocklist-vs-allowlist decision that must never invert.
- `TokenCodecTest` — token round-trip, cross-household rejection, tamper rejection.
- `TrustedClockTest` — wall-time reconstruction and the reboot/tamper fallbacks.
- `BreaksTest` — the weekly key and the break-cost wording.

---

## Continuous integration

`.github/workflows/android.yml` runs on every push and PR: unit tests, `lintDebug`, and
`assembleDebug`, uploading the debug APK and lint report as artifacts.

## Releases

`.github/workflows/release.yml` builds an APK and attaches it to a GitHub Release when you push a
`vX.Y.Z` tag (or run it manually) — download it straight onto the phone to install. With no
signing secrets it ships a **debug-signed** APK, which installs and runs fine for dogfooding. For a
**signed release** APK, add four repository secrets (Settings → Secrets and variables → Actions):

| Secret | What it is |
|---|---|
| `SNIB_KEYSTORE_BASE64` | your release keystore, base64-encoded (`base64 -i snib-release.jks`) |
| `SNIB_KEYSTORE_PASSWORD` | the keystore password |
| `SNIB_KEY_ALIAS` | the key alias |
| `SNIB_KEY_PASSWORD` | the key password |

Locally, the same signing is picked up from a gitignored `keystore.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). With no secrets present, the release
build is simply left unsigned and `assembleDebug` still works with nothing configured.

## Website

The marketing site is in [`site/`](site/) — a dependency-free static site, deployed on Vercel with
the project **Root Directory** set to `site`. It presents Snib accurately: in development, open
source, and explicit about the one thing that matters — **hard to undo, never impossible.**

---

## Architecture

```
com.snib.app/
├── MainActivity.kt              NFC intent handling, reader mode, singleTop
├── data/
│   ├── BlockDecision.kt         the pure block/allow rule (unit-tested)
│   ├── SessionRepository.kt     single source of truth; hot path reads volatile fields
│   └── Snib{Dao,Database,Entities}.kt   Room
├── household/                   Keystore secret, HMAC token codec (codec is unit-tested)
├── nfc/                         tag IO and tap policy
├── service/
│   ├── BlockingService.kt       AccessibilityService — foreground-activity enforcer
│   ├── SessionService.kt        foreground service — Usage Access fallback + degraded notice
│   └── BootReceiver.kt          re-snib across reboots
├── block/
│   ├── BlockOverlay.kt          the block window (accessibility-overlay type)
│   └── SelfDefence.kt           bounces navigation toward the off switch
├── recovery/
│   ├── TrustedClock.kt          elapsedRealtime + BOOT_COUNT (unit-tested)
│   ├── Breaks.kt                three a week, five minutes (unit-tested)
│   ├── RecoveryCodes.kt         eight single-use codes
│   └── StrandedUnlock.kt        built, ENABLED = false
└── ui/                          Compose screens + theme
```

---

## Bypass-hardening checklist

Hard to undo, never impossible. The ceiling is stated in the app and the marketing.

- [x] Volume key / notification shade / keyboard while the block screen is up — the Chrome bug,
      fixed by tracking the foreground *activity* and never clearing on a non-activity event
- [x] A break expires while you're inside a blocked app — `BlockingService.recheck()` on expiry
- [x] Clock wound back during a break; date wound forward a week — `TrustedClock`
- [x] Freeform / mini-window / split-screen — window sweep
- [x] Accessibility toggled off — degraded notification **and** Usage Access fallback keeps blocking
- [x] A blocked app that hides overlays — accessibility-overlay window type
- [x] Launcher / recents can't be blocked — added to the protected floor
- [x] Force-stop mid-session — state in Room, service is `START_STICKY`, system restarts the a11y service
- [ ] App data cleared — **open.** Clears the household secret and session together; Device Admin
      makes it an extra step. Not closeable at this tier.
- [ ] Safe-mode boot — **open.** Accessibility services don't run in safe mode. Not closeable by
      anyone at this tier.
- [ ] The gap between boot and `BOOT_COMPLETED` — **open**, a second or two, unavoidable.

### To verify on a real device (from the teardown)

Incoming call while blocked (and the block returns after); recents from the block screen; Play
Store "Open" on a blocked app; a link opening in a Custom Tab; voice-assistant launch; Quick
Settings tiles; Battery Saver while snibbed; reinstall-from-Studio showing the degraded notice.

---

*An [Apps by Dave](https://appsbydave.com) project. Snib — Scots for the catch on a door.*
