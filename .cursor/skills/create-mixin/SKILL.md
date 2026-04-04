---
name: create-mixin
description: >-
  Adds a Hyxin (Sponge Mixin) class for com.hypixel.hytale.server.core.modules.entity.player.PlayerInput,
  registers it in jossdoublejump.mixins.json, and wires DoubleJumpTicking.rawJumpPressedFromInput to use the
  mixin when reflection finds no raw jump accessor. Use when exposing raw jump key state (e.g. isJumpKeyDown),
  fixing double-jump after a Hytale API change, or when the user mentions Hyxin mixins, PlayerInput, or
  reflection fallback for JossDoubleJump.
---

# Hyxin mixin: raw `PlayerInput` jump signal

## Goal

Expose a reliable **raw jump pressed** signal from `PlayerInput` when `DoubleJumpTicking`’s reflection path (`RAW_JUMP_METHOD_NAMES`, `RAW_MS_GETTER_NAMES`, etc.) returns `null`. Do **not** add a new keybind; keep using the default jump input only.

## Repo facts (do not guess)

| Item | Location |
|------|----------|
| Mixin package | `ca.joss.jossdoublejump.mixin` |
| Mixin config | `jar-assets/jossdoublejump.mixins.json` (copied into the JAR by `scripts/build.ps1` / `repack-jar.ps1`) |
| Hyxin registration | `jar-assets/manifest.json` → `"Hyxin": { "Configs": [ "jossdoublejump.mixins.json" ] }` |
| Consumer | `DoubleJumpTicking.rawJumpPressedFromInput(PlayerInput)` — add mixin fallback **after** existing reflection attempts, **before** returning `null` |
| Existing mixin pattern | `PlayerInputQueueMixin`: `@Mixin(PlayerInput.class)`, `public static` helpers for `DoubleJumpTicking`, `@Inject` on `queue` |

Runtime: Hyxin loads mixin configs from JARs in **`earlyplugins/`**; sync with `scripts/sync-to-earlyplugins.ps1` when testing mixins (see `docs/DEVELOPMENT.md`).

## Workflow

1. **Inspect `PlayerInput` (server API)**  
   Identify how raw jump is stored: private field, getter, or derived from `MovementStates`. Pick **one** stable approach per game version.

2. **Add a new mixin class** under `src/main/java/ca/joss/jossdoublejump/mixin/`  
   - Annotate `@Mixin(PlayerInput.class)`.  
   - Prefer **`@Shadow`** + package-private or **public `static` bridge** methods that `DoubleJumpTicking` can call without reflection — same pattern as `PlayerInputQueueMixin.consumeJumpRisingEdge`.  
   - Name injected methods with a unique prefix (e.g. `jossDoubleJump$…`) to avoid collisions.  
   - Keep static maps **`WeakHashMap`** + `synchronizedMap` if tracking per-`PlayerInput` state from injects (see `PlayerInputQueueMixin`).  
   - If only exposing a boolean: e.g. `public static boolean rawJumpPressed(PlayerInput input)` returning whether the mixin can read state; use `Boolean` or a tri-state only if “unknown” matters.

3. **Register the mixin**  
   Append the new class **simple name** to the `"mixins"` array in `jar-assets/jossdoublejump.mixins.json` (same `package` as existing entries). Do not duplicate names.

4. **Wire `DoubleJumpTicking`**  
   - Import the new mixin class.  
   - At the **end** of `rawJumpPressedFromInput`, after all reflection branches fail, call the mixin helper; if it returns a non-`null` `Boolean`, return it.  
   - Preserve signal order in `AfterInputSystem`: `rawJump` from `rawJumpPressedFromInput` stays first in the `raw` / `pendingMs` / queue / `fallback` chain.

5. **Build and verify**  
   - `scripts/build.ps1` → `dist/JossDoubleJump.jar`.  
   - Deploy to server `earlyplugins/` (and config sync as usual).  
   - Confirm with **Amore** (`/amoretraceon`, then inspect dumps / ring data) that the raw path is used when reflection previously fell through to `fallback` (in traces, source encodes as `raw` via `JossDoubleJumpTraceBridge`).

## Checklist

- [ ] New mixin class compiles (Hyxin `Mixin` annotations on classpath via `Hyxin-*-all.jar`).
- [ ] `jossdoublejump.mixins.json` lists the new mixin.
- [ ] `rawJumpPressedFromInput` calls mixin only as **fallback after reflection**.
- [ ] No new keybind or client-only API requirement.

## Optional: `reference.md`

If the mixin uses version-specific field names, add a one-line note in a sibling `reference.md` with the **ServerVersion** from `manifest.json` and the shadowed member names — keep `SKILL.md` short.
