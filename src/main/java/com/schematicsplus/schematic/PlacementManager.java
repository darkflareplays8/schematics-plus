package com.schematicsplus.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class PlacementManager {

    public enum PreviewState { NONE, FLOATING, CONFIRMED }

    private static final PlacementManager INSTANCE = new PlacementManager();
    public static PlacementManager getInstance() { return INSTANCE; }

    private SchematicData activeSchematic = null;
    private String activeName = null;
    private BlockPos anchorPos = BlockPos.ORIGIN;
    private PreviewState state = PreviewState.NONE;

    // Mutable copy of blocks — positions are removed when the player breaks them
    private Map<BlockPos, BlockState> relativeBlocks = new HashMap<>();

    public void startPreview(SchematicData data, String name, BlockPos anchor) {
        this.activeSchematic = data;
        this.activeName = name;
        this.anchorPos = anchor;
        this.state = PreviewState.FLOATING;
        // Copy blocks into mutable map keyed by RELATIVE pos
        this.relativeBlocks = new HashMap<>(data.getBlocks());
    }

    public void confirm() {
        if (state == PreviewState.FLOATING) state = PreviewState.CONFIRMED;
    }

    public void clearPreview() {
        activeSchematic = null;
        activeName = null;
        anchorPos = BlockPos.ORIGIN;
        state = PreviewState.NONE;
        relativeBlocks.clear();
    }

    /**
     * Called when the player breaks a block at a world position.
     * Removes that position from the schematic so the ghost doesn't reappear.
     */
    public void removeBlockAt(BlockPos worldPos) {
        if (state == PreviewState.NONE) return;
        BlockPos rel = worldPos.subtract(anchorPos);
        relativeBlocks.remove(rel);
    }

    public boolean hasPreview()  { return state != PreviewState.NONE; }
    public boolean isConfirmed() { return state == PreviewState.CONFIRMED; }
    public boolean isFloating()  { return state == PreviewState.FLOATING; }

    public SchematicData getActiveSchematic() { return activeSchematic; }
    public String getActiveName()             { return activeName; }
    public BlockPos getAnchorPos()            { return anchorPos; }
    public PreviewState getState()            { return state; }

    public void updateAnchor(ClientPlayerEntity player) {
        if (state != PreviewState.FLOATING) return;
        Vec3d feet = player.getPos();
        net.minecraft.util.math.Direction facing = player.getHorizontalFacing();
        int offsetX = facing.getOffsetX() * 3;
        int offsetZ = facing.getOffsetZ() * 3;
        anchorPos = new BlockPos(
                (int) Math.floor(feet.x) + offsetX,
                (int) Math.floor(feet.y),
                (int) Math.floor(feet.z) + offsetZ
        );
    }

    public Map<BlockPos, BlockState> getWorldBlocks() {
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (var entry : relativeBlocks.entrySet()) {
            result.put(anchorPos.add(entry.getKey()), entry.getValue());
        }
        return result;
    }

    public Map<BlockPos, BlockState> getRemainingBlocks() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return getWorldBlocks();

        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (var entry : relativeBlocks.entrySet()) {
            BlockPos worldPos = anchorPos.add(entry.getKey());
            BlockState schematicState = entry.getValue();
            BlockState worldState = client.world.getBlockState(worldPos);
            if (!worldState.getBlock().equals(schematicState.getBlock())) {
                result.put(worldPos, schematicState);
            }
        }
        return result;
    }
}