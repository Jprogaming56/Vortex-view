package com.personal.vortexview.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import com.personal.vortexview.AitBridge;
import com.personal.vortexview.VortexConfig;
import com.personal.vortexview.VortexViewMod;
import com.personal.vortexview.net.ViewPacket;
import com.personal.vortexview.net.VortexNet;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Full-screen time vortex. The vortex tunnel is AiT's own (using whatever vortex style the
 * TARDIS has selected), and the TARDIS is drawn with its current exterior, drifting, banking
 * and tumbling through it.
 */
public class VortexViewScreen extends Screen {
    private static final String ITEM_REGISTRY = "dev.amble.ait.registry.impl.exterior.ClientExteriorVariantRegistry";
    private static final String RENDER_LAYERS = "dev.amble.ait.client.renderers.AITRenderLayers";
    private static final String[] RENDER_NAMES = {"m_7695_", "renderToBuffer"};
    private static final int FULL_BRIGHT = 15728880;

    private final Screen parent;
    private final Object tardis;
    private final UUID tardisId;

    private Object vortex;
    private Object model;
    private ResourceLocation texture;
    private ResourceLocation emission;
    private boolean bedrock;
    private boolean broken;
    private int refreshIn;
    private int noticeTicks;

    public VortexViewScreen(Screen parent, Object tardis) throws Exception {
        super(Component.translatable("screen." + VortexViewMod.MOD_ID + ".title"));
        this.parent = parent;
        this.tardis = tardis;
        this.tardisId = (UUID) AitBridge.call(tardis, "getUuid");
    }

    @Override
    protected void init() {
        VortexNet.CHANNEL.sendToServer(new ViewPacket(tardisId, true));
        try {
            Object stats = AitBridge.call(tardis, "stats");
            Object reference = AitBridge.call(stats, "getVortexEffects");
            vortex = AitBridge.call(reference, "toUtil");
        } catch (Throwable t) {
            fail("vortex", t);
        }
        refreshExterior();
    }

    @Override
    public void removed() {
        VortexNet.CHANNEL.sendToServer(new ViewPacket(tardisId, false));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (noticeTicks > 0) noticeTicks--;
        if (--refreshIn <= 0) refreshExterior();
    }

    public void showEventNotice() {
        noticeTicks = 70;
    }

    // ---------------------------------------------------------------- data

    private void refreshExterior() {
        refreshIn = 20;
        try {
            Object exterior = AitBridge.call(tardis, "getExterior");
            Object parentVariant = AitBridge.call(exterior, "getVariant");
            Object variant = AitBridge.callStatic(tardis, ITEM_REGISTRY, "withParent", parentVariant);
            model = AitBridge.call(variant, "model");
            texture = (ResourceLocation) AitBridge.call(variant, "texture");
            ResourceLocation em = (ResourceLocation) AitBridge.call(variant, "emission");
            emission = em != null && Minecraft.getInstance().getResourceManager().getResource(em).isPresent() ? em : null;
            bedrock = model != null && model.getClass().getSimpleName().contains("Bedrock");
        } catch (Throwable t) {
            model = null;
            fail("exterior", t);
        }
    }

    private void fail(String what, Throwable t) {
        if (!broken) VortexViewMod.LOGGER.error("Vortex view: could not read " + what + " from AiT", t);
        broken = true;
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF000000);
        g.flush();

        if (!broken || vortex != null) {
            try {
                renderScene(partialTick);
            } catch (Throwable t) {
                fail("scene", t);
            }
        }

