package com.schematicsplus.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the active preview schematic state.
 *
 * FLOATING  = follows the player (anchor updates every tick, offset in front)
 * CONFIRMED = locked in place, anchor no longer moves
 */
public class PlacementManager {

    public enum PreviewState { NONE, FLOATING, CONFIRMED }

    private static final PlacementManager INSTANCE = new PlacementManager();
    public static PlacementManager getInstance() { return INSTANCE; }

    private SchematicData activeSchematic = null;
    private String activeName = null;
    private BlockPos anchorPos = BlockPos.ORIGIN;
    private PreviewState state = PreviewState.NONE;

    // ----------------------------------------------------------------

    public void startPreview(SchematicData data, String name, BlockPos anchor) {
        this.activeSchematic = data;
        this.activeName = name;
        this.anchorPos = anchor;
        this.state = PreviewState.FLOATING;
    }

    public void confirm() {
        if (state == PreviewState.FLOATING) state = PreviewState.CONFIRMED;
    }

    public void clearPreview() {
        activeSchematic = null;
        activeName = null;
        anchorPos = BlockPos.ORIGIN;
        state = PreviewState.NONE;
    }

    public boolean hasPreview()    { return state != PreviewState.NONE; }
    public boolean isConfirmed()   { return state == PreviewState.CONFIRMED; }
    public boolean isFloating()    { return state == PreviewState.FLOATING; }

    public SchematicData getActiveSchematic() { return activeSchematic; }
    public String getActiveName()             { return activeName; }
    public BlockPos getAnchorPos()            { return anchorPos; }
    public PreviewState getState()            { return state; }

    /**
     * Called every tick when FLOATING.
     * Places the schematic origin 3 blocks in front of the player
     * (horizontally), at foot level.
     */
    public void updateAnchor(ClientPlayerEntity player) {
        if (state != PreviewState.FLOATING) return;

        // Get the horizontal facing direction
        Direction facing = player.getHorizontalFacing();
        Vec3d feet = player.getPos();

        // Offset 3 blocks in front
        int offsetX = facing.getOffsetX() * 3;
        int offsetZ = facing.getOffsetZ() * 3;

        anchorPos = new BlockPos(
                (int) Math.floor(feet.x) + offsetX,
                (int) Math.floor(feet.y),
                (int) Math.floor(feet.z) + offsetZ
        );
    }

    /**
     * Absolute world positions → BlockState for the current anchor.
     * Only returns blocks that STILL need placing (not already in world).
     */
    public Map<BlockPos, BlockState> getWorldBlocks() {
        if (activeSchematic == null) return Map.of();
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (var entry : activeSchematic.getBlocks().entrySet()) {
            result.put(anchorPos.add(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /**
     * Returns only blocks that haven't been placed yet in the world.
     * Used for HUD material tracking.
     */
    public Map<BlockPos, BlockState> getRemainingBlocks() {
        if (activeSchematic == null) return Map.of();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return getWorldBlocks();

        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (var entry : activeSchematic.getBlocks().entrySet()) {
            BlockPos worldPos = anchorPos.add(entry.getKey());
            BlockState schematicState = entry.getValue();
            BlockState worldState = client.world.getBlockState(worldPos);
            // If the world block doesn't match the schematic block, it still needs placing
            if (!worldState.getBlock().equals(schematicState.getBlock())) {
                result.put(worldPos, schematicState);
            }
        }
        return result;
    }
}