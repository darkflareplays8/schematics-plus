package com.schematicsplus.render;

import com.schematicsplus.schematic.SchematicManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class SelectionBoxRenderer {

    // Grid lines only render when selection is <= this size in all axes
    private static final int GRID_MAX_SIZE = 20;

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(SelectionBoxRenderer::render);
    }

    private static void render(WorldRenderContext ctx) {
        SchematicManager mgr = SchematicManager.getInstance();
        SchematicManager.SelectionState state = mgr.getSelectionState();

        if (state == SchematicManager.SelectionState.NONE) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        BlockPos point1 = mgr.getPoint1();
        BlockPos point2 = (state == SchematicManager.SelectionState.POINT1_SET)
                ? client.player.getBlockPos()
                : mgr.getPoint2();

        int minX = Math.min(point1.getX(), point2.getX());
        int minY = Math.min(point1.getY(), point2.getY());
        int minZ = Math.min(point1.getZ(), point2.getZ());
        int maxX = Math.max(point1.getX(), point2.getX()) + 1;
        int maxY = Math.max(point1.getY(), point2.getY()) + 1;
        int maxZ = Math.max(point1.getZ(), point2.getZ()) + 1;

        int sizeX = maxX - minX;
        int sizeY = maxY - minY;
        int sizeZ = maxZ - minZ;

        // Frustum cull — skip rendering if the box is fully outside the view
        Vec3d cam = ctx.camera().getPos();
        double distSq = distanceSqToBox(cam, minX, minY, minZ, maxX, maxY, maxZ);
        if (distSq > 256 * 256) return; // don't render beyond 256 blocks

        float r, g, b;
        if (state == SchematicManager.SelectionState.POINT1_SET) {
            r = 0.0f; g = 0.4f; b = 1.0f; // blue
        } else {
            r = 0.0f; g = 1.0f; b = 0.3f; // green
        }

        MatrixStack matrices = ctx.matrixStack();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(2.0f);

        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Always draw the outer bounding box
        drawOuterBox(lines, matrix, minX, minY, minZ, maxX, maxY, maxZ, r, g, b);

        // Only draw grid if small enough in all three axes
        boolean drawGrid = sizeX <= GRID_MAX_SIZE && sizeY <= GRID_MAX_SIZE && sizeZ <= GRID_MAX_SIZE;
        if (drawGrid) {
            float gr = r * 0.6f, gg = g * 0.6f, gb = b * 0.6f;

            // X-axis grid lines (walk Y and Z, draw along X)
            for (int y = minY + 1; y < maxY; y++) {
                for (int z = minZ + 1; z < maxZ; z++) {
                    line(lines, matrix, minX, y, z, maxX, y, z, gr, gg, gb, 0.35f);
                }
            }
            // Z-axis grid lines (walk X and Y, draw along Z)
            for (int x = minX + 1; x < maxX; x++) {
                for (int y = minY + 1; y < maxY; y++) {
                    line(lines, matrix, x, y, minZ, x, y, maxZ, gr, gg, gb, 0.35f);
                }
            }
            // Y-axis grid lines (walk X and Z, draw along Y)
            for (int x = minX + 1; x < maxX; x++) {
                for (int z = minZ + 1; z < maxZ; z++) {
                    line(lines, matrix, x, minY, z, x, maxY, z, gr, gg, gb, 0.35f);
                }
            }
        }

        immediate.draw(RenderLayer.getLines());

        RenderSystem.disableBlend();
        matrices.pop();
    }

    // ----------------------------------------------------------------
    // Outer box — 12 edges, full opacity
    // ----------------------------------------------------------------

    private static void drawOuterBox(VertexConsumer lines, Matrix4f matrix,
                                     int x1, int y1, int z1,
                                     int x2, int y2, int z2,
                                     float r, float g, float b) {
        // Bottom
        line(lines, matrix, x1, y1, z1, x2, y1, z1, r, g, b, 1f);
        line(lines, matrix, x2, y1, z1, x2, y1, z2, r, g, b, 1f);
        line(lines, matrix, x2, y1, z2, x1, y1, z2, r, g, b, 1f);
        line(lines, matrix, x1, y1, z2, x1, y1, z1, r, g, b, 1f);
        // Top
        line(lines, matrix, x1, y2, z1, x2, y2, z1, r, g, b, 1f);
        line(lines, matrix, x2, y2, z1, x2, y2, z2, r, g, b, 1f);
        line(lines, matrix, x2, y2, z2, x1, y2, z2, r, g, b, 1f);
        line(lines, matrix, x1, y2, z2, x1, y2, z1, r, g, b, 1f);
        // Verticals
        line(lines, matrix, x1, y1, z1, x1, y2, z1, r, g, b, 1f);
        line(lines, matrix, x2, y1, z1, x2, y2, z1, r, g, b, 1f);
        line(lines, matrix, x2, y1, z2, x2, y2, z2, r, g, b, 1f);
        line(lines, matrix, x1, y1, z2, x1, y2, z2, r, g, b, 1f);
    }

    // ----------------------------------------------------------------
    // Single line helper
    // ----------------------------------------------------------------

    private static void line(VertexConsumer lines, Matrix4f matrix,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len == 0) len = 1;
        nx /= len; ny /= len; nz /= len;

        lines.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(nx, ny, nz);
        lines.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(nx, ny, nz);
    }

    // ----------------------------------------------------------------
    // Distance from camera to nearest point on the AABB
    // ----------------------------------------------------------------

    private static double distanceSqToBox(Vec3d cam,
                                          double minX, double minY, double minZ,
                                          double maxX, double maxY, double maxZ) {
        double dx = Math.max(0, Math.max(minX - cam.x, cam.x - maxX));
        double dy = Math.max(0, Math.max(minY - cam.y, cam.y - maxY));
        double dz = Math.max(0, Math.max(minZ - cam.z, cam.z - maxZ));
        return dx * dx + dy * dy + dz * dz;
    }
}