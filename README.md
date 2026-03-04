# Schematics+

A client-side Fabric mod for Minecraft — **Litematica, but entirely command-driven.**

No GUIs. No hotkeys. Just `/schematic` commands, with the mod guiding you through every step automatically.

---

## ✨ Features

- **Visual selection box** — blue while selecting, green when ready
- **Ghost preview** — schematic floats with you before you place it
  - 🔵 Cyan = clear to place
  - 🔴 Red = overlapping existing blocks
- **Survival-friendly** — uses blocks from your own inventory, no OP needed
- **Material list** — see exactly what you need and how much you have
- **Live HUD** — top-right overlay updates as you place blocks
- **Auto-build** — places everything it can from your inventory in one command

---

## 🚀 Workflow

```
/schematic select           ← stand at corner 1
/schematic select           ← walk to corner 2
/schematic save myhouse     ← saves the region

/schematic materials myhouse  ← check what blocks you need
/schematic load myhouse       ← starts a ghost preview that follows you
/schematic confirm            ← locks position (only works if no overlaps)
/schematic build              ← places all blocks you have in inventory
```

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
| `/schematic build` | Place all blocks you have from inventory |
| `/schematic materials <n>` | List all materials needed and what you have |
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
- Block placement on servers uses the normal placement pipeline — you still need to be able to place blocks.
- `/schematic build` only places blocks you physically have in your inventory.