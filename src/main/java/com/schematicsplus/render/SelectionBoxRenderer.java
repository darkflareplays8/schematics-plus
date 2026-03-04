package com.schematicsplus.render;

import com.schematicsplus.schematic.SchematicManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Renders a coloured wireframe box around the current schematic selection.
 *
 * Blue  = point 1 set, waiting for point 2 (box follows the player's position)
 * Green = both points set, ready to save
 */
public class SelectionBoxRenderer {

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
        BlockPos point2;

        if (state == SchematicManager.SelectionState.POINT1_SET) {
            // Second point follows the player live
            point2 = client.player.getBlockPos();
        } else {
            point2 = mgr.getPoint2();
        }

        // Build axis-aligned bounding box from the two corners
        double minX = Math.min(point1.getX(), point2.getX());
        double minY = Math.min(point1.getY(), point2.getY());
        double minZ = Math.min(point1.getZ(), point2.getZ());
        double maxX = Math.max(point1.getX(), point2.getX()) + 1.0;
        double maxY = Math.max(point1.getY(), point2.getY()) + 1.0;
        double maxZ = Math.max(point1.getZ(), point2.getZ()) + 1.0;

        Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);

        // Colour: blue while selecting, green when ready
        float r, g, b;
        if (state == SchematicManager.SelectionState.POINT1_SET) {
            r = 0.0f; g = 0.4f; b = 1.0f; // blue
        } else {
            r = 0.0f; g = 1.0f; b = 0.3f; // green
        }

        MatrixStack matrices = ctx.matrixStack();
        Vec3d cam = ctx.camera().getPos();

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0f);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        drawBox(buffer, matrix, box, r, g, b, 1.0f);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    private static void drawBox(BufferBuilder buffer, Matrix4f matrix, Box box, float r, float g, float b, float a) {
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        // Bottom face
        line(buffer, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(buffer, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(buffer, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);

        // Top face
        line(buffer, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(buffer, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(buffer, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);

        // Vertical edges
        line(buffer, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(buffer, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void line(BufferBuilder buf, Matrix4f m,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        buf.vertex(m, x1, y1, z1).color(r, g, b, a);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a);
    }
}