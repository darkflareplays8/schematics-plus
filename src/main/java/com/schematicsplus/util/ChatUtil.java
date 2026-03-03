package com.schematicsplus.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Helpers for sending nicely-formatted chat messages to the local player
 * and injecting suggestions into the chat bar.
 */
public class ChatUtil {

    private static final String PREFIX_STR = "§b[§3Schematics+§b]§r ";

    /** Aqua prefix + white message */
    public static void sendInfo(String message) {
        send(PREFIX_STR + "§f" + message);
    }

    /** Aqua prefix + green success message */
    public static void sendSuccess(String message) {
        send(PREFIX_STR + "§a" + message);
    }

    /** Aqua prefix + yellow hint / next-step message */
    public static void sendHint(String message) {
        send(PREFIX_STR + "§e" + message);
    }

    /** Aqua prefix + red error message */
    public static void sendError(String message) {
        send(PREFIX_STR + "§c" + message);
    }

    /**
     * Sends a message to the player's chat HUD (not to the server).
     */
    private static void send(String rawMessage) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal(rawMessage), false);
        }
    }

    /**
     * Pre-fills the chat bar with a command suggestion so the player
     * can just hit Enter (or edit) instead of typing from scratch.
     *
     * @param suggestion  the text to pre-fill, e.g. "/schematic save "
     */
    public static void suggestCommand(String suggestion) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        // Open the chat screen with the suggestion pre-filled
        client.execute(() -> {
            client.setScreen(new net.minecraft.client.gui.screen.ChatScreen(suggestion));
        });
    }
}
