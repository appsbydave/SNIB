# Snib — Design Brief v2

*Physical-key app blocking for Android. Revised after scope decisions.*

**Changes from v1:** household token model defined; recovery designed in; Tier 2 (Device Owner) removed entirely; schedules and website blocking confirmed out of scope.

---

## 1. What this is

A phone is snibbed by tapping it against a physical NFC token. While snibbed, the apps on your blocklist will not open. The only way to unsnib is to tap a token again.

**Confirmed product thesis:** the existing Android blockers fail because there is nothing to touch. Curbox, DigiPaws and StayFree are all administration surfaces — dashboards, rule builders, streak counters — and the off switch always lives on the same screen as the block. Snib's entire differentiator is that the key is an object in the room.

This has a design consequence, and it should be applied ruthlessly: **every feature that can be operated on-screen weakens the product.** Test each request against it. Most fail.

---

## 2. Name and identity

### Name: Snib

Scots for the catch on a door. "Put the snib on."

Verb-able (snib / snibbed / unsnib), arbitrary and therefore legally strong, and the metaphor is load-bearing rather than decorative. Unfamiliar outside Scotland and northern England, which is an acceptable cost — nobody knew what a Brick was either.

**Alternates if it doesn't clear:** Bothy (strong register, crowded with outdoor brands), Cairn (pretty, several existing companies), Kist (obscure, weak recall).

**Do first:** UK IPO search in **Class 9** and **Class 42**, EUIPO check, Companies House, and domain availability for snib.app. Don't design toward a name you can't own. Avoid anything Brick-adjacent in name, mark, or comparative marketing.

### Voice

Plain, dry, understated. Never motivational, never gamified.

| Don't write | Write |
|---|---|
| "Great job! 2h 14m focused! 🎉" | "Snibbed for 2h 14m." |
| "Are you sure?" | "This uses one of three. Two left until Monday." |
| "Oops! Something went wrong." | "The token didn't read. Hold the phone flat against it." |

Errors explain and instruct. They don't apologise and they're never vague.

---

## 3. Visual identity

### The mark

The mark is a snib — a bar turning on a pin, set in a plate. Its signature property: **the mark's state is the app's state.**

- Bar **vertical** = snibbed
- Bar **horizontal** = open

Same geometry, rotated 90°. One idea doing four jobs: app icon, home-screen widget, live state indicator, and the tap animation (a single 240ms rotation).

`snib-mark.svg` is the snibbed state; the open state rotates the `#bar` group 90° about (32,32).

### Palette

Drawn from Scottish door ironmongery — brass fittings, slate, harl render. Deliberately avoids the cream-and-terracotta and black-and-acid-green pairings that every AI-assisted app design currently lands on.

| Token | Hex | Use |
|---|---|---|
| `slate` | `#2A2E33` | Primary ground. Cool dark, not black |
| `slate-lift` | `#383D44` | Cards, raised surfaces |
| `brass` | `#B08A4A` | The mark, snibbed state, primary action |
| `harl` | `#DCDAD3` | Body text on slate |
| `verdigris` | `#3E7C74` | Open state, confirmations |
| `rust` | `#A63D2F` | Emergency breaks and recovery only |

Dark by default. This is used at 11pm to stop you opening Instagram; a white screen is the wrong instrument.

### Type

- **Display:** condensed grotesque with weight — *Archivo Condensed* or *Oswald*. Session timer, screen titles, the word SNIBBED. Never body copy.
- **Body/UI:** *Inter*. Boring deliberately. The app picker is the one dense screen and needs legibility, not character.
- **Numerals:** tabular figures, so the timer doesn't jitter.

Scale: 44 / 28 / 20 / 16 / 13. Five sizes, no more.

---

## 4. Enforcement

**One tier. Hardened accessibility. No Device Owner, ever.**

AccessibilityService detects app launches and presents a full-screen block. A foreground service keeps the session alive. Device Admin registration is offered as optional uninstall friction. Self-defence watches for navigation toward the off switch and bounces the user back.

**Stated limit, in the brief and in the marketing:** a determined technical user gets past this in about ninety seconds via Settings → Accessibility, or instantly via ADB. Brick operates under exactly the same ceiling. The job is defeating the reflex, not defeating you-with-a-laptop.

Two consequences of dropping Tier 2, both good:

- No ADB provisioning, no account stripping, no factory-reset removal path, no second phone needed. You can develop this on your Pixel.
- Play Store distribution stays viable. Play prohibits apps that prevent users disabling or uninstalling software outside parental-control and enterprise contexts; Tier 1 sits inside that line as long as the copy never claims otherwise. Say "hard to undo", never "impossible".

### Bypass-hardening checklist

Each needs a test case. You will not close all of them; close the top six and be honest about the rest.

