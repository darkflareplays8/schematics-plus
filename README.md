# Schematics+

A client-side Fabric mod for Minecraft — **Litematica, but entirely command-driven.**

No GUIs, no hotkeys to memorize. Every action is a `/schematic` command, and after each step the mod automatically suggests your next command in the chat bar.

---

## 🚀 Quick Start

```
/schematic select       ← stand at corner 1, run this
/schematic select       ← walk to corner 2, run this again
/schematic save myhouse ← saves the region
/schematic load myhouse ← pastes it back at your feet later
```

---

## 📋 Commands

| Command | What it does |
|---|---|
| `/schematic` or `/schematic help` | Show all commands |
| `/schematic select` | Mark your first or second selection corner (at player feet) |
| `/schematic cancel` | Clear the current selection |
| `/schematic save <n>` | Save the selected region to disk |
| `/schematic load <n>` | Paste a saved schematic at your feet |
| `/schematic list` | List all your saved schematics |
| `/schematic info <n>` | Show size & block count of a saved schematic |

---

## 💾 Where are files saved?

Schematics are saved as compressed `.nbt` files in:

```
.minecraft/schematics+/<n>.nbt
```

---

## 📦 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/)
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Drop the Schematics+ jar into your `.minecraft/mods/` folder

Check the [releases](../../releases) page for the correct jar for your Minecraft version.

---

## ⚠️ Notes

- **Client-side only** — works in singleplayer and on any server (block placement still requires normal server permissions).
- Tab completion works on `/schematic load` and `/schematic info` — it will suggest your saved schematic names.