package com.schematicsplus;

import com.schematicsplus.command.SchematicCommand;
import com.schematicsplus.render.SelectionBoxRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
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
        // Create the schematics save directory inside .minecraft/schematics+/
        SCHEMATICS_DIR = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getGameDir()
                .resolve("schematics+");

        try {
            Files.createDirectories(SCHEMATICS_DIR);
            LOGGER.info("[Schematics+] Save directory ready at: {}", SCHEMATICS_DIR);
        } catch (Exception e) {
            LOGGER.error("[Schematics+] Failed to create schematics directory!", e);
        }

        // Register the selection box renderer
        SelectionBoxRenderer.register();

        // Register all /schematic sub-commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            SchematicCommand.register(dispatcher);
        });

        LOGGER.info("[Schematics+] Loaded! Use /schematic to get started.");
    }
}