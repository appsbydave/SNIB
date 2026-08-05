# Snib

Physical-key app blocking for Android. A phone is snibbed by tapping it against an NFC token.
While snibbed, the apps on your blocklist will not open. The only way to unsnib is to tap a token.

Built from `snib-design-brief.md` v2. Kotlin, Jetpack Compose, Material 3, minSdk 26.

---

## Build it

```bash
cd snib
gradle wrapper --gradle-version 8.9   # the wrapper jar isn't checked in
./gradlew installDebug                # Pixel plugged in, USB debugging on
```

Requires JDK 17 and Android SDK 35. Nothing else — no keys, no accounts, no backend.

First run walks the setup flow. You need a blank NTAG213 or better (144 bytes minimum) for the
token step; a 30p sticker is fine for development.

---

## What's here

| Brief section | Where it lives | State |
|---|---|---|
| The mark, animated | `ui/SnibMark.kt`, `res/drawable/ic_snib_mark.xml` | Done |
| Palette, type, five-size scale | `ui/theme/` | Done, display font pending |
| Blocking | `service/BlockingService.kt` | Done |
| Block screen | `block/BlockOverlay.kt`, `ui/BlockScreen.kt` | Done |
| Self-defence | `block/SelfDefence.kt` | Done |
| Session + notification | `service/SessionService.kt` | Done |
| Boot persistence | `service/BootReceiver.kt` | Done |
| Household tokens | `household/`, `nfc/` | Done |
| Layer 1 — another token | `nfc/TokenController.kt` | Done |
| Layer 2 — emergency breaks | `recovery/Breaks.kt`, `ui/RecoveryScreens.kt` | Done |
| Layer 3 — recovery codes | `recovery/RecoveryCodes.kt` | Done |
| Layer 4 — stranded unlock | `recovery/StrandedUnlock.kt` | Written, `ENABLED = false` |
| Setup flow | `ui/setup/SetupFlow.kt` | Done |
| Blocklist | `ui/BlocklistScreen.kt` | Done |

That covers stages 0.2 through 0.8 of the build sequence. Stage 0.6 — two weeks of dogfooding —
is the one nobody else can do for you, and it is the stage that decides what in here is wrong.

---

## Decisions I made where the brief left a choice

**The token carries the household secret.** 93 bytes: magic, version, household id, token id, the
secret, and an HMAC over the header. Verification recomputes the MAC with *our* stored secret, so a
foreign token that is internally consistent still fails. The tag UID is never trusted.

Putting the secret on the tag is what makes the one-tap join work with no account and no server.
It is defensible because possession of the tag already authorises snibbing and unsnibbing every
phone in the household — it is a door key, and door keys can be copied by whoever holds them. It
gives away nothing that possession did not already give away. This is written up in the header of
`TokenCodec.kt` so the next person doesn't quietly "fix" it.

**Recovery codes are reachable while snibbed, from the foot of the home screen.** The brief says
codes must not be reachable *by accident* from inside a snibbed phone, but the Glasgow scenario
only works if they are reachable deliberately. Two quiet lines of small text at the foot of the
snibbed home screen: the emergency break, and "Use a recovery code". Neither is a button.

**The block screen is a real overlay window, not an activity.** An activity enters recents and can
be swiped away mid-session. The window is focusable, which is what lets it swallow the back key.
Compose needs a lifecycle, a saved-state registry and a view-model store to run in a non-activity
window; that plumbing is `BlockOverlay.Host`.

**Multiple tokens per household is exposed in the UI** (decision 12.2). It costs one screen, and
"one by the desk, one by the bed" is the thing that makes layer 1 recovery actually work.

**Stranded unlock is built but off** (decision 12.3). Flipping `StrandedUnlock.ENABLED` is the
whole change. Decide after dogfooding, as the brief says.

**Room queries run on the main thread**, deliberately, and it is commented as such. Two callers are
latency-critical and synchronous by nature: the accessibility callback and the boot receiver. The
database is a few dozen rows, and the blocking hot path reads an in-memory cache anyway.

---

## What is not here, and why

- **The display font.** Oswald and Archivo Condensed are both OFL, but I'm not shipping a binary I
  can't verify. Drop the TTF into `res/font/` and flip `USE_BUNDLED_DISPLAY_FONT` in `Type.kt`.
  Until then the platform condensed sans stands in.
- **The home-screen widget.** The mark is ready for it; the widget itself is a v1.1 job.
- **Play Console assets** — listing copy, accessibility declaration, review video. Stage 1.0.
- **Tests.** There are none yet. The first ones should be `TokenCodec` round-trips and
  cross-household rejection, which are pure JVM and cost an hour.

---

## Bypass-hardening checklist

Close the top six, be honest about the rest.

- [x] Freeform / mini-window launch — window sweep in `BlockingService.sweepWindows()`
- [x] Split-screen launch — same sweep
- [x] Notification deep-link — caught on `TYPE_WINDOW_STATE_CHANGED` like any other launch
- [x] Home-screen widget of a blocked app — the widget's tap resolves to a launch, same path
- [x] Recents-screen resume — same path
- [x] Force-stop mid-session — state is in Room; the accessibility service is restarted by the
      system, and `SessionService` is `START_STICKY`
- [x] Accessibility service toggled off — `SelfDefence` bounces navigation to that page while
      snibbed. Ninety seconds if you mean it. That is the stated ceiling.
- [x] Device Admin revoked — same bounce
- [ ] App data cleared — **open.** Clearing data destroys the household secret and the session
      together, so the phone unsnibs. Device Admin makes it an extra step. Not closeable at Tier 1.
- [ ] Safe mode boot — **open.** Accessibility services don't run in safe mode. Not closeable at
      Tier 1 by anyone, including Brick.
- [ ] The gap between boot and `BOOT_COMPLETED` — **open**, a second or two, unavoidable.

Each of the ticked ones needs a test case on a real device before any of them counts. The copy must
never claim more than this: **hard to undo, never impossible.**

---

## Before you write another line of code

Clear the name. UK IPO in classes 9 and 42, EUIPO, Companies House, `snib.app`. Don't design toward
a name you can't own — and this codebase is now full of it.