- [ ] Freeform / mini-window launch *(a documented real-world Brick failure)*
- [ ] Split-screen launch
- [ ] Notification deep-link
- [ ] Home-screen widget
- [ ] App pairs and launcher shortcuts
- [ ] Recents-screen resume
- [ ] Force-stop mid-session
- [ ] Accessibility service toggled off
- [ ] Device Admin revoked
- [ ] App data cleared
- [ ] Safe mode boot

---

## 5. The token model

### Household scoping

Tokens are **not** paired to a single phone, and they are **not** universal across all customers. They belong to a household.

**Why not universal:** a fixed shared payload can be written to a 30p NTAG sticker by anyone with NFC Tools in under a minute. That's not friction. Universal tokens would mean the product's only real defence is that the user hasn't thought of it yet.

**How it works:**

1. **First setup.** Snib generates a random household secret and writes a derived token payload to your first tag. The secret is stored locally on the phone. No account, no email, no server.
2. **Adding a phone.** Soph installs Snib, taps any of your existing tokens, and the app offers: *"Join this household? Your tokens will work on this phone too."* One tap. Done.
3. **Adding a token.** From an unsnibbed state, hold a blank tag against the phone. It's written into the same household.

Result: your tokens work on her phone, hers work on yours, and someone else's Snib token does nothing on either.

**Honest limit:** because you hold your own household secret, you can write yourself a spare token whenever you like. That's fine and intentional — the barrier is against the reflex, and a person who sits down to write a duplicate tag has already broken the reflex. Don't try to engineer around this; it costs complexity and buys nothing.

**Commercial upside:** tokens ship blank and are paired by the customer, so there is no per-unit provisioning and no serial tracking. Manufacturing is just "make a nice object with an NTAG in it."

### Technical detail

- Payload: an NDEF text record containing the household ID plus an HMAC derived from the household secret. Verify the HMAC on read; don't trust the tag UID, which is trivially cloned.
- Don't set lock bytes during development. Lock before shipping tokens to customers.
- Household secret lives in Android Keystore, backed up nowhere.
- Losing every phone in a household means the tokens are orphaned and must be rewritten. Acceptable; document it.

---

## 6. Recovery

You flagged this as crucial. Four layers, in the order a person will reach for them.

**Layer 1 — another household token.** Usually the answer. Soph's is on her keys. This is the main reason household scoping is worth building.

**Layer 2 — emergency breaks.** Three per calendar week, five minutes each, resetting Monday 00:00.

Friction, in order: buried as small text at the foot of the home screen, not a button; five-second press-and-hold with a `rust` progress ring that cancels if released early; confirmation stating the cost in plain numbers; a `rust` banner and countdown notification throughout, so the break feels slightly wrong; silent re-snib at expiry with no thirty-second warning.

Never sell extra breaks, never trade an ad for one, never allow an extension. The scarcity is the product.

**Layer 3 — recovery codes.** Eight single-use codes generated at setup and shown exactly once. The setup flow will not continue until the user ticks "I've written these down", and offers a print/share action. Each code fully unsnibs. Codes can only be regenerated while unsnibbed.

This is the wallet-card answer to "token's at home, I'm in Glasgow, I need my banking app." It works offline, needs no server, and can't be reached from inside a snibbed phone by accident.

**Layer 4 — stranded unlock.** If a session has run past 24 hours with no token seen, the block screen offers a way out that requires the screen to stay on and untouched for ten minutes. Long enough that nobody uses it out of impatience, reliable enough that the phone can never permanently trap you.

*Ship layers 1–3 in v1. Layer 4 is v1.1 unless dogfooding proves you need it sooner.*

**Non-negotiable, at every layer:** the dialler, emergency calling, and Settings are never blockable. They appear in a greyed "Always allowed" list so users can see they're safe rather than assume they're missing.

---

## 7. Core flows

### Setup

1. **Welcome.** One screen, one sentence, one button. No account, no carousel.
2. **Pick your apps.**
3. **Permissions,** one at a time with a plain reason each: Accessibility → Overlay → Notifications → Device Admin (optional, framed as "make it harder to undo"). Never four requests at once.
4. **Write your first token,** or join an existing household by tapping one.
5. **Recovery codes.** Shown once, confirmation required.
6. **Test snib.** A forced five-second demo so the user sees the block screen before trusting it. This is where trust is built — don't cut it.

### The blocklist

A scrolling checkbox list: icon, name, checkbox. Search at the top. No categories, no presets, no per-app stats.

- **Protected and unblockable:** dialler, Messages, Settings, Snib, emergency calling.
- **Sort** by most-used on first load (UsageStatsManager), alphabetical after. The apps you want blocked are the ones you use most.
- **Editable only while unsnibbed.** State this explicitly or someone will build an edit path that becomes the bypass.

### Snibbing and unsnibbing

Tap the token. That's the flow, both directions.

