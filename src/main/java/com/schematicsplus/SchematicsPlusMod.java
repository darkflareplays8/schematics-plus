package com.schematicsplus;

import com.schematicsplus.command.SchematicCommand;
import com.schematicsplus.render.PreviewRenderer;
import com.schematicsplus.render.SelectionBoxRenderer;
import com.schematicsplus.schematic.PlacementManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.AirBlock;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class SchematicsPlusMod implements ClientModInitializer {

    public static final String MOD_ID = "schematicsplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static Path SCHEMATICS_DIR;

    @Override
    public void onInitializeClient() {
        SCHEMATICS_DIR = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getGameDir()
                .resolve("schematics+");

        try {
            Files.createDirectories(SCHEMATICS_DIR);
        } catch (Exception e) {
            LOGGER.error("[Schematics+] Failed to create schematics directory!", e);
        }

        SelectionBoxRenderer.register();
        PreviewRenderer.register();

        // Every tick: update anchor when floating, and re-apply any broken blocks
        // that the server tried to restore
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PlacementManager pm = PlacementManager.getInstance();

            if (client.player == null || client.world == null) return;

            if (pm.isFloating()) {
                pm.updateAnchor(client.player);
            }

            // Nothing to do here — block restoration is handled by PlayerBlockBreakEvents
        });

        // When a block is fully broken, remove it from the schematic tracking
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            PlacementManager pm = PlacementManager.getInstance();
            if (pm.hasPreview()) {
                pm.removeBlockAt(pos);
            }
        });

        // Every tick: if a manually broken block has been restored by the server,
        // re-break it using interactionManager so the server agrees
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PlacementManager pm = PlacementManager.getInstance();
            if (!pm.isConfirmed() || client.world == null || client.interactionManager == null) return;
            for (BlockPos pos : pm.getManuallyBroken()) {
                net.minecraft.block.BlockState current = client.world.getBlockState(pos);
                if (!(current.getBlock() instanceof AirBlock)) {
                    client.interactionManager.attackBlock(pos, net.minecraft.util.math.Direction.UP);
                }
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                SchematicCommand.register(dispatcher));

        LOGGER.info("[Schematics+] Loaded! Use /schematic to get started.");
    }
}