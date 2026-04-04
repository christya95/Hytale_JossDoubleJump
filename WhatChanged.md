# What changed

Short log (max three bullets per version). Newest first.

## 0.3.8 (2026-04-04)

- **Defaults:** Shipped JSON + Java fallbacks aligned with **high-reliability** jump-key tuning: `queueJumpEdgeBufferMs` **300**, `postLiftoffJumpSignalIgnoreTicks` **6**, `tapAssistMinWaitingTicks` **2**, `secondPressGraceTicks` **10**, `secondJumpMinNonSmsUpdates` **2**, `secondJumpMinTotalQueueUpdates` **3**; `initialJumpBoostY` default **1.0** in JAR sample + Java.
- **Noise:** Removed **`DoubleJumpTrace`** (hardcoded username) and related per-tick log strings; use **Amore** (`/amoretraceon`, dump commands) for diagnostics. Docs/README/skills updated accordingly.

## 0.3.7 (2026-04-03)

- **Tap assist:** If `tapAssistMinWaitingTicks` &gt; 0 (default **4**), after a release was seen (`sawSignalLowWhileWaiting`) and the player stays in `WAITING_FOR_PRESS` long enough, a second jump can fire **without** a clean `signal` edge when fallback `st.jumping` stays high — avoids missed double-jumps on fast taps. Gated by `!edge` and one consume per airborne period in infinite mode (`tapAssistConsumedThisAirborne`).
- **FSM bugfix:** `requireReleaseForDoubleJump` now uses **`inputStateAtTickStart`** (pre-FSM) with the edge check, not post-FSM `inputState` (which was already `HELD` after the same tick’s transition).
- **Defaults:** `postLiftoffJumpSignalIgnoreTicks` default **10** (was 6); component field **`rawSignalLast`** (renamed from `jumpSignalLast`). Shipped defaults / runtime sample JSON updated; missing keys merge from JAR on load.

## 0.3.6 (2026-04-03)

- **Post-liftoff mask vs edges:** Rising-edge detection for the mod air jump now uses **unmasked** `signal` and stores **`jumpSignalLast = signal`** each tick — the mask only drives `effectiveSignal` for HELD/WAITING FSM, so a held key no longer looks like a new press when the mask ends (single jump works again).
- **Optional:** `requireReleaseForDoubleJump` (default false) — when true, an unmasked edge only counts if `inputState == WAITING_FOR_PRESS` (stricter release-then-press).
- **Docs:** Clarified `postLiftoffJumpSignalIgnoreTicks` as FSM-only; double-jump trigger is edge/queue, not mask expiry.

## 0.3.5 (2026-04-03)

- **FSM frames:** Tighter defaults — `inputDebounceFrames` default **1** (was 2 in Java default), `postLiftoffJumpSignalIgnoreTicks` **6** (was 5; aligns with tuned scale e.g. 19 → 6; migrate old values with `(old + 1) / 3`).
- **Why:** Shorter debounce and liftoff mask improve jump-spam responsiveness while keeping charge/phase logic unchanged.
- **Configs:** `jar-assets/double_jump_defaults.json` and `runtime-config/JossDoubleJumpConfig.json` updated to match.

## 0.3.4 (2026-04-03)

- **queueSmsLive:** After `ProcessPlayerInput`, re-walk the movement queue for the last `SetMovementStates` jump bit so the signal matches post-process state (fixes desync with pre-scan `queueSms`).
- **Config / FSM:** Post-liftoff jump mask fields are on `main` in component + config; default queue edge buffer raised to **220 ms** in shipped defaults.
- **Deploy:** `sync-to-earlyplugins.ps1` copies `JossDoubleJump.jar` to both `earlyplugins/` and `mods/` (Hyxin mixins + JavaPlugin load).

## 0.3.3 (2026-04-03)

- **queueSms signal:** Use `jumpHeldLastQueue` from `QueueScanner` when the queue had SMS (`src=queueSms`), instead of Hyxin `lastQueuedJumping()` (often null in traces).
- **Mixin:** `PlayerInput` tracker is created on every `queue()` call so rising-edge state is not lost when non-SMS updates arrive first.

## 0.3.2 (earlier)

- **Deploy path:** Documented and scripted copying the same JAR into **`mods/`** as well as **`earlyplugins/`** so `joss:JossDoubleJump` enables and `setup()` runs.
