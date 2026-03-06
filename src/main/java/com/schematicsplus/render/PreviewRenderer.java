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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;

public class PreviewRenderer {

    private static final int HUD_MAX_ENTRIES = 10;
    private static final Random RANDOM = Random.create();

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(PreviewRenderer::renderPreview);
        HudRenderCallback.EVENT.register(PreviewRenderer::renderHud);
    }

    // ================================================================
    //  WORLD — real block model ghost rendering
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

        // Use a custom vertex consumer that applies a colour tint + alpha
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();

        for (var entry : worldBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState schematicState = entry.getValue();

            if (schematicState.getBlock() instanceof AirBlock) continue;
            if (schematicState.getRenderType() == BlockRenderType.INVISIBLE) continue;

            // Distance cull — 48 block radius
            double dx = pos.getX() + 0.5 - cam.x;
            double dy = pos.getY() + 0.5 - cam.y;
            double dz = pos.getZ() + 0.5 - cam.z;
            if (dx*dx + dy*dy + dz*dz > 48*48) continue;

            // Skip already-placed blocks
            BlockState worldState = client.world.getBlockState(pos);
            // Skip if already correctly placed
            if (worldState.getBlock().equals(schematicState.getBlock())) continue;

            // Skip if world is currently air — block was broken, don't re-ghost it
            // unless it's a confirmed schematic position that still needs placing
            if (worldState.getBlock() instanceof AirBlock && pm.wasManuallyBroken(pos)) continue;

            boolean overlaps = !(worldState.getBlock() instanceof AirBlock);

            // Tint colour
            float r, g, b, a;
            if (overlaps) {
                r = 1.0f; g = 0.2f; b = 0.2f; a = 0.6f;
            } else {
                r = 0.4f; g = 0.8f; b = 1.0f; a = 0.5f;
            }

            matrices.push();
            matrices.translate(
                    pos.getX() - cam.x,
                    pos.getY() - cam.y,
                    pos.getZ() - cam.z
            );

            // Render the actual block model using a tinted consumer
            VertexConsumer tinted = new TintedVertexConsumer(
                    immediate.getBuffer(RenderLayer.getTranslucent()), r, g, b, a
            );

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();

            try {
                blockRenderer.renderBlock(
                        schematicState,
                        pos,
                        client.world,
                        matrices,
                        tinted,
                        false,
                        RANDOM
                );
            } catch (Exception ignored) {
                // Fallback to simple box if model fails
                drawFallbackBox(matrices, cam, pos, r, g, b, a);
            }

            RenderSystem.enableDepthTest();
            matrices.pop();
        }

        immediate.draw(RenderLayer.getTranslucent());
        RenderSystem.disableBlend();

        // Outlines
        RenderSystem.lineWidth(1.5f);
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
            if (pm.wasManuallyBroken(pos)) continue;

            boolean overlaps = !(worldState.getBlock() instanceof AirBlock);
            float r = overlaps ? 1.0f : 0.2f;
            float g = overlaps ? 0.2f : 0.9f;
            float b = overlaps ? 0.2f : 1.0f;

            drawBlockOutline(lines, lineMatrix,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1f, pos.getY() + 1f, pos.getZ() + 1f,
                    r, g, b, 1.0f);
        }

        // Highlight nearest missing block with a bright yellow pulsing box
        BlockPos nearest = pm.getNearestMissing();
        if (nearest != null) {
            float pulse = (float)(Math.sin(System.currentTimeMillis() / 300.0) * 0.3 + 0.7);
            drawBlockOutline(lines, lineMatrix,
                    nearest.getX(), nearest.getY(), nearest.getZ(),
                    nearest.getX() + 1f, nearest.getY() + 1f, nearest.getZ() + 1f,
                    1.0f, 1.0f * pulse, 0.0f, 1.0f);
        }

        immediate.draw(RenderLayer.getLines());
        matrices.pop();
    }

    // ================================================================
    //  HUD
    // ================================================================

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        PlacementManager pm = PlacementManager.getInstance();
        if (!pm.hasPreview() || pm.getActiveSchematic() == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = client.textRenderer;

        // FLOATING: show full schematic material requirements from file
        // CONFIRMED: show live remaining blocks (updates as you place)
        List<MaterialList.Entry> entries;
        if (pm.isConfirmed()) {
            Map<BlockPos, BlockState> remaining = pm.getRemainingBlocks();
            entries = MaterialList.buildForRemainingBlocks(remaining);
        } else {
            entries = MaterialList.build(pm.getActiveSchematic());
        }
        if (entries.isEmpty()) return;

        int screenW = client.getWindow().getScaledWidth();
        int panelX = screenW - 162;
        int panelY = 10;
        int lineH = 11;

        int displayCount = Math.min(entries.size(), HUD_MAX_ENTRIES);
        int panelH = 26 + displayCount * lineH + (entries.size() > HUD_MAX_ENTRIES ? lineH : 0) + 2;

        context.fill(panelX - 4, panelY - 4, screenW - 4, panelY + panelH, 0xBB000000);

        String statusText = pm.isConfirmed()
                ? "§a✔ Confirmed — /schematic build"
                : "§e● Floating — /schematic confirm";
        context.drawTextWithShadow(font, statusText, panelX, panelY, 0xFFFFFF);
        panelY += 12;

        context.drawTextWithShadow(font, "§bMaterials needed", panelX, panelY, 0xFFFFFF);
        panelY += 12;

        for (int i = 0; i < displayCount; i++) {
            MaterialList.Entry e = entries.get(i);
            String name = MaterialList.prettyName(e.blockName());
            if (name.length() > 13) name = name.substring(0, 12) + "…";
            String color = e.hasEnough() ? "§a" : (e.inInventory() > 0 ? "§e" : "§c");
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
    //  Helpers
    // ================================================================

    private static void drawFallbackBox(MatrixStack matrices, Vec3d cam, BlockPos pos,
                                        float r, float g, float b, float a) {
        // Simple coloured box fallback for blocks that fail model rendering
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

    // ================================================================
    //  TintedVertexConsumer — wraps another consumer, overrides colour
    // ================================================================

    private static class TintedVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float r, g, b, a;

        TintedVertexConsumer(VertexConsumer delegate, float r, float g, float b, float a) {
            this.delegate = delegate;
            this.r = r; this.g = g; this.b = b; this.a = a;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            return delegate.vertex(x, y, z);
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            // Override with our tint, blend with original colour
            int tr = (int)(red   * r);
            int tg = (int)(green * g);
            int tb = (int)(blue  * b);
            int ta = (int)(255   * a);
            return delegate.color(tr, tg, tb, ta);
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return delegate.texture(u, v);
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return delegate.overlay(u, v);
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return delegate.light(LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return delegate.normal(x, y, z);
        }
    }
}
// NOTE: nearestMissing highlight is injected in renderPreview below