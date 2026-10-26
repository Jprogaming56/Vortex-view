package com.personal.vortexview.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Everything that makes the vortex feel like a place the TARDIS is travelling through rather than a
 * backdrop it spins in front of: rushing light streaks, energy gates and billows that stream past the
 * camera, a glow at the end of the tunnel, a glowing wake behind the ship, vortex lightning that
 * strikes and jolts it, and a cycle of different "zones" (calm tunnel, storm, golden eddy, deep drift,
 * turbulence) that blend into each other.
 *
 * <p>All of it is drawn with plain coloured triangles in additive blending, and everything is a pure
 * function of time (plus a distance counter the screen advances every tick), so it is smooth between
 * ticks and needs no state of its own.
 */
final class VortexFx {
    private static final float TAU = (float) (Math.PI * 2.0);

    private VortexFx() {
    }

    // ------------------------------------------------------------------ zones

    /** One kind of stretch of vortex. */
    record Env(String name, float r1, float g1, float b1, float r2, float g2, float b2,
               float speed, float lightning, float shake, float roll, float rings, float clouds, float energy) {
    }

    private static final Env[] ENVS = {
            new Env("TIME TUNNEL", 0.25f, 0.65f, 1.00f, 0.60f, 0.90f, 1.00f, 1.00f, 0.25f, 0.15f, 6f, 0.50f, 0.50f, 1.00f),
            new Env("TEMPORAL STORM", 0.65f, 0.25f, 1.00f, 1.00f, 0.50f, 0.90f, 1.30f, 0.90f, 0.50f, 14f, 0.30f, 0.90f, 1.40f),
            new Env("GOLDEN EDDY", 1.00f, 0.62f, 0.15f, 1.00f, 0.85f, 0.45f, 1.15f, 0.35f, 0.25f, 9f, 0.90f, 0.70f, 1.10f),
            new Env("DEEP DRIFT", 0.20f, 0.95f, 0.75f, 0.60f, 0.70f, 1.00f, 0.70f, 0.10f, 0.05f, 4f, 0.20f, 1.00f, 0.60f),
            new Env("TURBULENCE", 1.00f, 0.28f, 0.12f, 1.00f, 0.70f, 0.30f, 1.70f, 0.80f, 1.00f, 20f, 0.60f, 0.50f, 1.80f),
    };
    private static final float ENV_TICKS = 640f;
    private static final float BLEND_TICKS = 140f;

    static Env envAt(float t) {
        int i = (int) Math.floor(t / ENV_TICKS);
        float local = t - i * ENV_TICKS;
        Env cur = ENVS[Math.floorMod(i, ENVS.length)];
        if (i <= 0 || local >= BLEND_TICKS) return cur;
        Env prev = ENVS[Math.floorMod(i - 1, ENVS.length)];
        float k = smooth(local / BLEND_TICKS);
        return new Env(k < 0.5f ? prev.name : cur.name,
                lerp(prev.r1, cur.r1, k), lerp(prev.g1, cur.g1, k), lerp(prev.b1, cur.b1, k),
                lerp(prev.r2, cur.r2, k), lerp(prev.g2, cur.g2, k), lerp(prev.b2, cur.b2, k),
                lerp(prev.speed, cur.speed, k), lerp(prev.lightning, cur.lightning, k),
                lerp(prev.shake, cur.shake, k), lerp(prev.roll, cur.roll, k),
                lerp(prev.rings, cur.rings, k), lerp(prev.clouds, cur.clouds, k),
                lerp(prev.energy, cur.energy, k));
    }

    // ------------------------------------------------------------------ the ship's path

    /** Where the TARDIS flies: {x, y, z, sideways velocity}. A long lazy weave plus surges ahead and back. */
    static float[] path(float t, float wobble) {
        float x = wobble * (3.3f * Mth.sin(t * 0.021f) + 1.1f * Mth.sin(t * 0.057f + 0.6f));
        float y = wobble * (1.9f * Mth.sin(t * 0.031f + 1.0f) + 0.6f * Mth.cos(t * 0.069f));
        float z = -7.6f + wobble * 2.4f * Mth.sin(t * 0.017f);
        float vx = wobble * (3.3f * 0.021f * Mth.cos(t * 0.021f) + 1.1f * 0.057f * Mth.cos(t * 0.057f + 0.6f));
        return new float[]{x, y, z, vx};
    }

