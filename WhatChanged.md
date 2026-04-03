# What changed

Short log (max three bullets per version). Newest first.

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
