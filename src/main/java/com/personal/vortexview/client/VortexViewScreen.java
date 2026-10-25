package com.personal.vortexview.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import com.personal.vortexview.AitBridge;
import com.personal.vortexview.VortexConfig;
import com.personal.vortexview.VortexViewMod;
import com.personal.vortexview.net.DematPacket;
import com.personal.vortexview.net.LandingPacket;
import com.personal.vortexview.net.ViewPacket;
import com.personal.vortexview.net.VortexNet;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.platform.Lighting;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Full-screen time vortex. The vortex tunnel is AiT's own (using whatever vortex style the
 * TARDIS has selected), and the TARDIS is drawn with its current exterior, drifting, banking
 * and tumbling through it. When the flight ends, the view cuts to the landing site and shows the
 * TARDIS materialising there.
 */
public class VortexViewScreen extends Screen {
    private static final String ITEM_REGISTRY = "dev.amble.ait.registry.impl.exterior.ClientExteriorVariantRegistry";
    private static final String RENDER_LAYERS = "dev.amble.ait.client.renderers.AITRenderLayers";
    private static final String[] RENDER_NAMES = {"m_7695_", "renderToBuffer"};
    private static final int FULL_BRIGHT = 15728880;

    /** Landing scene: white flash length, and how long it lingers once AiT says the TARDIS has landed. */
    private static final int FLASH_TICKS = 14;
    /** How long the TARDIS takes to materialise inside the vortex after take-off. */
    private static final int VORTEX_MAT_TICKS = 110;
    private static final int LINGER_TICKS = 70;
    /** Safety net in case the TARDIS never reports LANDED (for example after a crash). */
    private static final int MAX_LANDING_TICKS = 600;

    private record Cell(int x, int y, int z, BlockState state) {
    }

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

    private LandingPacket landing;
    private List<Cell> cells = List.of();
    private int landingTicks;
    private int landedTicks;

    /** Ticks since the view cut to the vortex after take-off, so the TARDIS can materialise there. */
    private int vortexAge = 1000;
    private int clickCooldown;
    private VortexButton dematButton;

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

