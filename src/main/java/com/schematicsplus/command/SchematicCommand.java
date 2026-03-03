package com.schematicsplus.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.schematicsplus.schematic.SchematicData;
import com.schematicsplus.schematic.SchematicManager;
import com.schematicsplus.util.ChatUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Registers the /schematic command tree.
 *
 * Sub-commands:
 *   /schematic help           – shows all commands
 *   /schematic select         – marks point 1 or 2 at player feet
 *   /schematic cancel         – clears current selection
 *   /schematic save <name>    – saves selected region to disk
 *   /schematic load <name>    – pastes a saved schematic at player feet
 *   /schematic list           – lists all saved schematics
 *   /schematic info <name>    – shows size / block count of a saved schematic
 */
public class SchematicCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {

        dispatcher.register(
            ClientCommandManager.literal("schematic")

                // ── /schematic (no args) → help ──────────────────────────────
                .executes(ctx -> {
                    showHelp();
                    return 1;
                })

                // ── /schematic help ──────────────────────────────────────────
                .then(ClientCommandManager.literal("help")
                    .executes(ctx -> {
                        showHelp();
                        return 1;
                    }))

                // ── /schematic select ────────────────────────────────────────
                .then(ClientCommandManager.literal("select")
                    .executes(SchematicCommand::executeSelect))

                // ── /schematic cancel ────────────────────────────────────────
                .then(ClientCommandManager.literal("cancel")
                    .executes(ctx -> {
                        SchematicManager.getInstance().clearSelection();
                        ChatUtil.sendInfo("Selection cleared.");
                        return 1;
                    }))

                // ── /schematic save <name> ───────────────────────────────────
                .then(ClientCommandManager.literal("save")
                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(ctx -> executeSave(ctx, StringArgumentType.getString(ctx, "name"))))
                    // No name provided – remind them
                    .executes(ctx -> {
                        ChatUtil.sendError("Please provide a name! Usage: /schematic save <name>");
                        ChatUtil.suggestCommand("/schematic save ");
                        return 0;
                    }))