        renderOverlay(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderScene(float partialTick) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float t = mc.player.tickCount + partialTick;

        Matrix4f oldProjection = RenderSystem.getProjectionMatrix();
        VertexSorting oldSorting = RenderSystem.getVertexSorting();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();

        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        RenderSystem.setProjectionMatrix(
                new Matrix4f().perspective((float) Math.toRadians(75.0), aspect, 0.05f, 3000f),
                VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        FogRenderer.setupNoFog();
        mc.gameRenderer.lightTexture().turnOnLightLayer();

        try {
            PoseStack ps = new PoseStack();

            // The whole shot sways a little, like a handheld camera in the show.
            ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.021f) * 5f));
            ps.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.013f) * 3f));
            ps.mulPose(Axis.YP.rotationDegrees(Mth.sin(t * 0.017f) * 3f));

            if (vortex != null) {
                ps.pushPose();
                ps.mulPose(Axis.ZP.rotationDegrees(t * 1.4f));
                ps.translate(0f, 0f, 500f);
                AitBridge.call(vortex, "renderVortex", ps);
                AitBridge.call(vortex, "renderVortexLayer", ps, 1.5f);
                AitBridge.call(vortex, "renderVortexLayer", ps, 2.5f);
                ps.popPose();
            }

            if (model != null && texture != null) renderTardis(ps, t);
        } finally {
            mc.gameRenderer.lightTexture().turnOffLightLayer();
            RenderSystem.enableCull();
            RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    private void renderTardis(PoseStack ps, float t) throws Exception {
        Minecraft mc = Minecraft.getInstance();

        // Path: a slow figure-eight drift with some depth, so it swoops toward and away from you.
        float x = 2.4f * Mth.sin(t * 0.031f) + 0.7f * Mth.sin(t * 0.077f);
        float y = 1.2f * Mth.sin(t * 0.043f + 1.0f) + 0.4f * Mth.cos(t * 0.09f);
        float z = -7.5f + 2.0f * Mth.sin(t * 0.021f);

        // It banks into the turns, and tumbles on every axis at different speeds.
        float vx = 2.4f * 0.031f * Mth.cos(t * 0.031f) + 0.7f * 0.077f * Mth.cos(t * 0.077f);
        float bank = -vx * 140f;
        float pitch = 22f * Mth.sin(t * 0.019f) + t * 0.35f;
        float yaw = t * 1.1f;
        float roll = 18f * Mth.sin(t * 0.027f) + bank;

        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.ZP.rotationDegrees(roll));
        // AIT models are built upside-down (like all entity models) and stand on the origin.
        ps.mulPose(Axis.ZP.rotationDegrees(180f));
        ps.translate(0f, bedrock ? 1.25f : 0.95f, 0f);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer base = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        AitBridge.callAny(model, RENDER_NAMES, ps, base, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        buffers.endBatch();

        if (emission != null && hasPower()) {
            RenderType glow = (RenderType) AitBridge.callStatic(tardis, RENDER_LAYERS, "tardisEmissiveCullZOffset", emission, true);
            VertexConsumer glowBuffer = buffers.getBuffer(glow);
            AitBridge.callAny(model, RENDER_NAMES, ps, glowBuffer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            buffers.endBatch();
        }
        ps.popPose();
    }

    private boolean hasPower() {
        Object fuel = AitBridge.tryCall(tardis, "fuel");
        Object has = fuel == null ? null : AitBridge.tryCall(fuel, "hasPower");
        return has == null || (Boolean) has;
    }

    // ---------------------------------------------------------------- overlay

    private void renderOverlay(GuiGraphics g) {
        var font = Minecraft.getInstance().font;

        if (broken && vortex == null) {
            g.drawCenteredString(font, "Could not read the TARDIS from AIT (see latest.log)", width / 2, height / 2, 0xFFFF5555);
        }

        g.drawCenteredString(font, Component.translatable("screen." + VortexViewMod.MOD_ID + ".exit"),
                width / 2, height - 14, 0x99FFFFFF);

        if (VortexConfig.SHOW_HUD.get()) {
            int y = 8;
            for (String line : statusLines()) {
                g.drawString(font, line, 10, y, 0xFF55FFFF, true);
                y += 11;
            }
        }

        if (noticeTicks > 0) {
            int alpha = Math.min(255, noticeTicks * 12);
            int color = (alpha << 24) | 0x55FF99;
            g.drawCenteredString(font, Component.translatable("screen." + VortexViewMod.MOD_ID + ".event_done"),
                    width / 2, 28, color);
        }
    }

    private java.util.List<String> statusLines() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        try {
            Object travel = AitBridge.call(tardis, "travel");
            lines.add("STATE  " + AitBridge.call(travel, "getState"));
            lines.add("SPEED  " + AitBridge.call(travel, "speed"));
            lines.add("TRIP   " + AitBridge.call(travel, "getDurationAsPercentage") + "%");
            Object stab = AitBridge.call(AitBridge.call(tardis, "subsystems"), "stabilisers");
            lines.add("STABILISERS  " + ((Boolean) AitBridge.call(stab, "isEnabled") ? "ON" : "OFF"));
        } catch (Throwable ignored) {
            // Status text is cosmetic; the view works without it.
        }
        return lines;
    }
}
