# Hytale · JossDoubleJump

Server-side **double jump**: an extra air jump when you press jump again while airborne (configurable stamina, boosts, and activation mode).

**→ Server admins:** [Quick install](#quick-install) · [Config](#config)  
**→ Developers:** [Repo layout](#repository-layout) · [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) (build, env vars, scripts)

---

## Repository layout

```text
Hytale_JossDoubleJump/
├── docs/                    # Developer guide (build, layout, incremental compile)
├── jar-assets/              # manifest.json + double_jump_defaults.json → baked into the JAR
├── runtime-config/          # Sample double_jump_config.json for the server
├── scripts/                 # build.ps1, repack-jar.ps1, sync-to-mods.ps1, push-github.*
├── src/main/java/.../       # Java package ca.joss.jossdoublejump
├── dist/                    # JossDoubleJump.jar output (gitignored)
├── build/                   # Compile scratch only (gitignored)
└── tools/                   # Local JDK 25 (gitignored)
```

| Path | Purpose |
|------|--------|
| **`src/main/java/ca/joss/jossdoublejump/`** | Plugin code; compile root is **`src/main/java`**. |
| **`jar-assets/`** | Files merged into the JAR by **`scripts/build.ps1`** / **`repack-jar.ps1`**. |
| **`runtime-config/`** | Canonical server config sample; sync with **`scripts/sync-to-mods.ps1`**. |
| **`dist/`** | **`JossDoubleJump.jar`** after build (gitignored). |
| **`scripts/`** | Build and sync automation. |

---

## Features

| Feature | Description |
|--------|-------------|
| **Jump-key double jump** | Second jump press in the air (when enabled in config). |
| **Ability 2 / 3** | Optional; default config avoids binding double jump to weapon abilities. |
| **Stamina** | Flat or percentage cost, configurable. |
| **Server-only** | Install the JAR on the Hytale dedicated server. |

---

## Quick install

1. Avoid loading another double-jump mod at the same time.
2. Upload **`JossDoubleJump.jar`** to **`mods/`** (only this file is required).
3. Start or restart the server. On first run the mod creates **`mods/double_jump_config.json`** from the defaults baked into the JAR (you can edit that file later).

Optional: copy **`runtime-config/double_jump_config.json`** into **`mods/`** yourself if you want specific values before the first boot.

---

## Config

Edit **`mods/double_jump_config.json`** on the server when you want to tune settings. The sample in **`runtime-config/`** matches the embedded defaults.

- **`useJumpKey`** — use jump in the air for the extra jump (recommended).
- **`useAbility2` / `useAbility3`** — optional ability-based activation.
- **`horizontalBoost` / `verticalBoost`**, **`staminaCost`**, **`maxJumps`**, etc.

---

## Building

See **[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)** for requirements (**JDK 25**, **`HytaleServer.jar`**, template JAR), **`PEBBLE_SERVER_ROOT`**, and incremental compile.

- **`scripts/build.ps1`** — full compile → **`dist/JossDoubleJump.jar`**.
- **`scripts/repack-jar.ps1`** — no compile; refresh JAR from a template + **`jar-assets/`**.
- **`scripts/sync-to-mods.ps1`** — copy **`dist/`** + **`runtime-config/`** into **`mods/`** (or your server root).

**`dist/*.jar`** and local **`mods/`** are gitignored.

---

## GitHub

```bash
cd "/path/to/Hytale_JossDoubleJump"
git remote add origin https://github.com/<USER>/<REPO>.git
git push -u origin main
```

Attach **`JossDoubleJump.jar`** to a GitHub **Release** for downloads.