    // ------------------------------------------------------------------ lightning

    private static final float BUCKET = 80f;

    private record Strike(float time, boolean direct, int n) {
    }

    private static Strike strike(int n, float intensity) {
        if (n < 0 || intensity <= 0f) return null;
        Env e = envAt(n * BUCKET);
        if (h(n, 11) >= e.lightning * intensity) return null;
        return new Strike(n * BUCKET + h(n, 12) * (BUCKET - 10f), h(n, 13) < 0.65f, n);
    }

    private static float flickerPulse(float age) {
        if (age < 0f || age > 22f) return 0f;
        if (age < 2f) return 1f;
        if (age < 4f) return 0.3f;
        if (age < 6f) return 0.9f;
        return Math.max(0f, 1f - (age - 6f) / 16f);
    }

    /** True if a bolt that hits the TARDIS starts in the time window (a, b]. */
    static boolean directStrikeBetween(float a, float b, float intensity) {
        for (int n = (int) Math.floor(a / BUCKET); n <= (int) Math.floor(b / BUCKET); n++) {
            Strike s = strike(n, intensity);
            if (s != null && s.direct && s.time > a && s.time <= b) return true;
        }
        return false;
    }

    /** Brightness (0..1) of the screen flash from lightning at time t. */
    static float flash(float t, float intensity) {
        float f = 0f;
        int bucket = (int) Math.floor(t / BUCKET);
        for (int n = bucket - 1; n <= bucket; n++) {
            Strike s = strike(n, intensity);
            if (s == null) continue;
            f = Math.max(f, flickerPulse(t - s.time) * (s.direct ? 0.30f : 0.09f));
        }
        return f;
    }

    /** What a strike does to the ship: {dx, dy, spinDegrees, pitchDegrees, shake}. */
    static float[] jolt(float t, float intensity) {
        float dx = 0, dy = 0, spin = 0, pitch = 0, shake = 0;
        int bucket = (int) Math.floor(t / BUCKET);
        for (int n = bucket - 1; n <= bucket; n++) {
            Strike s = strike(n, intensity);
            if (s == null) continue;
            float age = t - s.time;
            if (age < 0f) continue;
            float d = (float) Math.exp(-age / 14.0);
            if (s.direct) {
                if (age <= 75f) {
                    dx += (h(n, 21) - 0.5f) * 5.0f * d * Mth.cos(age * 0.45f);
                    dy += (h(n, 22) - 0.5f) * 3.6f * d * Mth.sin(age * 0.5f);
                    spin += 360f * (1f - d) * (h(n, 23) < 0.5f ? 1f : -1f);
                    pitch += (h(n, 24) - 0.5f) * 80f * d * Mth.sin(age * 0.6f);
                }
                if (age <= 60f) shake += d;
            } else if (age <= 40f) {
                shake += 0.3f * d;
            }
        }
        return new float[]{dx, dy, spin, pitch, shake};
    }

    // ------------------------------------------------------------------ drawing context

    static final class Ctx {
        float t;          // smooth time in ticks since this flight started
        float dist;       // distance travelled, in streak units
        float twist;      // slow rotation of the tunnel's contents, radians
        float tx, ty, tz; // where the TARDIS is (after the lightning jolt)
        float baseX, baseY; // where it would be without the jolt
        float wobble;     // the player's wobble setting, so the wake follows the same path
        Env env;
        float intensity;  // overall strength of the effects (config)
        boolean lightning;
    }

    // ------------------------------------------------------------------ layers

