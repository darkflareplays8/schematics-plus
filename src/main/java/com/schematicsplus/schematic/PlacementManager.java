package com.schematicsplus.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class PlacementManager {

    public enum PreviewState { NONE, FLOATING, CONFIRMED }

    private static final PlacementManager INSTANCE = new PlacementManager();
    public static PlacementManager getInstance() { return INSTANCE; }

    private SchematicData activeSchematic = null;
    private String activeName = null;
    private BlockPos anchorPos = BlockPos.ORIGIN;
    private PreviewState state = PreviewState.NONE;

    // Current transform state
    private int rotationSteps = 0; // 0=0°, 1=90°, 2=180°, 3=270°
    private boolean flippedX = false;
    private boolean flippedZ = false;

    // Mutable block map (relative coords) after transforms applied
    private Map<BlockPos, BlockState> relativeBlocks = new HashMap<>();
    private final Set<BlockPos> manuallyBroken = new HashSet<>();

    // Nearest missing block cache (updated on demand)
    private BlockPos nearestMissing = null;

    // ----------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------

    public void startPreview(SchematicData data, String name, BlockPos anchor) {
        this.activeSchematic = data;
        this.activeName = name;
        this.anchorPos = anchor;
        this.state = PreviewState.FLOATING;
        this.rotationSteps = 0;
        this.flippedX = false;
        this.flippedZ = false;
        this.manuallyBroken.clear();
        this.nearestMissing = null;
        rebuildTransformedBlocks();
    }

    public void confirm() {
        if (state == PreviewState.FLOATING) state = PreviewState.CONFIRMED;
    }

    public void clearPreview() {
        activeSchematic = null;
        activeName = null;
        anchorPos = BlockPos.ORIGIN;
        state = PreviewState.NONE;
        rotationSteps = 0;
        flippedX = false;
        flippedZ = false;
        relativeBlocks.clear();
        manuallyBroken.clear();
        nearestMissing = null;
    }

    // ----------------------------------------------------------------
    // Transform operations
    // ----------------------------------------------------------------

    /** Rotate 90° clockwise around Y axis */
    public void rotate() {
        rotationSteps = (rotationSteps + 1) % 4;
        rebuildTransformedBlocks();
    }

    /** Flip on X axis (mirrors east/west) */
    public void flipX() {
        flippedX = !flippedX;
        rebuildTransformedBlocks();
    }

    /** Flip on Z axis (mirrors north/south) */
    public void flipZ() {
        flippedZ = !flippedZ;
        rebuildTransformedBlocks();
    }

    public int getRotationDegrees() { return rotationSteps * 90; }
    public boolean isFlippedX() { return flippedX; }
    public boolean isFlippedZ() { return flippedZ; }

    /**
     * Rebuilds relativeBlocks from the original schematic data,
     * applying the current rotation and flip transforms.
     */
    private void rebuildTransformedBlocks() {
        if (activeSchematic == null) return;

        BlockRotation rotation = switch (rotationSteps) {
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };

        Map<BlockPos, BlockState> transformed = new HashMap<>();

        for (var entry : activeSchematic.getBlocks().entrySet()) {
            BlockPos rel = entry.getKey();
            BlockState state = entry.getValue();

            // Apply flip
            int x = rel.getX();
            int y = rel.getY();
            int z = rel.getZ();

            if (flippedX) x = -x;
            if (flippedZ) z = -z;

            // Apply rotation to position
            BlockPos rotated = rotatePos(new BlockPos(x, y, z), rotation);

            // Apply rotation + flip to block state properties (facing, axis etc)
            BlockState transformedState = state.rotate(rotation);
            if (flippedX) transformedState = transformedState.mirror(BlockMirror.LEFT_RIGHT);
            if (flippedZ) transformedState = transformedState.mirror(BlockMirror.FRONT_BACK);

            transformed.put(rotated, transformedState);
        }

        // Normalize so minimum coord is 0,0,0
        int minX = transformed.keySet().stream().mapToInt(BlockPos::getX).min().orElse(0);
        int minY = transformed.keySet().stream().mapToInt(BlockPos::getY).min().orElse(0);
        int minZ = transformed.keySet().stream().mapToInt(BlockPos::getZ).min().orElse(0);

        relativeBlocks.clear();
        for (var entry : transformed.entrySet()) {
            BlockPos norm = entry.getKey().subtract(new BlockPos(minX, minY, minZ));
            relativeBlocks.put(norm, entry.getValue());
        }

        manuallyBroken.clear();
    }

    private static BlockPos rotatePos(BlockPos pos, BlockRotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90      -> new BlockPos(-pos.getZ(), pos.getY(),  pos.getX());
            case CLOCKWISE_180     -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos( pos.getZ(), pos.getY(), -pos.getX());
            default                -> pos;
        };
    }

    // ----------------------------------------------------------------
    // Nudge
    // ----------------------------------------------------------------

    public void nudge(Direction direction) {
        anchorPos = anchorPos.offset(direction);
    }

    // ----------------------------------------------------------------
    // Block break tracking
    // ----------------------------------------------------------------

    public void removeBlockAt(BlockPos worldPos) {
        if (state == PreviewState.NONE) return;
        BlockPos rel = worldPos.subtract(anchorPos);
        relativeBlocks.remove(rel);
        manuallyBroken.add(worldPos.toImmutable());
        nearestMissing = null; // invalidate cache
    }

    public boolean wasManuallyBroken(BlockPos worldPos) {
        return manuallyBroken.contains(worldPos);
    }

    // ----------------------------------------------------------------
    // Nearest missing block
    // ----------------------------------------------------------------

    /**
     * Finds and caches the world position of the nearest block that
     * still needs placing, relative to the given position.
     */
    public BlockPos findNearestMissing(BlockPos from) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;

        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (var entry : relativeBlocks.entrySet()) {
            BlockPos worldPos = anchorPos.add(entry.getKey());
            BlockState worldState = client.world.getBlockState(worldPos);
            if (worldState.getBlock().equals(entry.getValue().getBlock())) continue; // already placed

            double dist = from.getSquaredDistance(worldPos);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = worldPos;
            }
        }

        nearestMissing = nearest;
        return nearest;
    }

    public BlockPos getNearestMissing() { return nearestMissing; }

    // ----------------------------------------------------------------
    // Anchor + state
    // ----------------------------------------------------------------

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
        Direction facing = player.getHorizontalFacing();

        // Calculate the schematic's depth along the facing axis so it
        // sits fully in front of the player with a 2-block gap.
        int depthX = relativeBlocks.keySet().stream().mapToInt(BlockPos::getX).max().orElse(0) + 1;
        int depthZ = relativeBlocks.keySet().stream().mapToInt(BlockPos::getZ).max().orElse(0) + 1;
        int gap = 2;
        int offsetX = facing.getOffsetX() * (gap + (facing.getAxis() == net.minecraft.util.math.Direction.Axis.X ? depthX : depthZ));
        int offsetZ = facing.getOffsetZ() * (gap + (facing.getAxis() == net.minecraft.util.math.Direction.Axis.Z ? depthZ : depthX));

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

            // Already correctly placed — skip
            if (worldState.getBlock().equals(schematicState.getBlock())) continue;

            // Overlapping with a DIFFERENT block — don't count as needing materials
            // (you can't place there yet anyway; once the overlap is cleared it will appear)
            if (!(worldState.getBlock() instanceof net.minecraft.block.AirBlock)) continue;

            result.put(worldPos, schematicState);
        }
        return result;
    }
}