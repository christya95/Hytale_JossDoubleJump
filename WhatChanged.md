# What changed

Short log (max three bullets per version). Newest first.

## 0.3.4 (2026-04-03)

- **queueSmsLive:** After `ProcessPlayerInput`, re-walk the movement queue for the last `SetMovementStates` jump bit so the signal matches post-process state (fixes desync with pre-scan `queueSms`).
- **Config / FSM:** Post-liftoff jump mask fields are on `main` in component + config; default queue edge buffer raised to **220 ms** in shipped defaults.
- **Deploy:** `sync-to-earlyplugins.ps1` copies `JossDoubleJump.jar` to both `earlyplugins/` and `mods/` (Hyxin mixins + JavaPlugin load).

## 0.3.3 (2026-04-03)

- **queueSms signal:** Use `jumpHeldLastQueue` from `QueueScanner` when the queue had SMS (`src=queueSms`), instead of Hyxin `lastQueuedJumping()` (often null in traces).
- **Mixin:** `PlayerInput` tracker is created on every `queue()` call so rising-edge state is not lost when non-SMS updates arrive first.

## 0.3.2 (earlier)

- **Deploy path:** Documented and scripted copying the same JAR into **`mods/`** as well as **`earlyplugins/`** so `joss:JossDoubleJump` enables and `setup()` runs.
