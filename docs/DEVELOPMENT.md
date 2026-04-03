# Development

## Layout (quick map)

| Path | Role |
|------|------|
| `src/main/java/ca/joss/jossdoublejump/` | Plugin code (`ca.joss.jossdoublejump` + `ui`, `util`). |
| `jar-assets/` | **`manifest.json`**, **`double_jump_defaults.json`**, **`jossdoublejump.mixins.json`** (Hyxin) copied into the JAR by the build scripts. |
| `runtime-config/` | Sample **`JossDoubleJumpConfig.json`** (optional; the mod writes **`mods/JossDoubleJumpConfig.json`** on first run from JAR defaults). |
| `scripts/` | **`build.ps1`** (full compile), **`repack-jar.ps1`** (refresh JAR without javac), **`sync-to-mods.ps1`** (copy to `mods/`). |
| `dist/` | **`JossDoubleJump.jar`** output (gitignored). |
| `build/` | Scratch: classes, unpack dirs (gitignored). |
| `tools/jdk25/` | Local JDK 25 for `javac` / `jar` (gitignored). |

The running server reads config from **`mods/JossDoubleJump/`** (path inside the plugin). After editing **`runtime-config/`**, run **`scripts/sync-to-mods.ps1`** before starting the server if you test from this repo.

## Requirements

- **JDK 25** under **`tools/jdk25/`** (install locally; folder is gitignored).
- **`HytaleServer.jar`** on the classpath: set **`PEBBLE_SERVER_ROOT`** to the folder that contains it, or place the repo next to **`PebbleHotServerRoot`** so the scripts can find it.
- **`Hyxin-*-all.jar`** (e.g. from [CurseForge Hyxin](https://www.curseforge.com/hytale/mods/hyxin)) in **`earlyplugins/`** or **`mods/`** under that same server root — required for **`javac`** (Mixin annotations) and for runtime mixin application.
- A **template `JossDoubleJump.jar`** in **`dist/`** or **`mods/`** (from a previous build or repack) — full compile unpacks it for non-class assets and uses it on the compile classpath.

## Scripts

- **`scripts/build.ps1`** — compiles all of **`src/main/java`**, merges into the unpacked template, overwrites manifest/defaults from **`jar-assets/`**, writes **`dist/JossDoubleJump.jar`**.
- **`scripts/repack-jar.ps1`** — no compile; unpacks a template JAR, applies **`jar-assets/`**, repacks **`dist/JossDoubleJump.jar`**.
- **`scripts/sync-to-mods.ps1`** — copies **`dist/JossDoubleJump.jar`** and **`runtime-config/JossDoubleJumpConfig.json`** to **`<server>/mods/`** (use **`PEBBLE_SERVER_ROOT`** when the server is not this repo).
- **`scripts/sync-to-earlyplugins.ps1`** — copies **`dist/JossDoubleJump.jar`** to **`<server>/earlyplugins/`** and config to **`mods/`**. Use this when relying on **Hyxin** (mixins load only from **`earlyplugins/`**). Remove **`mods/JossDoubleJump.jar`** if you move the plugin to **`earlyplugins/`** so it is not loaded twice.

## Incremental compile

For small edits: compile only changed `.java` files with **`javac`** using **`HytaleServer.jar`** + **`dist/JossDoubleJump.jar`** on the classpath, then:

```text
jar uf dist/JossDoubleJump.jar -C <classes> <path/to/class>
```

Full tree compile may fail if sources drift from your server API; repack or adjust stubs as needed.

## Environment

- **`PEBBLE_SERVER_ROOT`** — optional; points at the server root containing **`HytaleServer.jar`** and **`mods/`** when that tree is not beside this repo.
