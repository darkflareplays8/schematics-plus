package com.schematicsplus.importer;

import com.schematicsplus.schematic.SchematicData;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports WorldEdit schematic files.
 *
 * Supports:
 *  - Sponge Schematic v2/v3  (.schem)  — modern WorldEdit format
 *  - MCEdit Schematic        (.schematic) — legacy format, still common
 */
public class SpongeSchematicImporter {

    public static SchematicData load(Path file, RegistryWrapper.WrapperLookup registryLookup) throws IOException {
        NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());

        // Detect format: Sponge v2/v3 has "Version" key, MCEdit has "Blocks" byte array
        if (root.contains("Version") || root.contains("Schematic")) {
            return loadSponge(root, registryLookup);
        } else if (root.contains("Blocks")) {
            return loadMcEdit(root);
        } else {
            throw new IOException("Unknown schematic format.");
        }
    }

    // ----------------------------------------------------------------
    // Sponge Schematic v2 / v3
    // ----------------------------------------------------------------

    private static SchematicData loadSponge(NbtCompound root, RegistryWrapper.WrapperLookup registryLookup) throws IOException {
        // v3 wraps everything under "Schematic" key
        NbtCompound data = root.contains("Schematic") ? root.getCompound("Schematic") : root;

        int width  = data.getShort("Width")  & 0xFFFF;
        int height = data.getShort("Height") & 0xFFFF;
        int length = data.getShort("Length") & 0xFFFF;

        if (width == 0 || height == 0 || length == 0) {
            throw new IOException("Invalid schematic dimensions.");
        }

        // Build palette: index → BlockState
        NbtCompound paletteNbt = data.getCompound("Palette");
        BlockState[] palette = new BlockState[paletteNbt.getKeys().size()];

        for (String blockStr : paletteNbt.getKeys()) {
            int index = paletteNbt.getInt(blockStr);
            palette[index] = parseBlockStateString(blockStr);
        }

        // Block data is a varint-encoded byte array
        byte[] blockData = data.getByteArray("BlockData");
        Map<BlockPos, BlockState> blocks = new HashMap<>();

        int i = 0;
        int blockIndex = 0;
        while (i < blockData.length && blockIndex < width * height * length) {
            // Decode varint
            int value = 0;
            int shift = 0;
            byte b;
            do {
                b = blockData[i++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0 && i < blockData.length);

            if (value >= palette.length) { blockIndex++; continue; }
            BlockState state = palette[value];
            if (state == null || state.getBlock() == Blocks.AIR) { blockIndex++; continue; }

            // Sponge index order: y*width*length + z*width + x
            int x = blockIndex % width;
            int z = (blockIndex / width) % length;
            int y = blockIndex / (width * length);

            blocks.put(new BlockPos(x, y, z), state);
            blockIndex++;
        }

        if (blocks.isEmpty()) throw new IOException("Schematic contains no blocks.");

        BlockPos origin = new BlockPos(0, 0, 0);
        return new SchematicData(blocks, origin, width, height, length);
    }

    // ----------------------------------------------------------------
    // MCEdit legacy .schematic
    // ----------------------------------------------------------------

    private static SchematicData loadMcEdit(NbtCompound root) throws IOException {
        int width  = root.getShort("Width")  & 0xFFFF;
        int height = root.getShort("Height") & 0xFFFF;
        int length = root.getShort("Length") & 0xFFFF;

        if (width == 0 || height == 0 || length == 0) {
            throw new IOException("Invalid MCEdit schematic dimensions.");
        }

        byte[] blockIds   = root.getByteArray("Blocks");
        byte[] blockData  = root.getByteArray("Data");

        Map<BlockPos, BlockState> blocks = new HashMap<>();

        for (int index = 0; index < blockIds.length; index++) {
            int id = blockIds[index] & 0xFF;
            if (id == 0) continue; // air

            int x = index % width;
            int z = (index / width) % length;
            int y = index / (width * length);

            // Best-effort legacy ID → modern block conversion
            BlockState state = legacyIdToState(id);
            if (state != null) {
                blocks.put(new BlockPos(x, y, z), state);
            }
        }

        if (blocks.isEmpty()) throw new IOException("MCEdit schematic contains no recognizable blocks.");

        return new SchematicData(blocks, BlockPos.ORIGIN, width, height, length);
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /**
     * Parses a block state string like "minecraft:oak_stairs[facing=north,half=bottom]"
     */
    private static BlockState parseBlockStateString(String blockStr) {
        try {
            String blockId = blockStr.contains("[") ? blockStr.substring(0, blockStr.indexOf('[')) : blockStr;
            String propsStr = blockStr.contains("[") ? blockStr.substring(blockStr.indexOf('[') + 1, blockStr.length() - 1) : "";

            net.minecraft.block.Block block = Registries.BLOCK.get(Identifier.of(blockId));
            BlockState state = block.getDefaultState();

            if (!propsStr.isEmpty()) {
                for (String prop : propsStr.split(",")) {
                    String[] kv = prop.split("=");
                    if (kv.length == 2) {
                        state = applyProperty(state, kv[0].trim(), kv[1].trim());
                    }
                }
            }
            return state;
        } catch (Exception e) {
            return Blocks.AIR.getDefaultState();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState applyProperty(BlockState state, String key, String value) {
        try {
            for (net.minecraft.state.property.Property<?> prop : state.getProperties()) {
                if (prop.getName().equals(key)) {
                    var parsed = prop.parse(value);
                    if (parsed.isPresent()) {
                        return state.with((net.minecraft.state.property.Property) prop, (Comparable) parsed.get());
                    }
                }
            }
        } catch (Exception ignored) {}
        return state;
    }

    /**
     * Very basic legacy numeric block ID → modern BlockState.
     * Covers the most common blocks. Full flattening conversion would
     * require a full lookup table (thousands of entries).
     */
    private static BlockState legacyIdToState(int id) {
        return switch (id) {
            case 1  -> Registries.BLOCK.get(Identifier.of("minecraft:stone")).getDefaultState();
            case 2  -> Registries.BLOCK.get(Identifier.of("minecraft:grass_block")).getDefaultState();
            case 3  -> Registries.BLOCK.get(Identifier.of("minecraft:dirt")).getDefaultState();
            case 4  -> Registries.BLOCK.get(Identifier.of("minecraft:cobblestone")).getDefaultState();
            case 5  -> Registries.BLOCK.get(Identifier.of("minecraft:oak_planks")).getDefaultState();
            case 7  -> Registries.BLOCK.get(Identifier.of("minecraft:bedrock")).getDefaultState();
            case 8  -> Registries.BLOCK.get(Identifier.of("minecraft:water")).getDefaultState();
            case 10 -> Registries.BLOCK.get(Identifier.of("minecraft:lava")).getDefaultState();
            case 12 -> Registries.BLOCK.get(Identifier.of("minecraft:sand")).getDefaultState();
            case 13 -> Registries.BLOCK.get(Identifier.of("minecraft:gravel")).getDefaultState();
            case 14 -> Registries.BLOCK.get(Identifier.of("minecraft:gold_ore")).getDefaultState();
            case 15 -> Registries.BLOCK.get(Identifier.of("minecraft:iron_ore")).getDefaultState();
            case 16 -> Registries.BLOCK.get(Identifier.of("minecraft:coal_ore")).getDefaultState();
            case 17 -> Registries.BLOCK.get(Identifier.of("minecraft:oak_log")).getDefaultState();
            case 18 -> Registries.BLOCK.get(Identifier.of("minecraft:oak_leaves")).getDefaultState();
            case 20 -> Registries.BLOCK.get(Identifier.of("minecraft:glass")).getDefaultState();
            case 24 -> Registries.BLOCK.get(Identifier.of("minecraft:sandstone")).getDefaultState();
            case 35 -> Registries.BLOCK.get(Identifier.of("minecraft:white_wool")).getDefaultState();
            case 41 -> Registries.BLOCK.get(Identifier.of("minecraft:gold_block")).getDefaultState();
            case 42 -> Registries.BLOCK.get(Identifier.of("minecraft:iron_block")).getDefaultState();
            case 43 -> Registries.BLOCK.get(Identifier.of("minecraft:smooth_stone_slab")).getDefaultState();
            case 45 -> Registries.BLOCK.get(Identifier.of("minecraft:bricks")).getDefaultState();
            case 46 -> Registries.BLOCK.get(Identifier.of("minecraft:tnt")).getDefaultState();
            case 47 -> Registries.BLOCK.get(Identifier.of("minecraft:bookshelf")).getDefaultState();
            case 48 -> Registries.BLOCK.get(Identifier.of("minecraft:mossy_cobblestone")).getDefaultState();
            case 49 -> Registries.BLOCK.get(Identifier.of("minecraft:obsidian")).getDefaultState();
            case 52 -> Registries.BLOCK.get(Identifier.of("minecraft:spawner")).getDefaultState();
            case 54 -> Registries.BLOCK.get(Identifier.of("minecraft:chest")).getDefaultState();
            case 56 -> Registries.BLOCK.get(Identifier.of("minecraft:diamond_ore")).getDefaultState();
            case 57 -> Registries.BLOCK.get(Identifier.of("minecraft:diamond_block")).getDefaultState();
            case 58 -> Registries.BLOCK.get(Identifier.of("minecraft:crafting_table")).getDefaultState();
            case 61 -> Registries.BLOCK.get(Identifier.of("minecraft:furnace")).getDefaultState();
            case 67 -> Registries.BLOCK.get(Identifier.of("minecraft:cobblestone_stairs")).getDefaultState();
            case 73 -> Registries.BLOCK.get(Identifier.of("minecraft:redstone_ore")).getDefaultState();
            case 79 -> Registries.BLOCK.get(Identifier.of("minecraft:ice")).getDefaultState();
            case 80 -> Registries.BLOCK.get(Identifier.of("minecraft:snow_block")).getDefaultState();
            case 82 -> Registries.BLOCK.get(Identifier.of("minecraft:clay")).getDefaultState();
            case 86 -> Registries.BLOCK.get(Identifier.of("minecraft:carved_pumpkin")).getDefaultState();
            case 87 -> Registries.BLOCK.get(Identifier.of("minecraft:netherrack")).getDefaultState();
            case 88 -> Registries.BLOCK.get(Identifier.of("minecraft:soul_sand")).getDefaultState();
            case 89 -> Registries.BLOCK.get(Identifier.of("minecraft:glowstone")).getDefaultState();
            case 98 -> Registries.BLOCK.get(Identifier.of("minecraft:stone_bricks")).getDefaultState();
            case 99 -> Registries.BLOCK.get(Identifier.of("minecraft:brown_mushroom_block")).getDefaultState();
            case 100-> Registries.BLOCK.get(Identifier.of("minecraft:red_mushroom_block")).getDefaultState();
            case 103-> Registries.BLOCK.get(Identifier.of("minecraft:melon")).getDefaultState();
            case 110-> Registries.BLOCK.get(Identifier.of("minecraft:mycelium")).getDefaultState();
            case 112-> Registries.BLOCK.get(Identifier.of("minecraft:nether_bricks")).getDefaultState();
            case 121-> Registries.BLOCK.get(Identifier.of("minecraft:end_stone")).getDefaultState();
            case 133-> Registries.BLOCK.get(Identifier.of("minecraft:emerald_block")).getDefaultState();
            case 155-> Registries.BLOCK.get(Identifier.of("minecraft:quartz_block")).getDefaultState();
            case 159-> Registries.BLOCK.get(Identifier.of("minecraft:white_terracotta")).getDefaultState();
            case 162-> Registries.BLOCK.get(Identifier.of("minecraft:acacia_log")).getDefaultState();
            case 168-> Registries.BLOCK.get(Identifier.of("minecraft:prismarine")).getDefaultState();
            case 169-> Registries.BLOCK.get(Identifier.of("minecraft:sea_lantern")).getDefaultState();
            case 172-> Registries.BLOCK.get(Identifier.of("minecraft:terracotta")).getDefaultState();
            case 173-> Registries.BLOCK.get(Identifier.of("minecraft:coal_block")).getDefaultState();
            case 174-> Registries.BLOCK.get(Identifier.of("minecraft:packed_ice")).getDefaultState();
            default -> null;
        };
    }
}