Screen on and unlocked (confirmed working on your Pixel). NDEF intent filter when backgrounded, foreground dispatch when open. Feedback: sharp haptic, mark rotates, timer starts or stops. No dialog, no "are you sure" — the physical act is the confirmation.

Unknown token → "That's not your token." Never offer to pair a tag mid-session.

### While snibbed

- **Home:** the mark, elapsed time in display type, remaining breaks in small text. Nothing else. No charts, no counters.
- **Notification:** low-priority ongoing, "Snibbed — 1h 12m".
- **Block screen:** full slate, mark vertical, "Instagram is snibbed." Below in small harl: "Tap your token to open up." One dismiss returning home. No countdown, and **no count of failed attempts** — counting turns a moment of failure into a score.

---

## 8. Persistence

Reboot into a still-snibbed state.

- Session state written synchronously to Room on snib. Never memory-only.
- `BOOT_COMPLETED` receiver restarts the foreground service and re-reads state.
- AccessibilityService is re-enabled by the system on boot unless revoked — verify on your Pixel, as battery managers have historically interfered.
- There is a short window between boot and service start where blocked apps will open. Keep it small and accept it.
- Request battery optimisation exemption during setup, explained as "so a snib survives a restart".

---

## 9. Architecture

Kotlin, Jetpack Compose, Material 3, minSdk 26, target current.

```
com.snib.app/
├── MainActivity.kt              NFC intent handling, singleTop
├── data/                        Room — sessions, blocklist, breaks, codes
├── household/
│   ├── HouseholdKey.kt          Keystore-backed secret
│   └── TokenCodec.kt            NDEF write/verify, HMAC
├── service/
│   ├── BlockingService.kt       AccessibilityService
│   ├── SessionService.kt        Foreground service + notification
│   └── BootReceiver.kt
├── block/
│   ├── BlockOverlay.kt
│   └── SelfDefence.kt           Settings-navigation watcher
├── recovery/                    Breaks, codes, stranded unlock
└── ui/                          Screens + theme tokens
```

**Blocking:** listen for `TYPE_WINDOW_STATE_CHANGED`, match package against blocklist, present the overlay. Prefer the overlay to `GLOBAL_ACTION_HOME` — being silently bounced home is confusing, and the block screen is where the product's voice lives.

**Read before writing:** Curbox (GPLv3) for accessibility patterns and its handling of freeform windows, DigiPaws for anti-uninstall. Both on F-Droid with source. Borrow ideas freely; note that GPLv3 is viral if you borrow code, and Snib is commercial.

---

## 10. Distribution

Google Play, single tier.

- Accessibility declaration in Play Console. **Do not** set `isAccessibilityTool=true` — Snib is not an assistive tool and claiming so is the fastest route to removal.
- Prominent in-app disclosure with affirmative consent before the permission request.
- Demo video for review showing the disclosure.
- Copy must never claim the app can't be uninstalled or disabled.

**Model:** free app, paid token. Brick is $59, Unpluq around £26 plus subscription. A well-made token at £15–20 with no subscription is defensible, and "no subscription" is a genuine differentiator. Blank NTAGs cost pennies, so all the value is in the object — it needs to be worth picking up. Worth a conversation with a Scottish maker about brass or slate.

---

## 11. Build sequence

| Stage | Goal | Effort |
|---|---|---|
| **0.1** | Kotlin/Compose hello-world on your Pixel. Prove the toolchain. | 1 evening |
| **0.2** | Read a tag, write a payload, read it back. | 1 evening |
| **0.3** | AccessibilityService logging every app launch. No blocking. | 1–2 evenings |
| **0.4** | Blocklist in Room + working block overlay. **First usable build.** | 1 weekend |
| **0.5** | NFC drives session start/stop. Foreground service. Boot persistence. | 1 weekend |
| **0.6** | **Dogfood two weeks.** Log every bypass and every moment you wanted to quit. | 2 weeks |
| **0.7** | Household pairing, recovery codes, breaks, hardening checklist. | 1–2 weekends |
| **0.8** | Identity applied. Setup flow. Permission choreography. | 1 weekend |
| **1.0** | Play listing, accessibility declaration, review video, store assets. | 1 weekend + review |

Protect stage 0.6. Two weeks of honest self-use will teach you more than any spec.

---

## 12. Remaining decisions

1. **Token object.** Blank NTAG for now. Before launch: what is it made of, who makes it, at what unit cost and volume? This is the thing customers actually pay for.
2. **Multiple tokens per household** falls out of the design for free — one by the desk, one by the bed. Confirm you want it exposed in the UI or left implicit.
3. **Stranded unlock (layer 4)** — build in v1 or wait for dogfooding to prove the need?
4. **iOS.** Out of scope, but worth knowing the ceiling: iOS can't intercept app launches the way Android can, so any future iOS version would be a materially weaker product built on Screen Time.

---

*Next: clear the name at UK IPO, then build to 0.4.*
