package com.schematicsplus.importer;

import com.schematicsplus.schematic.SchematicData;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports .litematic files (Litematica mod format).
 *
 * Structure:
 *   root
 *   └── Regions
 *       └── <RegionName>
 *           ├── BlockStatePalette (list of NbtCompound block states)
 *           ├── BlockStates (long[])  — packed bit array
 *           └── Size (x/y/z)
 */
public class LitematicaImporter {

    public static SchematicData load(Path file, RegistryWrapper.WrapperLookup registryLookup) throws IOException {
        NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());

        NbtCompound regions = root.getCompound("Regions");
        if (regions.isEmpty()) {
            throw new IOException("No regions found in .litematic file.");
        }

        // Merge all regions into one flat block map
        Map<BlockPos, BlockState> allBlocks = new HashMap<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (String regionName : regions.getKeys()) {
            NbtCompound region = regions.getCompound(regionName);

            // Region position offset
            NbtCompound pos = region.getCompound("Position");
            int posX = pos.getInt("x");
            int posY = pos.getInt("y");
            int posZ = pos.getInt("z");

            // Region size (can be negative in Litematica)
            NbtCompound size = region.getCompound("Size");
            int sizeX = size.getInt("x");
            int sizeY = size.getInt("y");
            int sizeZ = size.getInt("z");

            int absX = Math.abs(sizeX);
            int absY = Math.abs(sizeY);
            int absZ = Math.abs(sizeZ);
            int totalBlocks = absX * absY * absZ;

            // Build palette
            NbtList paletteNbt = region.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
            List<BlockState> palette = new ArrayList<>();
            for (int i = 0; i < paletteNbt.size(); i++) {
                NbtCompound entry = paletteNbt.getCompound(i);
                BlockState state = parseBlockState(entry, registryLookup);
                palette.add(state);
            }

            if (palette.isEmpty()) continue;

            // Calculate bits per block (minimum 2)
            int bitsPerBlock = Math.max(2, ceilLog2(palette.size()));

            // Read packed long array
            long[] blockStates = region.getLongArray("BlockStates");

            // Decode each block
            for (int i = 0; i < totalBlocks; i++) {
                int paletteIndex = readBits(blockStates, i, bitsPerBlock);
                if (paletteIndex < 0 || paletteIndex >= palette.size()) continue;

                BlockState state = palette.get(paletteIndex);
                if (state.getBlock() == Blocks.AIR) continue;

                // Litematica index order: x + z*sizeX + y*sizeX*sizeZ
                int x = i % absX;
                int z = (i / absX) % absZ;
                int y = i / (absX * absZ);

                BlockPos blockPos = new BlockPos(posX + x, posY + y, posZ + z);
                allBlocks.put(blockPos, state);

                minX = Math.min(minX, blockPos.getX());
                minY = Math.min(minY, blockPos.getY());
                minZ = Math.min(minZ, blockPos.getZ());
                maxX = Math.max(maxX, blockPos.getX());
                maxY = Math.max(maxY, blockPos.getY());
                maxZ = Math.max(maxZ, blockPos.getZ());
            }
        }

        if (allBlocks.isEmpty()) {
            throw new IOException("Litematic file contains no blocks.");
        }

        // Normalize to relative coords
        BlockPos origin = new BlockPos(minX, minY, minZ);
        Map<BlockPos, BlockState> relativeBlocks = new HashMap<>();
        for (var entry : allBlocks.entrySet()) {
            relativeBlocks.put(entry.getKey().subtract(origin), entry.getValue());
        }

        int sX = maxX - minX + 1;
        int sY = maxY - minY + 1;
        int sZ = maxZ - minZ + 1;

        return new SchematicData(relativeBlocks, origin, sX, sY, sZ);
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private static BlockState parseBlockState(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        try {
            String name = nbt.getString("Name");
            net.minecraft.util.Identifier id = net.minecraft.util.Identifier.of(name);
            net.minecraft.block.Block block = net.minecraft.registry.Registries.BLOCK.get(id);

            BlockState state = block.getDefaultState();

            if (nbt.contains("Properties", NbtElement.COMPOUND_TYPE)) {
                NbtCompound props = nbt.getCompound("Properties");
                for (String key : props.getKeys()) {
                    String value = props.getString(key);
                    state = applyProperty(state, key, value);
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
                    prop.parse(value).ifPresent(v ->
                            ((BlockState) state).with((net.minecraft.state.property.Property) prop, (Comparable) v)
                    );
                    // Re-fetch since BlockState is immutable
                    var parsed = prop.parse(value);
                    if (parsed.isPresent()) {
                        return state.with((net.minecraft.state.property.Property) prop, (Comparable) parsed.get());
                    }
                }
            }
        } catch (Exception ignored) {}
        return state;
    }

    private static int readBits(long[] array, int index, int bitsPerBlock) {
        long bitIndex = (long) index * bitsPerBlock;
        int longIndex = (int) (bitIndex / 64);
        int bitOffset = (int) (bitIndex % 64);

        if (longIndex >= array.length) return 0;

        long value = (array[longIndex] >>> bitOffset);

        // Handle crossing long boundary
        if (bitOffset + bitsPerBlock > 64 && longIndex + 1 < array.length) {
            value |= array[longIndex + 1] << (64 - bitOffset);
        }

        int mask = (1 << bitsPerBlock) - 1;
        return (int) (value & mask);
    }

    private static int ceilLog2(int value) {
        if (value <= 1) return 1;
        return 32 - Integer.numberOfLeadingZeros(value - 1);
    }
}