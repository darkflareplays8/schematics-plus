package com.schematicsplus.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.schematicsplus.schematic.*;
import com.schematicsplus.util.ChatUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.AirBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;

public class SchematicCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {

        dispatcher.register(
                ClientCommandManager.literal("schematic")
                        .executes(ctx -> { showHelp(); return 1; })

                        .then(ClientCommandManager.literal("help")
                                .executes(ctx -> { showHelp(); return 1; }))

                        .then(ClientCommandManager.literal("select")
                                .executes(SchematicCommand::executeSelect))

                        .then(ClientCommandManager.literal("cancel")
                                .executes(ctx -> {
                                    SchematicManager.getInstance().clearSelection();
                                    PlacementManager.getInstance().clearPreview();
                                    ChatUtil.sendInfo("Selection and preview cleared.");
                                    return 1;
                                }))

                        .then(ClientCommandManager.literal("save")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .executes(ctx -> executeSave(ctx, StringArgumentType.getString(ctx, "name"))))
                                .executes(ctx -> {
                                    ChatUtil.sendError("Usage: /schematic save <n>");
                                    ChatUtil.suggestCommand("/schematic save ");
                                    return 0;
                                }))

                        .then(ClientCommandManager.literal("load")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            SchematicManager.getInstance().listSchematics().forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> executeLoad(ctx, StringArgumentType.getString(ctx, "name"))))
                                .executes(ctx -> {
                                    ChatUtil.sendError("Usage: /schematic load <n>");
                                    ChatUtil.suggestCommand("/schematic load ");
                                    return 0;
                                }))

                        .then(ClientCommandManager.literal("confirm")
                                .executes(SchematicCommand::executeConfirm))

                        .then(ClientCommandManager.literal("build")
                                .executes(SchematicCommand::executeBuild))

                        // ── rotate ───────────────────────────────────────────
                        .then(ClientCommandManager.literal("rotate")
                                .executes(ctx -> {
                                    PlacementManager pm = PlacementManager.getInstance();
                                    if (!pm.hasPreview()) {
                                        ChatUtil.sendError("No active preview!");
                                        return 0;
                                    }
                                    if (pm.isConfirmed()) {
                                        ChatUtil.sendError("Cannot rotate after confirming. Cancel and reload to reposition.");
                                        return 0;
                                    }
                                    pm.rotate();
                                    ChatUtil.sendSuccess("Rotated §f" + pm.getRotationDegrees() + "°§a clockwise.");
                                    return 1;
                                }))

                        // ── flip ─────────────────────────────────────────────
                        .then(ClientCommandManager.literal("flip")
                                .then(ClientCommandManager.literal("x")
                                        .executes(ctx -> {
                                            PlacementManager pm = PlacementManager.getInstance();
                                            if (!pm.hasPreview()) { ChatUtil.sendError("No active preview!"); return 0; }
                                            if (pm.isConfirmed()) { ChatUtil.sendError("Cannot flip after confirming."); return 0; }
                                            pm.flipX();
                                            ChatUtil.sendSuccess("Flipped on §fX axis§a (east/west mirror). Flipped: §f" + pm.isFlippedX());
                                            return 1;
                                        }))
                                .then(ClientCommandManager.literal("z")
                                        .executes(ctx -> {
                                            PlacementManager pm = PlacementManager.getInstance();
                                            if (!pm.hasPreview()) { ChatUtil.sendError("No active preview!"); return 0; }
                                            if (pm.isConfirmed()) { ChatUtil.sendError("Cannot flip after confirming."); return 0; }
                                            pm.flipZ();
                                            ChatUtil.sendSuccess("Flipped on §fZ axis§a (north/south mirror). Flipped: §f" + pm.isFlippedZ());
                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    ChatUtil.sendError("Usage: /schematic flip x  OR  /schematic flip z");
                                    ChatUtil.suggestCommand("/schematic flip ");
                                    return 0;
                                }))

                        // ── nudge ────────────────────────────────────────────
                        .then(ClientCommandManager.literal("nudge")
                                .then(ClientCommandManager.literal("north")
                                        .executes(ctx -> executeNudge(Direction.NORTH)))
                                .then(ClientCommandManager.literal("south")
                                        .executes(ctx -> executeNudge(Direction.SOUTH)))
                                .then(ClientCommandManager.literal("east")
                                        .executes(ctx -> executeNudge(Direction.EAST)))
                                .then(ClientCommandManager.literal("west")
                                        .executes(ctx -> executeNudge(Direction.WEST)))
                                .then(ClientCommandManager.literal("up")
                                        .executes(ctx -> executeNudge(Direction.UP)))
                                .then(ClientCommandManager.literal("down")
                                        .executes(ctx -> executeNudge(Direction.DOWN)))
                                .executes(ctx -> {
                                    ChatUtil.sendError("Usage: /schematic nudge <north|south|east|west|up|down>");
                                    ChatUtil.suggestCommand("/schematic nudge ");
                                    return 0;
                                }))

                        // ── next ─────────────────────────────────────────────
                        .then(ClientCommandManager.literal("next")
                                .executes(SchematicCommand::executeNext))

                        // ── materials ────────────────────────────────────────
                        .then(ClientCommandManager.literal("materials")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            SchematicManager.getInstance().listSchematics().forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> executeMaterials(ctx, StringArgumentType.getString(ctx, "name"))))
                                .executes(ctx -> {
                                    ChatUtil.sendError("Usage: /schematic materials <n>");
                                    ChatUtil.suggestCommand("/schematic materials ");
                                    return 0;
                                }))

                        // ── delete ───────────────────────────────────────────
                        .then(ClientCommandManager.literal("delete")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            SchematicManager.getInstance().listSchematics().forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> executeDelete(ctx, StringArgumentType.getString(ctx, "name"))))
                                .executes(ctx -> {
                                    ChatUtil.sendError("Usage: /schematic delete <n>");
                                    ChatUtil.suggestCommand("/schematic delete ");
                                    return 0;
                                }))

                        // ── copy ─────────────────────────────────────────────
                        .then(ClientCommandManager.literal("copy")
                                .then(ClientCommandManager.argument("source", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            SchematicManager.getInstance().listSchematics().forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .then(ClientCommandManager.argument("dest", StringArgumentType.word())
                                                .executes(ctx -> executeCopy(ctx,
                                                        StringArgumentType.getString(ctx, "source"),
                                                        StringArgumentType.getString(ctx, "dest")))))
                                .executes(ctx -> {
                                    ChatUtil.sendError("Usage: /schematic copy <source> <dest>");
                                    ChatUtil.suggestCommand("/schematic copy ");
                                    return 0;
                                }))

                        .then(ClientCommandManager.literal("list")
                                .executes(SchematicCommand::executeList))

                        .then(ClientCommandManager.literal("info")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            SchematicManager.getInstance().listSchematics().forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> executeInfo(ctx, StringArgumentType.getString(ctx, "name"))))
                                .executes(ctx -> {
                                    ChatUtil.sendError("Usage: /schematic info <n>");
                                    ChatUtil.suggestCommand("/schematic info ");
                                    return 0;
                                }))
        );
    }

    // ================================================================
    //  SELECT
    // ================================================================

    private static int executeSelect(CommandContext<FabricClientCommandSource> ctx) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return 0;
        BlockPos pos = player.getBlockPos();
        SchematicManager mgr = SchematicManager.getInstance();
        int point = mgr.recordSelectPoint(pos);
        if (point == 1) {
            ChatUtil.sendSuccess("Point 1 set at §f(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")§a!");
            ChatUtil.sendHint("Walk to your 2nd corner and run §f/schematic select §eagain.");
            ChatUtil.suggestCommand("/schematic select");
        } else {
            BlockPos p1 = mgr.getPoint1();
            int dx = Math.abs(pos.getX() - p1.getX()) + 1;
            int dy = Math.abs(pos.getY() - p1.getY()) + 1;
            int dz = Math.abs(pos.getZ() - p1.getZ()) + 1;
            ChatUtil.sendSuccess("Point 2 set at §f(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")§a!");
            ChatUtil.sendInfo("Region: §f" + dx + "x" + dy + "x" + dz + " §7(" + (dx*dy*dz) + " blocks)");
            ChatUtil.sendHint("Run §f/schematic save <n> §eto save.");
            ChatUtil.suggestCommand("/schematic save ");
        }
        return 1;
    }

    // ================================================================
    //  SAVE
    // ================================================================

    private static int executeSave(CommandContext<FabricClientCommandSource> ctx, String name) {
        SchematicManager mgr = SchematicManager.getInstance();
        if (mgr.getSelectionState() != SchematicManager.SelectionState.READY) {
            ChatUtil.sendError("No complete selection! Use §f/schematic select §ctwice first.");
            ChatUtil.suggestCommand("/schematic select");
            return 0;
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return 0;
        try {
            mgr.saveSchematic(name, world, world.getRegistryManager());
            ChatUtil.sendSuccess("Saved §f'" + name + "'§a!");
            ChatUtil.sendHint("Load it with §f/schematic load " + name);
            mgr.clearSelection();
            ChatUtil.suggestCommand("/schematic load " + name);
        } catch (Exception e) {
            ChatUtil.sendError("Save failed: " + e.getMessage());
        }
        return 1;
    }

    // ================================================================
    //  LOAD
    // ================================================================

    private static int executeLoad(CommandContext<FabricClientCommandSource> ctx, String name) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        ClientWorld world = MinecraftClient.getInstance().world;
        if (player == null || world == null) return 0;
        try {
            SchematicData data = SchematicManager.getInstance()
                    .loadSchematic(name, world.getRegistryManager());
            BlockPos anchor = player.getBlockPos();
            PlacementManager.getInstance().startPreview(data, name, anchor);
            ChatUtil.sendSuccess("Preview started for §f'" + name + "'§a!");
            ChatUtil.sendInfo("§f" + data.getBlockCount() + " §7blocks — moves in front of you.");
            ChatUtil.sendHint("§cRed §7= overlapping. §bCyan §7= clear.");
            ChatUtil.sendHint("§f/schematic rotate §7— rotate 90°  |  §f/schematic flip x/z §7— mirror");
            ChatUtil.sendHint("§f/schematic nudge <dir> §7— move 1 block  |  §f/schematic confirm §7— lock");
            ChatUtil.suggestCommand("/schematic confirm");
        } catch (Exception e) {
            ChatUtil.sendError("Load failed: " + e.getMessage());
            ChatUtil.suggestCommand("/schematic list");
        }
        return 1;
    }

    // ================================================================
    //  CONFIRM
    // ================================================================

    private static int executeConfirm(CommandContext<FabricClientCommandSource> ctx) {
        PlacementManager pm = PlacementManager.getInstance();
        if (!pm.hasPreview()) {
            ChatUtil.sendError("No active preview! Use §f/schematic load <n> §cfirst.");
            ChatUtil.suggestCommand("/schematic load ");
            return 0;
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return 0;

        int overlaps = 0;
        for (var entry : pm.getWorldBlocks().entrySet()) {
            if (entry.getValue().getBlock() instanceof AirBlock) continue;
            if (!(world.getBlockState(entry.getKey()).getBlock() instanceof AirBlock)) overlaps++;
        }
        if (overlaps > 0) {
            ChatUtil.sendError("Cannot confirm — §f" + overlaps + " §cblocks are overlapping!");
            ChatUtil.sendHint("Use §f/schematic nudge §eto reposition, or §f/schematic cancel §eto abort.");
            return 0;
        }
        pm.confirm();
        ChatUtil.sendSuccess("Position locked! §f" + pm.getActiveName() + "§a confirmed.");
        ChatUtil.sendHint("Run §f/schematic build §eto place blocks from your inventory.");
        ChatUtil.sendHint("Run §f/schematic next §eto find the nearest missing block.");
        ChatUtil.suggestCommand("/schematic build");
        return 1;
    }

    // ================================================================
    //  BUILD
    // ================================================================

    private static int executeBuild(CommandContext<FabricClientCommandSource> ctx) {
        PlacementManager pm = PlacementManager.getInstance();
        if (!pm.hasPreview()) {
            ChatUtil.sendError("No active preview! Load one with §f/schematic load <n>");
            ChatUtil.suggestCommand("/schematic load ");
            return 0;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        ClientWorld world = MinecraftClient.getInstance().world;
        if (player == null || world == null) return 0;

        Map<BlockPos, BlockState> worldBlocks = pm.getWorldBlocks();
        Map<String, Integer> inventoryCount = MaterialList.countPlayerInventory();
        int placed = 0, skipped = 0, noBlocks = 0;

        for (var entry : worldBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            if (state.getBlock() instanceof AirBlock) continue;
            if (!(world.getBlockState(pos).getBlock() instanceof AirBlock)) { skipped++; continue; }
            String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
            int have = inventoryCount.getOrDefault(blockId, 0);
            if (have <= 0) { noBlocks++; continue; }
            inventoryCount.put(blockId, have - 1);
            world.setBlockState(pos, state, 3);
            placed++;
        }

        ChatUtil.sendSuccess("Built §f" + placed + "§a blocks!");
        if (skipped > 0) ChatUtil.sendInfo("Skipped §f" + skipped + "§7 (already occupied).");
        if (noBlocks > 0) {
            ChatUtil.sendHint("Missing materials for §f" + noBlocks + "§e blocks — gather more and run §f/schematic build §eagain.");
        } else if (skipped == 0) {
            PlacementManager.getInstance().clearPreview();
            ChatUtil.sendSuccess("Schematic complete! Preview cleared.");
        }
        return 1;
    }

    // ================================================================
    //  NUDGE
    // ================================================================

    private static int executeNudge(Direction direction) {
        PlacementManager pm = PlacementManager.getInstance();
        if (!pm.hasPreview()) {
            ChatUtil.sendError("No active preview!");
            return 0;
        }
        pm.nudge(direction);
        BlockPos anchor = pm.getAnchorPos();
        ChatUtil.sendInfo("Nudged §f" + direction.getName() + "§7. Anchor: §f("
                + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ() + ")");
        return 1;
    }

    // ================================================================
    //  NEXT — find nearest missing block
    // ================================================================

    private static int executeNext(CommandContext<FabricClientCommandSource> ctx) {
        PlacementManager pm = PlacementManager.getInstance();
        if (!pm.hasPreview()) {
            ChatUtil.sendError("No active preview!");
            return 0;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return 0;

        BlockPos nearest = pm.findNearestMissing(player.getBlockPos());
        if (nearest == null) {
            ChatUtil.sendSuccess("All blocks are placed! Run §f/schematic cancel §ato clear.");
            return 1;
        }

        double dist = Math.sqrt(player.getBlockPos().getSquaredDistance(nearest));
        ChatUtil.sendInfo("§eNearest missing block: §f("
                + nearest.getX() + ", " + nearest.getY() + ", " + nearest.getZ()
                + ") §7— §f" + String.format("%.1f", dist) + " §7blocks away");
        ChatUtil.sendHint("It's highlighted in §eyellow §ein the world.");
        return 1;
    }

    // ================================================================
    //  MATERIALS
    // ================================================================

    private static int executeMaterials(CommandContext<FabricClientCommandSource> ctx, String name) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return 0;
        try {
            SchematicData data = SchematicManager.getInstance()
                    .loadSchematic(name, world.getRegistryManager());
            List<MaterialList.Entry> entries = MaterialList.build(data);
            ChatUtil.sendInfo("§7── Materials: §f" + name + " §7──");
            for (MaterialList.Entry e : entries) {
                String pretty = MaterialList.prettyName(e.blockName());
                String color = e.hasEnough() ? "§a" : (e.inInventory() > 0 ? "§e" : "§c");
                String status = e.hasEnough() ? "§a✔" : "§cneed §f" + e.missing() + "§c more";
                ChatUtil.sendInfo("  " + color + pretty + " §7x" + e.needed() +
                        " §8(have §f" + e.inInventory() + "§8) " + status);
            }
            long ready = entries.stream().filter(MaterialList.Entry::hasEnough).count();
            ChatUtil.sendInfo("§7" + ready + "/" + entries.size() + " materials ready.");
        } catch (Exception e) {
            ChatUtil.sendError("Could not read '" + name + "': " + e.getMessage());
        }
        return 1;
    }

    // ================================================================
    //  DELETE
    // ================================================================

    private static int executeDelete(CommandContext<FabricClientCommandSource> ctx, String name) {
        try {
            SchematicManager.getInstance().deleteSchematic(name);
            ChatUtil.sendSuccess("Deleted §f'" + name + "'§a.");
        } catch (Exception e) {
            ChatUtil.sendError("Failed to delete '" + name + "': " + e.getMessage());
        }
        return 1;
    }

    // ================================================================
    //  COPY
    // ================================================================

    private static int executeCopy(CommandContext<FabricClientCommandSource> ctx, String source, String dest) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return 0;
        try {
            SchematicManager.getInstance().copySchematic(source, dest, world.getRegistryManager());
            ChatUtil.sendSuccess("Copied §f'" + source + "'§a to §f'" + dest + "'§a!");
            ChatUtil.sendHint("Load it with §f/schematic load " + dest);
        } catch (Exception e) {
            ChatUtil.sendError("Copy failed: " + e.getMessage());
        }
        return 1;
    }

    // ================================================================
    //  LIST
    // ================================================================

    private static int executeList(CommandContext<FabricClientCommandSource> ctx) {
        List<String> names = SchematicManager.getInstance().listSchematics();
        if (names.isEmpty()) {
            ChatUtil.sendInfo("No saved schematics yet.");
            ChatUtil.sendHint("Select a region with §f/schematic select §ethen save it.");
            ChatUtil.suggestCommand("/schematic select");
        } else {
            ChatUtil.sendInfo("§7Saved schematics §f(" + names.size() + ")§7:");
            for (String n : names) ChatUtil.sendInfo("  §8› §f" + n + "  §7— /schematic load " + n);
        }
        return 1;
    }

    // ================================================================
    //  INFO
    // ================================================================

    private static int executeInfo(CommandContext<FabricClientCommandSource> ctx, String name) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return 0;
        try {
            SchematicData data = SchematicManager.getInstance()
                    .loadSchematic(name, world.getRegistryManager());
            ChatUtil.sendInfo("§7── Info: §f" + name + " §7──");
            ChatUtil.sendInfo("  Size:   §f" + data.getSizeX() + "x" + data.getSizeY() + "x" + data.getSizeZ());
            ChatUtil.sendInfo("  Blocks: §f" + data.getBlockCount());
            ChatUtil.sendHint("Load: §f/schematic load " + name);
            ChatUtil.suggestCommand("/schematic load " + name);
        } catch (Exception e) {
            ChatUtil.sendError("Could not read '" + name + "': " + e.getMessage());
        }
        return 1;
    }

    // ================================================================
    //  HELP
    // ================================================================

    private static void showHelp() {
        ChatUtil.sendInfo("§7──── §bSchematics+ Help §7────");
        ChatUtil.sendInfo("§f/schematic select           §7– Mark corner 1 or 2");
        ChatUtil.sendInfo("§f/schematic cancel           §7– Clear selection or preview");
        ChatUtil.sendInfo("§f/schematic save §3<n>        §7– Save selected region");
        ChatUtil.sendInfo("§f/schematic load §3<n>        §7– Start ghost preview");
        ChatUtil.sendInfo("§f/schematic rotate           §7– Rotate preview 90°");
        ChatUtil.sendInfo("§f/schematic flip §3<x|z>      §7– Mirror preview on axis");
        ChatUtil.sendInfo("§f/schematic nudge §3<dir>     §7– Move preview 1 block");
        ChatUtil.sendInfo("§f/schematic confirm          §7– Lock preview position");
        ChatUtil.sendInfo("§f/schematic build            §7– Place blocks from inventory");
        ChatUtil.sendInfo("§f/schematic next             §7– Find nearest missing block");
        ChatUtil.sendInfo("§f/schematic materials §3<n>   §7– Material list");
        ChatUtil.sendInfo("§f/schematic copy §3<src> §3<dst> §7– Duplicate a schematic");
        ChatUtil.sendInfo("§f/schematic delete §3<n>      §7– Delete a schematic");
        ChatUtil.sendInfo("§f/schematic list             §7– List all schematics");
        ChatUtil.sendInfo("§f/schematic info §3<n>        §7– Size and block count");
        ChatUtil.suggestCommand("/schematic select");
    }
}