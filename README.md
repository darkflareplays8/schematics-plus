# Schematics+

A client-side Fabric mod for Minecraft 1.21.1 — **Litematica, but entirely command-driven.**

No GUIs, no hotkeys to memorize. Every action is a `/schematic` command with prompts that guide you through the next step automatically.

---

## 🚀 Quick Start

```
/schematic select       ← stand at corner 1, run this
/schematic select       ← walk to corner 2, run this again
/schematic save myhouse ← saves the region
/schematic load myhouse ← pastes it back at your feet later
```

The mod automatically **pre-fills the next command in your chat bar** so you never have to guess what to do.

---

## 📋 Commands

| Command | What it does |
|---|---|
| `/schematic` or `/schematic help` | Show all commands |
| `/schematic select` | Mark your first or second selection corner (at player feet) |
| `/schematic cancel` | Clear the current selection |
| `/schematic save <name>` | Save the selected region to disk |
| `/schematic load <name>` | Paste a saved schematic at your feet |
| `/schematic list` | List all your saved schematics |
| `/schematic info <name>` | Show size & block count of a saved schematic |

---

## 💾 Where are files saved?

Schematics are saved as compressed `.nbt` files in:

```
.minecraft/schematics+/<name>.nbt
```

---

## 🔨 Building

Requirements: **Java 21**, **Gradle**

```bash
./gradlew build
```

Output jar will be in `build/libs/schematics-plus-1.0.0.jar`.

Drop it in your `.minecraft/mods/` folder alongside Fabric API.

---

## 📦 Dependencies

- Fabric Loader ≥ 0.15.0
- Fabric API
- Minecraft 1.21.1

---

## ⚠️ Notes

- **Client-side only** — works in singleplayer and on any server (block placement uses the normal server pipeline, so you still need permission to place blocks on a server).
- Tab completion works on `/schematic load` and `/schematic info` — it will suggest your saved schematic names.
