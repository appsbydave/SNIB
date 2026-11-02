# Digital Detox, taken apart — and what it means for Snib's code

*Research note, 1 November 2026. Subject: Urbandroid's **Digital Detox: Focus & Live** (`com.urbandroid.ddc`, 1.6M installs, on Play since 2015) — the app the launcher redesign took its cue from.*

> This is the rationale behind the November 2026 engine rework. The fixes below are now implemented;
> see `README.md` → "What changed in this rework" and the files it points to.

---

## The short version

1. **Digital Detox's core blocking doesn't run on accessibility.** Since v2.7 its whitelist works without the accessibility service. Accessibility is an optional "cheating protection" layer on top. The core is a foreground service plus Usage Access plus draw-over-other-apps: work out which *activity* is in front, and if it isn't allowed, put the detox screen back.
2. **It decides on "which app is in front", not "which window just sent an event".** Snib's August `BlockingService` does the second. That is the most likely cause of the Chrome bug. Once the block screen is up, any event from a window that isn't blocked (keyboard, notification shade, volume panel) takes the block down, and Chrome is still underneath. Repro and fix in §3.
3. **Its ten-year changelog is a free bypass test plan.** Split-screen, Samsung pop-ups, Play Store, Gmail, reboot-before-schedule, clock changes, calls, the power button. §7 turns it into test cases.
4. **Changing the clock is an open hole in Snib.** Breaks and the weekly reset run on the wall clock. Set the time back during a break and it lasts as long as you like. Set the date forward a week and you get three fresh breaks. Digital Detox needed several releases of "time meddling protection". §6.
5. **Two smaller code issues turned up on the way.** The `specialUse` manifest property is misnamed, and the block window's type can be hidden by any app that asks. §5 and §9.

**What this is based on:** the Play listing, release notes back to v2.4, Urbandroid's support pages, published permission lists, and Snib's code from the last commit with source in `appsbydave/SNIB` (`1ab823d`, 5 Aug; the next commit deleted the folder). That's *before* the September allowlist, launcher and `shouldBlock()` work. I didn't decompile Digital Detox. Anything about its internals is inferred from permissions, changelog and docs, and labelled *inferred*. Check each Snib finding against your current files.

---

## 2. The lesson in one line

> **Track the app in front. Re-decide whenever anything changes. Don't trust a single signal.**

Digital Detox gets this from Usage Access, which only reports activities coming to the front, so keyboards, the shade, toasts and dialogs can't confuse it. Accessibility sits on top for the things Usage Access can't see. Snib had it the other way round: everything rode on one accessibility event stream, and the decision was driven by whichever package sent the last event.

---

## 3. The Chrome bug — most likely cause, repro, fix

### What the August code did

```kotlin
// BlockingService.onAccessibilityEvent, Aug 5
val offender = packageName.takeIf { SessionRepository.shouldBlock(it) } ?: sweepWindows()
if (offender != null) overlay.show(labelOf(offender))
else if (overlay.isShowing) overlay.hide()
```

1. Open Chrome. A `TYPE_WINDOW_STATE_CHANGED` from `com.android.chrome` puts the block screen up. Correct.
2. Something else sends a window-state event: the keyboard appears, you pull the shade, you press volume (the block screen deliberately lets volume keys through), a heads-up notification lands. The package is Gboard or `com.android.systemui`, which aren't blocked.
3. So the code falls through to `sweepWindows()`. But the block screen is full-screen, opaque and focused, and Android leaves completely covered windows out of the accessibility window list. Chrome isn't in the list.
4. `offender == null`, so `overlay.hide()` runs. Chrome is in front. Nothing about Chrome has changed, so no new Chrome event arrives, and you're in.

**Second gap, same root cause:** when an emergency break ends, nothing re-decides, so if you're in Instagram when the break expires you can carry on scrolling until you leave.

**Repro (30 seconds):** snib, open Chrome, wait for the block screen, press volume up. If the block screen vanishes, this is the bug.

### The fix (now implemented in `service/BlockingService.kt`)

Keep a `foreground` field that only *activities* can change. Windows that aren't activities may add a block but never clear one. Re-run the decision whenever session state changes and when a break ends. The service also collects `SessionRepository.state` and calls `enforce()` on every emission, and a `<queries>` entry for the HOME intent lets the launcher resolve as an activity.

---

## 4. Make "not enforcing" impossible to miss — and survive it

