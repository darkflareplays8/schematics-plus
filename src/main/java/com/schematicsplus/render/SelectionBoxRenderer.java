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
            point2 = client.player.getBlockPos();
        } else {
            point2 = mgr.getPoint2();
        }

        double minX = Math.min(point1.getX(), point2.getX());
        double minY = Math.min(point1.getY(), point2.getY());
        double minZ = Math.min(point1.getZ(), point2.getZ());
        double maxX = Math.max(point1.getX(), point2.getX()) + 1.0;
        double maxY = Math.max(point1.getY(), point2.getY()) + 1.0;
        double maxZ = Math.max(point1.getZ(), point2.getZ()) + 1.0;

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
        RenderSystem.lineWidth(3.0f);

        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        org.joml.Matrix3f normalMatrix = matrices.peek().getNormalMatrix();

        drawBox(lines, matrix, normalMatrix, minX, minY, minZ, maxX, maxY, maxZ, r, g, b);

        immediate.draw(RenderLayer.getLines());

        RenderSystem.disableBlend();
        matrices.pop();
    }

    private static void drawBox(VertexConsumer lines, Matrix4f matrix, org.joml.Matrix3f normal,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                float r, float g, float b) {
        float ax = (float) x1, ay = (float) y1, az = (float) z1;
        float bx = (float) x2, by = (float) y2, bz = (float) z2;

        // Bottom face
        line(lines, matrix, normal, ax, ay, az, bx, ay, az, r, g, b);
        line(lines, matrix, normal, bx, ay, az, bx, ay, bz, r, g, b);
        line(lines, matrix, normal, bx, ay, bz, ax, ay, bz, r, g, b);
        line(lines, matrix, normal, ax, ay, bz, ax, ay, az, r, g, b);
        // Top face
        line(lines, matrix, normal, ax, by, az, bx, by, az, r, g, b);
        line(lines, matrix, normal, bx, by, az, bx, by, bz, r, g, b);
        line(lines, matrix, normal, bx, by, bz, ax, by, bz, r, g, b);
        line(lines, matrix, normal, ax, by, bz, ax, by, az, r, g, b);
        // Vertical edges
        line(lines, matrix, normal, ax, ay, az, ax, by, az, r, g, b);
        line(lines, matrix, normal, bx, ay, az, bx, by, az, r, g, b);
        line(lines, matrix, normal, bx, ay, bz, bx, by, bz, r, g, b);
        line(lines, matrix, normal, ax, ay, bz, ax, by, bz, r, g, b);
    }

    private static void line(VertexConsumer lines, Matrix4f matrix, org.joml.Matrix3f normal,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b) {
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len == 0) len = 1;
        nx /= len; ny /= len; nz /= len;

        lines.vertex(matrix, x1, y1, z1).color(r, g, b, 1.0f).normal(normal, nx, ny, nz);
        lines.vertex(matrix, x2, y2, z2).color(r, g, b, 1.0f).normal(normal, nx, ny, nz);
    }
}