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
        AitBridge.rememberLoader(tardis);
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

    /**
     * Total angle (degrees) of a flip that happens once per {@code period} ticks and takes
     * {@code duration} ticks, eased in and out. It only ever grows, so it never snaps back.
     */
    private static float flipAngle(float t, float period, float duration, float offset) {
        float shifted = t + offset;
        int done = (int) Math.floor(shifted / period);
        float into = shifted - done * period;
        float p = Mth.clamp(into / duration, 0f, 1f);
        float eased = p * p * (3f - 2f * p);
        return 360f * (done + eased);
    }

    private void renderTardis(PoseStack ps, float t) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        float spin = VortexConfig.SPIN_SPEED.get().floatValue();
        float wobble = VortexConfig.WOBBLE.get().floatValue();
        boolean tumbles = VortexConfig.TUMBLES.get();

        // Where it is: a lazy swooping path across the tunnel that also pushes in and out of
        // the screen, so it seems to be hurtling along the vortex rather than hovering.
        float x = wobble * (2.6f * Mth.sin(t * 0.027f) + 0.9f * Mth.sin(t * 0.071f + 0.6f));
        float y = wobble * (1.5f * Mth.sin(t * 0.039f + 1.0f) + 0.5f * Mth.cos(t * 0.083f));
        float z = -6.8f + wobble * 2.2f * Mth.sin(t * 0.019f);

        // It leans into the sideways drift like a ship banking through a turn.
        float vx = wobble * (2.6f * 0.027f * Mth.cos(t * 0.027f) + 0.9f * 0.071f * Mth.cos(t * 0.071f + 0.6f));
        float bank = -vx * 160f;

        // How it points. In the show the TARDIS spins steadily about its own vertical axis
        // while it rocks and nods through the vortex, and every so often it tumbles end over end.
        float yaw = t * 3.1f * spin;
        float nod = wobble * (21f * Mth.sin(t * 0.052f) + 7f * Mth.sin(t * 0.131f));
        float rock = wobble * (15f * Mth.sin(t * 0.037f + 1.2f)) + bank;
        float flipX = tumbles ? flipAngle(t, 300f, 60f, 70f) : 0f;
        float flipZ = tumbles ? flipAngle(t, 520f, 75f, 260f) : 0f;

        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.XP.rotationDegrees(nod + flipX));
        ps.mulPose(Axis.ZP.rotationDegrees(rock + flipZ));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
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
