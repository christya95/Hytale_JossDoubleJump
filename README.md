# 🎮 Hytale · JossDoubleJump

*Because one jump is never enough.* 🦘✨

**JossDoubleJump** is **Joss**’s fork of narwhals’ classic **Double Jump** server mod—built so you can **pop a second jump with the jump key in mid-air** without hogging **Ability 2 / 3** on weapons and tools. Swords, pickaxes, bows, bare hands… if you’re airborne and you press jump again, the server can fire a stylish double-jump (stamina allowing).

---

## 🧰 What you get

| Feature | Vibe |
|--------|------|
| 🎯 **Jump-key double jump** | Second **jump press** while in the air — works across **all item types** when configured that way |
| ⚔️ **No ability-slot wars** | Default config disables Ability 2/3 injection so gear keeps its real abilities |
| ⚡ **Stamina-aware** | Costs are configurable (flat or percentage) |
| 🛠️ **Server-side** | Drop the JAR in your **Hytale dedicated server** `mods` folder |

---

## 🚀 Quick install (server admins)

1. **Remove** any other double-jump mod (e.g. duplicate `Double Jump` JARs) so they don’t fight.
2. Copy **`dist/JossDoubleJump.jar`** → `<server>/mods/JossDoubleJump.jar`
3. Copy **`runtime-config/double_jump_config.json`** → `<server>/mods/JossDoubleJump/double_jump_config.json`
4. Start the server. **BOOM.** 💥 (Okay, more like *whoosh*.)

📂 **Jar-only / download hub:** see [`download/README.md`](download/README.md).

---

## ⚙️ Config highlights

Edit `mods/JossDoubleJump/double_jump_config.json` (after first run, or sync from `runtime-config/`).

- **`useJumpKey: true`** — double jump from **jump in the air** (recommended).
- **`useAbility2` / `useAbility3`** — leave `false` if you don’t want the mod stuffing `Root_DoubleJump` into ability slots.
- **`horizontalBoost` / `verticalBoost`** — how spicy the impulse is. 🌶️
- **`staminaCost`** / **`usePercentageStamina`** — balance however your server likes it.
- **`maxJumps`** — extra jumps per leave-the-ground (typical **1** = one double jump).

Canonical sample lives in **`runtime-config/double_jump_config.json`**.

---

## 🧑‍💻 Building & patching (developers)

- **Incremental patch** (usual workflow): JDK **25**, compile changed classes against `HytaleServer.jar` + existing `JossDoubleJump.jar`, then `jar uf` into `dist/JossDoubleJump.jar`. See **`WORKSPACE.txt`**.
- **`scripts/build.ps1`** — full rebuild path (needs upstream stub jar + full compile; heavier).
- **`scripts/sync-to-mods.ps1`** — copies `dist/` + `runtime-config/` into a sibling **`PebbleHotServerRoot`** layout for local testing.

Large **`tools/`** (JDK, CFR) are **gitignored**—install your own toolchain or unpack locally.

---

## 📜 Credits & license

Based on **narwhals**’ Double Jump mod; fork maintained here as **JossDoubleJump**.  
Respect upstream and Hytale modding terms when you ship or modify. 🤝

---

## 🎉 Have fun

Go launch off cliffs responsibly, speedrun your castle walls, and remember: **the floor is optional.** 🪐

*— Joss · JossDoubleJump*

---

## 🌐 First-time publish to GitHub

`GitHub CLI` isn’t required—create the repo in the browser, then:

```bash
cd "/path/to/JossDoubleJump"
git remote add origin https://github.com/<YOUR_USER>/Hytale-JossDoubleJump.git
git push -u origin main
```

**Tip:** After the first push, cut a **Release** and attach `dist/JossDoubleJump.jar` so players get a one-click download. The [`download/`](download/) folder + **`dist/`** stay the “grab the JAR” locations in-tree.