Digital Detox's core works with accessibility off. Snib's didn't, so Snib now *knows* when it's off.

- **Loud degraded state (implemented).** When snibbed and `!BlockingService.isConnected`, `SessionService` shows a rust "Snibbed, but not blocking" notification whose tap opens Accessibility settings. It uses the live binding, not the `ENABLED_ACCESSIBILITY_SERVICES` string, which can read "on" while the service is dead.
- **Usage Access fallback (implemented).** While snibbed and the screen is on, `SessionService` polls `UsageStatsManager` for the latest `ACTIVITY_RESUMED`. Accessibility up: it feeds that as a second source for `foreground`, catching missed events. Accessibility down: it draws the block window itself (`TYPE_APPLICATION_OVERLAY`) — slower, blind to split-screen, but it still blocks.

---

## 5. Block window type — use the accessibility overlay (implemented)

`TYPE_APPLICATION_OVERLAY` can be hidden by any app that declares `HIDE_OVERLAY_WINDOWS` and calls `setHideOverlayWindows(true)` — the recommended defence on sensitive screens, so expect it in banking apps. `TYPE_ACCESSIBILITY_OVERLAY`, added from inside the accessibility service, isn't a "non-system overlay" and is unaffected, and needs no overlay permission. `BlockOverlay` now picks the accessibility type when its host is the accessibility service, and keeps `TYPE_APPLICATION_OVERLAY` for the session-service fallback.

---

## 6. Time is an attack surface (implemented: `recovery/TrustedClock.kt`)

Durations are measured on `SystemClock.elapsedRealtime()`, which the user can't change and which resets at boot, with `Settings.Global.BOOT_COUNT` to tell whether a stored anchor is from this boot.

- **Breaks:** store `breakStartedElapsed` and `breakBoot`. On a break iff it's the same boot and less than five minutes of elapsed time have passed. A reboot ends a break — fine, it's five minutes.
- **Weekly reset and stranded unlock:** an anchor captured at snib lets us reconstruct a trusted wall clock for the ISO week and the 24-hour stranded check.
- **Honest limit:** wind the clock *and* reboot and the anchor is gone. That takes intent — the same ceiling as everything else.

---

## 7. Test cases from Digital Detox's patch history

To run on the Pixel during dogfooding:

- [ ] Volume key, notification shade or keyboard while the block screen is up — the §3 bug
- [ ] A break expires while you're inside a blocked app — §3 `recheck()`
- [ ] Clock back during a break; date forward a week — §6
- [ ] Incoming call while the block screen is up; the block returns after you hang up
- [ ] Recents from the block screen and from Snib home — keep the recents/launcher package in the protected floor
- [ ] Play Store "Open" button on a blocked app
- [ ] A link from an allowed app opening in a Chrome Custom Tab — decide blocked or allowed deliberately
- [ ] "Hey Google, open Instagram" and Gemini
- [ ] Quick Settings tiles that launch activities
- [ ] Battery Saver and Extreme Battery Saver while snibbed
- [ ] Reinstall from Android Studio: the rust "not blocking" notification appears
- [ ] Samsung pop-up and freeform windows, if you ever test on a Samsung

---

## 9. Small fixes found on the way (implemented)

- **Manifest:** the FGS property is now `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` (the documented name Play reads), not `...FOREGROUND_SERVICE_SUBTYPE`.
- **Allowed-apps picker:** the launcher/recents package is in the protected floor, and a browser is flagged when chosen — *"A browser opens everything you've snibbed, on the web."*

---

## 10. What not to take from Digital Detox

- "Unbreakable" and "no loopholes" copy. Its own changelog says otherwise, and Play's policy line is the brief's: hard to undo, never impossible.
- Paid quits and accountability fees. Snib's scarcity is three breaks, never sold.
- Points, streaks, achievements, leaderboards.
- Its own browser with website blocking. Out of scope by the brief; just know that an allowed browser is the hole.
- Schedules. Out of scope.

---

## Sources

- Digital Detox Play listing, version history (2.4 → 11.42), published permission lists, and Urbandroid support pages.
- Android platform docs: background activity start exemptions, foreground service types, `UsageEvents.Event.ACTIVITY_RESUMED`, `HIDE_OVERLAY_WINDOWS`, `TYPE_ACCESSIBILITY_OVERLAY`, `SystemClock.elapsedRealtime()`, `BOOT_COUNT`.
- Snib `appsbydave/SNIB` at `1ab823d` (5 Aug 2026).
