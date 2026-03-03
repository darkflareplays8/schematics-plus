package com.schematicsplus.schematic;

import com.schematicsplus.SchematicsPlusMod;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton manager that tracks the selection state and handles
 * saving / loading schematics to disk.
 */
public class SchematicManager {

    private static final SchematicManager INSTANCE = new SchematicManager();
    public static SchematicManager getInstance() { return INSTANCE; }

    // ---------------------------------------------------------------
    // Selection state
    // ---------------------------------------------------------------

    private BlockPos point1 = null;
    private BlockPos point2 = null;

    public enum SelectionState { NONE, POINT1_SET, READY }

    public SelectionState getSelectionState() {
        if (point1 == null) return SelectionState.NONE;
        if (point2 == null) return SelectionState.POINT1_SET;
        return SelectionState.READY;
    }

    /**
     * Called each time the player runs /schematic select.
     * Returns which point was just set.
     */
    public int recordSelectPoint(BlockPos playerPos) {
        if (point1 == null) {
            point1 = playerPos;
            return 1;
        } else {
            point2 = playerPos;
            return 2;
        }
    }

    /** Clears both selection points */
    public void clearSelection() {
        point1 = null;
        point2 = null;
    }

    public BlockPos getPoint1() { return point1; }
    public BlockPos getPoint2() { return point2; }

    // ---------------------------------------------------------------
    // Save
    // ---------------------------------------------------------------

    /**
     * Captures all blocks in the selected region and writes them to
     * .minecraft/schematics+/<name>.nbt
     */
    public void saveSchematic(String name, ClientWorld world, RegistryWrapper.WrapperLookup registryLookup) throws IOException {
        if (point1 == null || point2 == null) {
            throw new IllegalStateException("Selection is incomplete.");
        }

        int minX = Math.min(point1.getX(), point2.getX());
        int minY = Math.min(point1.getY(), point2.getY());
        int minZ = Math.min(point1.getZ(), point2.getZ());
        int maxX = Math.max(point1.getX(), point2.getX());
        int maxY = Math.max(point1.getY(), point2.getY());
        int maxZ = Math.max(point1.getZ(), point2.getZ());

        BlockPos origin = new BlockPos(minX, minY, minZ);
        Map<BlockPos, BlockState> blocks = new HashMap<>();

        // Capture every block in the bounding box as relative coords
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos worldPos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(worldPos);
                    // Store as relative offset from origin
                    BlockPos relPos = new BlockPos(x - minX, y - minY, z - minZ);
                    blocks.put(relPos, state);
                }
            }
        }

        SchematicData data = new SchematicData(
                blocks,
                origin,
                maxX - minX + 1,
                maxY - minY + 1,
                maxZ - minZ + 1
        );

        Path filePath = SchematicsPlusMod.SCHEMATICS_DIR.resolve(sanitizeName(name) + ".nbt");
        NbtIo.writeCompressed(data.toNbt(), filePath);

        SchematicsPlusMod.LOGGER.info("[Schematics+] Saved '{}' ({} blocks) to {}", name, blocks.size(), filePath);
    }

    // ---------------------------------------------------------------
    // Load
    // ---------------------------------------------------------------

    /**
     * Reads a schematic file and places every block at its world position,
     * offset from the player's current feet position.
     */
    public SchematicData loadSchematic(String name, RegistryWrapper.WrapperLookup registryLookup) throws IOException {
        Path filePath = SchematicsPlusMod.SCHEMATICS_DIR.resolve(sanitizeName(name) + ".nbt");

        if (!Files.exists(filePath)) {
            throw new IOException("Schematic '" + name + "' not found. Check your spelling or use /schematic list.");
        }

        NbtCompound nbt = NbtIo.readCompressed(filePath, net.minecraft.nbt.NbtSizeTracker.ofUnlimitedBytes());
        return SchematicData.fromNbt(nbt, registryLookup);
    }

    // ---------------------------------------------------------------
    // List
    // ---------------------------------------------------------------

    public List<String> listSchematics() {
        List<String> names = new ArrayList<>();
        try {
            Files.list(SchematicsPlusMod.SCHEMATICS_DIR)
                    .filter(p -> p.toString().endsWith(".nbt"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        names.add(fileName.substring(0, fileName.length() - 4));
                    });
        } catch (IOException e) {
            SchematicsPlusMod.LOGGER.error("[Schematics+] Failed to list schematics", e);
        }
        return names;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Strip unsafe characters so the name is safe as a filename */
    private static String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
