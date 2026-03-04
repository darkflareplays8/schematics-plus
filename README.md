# Schematics+

A client-side Fabric mod for Minecraft — **Litematica, but entirely command-driven.**

No GUIs. No hotkeys. Just `/schematic` commands that guide you through every step, with a live ghost preview and full survival support.

---

## ✨ Features

- **Visual selection box** — blue while selecting, turns green when both corners are set
- **Ghost preview** — schematic floats in front of you before you commit to a position
  - 🔵 Cyan = clear to place
  - 🔴 Red = overlapping existing blocks (confirm is blocked until resolved)
- **Survival-friendly** — uses blocks directly from your own inventory, no OP needed
- **Break freely** — break any block in the schematic region without it snapping back
- **Material list** — see exactly what you need and how much you already have
- **Live HUD** — top-right overlay updates in real time as you place or break blocks
- **Auto-build** — places everything it can from your inventory in one command, picks up where it left off when you run it again

---

## 🚀 Workflow

**Saving a build:**
```
/schematic select           ← stand at corner 1
/schematic select           ← walk to corner 2
/schematic save myhouse
```

**Rebuilding it in survival:**
```
/schematic materials myhouse  ← check what blocks you need
/schematic load myhouse       ← ghost preview starts, follows you
/schematic confirm            ← locks position (blocked if overlapping)
/schematic build              ← places all blocks you have in inventory
```

Gather more materials and run `/schematic build` again — it picks up right where it left off.

---

## 📋 Commands

| Command | Description |
|---|---|
| `/schematic help` | Show all commands |
| `/schematic select` | Set corner 1 or 2 of your selection |
| `/schematic cancel` | Clear selection or active preview |
| `/schematic save <n>` | Save the selected region |
| `/schematic load <n>` | Start a ghost preview of a schematic |
| `/schematic confirm` | Lock the preview position in place |
| `/schematic build` | Place blocks from your inventory |
| `/schematic materials <n>` | Full material list with inventory counts |
| `/schematic list` | List all saved schematics |
| `/schematic info <n>` | Show size and block count |

---

## 💾 Where files are saved

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

- **Client-side only** — no server installation needed. Works in singleplayer and on any server.
- Block placement uses the normal placement pipeline — you still need permission to place blocks on the server.
- `/schematic build` only uses blocks physically in your inventory.