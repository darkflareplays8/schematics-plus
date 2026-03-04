package com.schematicsplus.schematic;

import net.minecraft.block.AirBlock;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Utility for counting materials in a schematic and comparing against
 * what the player currently has in their inventory.
 */
public class MaterialList {

    public record Entry(String blockName, int needed, int inInventory) {
        public boolean hasEnough() { return inInventory >= needed; }
        public int missing() { return Math.max(0, needed - inInventory); }
    }

    /**
     * Build a material list from a SchematicData, comparing against the
     * player's current inventory.
     */
    public static List<Entry> build(SchematicData data) {
        // Count how many of each block type the schematic needs
        Map<String, Integer> neededMap = new LinkedHashMap<>();

        for (BlockState state : data.getBlocks().values()) {
            // Skip air
            if (state.getBlock() instanceof AirBlock) continue;
            String name = Registries.BLOCK.getId(state.getBlock()).toString();
            neededMap.merge(name, 1, Integer::sum);
        }

        // Count what the player has
        Map<String, Integer> haveMap = countPlayerInventory();

        // Build result list, sorted by most needed first
        List<Entry> entries = new ArrayList<>();
        for (var e : neededMap.entrySet()) {
            int have = haveMap.getOrDefault(e.getKey(), 0);
            entries.add(new Entry(e.getKey(), e.getValue(), have));
        }
        entries.sort(Comparator.comparingInt(Entry::needed).reversed());
        return entries;
    }

    /**
     * Build a partial material list only for blocks that still need placing
     * at the given world positions (used during active build to update HUD).
     */
    public static List<Entry> buildForRemainingBlocks(Map<BlockPos, BlockState> remaining) {
        Map<String, Integer> neededMap = new LinkedHashMap<>();
        for (BlockState state : remaining.values()) {
            if (state.getBlock() instanceof AirBlock) continue;
            String name = Registries.BLOCK.getId(state.getBlock()).toString();
            neededMap.merge(name, 1, Integer::sum);
        }

        Map<String, Integer> haveMap = countPlayerInventory();

        List<Entry> entries = new ArrayList<>();
        for (var e : neededMap.entrySet()) {
            int have = haveMap.getOrDefault(e.getKey(), 0);
            entries.add(new Entry(e.getKey(), e.getValue(), have));
        }
        entries.sort(Comparator.comparingInt(Entry::needed).reversed());
        return entries;
    }

    public static Map<String, Integer> countPlayerInventory() {
        Map<String, Integer> map = new HashMap<>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return map;
        ClientPlayerEntity player = client.player;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            map.merge(id, stack.getCount(), Integer::sum);
        }
        return map;
    }

    /** Pretty-prints a block ID like "minecraft:oak_log" → "Oak Log" */
    public static String prettyName(String blockId) {
        String path = blockId.contains(":") ? blockId.split(":")[1] : blockId;
        String[] words = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}