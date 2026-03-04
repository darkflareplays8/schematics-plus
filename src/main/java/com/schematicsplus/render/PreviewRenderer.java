package com.schematicsplus.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.schematicsplus.schematic.MaterialList;
import com.schematicsplus.schematic.PlacementManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.AirBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Renders:
 *  1. Ghost block models for the active schematic preview
 *     - Normal block model, tinted cyan (clear) or red (overlap)
 *     - Blocks that are already placed correctly are skipped
 *  2. Top-right HUD overlay — live material tracker
 */
public class PreviewRenderer {

    private static final int HUD_MAX_ENTRIES = 10;

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(PreviewRenderer::renderPreview);
        HudRenderCallback.EVENT.register(PreviewRenderer::renderHud);
    }

    // ================================================================
    //  WORLD — ghost block rendering
    // ================================================================

    private static void renderPreview(WorldRenderContext ctx) {
        PlacementManager pm = PlacementManager.getInstance();
        if (!pm.hasPreview()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        Map<BlockPos, BlockState> worldBlocks = pm.getWorldBlocks();
        if (worldBlocks.isEmpty()) return;

        Vec3d cam = ctx.camera().getPos();
        MatrixStack matrices = ctx.matrixStack();

        BlockRenderManager blockRenderer = client.getBlockRenderManager();

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO
        );
        RenderSystem.disableDepthTest();
        RenderSystem.polygonOffset(-1f, -10f);
        RenderSystem.enablePolygonOffset();

        Tessellator tessellator = Tessellator.getInstance();

        for (var entry : worldBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState schematicState = entry.getValue();

            if (schematicState.getBlock() instanceof AirBlock) continue;
            if (schematicState.getRenderType() == BlockRenderType.INVISIBLE) continue;

            // Distance cull
            double dx = pos.getX() + 0.5 - cam.x;
            double dy = pos.getY() + 0.5 - cam.y;
            double dz = pos.getZ() + 0.5 - cam.z;
            if (dx*dx + dy*dy + dz*dz > 48*48) continue;

            BlockState worldState = client.world.getBlockState(pos);
            boolean alreadyPlaced = worldState.getBlock().equals(schematicState.getBlock());
            if (alreadyPlaced) continue; // already done, don't render ghost

            boolean overlaps = !(worldState.getBlock() instanceof AirBlock);

            // Tint: cyan = clear, red = conflict
            float r, g, b, a;
            if (overlaps) {
                r = 1.0f; g = 0.1f; b = 0.1f; a = 0.55f;
            } else {
                r = 0.3f; g = 0.85f; b = 1.0f; a = 0.45f;
            }

            matrices.push();
            matrices.translate(
                    pos.getX() - cam.x,
                    pos.getY() - cam.y,
                    pos.getZ() - cam.z
            );

            // Render a tinted wireframe box for this block
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            Matrix4f mat = matrices.peek().getPositionMatrix();
            drawFilledBox(buffer, mat, 0, 0, 0, 1, 1, 1, r, g, b, a);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            BufferRenderer.drawWithGlobalProgram(buffer.end());

            matrices.pop();
        }

        // Draw outlines on top
        RenderSystem.lineWidth(1.2f);
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f lineMatrix = matrices.peek().getPositionMatrix();

        for (var entry : worldBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState schematicState = entry.getValue();
            if (schematicState.getBlock() instanceof AirBlock) continue;

            double dx = pos.getX() + 0.5 - cam.x;
            double dy = pos.getY() + 0.5 - cam.y;
            double dz = pos.getZ() + 0.5 - cam.z;
            if (dx*dx + dy*dy + dz*dz > 48*48) continue;

            BlockState worldState = client.world.getBlockState(pos);
            if (worldState.getBlock().equals(schematicState.getBlock())) continue;

            boolean overlaps = !(worldState.getBlock() instanceof AirBlock);
            float r = overlaps ? 1.0f : 0.2f;
            float g = overlaps ? 0.1f : 0.9f;
            float b = overlaps ? 0.1f : 1.0f;

            drawBlockOutline(lines, lineMatrix,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1f, pos.getY() + 1f, pos.getZ() + 1f,
                    r, g, b, 0.9f);
        }

        immediate.draw(RenderLayer.getLines());
        matrices.pop();

        RenderSystem.disablePolygonOffset();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ================================================================
    //  HUD — top-right materials list, live updating
    // ================================================================

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        PlacementManager pm = PlacementManager.getInstance();
        if (!pm.hasPreview()) return;
        if (pm.getActiveSchematic() == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = client.textRenderer;

        // Use REMAINING blocks (not placed yet) for live tracking
        Map<BlockPos, BlockState> remaining = pm.getRemainingBlocks();
        List<MaterialList.Entry> entries = MaterialList.buildForRemainingBlocks(remaining);

        if (entries.isEmpty()) return;

        int screenW = client.getWindow().getScaledWidth();
        int panelX = screenW - 162;
        int panelY = 10;
        int lineH = 11;

        int displayCount = Math.min(entries.size(), HUD_MAX_ENTRIES);
        int extraLine = entries.size() > HUD_MAX_ENTRIES ? lineH : 0;
        int statusLine = pm.isConfirmed() ? lineH : 0;
        int panelH = 14 + displayCount * lineH + extraLine + statusLine + 2;

        // Background panel
        context.fill(panelX - 4, panelY - 4, screenW - 4, panelY + panelH, 0xBB000000);

        // Status line
        String statusText = pm.isConfirmed() ? "§a✔ Confirmed — /schematic build" : "§e● Floating — /schematic confirm";
        context.drawTextWithShadow(font, statusText, panelX, panelY, 0xFFFFFF);
        panelY += 12;

        // Title
        context.drawTextWithShadow(font, "§bMaterials needed", panelX, panelY, 0xFFFFFF);
        panelY += 12;

        for (int i = 0; i < displayCount; i++) {
            MaterialList.Entry e = entries.get(i);
            String name = MaterialList.prettyName(e.blockName());
            if (name.length() > 13) name = name.substring(0, 12) + "…";

            boolean enough = e.inInventory() >= e.needed();
            String color = enough ? "§a" : (e.inInventory() > 0 ? "§e" : "§c");
            String inv = e.inInventory() + "§7/§f" + e.needed();

            context.drawTextWithShadow(font, color + name, panelX, panelY, 0xFFFFFF);
            context.drawTextWithShadow(font, color + inv, panelX + 98, panelY, 0xFFFFFF);
            panelY += lineH;
        }

        if (entries.size() > HUD_MAX_ENTRIES) {
            context.drawTextWithShadow(font,
                    "§7+ " + (entries.size() - HUD_MAX_ENTRIES) + " more…",
                    panelX, panelY, 0xFFFFFF);
        }
    }

    // ================================================================
    //  Geometry helpers
    // ================================================================

    private static void drawFilledBox(BufferBuilder buf, Matrix4f m,
                                      float x1, float y1, float z1,
                                      float x2, float y2, float z2,
                                      float r, float g, float b, float a) {
        // 6 faces as quads
        // Bottom
        buf.vertex(m, x1, y1, z1).color(r, g, b, a);
        buf.vertex(m, x2, y1, z1).color(r, g, b, a);
        buf.vertex(m, x2, y1, z2).color(r, g, b, a);
        buf.vertex(m, x1, y1, z2).color(r, g, b, a);
        // Top
        buf.vertex(m, x1, y2, z1).color(r, g, b, a);
        buf.vertex(m, x1, y2, z2).color(r, g, b, a);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a);
        buf.vertex(m, x2, y2, z1).color(r, g, b, a);
        // North
        buf.vertex(m, x1, y1, z1).color(r, g, b, a);
        buf.vertex(m, x1, y2, z1).color(r, g, b, a);
        buf.vertex(m, x2, y2, z1).color(r, g, b, a);
        buf.vertex(m, x2, y1, z1).color(r, g, b, a);
        // South
        buf.vertex(m, x1, y1, z2).color(r, g, b, a);
        buf.vertex(m, x2, y1, z2).color(r, g, b, a);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a);
        buf.vertex(m, x1, y2, z2).color(r, g, b, a);
        // West
        buf.vertex(m, x1, y1, z1).color(r, g, b, a);
        buf.vertex(m, x1, y1, z2).color(r, g, b, a);
        buf.vertex(m, x1, y2, z2).color(r, g, b, a);
        buf.vertex(m, x1, y2, z1).color(r, g, b, a);
        // East
        buf.vertex(m, x2, y1, z1).color(r, g, b, a);
        buf.vertex(m, x2, y2, z1).color(r, g, b, a);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a);
        buf.vertex(m, x2, y1, z2).color(r, g, b, a);
    }

    private static void drawBlockOutline(VertexConsumer lines, Matrix4f m,
                                         float x1, float y1, float z1,
                                         float x2, float y2, float z2,
                                         float r, float g, float b, float a) {
        line(lines, m, x1,y1,z1, x2,y1,z1, r,g,b,a);
        line(lines, m, x2,y1,z1, x2,y1,z2, r,g,b,a);
        line(lines, m, x2,y1,z2, x1,y1,z2, r,g,b,a);
        line(lines, m, x1,y1,z2, x1,y1,z1, r,g,b,a);
        line(lines, m, x1,y2,z1, x2,y2,z1, r,g,b,a);
        line(lines, m, x2,y2,z1, x2,y2,z2, r,g,b,a);
        line(lines, m, x2,y2,z2, x1,y2,z2, r,g,b,a);
        line(lines, m, x1,y2,z2, x1,y2,z1, r,g,b,a);
        line(lines, m, x1,y1,z1, x1,y2,z1, r,g,b,a);
        line(lines, m, x2,y1,z1, x2,y2,z1, r,g,b,a);
        line(lines, m, x2,y1,z2, x2,y2,z2, r,g,b,a);
        line(lines, m, x1,y1,z2, x1,y2,z2, r,g,b,a);
    }

    private static void line(VertexConsumer buf, Matrix4f m,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        float nx = x2-x1, ny = y2-y1, nz = z2-z1;
        float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
        if (len == 0) len = 1;
        nx /= len; ny /= len; nz /= len;
        buf.vertex(m, x1, y1, z1).color(r, g, b, a).normal(nx, ny, nz);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a).normal(nx, ny, nz);
    }
}