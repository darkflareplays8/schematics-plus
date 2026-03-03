package com.schematicsplus.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds captured block state data for a schematic region.
 * Serializes/deserializes itself to/from NBT for file storage.
 */
public class SchematicData {

    /** Relative block positions (offset from origin) → BlockState */
    private final Map<BlockPos, BlockState> blocks;
    private final BlockPos origin;
    private final int sizeX, sizeY, sizeZ;

    public SchematicData(Map<BlockPos, BlockState> blocks, BlockPos origin, int sizeX, int sizeY, int sizeZ) {
        this.blocks = blocks;
        this.origin = origin;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    public Map<BlockPos, BlockState> getBlocks() {
        return blocks;
    }

    public BlockPos getOrigin() {
        return origin;
    }

    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }
    public int getBlockCount() { return blocks.size(); }

    // ---------------------------------------------------------------
    // NBT Serialization
    // ---------------------------------------------------------------

    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();

        // Save dimensions
        root.putInt("SizeX", sizeX);
        root.putInt("SizeY", sizeY);
        root.putInt("SizeZ", sizeZ);

        // Save origin
        NbtCompound originNbt = new NbtCompound();
        originNbt.putInt("X", origin.getX());
        originNbt.putInt("Y", origin.getY());
        originNbt.putInt("Z", origin.getZ());
        root.put("Origin", originNbt);

        // Save blocks as a list of {Pos, State} entries
        NbtList blockList = new NbtList();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            NbtCompound blockEntry = new NbtCompound();

            NbtCompound posNbt = new NbtCompound();
            posNbt.putInt("X", entry.getKey().getX());
            posNbt.putInt("Y", entry.getKey().getY());
            posNbt.putInt("Z", entry.getKey().getZ());
            blockEntry.put("Pos", posNbt);

            blockEntry.put("State", NbtHelper.fromBlockState(entry.getValue()));
            blockList.add(blockEntry);
        }
        root.put("Blocks", blockList);

        return root;
    }

    public static SchematicData fromNbt(NbtCompound root, net.minecraft.registry.RegistryWrapper.WrapperLookup registryLookup) {
        int sizeX = root.getInt("SizeX");
        int sizeY = root.getInt("SizeY");
        int sizeZ = root.getInt("SizeZ");

        NbtCompound originNbt = root.getCompound("Origin");
        BlockPos origin = new BlockPos(originNbt.getInt("X"), originNbt.getInt("Y"), originNbt.getInt("Z"));

        NbtList blockList = root.getList("Blocks", 10); // 10 = NbtCompound tag type
        Map<BlockPos, BlockState> blocks = new HashMap<>();

        for (int i = 0; i < blockList.size(); i++) {
            NbtCompound blockEntry = blockList.getCompound(i);

            NbtCompound posNbt = blockEntry.getCompound("Pos");
            BlockPos pos = new BlockPos(posNbt.getInt("X"), posNbt.getInt("Y"), posNbt.getInt("Z"));

            BlockState state = NbtHelper.toBlockState(
                    registryLookup.getOrThrow(net.minecraft.registry.RegistryKeys.BLOCK),
                    blockEntry.getCompound("State")
            );

            blocks.put(pos, state);
        }

        return new SchematicData(blocks, origin, sizeX, sizeY, sizeZ);
    }
}