    /** Everything that sits behind or around the ship. Call before drawing the TARDIS. */
    static void drawBack(PoseStack ps, Ctx c) {
        begin();
        try {
            Mesh m = new Mesh(ps.last().pose());
            try {
                endGlow(m, c);
                clouds(m, c);
                streaks(m, c, false);
                rings(m, c);
                wake(m, c);
            } finally {
                m.flush();
            }
        } finally {
            end();
        }
    }

    /** Streaks passing in front of the ship, lightning, sparks. Call after drawing the TARDIS. */
    static void drawFront(PoseStack ps, Ctx c) {
        begin();
        try {
            Mesh m = new Mesh(ps.last().pose());
            try {
                streaks(m, c, true);
                if (c.lightning) lightning(m, c);
            } finally {
                m.flush();
            }
        } finally {
            end();
        }
    }

    private static void begin() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
    }

    private static void end() {
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** A batch of coloured triangles. */
    private static final class Mesh {
        final BufferBuilder bb = Tesselator.getInstance().getBuilder();
        final Matrix4f m;

        Mesh(Matrix4f m) {
            this.m = m;
            bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        }

        void v(float x, float y, float z, float r, float g, float b, float a) {
            bb.vertex(m, x, y, z).color(r, g, b, a).endVertex();
        }

        void flush() {
            BufferUploader.drawWithShader(bb.end());
        }

        /** Soft round glow: opaque-ish in the middle, fading to nothing at the rim. */
        void disc(float x, float y, float z, float rad, int segs, float r, float g, float b, float a) {
            for (int i = 0; i < segs; i++) {
                float a0 = TAU * i / segs, a1 = TAU * (i + 1) / segs;
                v(x, y, z, r, g, b, a);
                v(x + rad * Mth.cos(a0), y + rad * Mth.sin(a0), z, r, g, b, 0f);
                v(x + rad * Mth.cos(a1), y + rad * Mth.sin(a1), z, r, g, b, 0f);
            }
        }

        /** A ribbon from p to q, half-width (sx,sy,sz) sideways, fading from alpha aq at q to ap at p. */
        void ribbon(float px, float py, float pz, float qx, float qy, float qz,
                    float sx, float sy, float sz, float r, float g, float b, float ap, float aq) {
            v(px - sx, py - sy, pz - sz, r, g, b, ap);
            v(px + sx, py + sy, pz + sz, r, g, b, ap);
            v(qx + sx, qy + sy, qz + sz, r, g, b, aq);
            v(px - sx, py - sy, pz - sz, r, g, b, ap);
            v(qx + sx, qy + sy, qz + sz, r, g, b, aq);
            v(qx - sx, qy - sy, qz - sz, r, g, b, aq);
        }
    }

    // ------------------------------------------------------------------ pieces

    /** The bright heart of the vortex far ahead; the whole tunnel seems to rush towards it. */
    private static void endGlow(Mesh m, Ctx c) {
        float pulse = 0.5f + 0.5f * Mth.sin(c.t * 0.07f);
        m.disc(0f, 0f, -150f, 95f, 28, c.env.r1, c.env.g1, c.env.b1, (0.38f + 0.12f * pulse) * c.intensity);
        m.disc(0f, 0f, -148f, 26f, 24, 1f, 1f, 1f, (0.40f + 0.20f * pulse) * c.intensity);
    }

    /** Big soft billows of vortex energy drifting past the sides. */
    private static void clouds(Mesh m, Ctx c) {
        int n = 14;
        float len = 150f;
        for (int i = 0; i < n; i++) {
            float z = -150f + (float) (((c.dist * 0.55f + h(i, 4) * len) % len + len) % len);
            float ang = h(i, 1) * TAU + c.twist * (0.3f + h(i, 9));
            float rho = 9f + 12f * h(i, 2);
            float size = 7f + 9f * h(i, 3);
            float fade = Mth.clamp((z + 150f) / 35f, 0f, 1f) * Mth.clamp((-3f - z) / 25f, 0f, 1f);
            float k = h(i, 6);
            float r = lerp(c.env.r1, c.env.r2, k), g = lerp(c.env.g1, c.env.g2, k), b = lerp(c.env.b1, c.env.b2, k);
            float a = 0.11f * c.env.clouds * fade * c.intensity;
            float x = rho * Mth.cos(ang), y = rho * Mth.sin(ang);
            m.disc(x, y, z, size, 14, r, g, b, a);
            m.disc(x + size * 0.35f, y - size * 0.2f, z, size * 0.7f, 12, r, g, b, a * 0.8f);
        }
    }

    /** Streaks of light racing towards and past the camera: the sense of speed. */
    private static void streaks(Mesh m, Ctx c, boolean front) {
        int n = 190;
        float len = 118f;
        for (int i = 0; i < n; i++) {
            float sm = 0.55f + h(i, 3) * 1.1f;
            float z = -115f + (float) (((c.dist * sm + h(i, 4) * len) % len + len) % len);
            if ((z > c.tz) != front) continue;
            float ang = h(i, 1) * TAU + c.twist * 0.5f;
            float rho = 2.2f + (float) Math.pow(h(i, 2), 1.4) * 20f;
            float tail = (1.2f + c.env.speed * 3.5f) * (0.5f + h(i, 5)) * (0.6f + sm * 0.4f);
            float fade = Mth.clamp((z + 115f) / 25f, 0f, 1f) * Mth.clamp((1f - z) / 5f, 0f, 1f);
            float a = 0.6f * fade * c.intensity;
            if (a <= 0.01f) continue;
            float k = h(i, 6);
            float r = lerp(c.env.r1, c.env.r2, k), g = lerp(c.env.g1, c.env.g2, k), b = lerp(c.env.b1, c.env.b2, k);
            float x = rho * Mth.cos(ang), y = rho * Mth.sin(ang);
            float tx = -Mth.sin(ang), ty = Mth.cos(ang);
            float w = 0.05f + 0.07f * h(i, 7);
            m.ribbon(x, y, z, x, y, z - tail, tx * w, ty * w, 0f, r, g, b, a, 0f);
            float w2 = w * 0.35f;
            m.ribbon(x, y, z, x, y, z - tail * 0.6f, tx * w2, ty * w2, 0f, 1f, 1f, 1f, a, 0f);
        }
    }

    /** Glowing energy gates (and broken arcs of them) that fly past as the ship races through. */
    private static void rings(Mesh m, Ctx c) {
        float spacing = 34f;
        float u = c.dist * 0.8f / spacing;
        int base = (int) Math.floor(u);
        float fr = u - base;
        for (int k = 0; k < 5; k++) {
            int id = base - k;
            if (h(id, 7) >= c.env.rings) continue;
            float z = -170f + (fr + k) * spacing;
            float fade = Mth.clamp((z + 170f) / 30f, 0f, 1f) * Mth.clamp((-3f - z) / 14f, 0f, 1f);
            float a = 0.55f * fade * c.intensity;
            if (a <= 0.01f) continue;
            float rad = 10f + 5f * h(id, 1);
            boolean full = h(id, 2) > 0.45f;
            float start = h(id, 3) * TAU + c.twist * (h(id, 4) - 0.5f) * 3f;
            float span = full ? TAU : 1.5f + 3f * h(id, 5);
            int segs = full ? 48 : 26;
            float wd = (0.35f + 0.5f * h(id, 6)) * (1f + (z + 170f) / 90f);
            float kk = h(id, 8);
            float r = lerp(c.env.r1, c.env.r2, kk), g = lerp(c.env.g1, c.env.g2, kk), b = lerp(c.env.b1, c.env.b2, kk);
            for (int j = 0; j < segs; j++) {
                float a0 = start + span * j / segs, a1 = start + span * (j + 1) / segs;
                float r0 = rad * (1f + 0.06f * Mth.sin(a0 * 3f + c.t * 0.08f + id));
                float r1 = rad * (1f + 0.06f * Mth.sin(a1 * 3f + c.t * 0.08f + id));
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                // inner edge, middle, outer edge of this slice
                float ix0 = (r0 - wd) * c0, iy0 = (r0 - wd) * s0, mx0 = r0 * c0, my0 = r0 * s0, ox0 = (r0 + wd) * c0, oy0 = (r0 + wd) * s0;
                float ix1 = (r1 - wd) * c1, iy1 = (r1 - wd) * s1, mx1 = r1 * c1, my1 = r1 * s1, ox1 = (r1 + wd) * c1, oy1 = (r1 + wd) * s1;
                m.v(ix0, iy0, z, r, g, b, 0f); m.v(mx0, my0, z, r, g, b, a); m.v(mx1, my1, z, r, g, b, a);
                m.v(ix0, iy0, z, r, g, b, 0f); m.v(mx1, my1, z, r, g, b, a); m.v(ix1, iy1, z, r, g, b, 0f);
                m.v(mx0, my0, z, r, g, b, a); m.v(ox0, oy0, z, r, g, b, 0f); m.v(ox1, oy1, z, r, g, b, 0f);
                m.v(mx0, my0, z, r, g, b, a); m.v(ox1, oy1, z, r, g, b, 0f); m.v(mx1, my1, z, r, g, b, a);
            }
        }
    }

    /** A glowing wake that trails behind the ship along the path it has just flown. */
    private static void wake(Mesh m, Ctx c) {
        int n = 18;
        for (int i = 1; i <= n; i++) {
            float[] p = path(c.t - i * 2.4f, c.wobble);
            float f = 1f - (float) i / (n + 1);
            float x = p[0] + (c.tx - c.baseX), y = p[1] + (c.ty - c.baseY);
            float z = p[2] + i * 0.4f;
            float k = (float) i / n;
            float r = lerp(c.env.r2, c.env.r1, k), g = lerp(c.env.g2, c.env.g1, k), b = lerp(c.env.b2, c.env.b1, k);
            m.disc(x, y, z, 0.35f + 1.1f * f, 12, r, g, b, 0.26f * f * c.intensity);
        }
        float flare = 0.7f + 0.3f * Mth.sin(c.t * 0.5f);
        m.disc(c.tx, c.ty, c.tz + 0.5f, 2.6f, 16, c.env.r2, c.env.g2, c.env.b2, 0.22f * flare * c.intensity);
    }

    // ------------------------------------------------------------------ lightning drawing

    private static void lightning(Mesh m, Ctx c) {
        int bucket = (int) Math.floor(c.t / BUCKET);
        for (int n = bucket - 1; n <= bucket; n++) {
            Strike s = strike(n, c.intensity);
            if (s == null) continue;
            float age = c.t - s.time;
            float pulse = flickerPulse(age);
            if (pulse <= 0f) continue;

            float ang = h(n, 14) * TAU;
            float rad = 15f + 4f * h(n, 15);
            float[] a = {rad * Mth.cos(ang), rad * Mth.sin(ang), c.tz - 22f - 14f * h(n, 16)};
            float[] b;
            if (s.direct) {
                b = new float[]{c.tx, c.ty, c.tz};
            } else {
                float ang2 = ang + 0.7f + 0.6f * h(n, 17);
                b = new float[]{rad * Mth.cos(ang2), rad * Mth.sin(ang2), a[2] + 36f * (h(n, 18) - 0.5f)};
            }

            // The bolt re-forks a little every few ticks, like real flickering lightning.
            long seed = n * 7919L + (long) (age / 3f) * 104729L;
            Random rnd = new Random(seed);
            List<float[]> pts = jag(a, b, rnd, Math.max(2.5f, dist(a, b) * 0.16f), 5);
            float w = 1f;
            polyline(m, pts, 0.85f * w, c.env.r1, c.env.g1, c.env.b1, 0.22f * pulse);
            polyline(m, pts, 0.32f * w, lerp(c.env.r2, 1f, 0.5f), lerp(c.env.g2, 1f, 0.5f), lerp(c.env.b2, 1f, 0.5f), 0.55f * pulse);
            polyline(m, pts, 0.11f * w, 1f, 1f, 1f, pulse);

            for (int br = 0; br < 3 && pts.size() > 8; br++) {
                float[] from = pts.get(4 + rnd.nextInt(pts.size() - 8));
                float[] to = {from[0] + (rnd.nextFloat() - 0.5f) * 16f, from[1] + (rnd.nextFloat() - 0.5f) * 16f,
                        from[2] + (rnd.nextFloat() - 0.2f) * 10f};
                List<float[]> sub = jag(from, to, rnd, 1.6f, 3);
                polyline(m, sub, 0.3f, c.env.r1, c.env.g1, c.env.b1, 0.2f * pulse);
                polyline(m, sub, 0.08f, 1f, 1f, 1f, 0.7f * pulse);
            }

            if (s.direct) {
                // The hit: a bloom around the ship and a spray of sparks that fly off it.
                m.disc(c.tx, c.ty, c.tz, 4.5f, 20, 1f, 1f, 1f, 0.75f * pulse);
                m.disc(c.tx, c.ty, c.tz, 8f, 20, c.env.r1, c.env.g1, c.env.b1, 0.45f * pulse);
                if (age <= 40f) {
                    for (int i = 0; i < 24; i++) {
                        float dx = h(n, 30 + i) - 0.5f, dy = h(n, 60 + i) - 0.5f, dz = h(n, 90 + i) - 0.5f;
                        float inv = 1f / Math.max(0.2f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
                        float travel = age * 0.55f * (0.4f + h(n, 120 + i));
                        float fade = 1f - age / 40f;
                        m.disc(c.tx + dx * inv * travel, c.ty + dy * inv * travel, c.tz + dz * inv * travel,
                                0.16f + 0.1f * h(n, 150 + i), 6, 1f, 0.95f, 0.8f, 0.9f * fade * c.intensity);
                    }
                }
            }
        }
    }

    private static List<float[]> jag(float[] a, float[] b, Random r, float disp, int depth) {
        List<float[]> pts = new ArrayList<>();
        pts.add(a);
        pts.add(b);
        for (int d = 0; d < depth; d++) {
            List<float[]> next = new ArrayList<>();
            for (int i = 0; i < pts.size() - 1; i++) {
                float[] p = pts.get(i), q = pts.get(i + 1);
                next.add(p);
                next.add(new float[]{
                        (p[0] + q[0]) * 0.5f + (r.nextFloat() - 0.5f) * 2f * disp,
                        (p[1] + q[1]) * 0.5f + (r.nextFloat() - 0.5f) * 2f * disp,
                        (p[2] + q[2]) * 0.5f + (r.nextFloat() - 0.5f) * 1.2f * disp});
            }
            next.add(pts.get(pts.size() - 1));
            pts = next;
            disp *= 0.55f;
        }
        return pts;
    }

    /** A line of camera-facing ribbons through the points. */
    private static void polyline(Mesh m, List<float[]> pts, float width, float r, float g, float b, float a) {
        for (int i = 0; i < pts.size() - 1; i++) {
            float[] p = pts.get(i), q = pts.get(i + 1);
            float dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2];
            float vx = (p[0] + q[0]) * 0.5f, vy = (p[1] + q[1]) * 0.5f, vz = (p[2] + q[2]) * 0.5f;
            float sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
            float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1e-4f) continue;
            float scale = width * Math.max(1f, (float) Math.sqrt(vx * vx + vy * vy + vz * vz) / 9f) / len;
            m.ribbon(p[0], p[1], p[2], q[0], q[1], q[2], sx * scale, sy * scale, sz * scale, r, g, b, a, a);
        }
    }

    // ------------------------------------------------------------------ maths

    private static float dist(float[] a, float[] b) {
        float dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    /** Cheap repeatable pseudo-random number in 0..1 from two numbers. */
    private static float h(float a, float b) {
        double s = Math.sin(a * 12.9898 + b * 78.233) * 43758.5453;
        return (float) (s - Math.floor(s));
    }
}
