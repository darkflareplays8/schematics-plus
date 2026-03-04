package com.schematicsplus;

import com.schematicsplus.command.SchematicCommand;
import com.schematicsplus.render.PreviewRenderer;
import com.schematicsplus.render.SelectionBoxRenderer;
import com.schematicsplus.schematic.PlacementManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
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

        // Update floating preview anchor every tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PlacementManager pm = PlacementManager.getInstance();
            if (pm.isFloating() && client.player != null) {
                pm.updateAnchor(client.player);
            }
        });

        // When a block is broken, remove it from the active schematic
        // so the ghost doesn't snap back
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            PlacementManager pm = PlacementManager.getInstance();
            if (pm.hasPreview()) {
                pm.removeBlockAt(pos);
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                SchematicCommand.register(dispatcher));

        LOGGER.info("[Schematics+] Loaded! Use /schematic to get started.");
    }
}