        Component label = Component.translatable("screen." + VortexViewMod.MOD_ID + ".demat");
        Component tip = Component.translatable("screen." + VortexViewMod.MOD_ID + ".demat.tooltip");
        dematButton = addRenderableWidget(new VortexButton(width / 2 - 60, height - 46, 120, 22, label, tip, 1.0f,
                this::pressDemat));
        dematButton.visible = false;
    }

    private void pressDemat() {
        if (clickCooldown > 0 || !"LANDED".equals(travelState())) return;
        VortexNet.CHANNEL.sendToServer(new DematPacket(tardisId));
        clickCooldown = 40; // if nothing happens by then (no power, cooldown) the button comes back
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
        if (clickCooldown > 0) clickCooldown--;

        String state = travelState();
        boolean landed = "LANDED".equals(state);

        if (landing != null) {
            landingTicks++;

            if ("FLIGHT".equals(state) && landingTicks > 40) {
                // The TARDIS finished dematerialising and is gone: cut to the vortex, where it
                // materialises (see renderVortexScene).
                landing = null;
                cells = List.of();
                vortexAge = 0;
            } else {
                if (landed) landedTicks++;
                if (VortexConfig.CLOSE_AFTER_LANDING.get()
                        && (landedTicks > LINGER_TICKS || landingTicks > MAX_LANDING_TICKS)) {
                    onClose();
                }
            }
        } else if (vortexAge < 1000) {
            vortexAge++;
        }

        if (dematButton != null) {
            dematButton.visible = landed;
            dematButton.active = landed && clickCooldown == 0;
        }
    }

    public void showEventNotice() {
        noticeTicks = 70;
    }

    /** The flight ended: switch to the landing scene built from the server's snapshot. */
    public void startLanding(LandingPacket packet) {
        if (landing != null) return;
        List<Cell> visible = new ArrayList<>();
        try {
            if (packet.radius() > 0) {
                BlockState[] states = new BlockState[packet.cells().length];
                for (int i = 0; i < states.length; i++) {
                    int id = packet.cells()[i];
                    BlockState s = id < 0 ? null : Block.BLOCK_STATE_REGISTRY.byId(id);
                    states[i] = s == null ? Blocks.AIR.defaultBlockState() : s;
                }
                int r = packet.radius();
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        for (int y = packet.yMin(); y <= packet.yMax(); y++) {
                            BlockState s = states[packet.index(x, y, z)];
                            if (s.isAir() || s.getRenderShape() != RenderShape.MODEL) continue;
                            if (buried(packet, states, x, y, z)) continue;
                            visible.add(new Cell(x, y, z, s));
                        }
                    }
                }
            } else {
                // No snapshot: a plain grass platform to land on.
                BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
                for (int x = -8; x <= 8; x++) {
                    for (int z = -8; z <= 8; z++) visible.add(new Cell(x, -1, z, grass));
                }
            }
        } catch (Throwable t) {
            VortexViewMod.LOGGER.error("Vortex view: could not build the landing scene", t);
            return;
        }
        if (visible.isEmpty()) {
            BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) visible.add(new Cell(x, -1, z, grass));
            }
        }
        cells = visible;
        landing = packet;
        landingTicks = packet.parked() ? 200 : 0;
        landedTicks = 0;
    }

    private static boolean buried(LandingPacket p, BlockState[] states, int x, int y, int z) {
        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] o : d) {
            int nx = x + o[0], ny = y + o[1], nz = z + o[2];
            if (Math.abs(nx) > p.radius() || Math.abs(nz) > p.radius() || ny < p.yMin() || ny > p.yMax()) return false;
            if (!states[p.index(nx, ny, nz)].isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return false;
        }
        return true;
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

    @FunctionalInterface
    private interface Body {
        void run() throws Exception;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (landing != null) {
            float[] top = skyTop();
            float[] bottom = skyHorizon();
            g.fillGradient(0, 0, width, height, argb(top), argb(bottom));
        } else {
            g.fill(0, 0, width, height, 0xFF000000);
        }
        g.flush();

        try {
            if (landing != null) {
                renderLandingScene(partialTick);
            } else if (!broken || vortex != null) {
                renderVortexScene(partialTick);
            }
        } catch (Throwable t) {
            fail("scene", t);
        }

        // A short white flash hides the cut: into the landing site on arrival, into the vortex on take-off.
        if (landing != null && landingTicks < FLASH_TICKS) {
            float a = 1f - (landingTicks + partialTick) / FLASH_TICKS;
            g.fill(0, 0, width, height, ((int) (Mth.clamp(a, 0f, 1f) * 255) << 24) | 0xFFFFFF);
        } else if (landing == null && vortexAge < FLASH_TICKS) {
            float a = 1f - (vortexAge + partialTick) / FLASH_TICKS;
            g.fill(0, 0, width, height, ((int) (Mth.clamp(a, 0f, 1f) * 255) << 24) | 0xFFFFFF);
        }

        renderOverlay(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Sets up a perspective 3D pass over the GUI, runs the body, and puts everything back. */
    private void in3D(Body body) throws Exception {
        Minecraft mc = Minecraft.getInstance();
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
            body.run();
        } finally {
            mc.gameRenderer.lightTexture().turnOffLightLayer();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            FogRenderer.setupNoFog();
            RenderSystem.enableCull();
            RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    // ---- vortex

    private void renderVortexScene(float partialTick) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float t = mc.player.tickCount + partialTick;

        in3D(() -> {
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

            if (model != null && texture != null) renderVortexTardis(ps, t, vortexTardisAlpha(partialTick));
        });
    }

    /**
     * Opacity of the TARDIS inside the vortex. Right after take-off it materialises into the vortex with
     * the same pulsing flicker as a materialisation; after that (or if the view was opened mid-flight) it
     * is simply solid.
     */
    private float vortexTardisAlpha(float partialTick) {
        float age = vortexAge + partialTick;
        float p = Mth.clamp(age / VORTEX_MAT_TICKS, 0f, 1f);
        if (p >= 1f) return 1f;
        float pulse = 0.5f + 0.5f * Mth.sin(age * (0.9f + 1.6f * p));
        return Mth.clamp(p * 1.2f - (1f - pulse) * (1f - p) * 0.9f, 0f, 1f);
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

    private void renderVortexTardis(PoseStack ps, float t, float alpha) throws Exception {
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
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawTardis(ps, alpha);
        ps.popPose();
    }

    /** Draws the exterior model at the current pose; alpha below 1 uses the see-through layer. */
    private void drawTardis(PoseStack ps, float alpha) throws Exception {
        if (alpha <= 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        RenderType base = alpha >= 0.999f ? RenderType.entityCutoutNoCull(texture) : RenderType.entityTranslucent(texture);
        VertexConsumer vc = buffers.getBuffer(base);
        AitBridge.callAny(model, RENDER_NAMES, ps, vc, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, alpha);
        buffers.endBatch();

        if (emission != null && alpha > 0.105f && hasPower()) {
            RenderType glow = (RenderType) AitBridge.callStatic(tardis, RENDER_LAYERS, "tardisEmissiveCullZOffset", emission, true);
            VertexConsumer glowBuffer = buffers.getBuffer(glow);
            // AiT dims the glow along with the fade (colour = 1 - (1 - alpha)).
            float c = Mth.clamp(alpha, 0f, 1f);
            AitBridge.callAny(model, RENDER_NAMES, ps, glowBuffer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, c, c, c, alpha);
            buffers.endBatch();
        }
    }

    private String travelState() {
        try {
            Object state = AitBridge.call(AitBridge.call(tardis, "travel"), "getState");
            return state instanceof Enum<?> e ? e.name() : String.valueOf(state);
        } catch (Throwable t) {
            return "";
        }
    }

    private boolean hasPower() {
        Object fuel = AitBridge.tryCall(tardis, "fuel");
        Object has = fuel == null ? null : AitBridge.tryCall(fuel, "hasPower");
        return has == null || (Boolean) has;
    }

    // ---- landing

    private void renderLandingScene(float partialTick) throws Exception {
        float lt = landingTicks + partialTick;
        float day = daylight();
        float[] horizon = skyHorizon();

        in3D(() -> {
            Minecraft mc = Minecraft.getInstance();

            // Distant parts of the little snapshot fade into the sky so its edges never show.
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
            RenderSystem.setShaderFogStart(7.5f);
            RenderSystem.setShaderFogEnd(12.5f);
            RenderSystem.setShaderFogColor(horizon[0], horizon[1], horizon[2]);
            RenderSystem.setShaderColor(day, day, day, 1f);
            Lighting.setupFor3DItems();

            // Camera: starts wide, drifts in toward the TARDIS and slowly orbits it.
            float ease = Mth.clamp(lt / 160f, 0f, 1f);
            ease = ease * ease * (3f - 2f * ease);
            float dist = Mth.lerp(ease, 9.5f, 6.0f);
            float eyeH = Mth.lerp(ease, 3.2f, 1.6f);
            // Start in front of the door (AiT's rotation points the door a quarter turn from the block's
            // facing), plus the player's own offset from the config, and a slow drift around it.
            float face = (float) Math.toRadians(landing.rotation() - 90f
                    + VortexConfig.CAMERA_OFFSET_DEGREES.get() + 20f * Mth.sin(lt * 0.012f));
            float cx = 0.5f, cy = 1.0f, cz = 0.5f;
            float ex = cx + dist * Mth.sin(face);
            float ey = eyeH;
            float ez = cz + dist * Mth.cos(face);

            float dx = cx - ex, dy = cy - ey, dz = cz - ez;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));

            PoseStack ps = new PoseStack();
            ps.mulPose(Axis.XP.rotationDegrees(pitch));
            ps.mulPose(Axis.YP.rotationDegrees(yaw + 180f));
            ps.translate(-ex, -ey, -ez);

            // The ground and whatever stands around the landing spot.
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            for (Cell c : cells) {
                ps.pushPose();
                ps.translate(c.x(), c.y(), c.z());
                mc.getBlockRenderer().renderSingleBlock(c.state(), ps, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
            buffers.endBatch();

            // The TARDIS is drawn with AiT's own materialisation animation: the same fade, flicker,
            // scale and spin the real exterior uses, read from the same animation clock. That keeps it in
            // step with the real materialising sound, which starts when that animation does.
            if (model != null && texture != null) {
                Object travel = AitBridge.call(tardis, "travel");
                float alpha = ((Number) AitBridge.call(travel, "getAlpha", partialTick)).floatValue();
                org.joml.Vector3f offset = (org.joml.Vector3f) AitBridge.call(travel, "getAnimationPosition", partialTick);
                org.joml.Vector3f turn = (org.joml.Vector3f) AitBridge.call(travel, "getAnimationRotation", partialTick);
                org.joml.Vector3f scale = (org.joml.Vector3f) AitBridge.call(travel, "getScale", partialTick);

                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                ps.pushPose();
                // Same order as AiT's ExteriorRenderer.
                ps.translate(offset.x(), offset.y(), offset.z());
                ps.translate(0.5f, 0f, 0.5f);
                ps.mulPose(Axis.XP.rotationDegrees(turn.z()));
                ps.mulPose(Axis.YP.rotationDegrees(turn.y()));
                ps.mulPose(Axis.ZP.rotationDegrees(turn.x()));
                ps.scale(scale.x(), scale.y(), scale.z());
                ps.mulPose(Axis.XP.rotationDegrees(180f));
                ps.mulPose(Axis.YP.rotationDegrees(landing.rotation() + 180f));
                ps.translate(0f, bedrock ? 0.3f : 0f, 0f);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                drawTardis(ps, alpha);
                ps.popPose();
            }
        });
    }

    // ---- sky colours for the landing scene

    private float timeOfDay() {
        return (float) (landing.dayTime() % 24000L) / 24000f;
    }

    /** 1 at noon, 0 at deep night (smoothed). */
    private float daylight() {
        String dim = landing.dimension();
        if (dim.endsWith("the_nether") || dim.endsWith("the_end")) return 0.8f;
        float h = Mth.cos((timeOfDay() - 0.25f) * Mth.TWO_PI); // 1 at noon, -1 at midnight
        float f = Mth.clamp((h + 0.25f) / 0.75f, 0f, 1f);
        f = f * f * (3f - 2f * f);
        float rain = landing.raining() ? 0.75f : 1f;
        return Mth.lerp(f, 0.28f, 1f) * rain;
    }

    private float[] skyTop() {
        String dim = landing.dimension();
        if (dim.endsWith("the_nether")) return new float[]{0.20f, 0.04f, 0.03f};
        if (dim.endsWith("the_end")) return new float[]{0.04f, 0.02f, 0.08f};
        float h = Mth.cos((timeOfDay() - 0.25f) * Mth.TWO_PI);
        float f = Mth.clamp((h + 0.25f) / 0.75f, 0f, 1f);
        float[] day = landing.raining() ? new float[]{0.45f, 0.5f, 0.58f} : new float[]{0.42f, 0.62f, 1.0f};
        float[] night = {0.01f, 0.015f, 0.05f};
        return new float[]{Mth.lerp(f, night[0], day[0]), Mth.lerp(f, night[1], day[1]), Mth.lerp(f, night[2], day[2])};
    }

    private float[] skyHorizon() {
        String dim = landing.dimension();
        if (dim.endsWith("the_nether")) return new float[]{0.35f, 0.08f, 0.05f};
        if (dim.endsWith("the_end")) return new float[]{0.12f, 0.08f, 0.2f};
        float h = Mth.cos((timeOfDay() - 0.25f) * Mth.TWO_PI);
        float f = Mth.clamp((h + 0.25f) / 0.75f, 0f, 1f);
        float[] day = landing.raining() ? new float[]{0.62f, 0.65f, 0.7f} : new float[]{0.74f, 0.85f, 1.0f};
        float[] night = {0.03f, 0.04f, 0.1f};
        float[] c = {Mth.lerp(f, night[0], day[0]), Mth.lerp(f, night[1], day[1]), Mth.lerp(f, night[2], day[2])};
        // Warm glow near sunrise and sunset.
        float glow = Mth.clamp(1f - Math.abs(h) * 3.5f, 0f, 1f) * (landing.raining() ? 0.3f : 1f);
        c[0] = Mth.lerp(glow, c[0], 1.0f);
        c[1] = Mth.lerp(glow, c[1], 0.58f);
        c[2] = Mth.lerp(glow, c[2], 0.32f);
        return c;
    }

    private static int argb(float[] c) {
        return 0xFF000000
                | ((int) (Mth.clamp(c[0], 0f, 1f) * 255) << 16)
                | ((int) (Mth.clamp(c[1], 0f, 1f) * 255) << 8)
                | (int) (Mth.clamp(c[2], 0f, 1f) * 255);
    }

    // ---------------------------------------------------------------- overlay

    private void renderOverlay(GuiGraphics g) {
        var font = Minecraft.getInstance().font;

        if (broken && vortex == null && landing == null) {
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

        if (landing != null) {
            String st = travelState();
            String label = "LANDED".equals(st) ? "LANDED" : "DEMAT".equals(st) ? "DEMATERIALISING" : "MATERIALISING";
            g.drawCenteredString(font, label,
                    width / 2, 12, 0xFFFFFFFF);
        } else if (noticeTicks > 0) {
            int alpha = Math.min(255, noticeTicks * 12);
            int color = (alpha << 24) | 0x55FF99;
            g.drawCenteredString(font, Component.translatable("screen." + VortexViewMod.MOD_ID + ".event_done"),
                    width / 2, 28, color);
        }
    }

    private List<String> statusLines() {
        List<String> lines = new ArrayList<>();
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