                // ── /schematic load <name> ───────────────────────────────────
                .then(ClientCommandManager.literal("load")
                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            // Tab-complete from saved schematics list
                            SchematicManager.getInstance().listSchematics()
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeLoad(ctx, StringArgumentType.getString(ctx, "name"))))
                    .executes(ctx -> {
                        ChatUtil.sendError("Please provide a name! Usage: /schematic load <name>");
                        ChatUtil.suggestCommand("/schematic load ");
                        return 0;
                    }))

                // ── /schematic list ──────────────────────────────────────────
                .then(ClientCommandManager.literal("list")
                    .executes(SchematicCommand::executeList))

                // ── /schematic info <name> ───────────────────────────────────
                .then(ClientCommandManager.literal("info")
                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            SchematicManager.getInstance().listSchematics()
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeInfo(ctx, StringArgumentType.getString(ctx, "name"))))
                    .executes(ctx -> {
                        ChatUtil.sendError("Usage: /schematic info <name>");
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

        // Use the block the player is standing on (feet position)
        BlockPos pos = player.getBlockPos();
        SchematicManager mgr = SchematicManager.getInstance();
        int point = mgr.recordSelectPoint(pos);

        if (point == 1) {
            ChatUtil.sendSuccess("§aPoint 1 selected at §f(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")§a!");
            ChatUtil.sendHint("Now walk to your 2nd corner and run §f/schematic select §eagain.");
            // Pre-fill the command for them
            ChatUtil.suggestCommand("/schematic select");
        } else {
            BlockPos p1 = mgr.getPoint1();
            int dx = Math.abs(pos.getX() - p1.getX()) + 1;
            int dy = Math.abs(pos.getY() - p1.getY()) + 1;
            int dz = Math.abs(pos.getZ() - p1.getZ()) + 1;

            ChatUtil.sendSuccess("§aPoint 2 selected at §f(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")§a!");
            ChatUtil.sendInfo("Region size: §f" + dx + "x" + dy + "x" + dz + " §7(" + (dx * dy * dz) + " blocks)");
            ChatUtil.sendHint("Run §f/schematic save <name> §eto save this region.");
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
            ChatUtil.sendError("You don't have a complete selection yet!");
            ChatUtil.sendHint("Stand at your first corner and run §f/schematic select§e, then do the same at the second corner.");
            ChatUtil.suggestCommand("/schematic select");
            return 0;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return 0;

        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return 0;

        try {
            mgr.saveSchematic(name, world, world.getRegistryManager());
            ChatUtil.sendSuccess("Schematic §f'" + name + "'§a saved successfully!");
            ChatUtil.sendHint("You can load it later with §f/schematic load " + name);
            ChatUtil.sendHint("Or view all saved schematics with §f/schematic list");
            // Clear selection after successful save
            mgr.clearSelection();
            ChatUtil.suggestCommand("/schematic load " + name);
        } catch (Exception e) {
            ChatUtil.sendError("Failed to save schematic: " + e.getMessage());
        }
        return 1;
    }

    // ================================================================
    //  LOAD
    // ================================================================

    private static int executeLoad(CommandContext<FabricClientCommandSource> ctx, String name) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return 0;

        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return 0;

        try {
            SchematicData data = SchematicManager.getInstance()
                    .loadSchematic(name, world.getRegistryManager());

            // Place every block relative to the player's current feet position
            BlockPos playerPos = player.getBlockPos();
            int placed = 0;
            int skipped = 0;

            for (var entry : data.getBlocks().entrySet()) {
                BlockPos relPos = entry.getKey();
                BlockState state = entry.getValue();
                BlockPos worldPos = playerPos.add(relPos);

                // Note: client-side setBlockState only updates the render — you
                // normally need server-side permission to actually change blocks.
                // This sets the block on the client world (works in single-player
                // and on servers where the player has the right to place blocks).
                if (world.setBlockState(worldPos, state, 3)) {
                    placed++;
                } else {
                    skipped++;
                }
            }

            ChatUtil.sendSuccess("Loaded §f'" + name + "'§a!");
            ChatUtil.sendInfo("Placed §f" + placed + "§7 blocks. Skipped §f" + skipped + "§7 (out-of-range / protected).");
            ChatUtil.sendInfo("Region: §f" + data.getSizeX() + "x" + data.getSizeY() + "x" + data.getSizeZ());

        } catch (Exception e) {
            ChatUtil.sendError("Failed to load schematic: " + e.getMessage());
            ChatUtil.sendHint("Use §f/schematic list §eto see your saved schematics.");
            ChatUtil.suggestCommand("/schematic list");
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
            ChatUtil.sendHint("Make a selection with §f/schematic select §ethen save it with §f/schematic save <name>");
            ChatUtil.suggestCommand("/schematic select");
        } else {
            ChatUtil.sendInfo("§7Saved schematics §f(" + names.size() + ")§7:");
            for (String n : names) {
                ChatUtil.sendInfo("  §8› §f" + n + "  §7— /schematic load " + n);
            }
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

            ChatUtil.sendInfo("§7── Schematic Info: §f" + name + " §7──");
            ChatUtil.sendInfo("  Size:   §f" + data.getSizeX() + " x " + data.getSizeY() + " x " + data.getSizeZ());
            ChatUtil.sendInfo("  Blocks: §f" + data.getBlockCount());
            ChatUtil.sendInfo("  Origin: §f" + data.getOrigin().getX() + ", " + data.getOrigin().getY() + ", " + data.getOrigin().getZ());
            ChatUtil.sendHint("Load it with: §f/schematic load " + name);
            ChatUtil.suggestCommand("/schematic load " + name);

        } catch (Exception e) {
            ChatUtil.sendError("Could not read schematic '" + name + "': " + e.getMessage());
        }
        return 1;
    }

    // ================================================================
    //  HELP
    // ================================================================

    private static void showHelp() {
        ChatUtil.sendInfo("§7──── §bSchematics+ Help §7────");
        ChatUtil.sendInfo("§f/schematic select   §7– Mark corner 1 or 2 at your feet");
        ChatUtil.sendInfo("§f/schematic cancel   §7– Clear current selection");
        ChatUtil.sendInfo("§f/schematic save §3<name>  §7– Save selected region");
        ChatUtil.sendInfo("§f/schematic load §3<name>  §7– Paste schematic at your feet");
        ChatUtil.sendInfo("§f/schematic list     §7– List all saved schematics");
        ChatUtil.sendInfo("§f/schematic info §3<name>  §7– Show size/block info");
        ChatUtil.sendHint("Start by standing at a corner and running §f/schematic select");
        ChatUtil.suggestCommand("/schematic select");
    }
}
