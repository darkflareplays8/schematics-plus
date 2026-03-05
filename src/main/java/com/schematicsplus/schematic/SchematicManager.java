package com.schematicsplus.schematic;

import com.schematicsplus.SchematicsPlusMod;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import com.schematicsplus.importer.LitematicaImporter;
import com.schematicsplus.importer.SpongeSchematicImporter;
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
     * Loads a schematic by name, auto-detecting format by extension.
     * Searches for: <name>.nbt, <name>.litematic, <name>.schem, <name>.schematic
     */
    public SchematicData loadSchematic(String name, RegistryWrapper.WrapperLookup registryLookup) throws IOException {
        String safe = sanitizeName(name);

        // Try each supported extension in order
        String[] extensions = {".nbt", ".litematic", ".schem", ".schematic"};
        Path found = null;
        String foundExt = null;

        for (String ext : extensions) {
            Path candidate = SchematicsPlusMod.SCHEMATICS_DIR.resolve(safe + ext);
            if (Files.exists(candidate)) {
                found = candidate;
                foundExt = ext;
                break;
            }
        }

        if (found == null) {
            throw new IOException("Schematic '" + name + "' not found. Supported formats: .nbt, .litematic, .schem, .schematic");
        }

        return switch (foundExt) {
            case ".litematic" -> LitematicaImporter.load(found, registryLookup);
            case ".schem", ".schematic" -> SpongeSchematicImporter.load(found, registryLookup);
            default -> {
                NbtCompound nbt = NbtIo.readCompressed(found, net.minecraft.nbt.NbtSizeTracker.ofUnlimitedBytes());
                yield SchematicData.fromNbt(nbt, registryLookup);
            }
        };
    }

    // ---------------------------------------------------------------
    // List
    // ---------------------------------------------------------------

    public List<String> listSchematics() {
        List<String> names = new ArrayList<>();
        try {
            Files.list(SchematicsPlusMod.SCHEMATICS_DIR)
                    .filter(p -> {
                        String n = p.toString();
                        return n.endsWith(".nbt") || n.endsWith(".litematic")
                                || n.endsWith(".schem") || n.endsWith(".schematic");
                    })
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        // Strip the extension
                        int dot = fileName.lastIndexOf('.');
                        String nameOnly = dot >= 0 ? fileName.substring(0, dot) : fileName;
                        // Show extension hint for foreign formats
                        String ext = dot >= 0 ? fileName.substring(dot) : "";
                        String display = ext.equals(".nbt") ? nameOnly : nameOnly + " §8(" + ext.substring(1) + ")";
                        names.add(display);
                    });
        } catch (IOException e) {
            SchematicsPlusMod.LOGGER.error("[Schematics+] Failed to list schematics", e);
        }
        return names;
    }

    // ---------------------------------------------------------------
    // Delete
    // ---------------------------------------------------------------

    public void deleteSchematic(String name) throws IOException {
        String safe = sanitizeName(name);
        String[] extensions = {".nbt", ".litematic", ".schem", ".schematic"};
        for (String ext : extensions) {
            Path candidate = SchematicsPlusMod.SCHEMATICS_DIR.resolve(safe + ext);
            if (Files.exists(candidate)) {
                Files.delete(candidate);
                SchematicsPlusMod.LOGGER.info("[Schematics+] Deleted '{}'", candidate);
                return;
            }
        }
        throw new IOException("Schematic '" + name + "' not found.");
    }

    // ---------------------------------------------------------------
    // Copy
    // ---------------------------------------------------------------

    public void copySchematic(String source, String dest, RegistryWrapper.WrapperLookup registryLookup) throws IOException {
        SchematicData data = loadSchematic(source, registryLookup);
        saveSchematic(dest, null, registryLookup, data);
    }

    /** Internal save that takes a pre-built SchematicData (used by copy) */
    private void saveSchematic(String name, ClientWorld world, RegistryWrapper.WrapperLookup registryLookup, SchematicData data) throws IOException {
        Path filePath = SchematicsPlusMod.SCHEMATICS_DIR.resolve(sanitizeName(name) + ".nbt");
        NbtIo.writeCompressed(data.toNbt(), filePath);
        SchematicsPlusMod.LOGGER.info("[Schematics+] Saved copy '{}' to {}", name, filePath);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Strip unsafe characters so the name is safe as a filename */
    private static String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}