package com.takem.titans.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class WorldFx {
    static final RenderType GLOW = RenderType.create(
        "takemtitans_glow",
        DefaultVertexFormat.POSITION_COLOR,
        Mode.QUADS,
        65536,
        false,
        false,
        CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
            .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .createCompositeState(false)
    );
    private static final List<WorldFx.Effect> EFFECTS = new ArrayList<>();
    private static final int MAX_EFFECTS = 400;

    private WorldFx() {
    }

    /** Effects added while the list is being ticked (an effect spawning more as it goes) wait here until it is done. */
    private static final List<WorldFx.Effect> PENDING = new ArrayList<>();
    private static boolean ticking;

    public static void add(WorldFx.Effect e) {
        if (ticking) {
            PENDING.add(e);
            return;
        }

        if (EFFECTS.size() >= 400) {
            EFFECTS.remove(0);
        }

        EFFECTS.add(e);
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            EFFECTS.clear();
            PENDING.clear();
        } else if (!mc.isPaused()) {
            EFFECTS.removeIf(ex -> ++ex.age >= ex.life);
            ticking = true;
            try {
                for (WorldFx.Effect e : EFFECTS) {
                    e.tick();
                }
            } finally {
                ticking = false;
            }

            for (WorldFx.Effect e : PENDING) {
                add(e);
            }

            PENDING.clear();
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() == Stage.AFTER_PARTICLES && !EFFECTS.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            Vec3 cam = event.getCamera().getPosition();
            float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            BufferSource bs = mc.renderBuffers().bufferSource();
            VertexConsumer vc = bs.getBuffer(GLOW);
            Matrix4f m = new PoseStack().last().pose();
            WorldFx.Draw d = new WorldFx.Draw(vc, m, cam);

            for (WorldFx.Effect e : EFFECTS) {
                e.render(d, pt);
            }

            bs.endBatch(GLOW);
        }
    }

    public static final class Bolt extends WorldFx.Effect {
        final Vec3[] pts;
        final float width;
        final float r;
        final float g;
        final float b;

        public Bolt(Vec3 from, Vec3 to, float width, int color, RandomSource rnd) {
            super(7);
            int n = Mth.clamp((int)(from.distanceTo(to) / 1.6), 6, 40);
            this.pts = new Vec3[n + 1];
            double jag = from.distanceTo(to) / (double)n * 0.9;

            for (int i = 0; i <= n; i++) {
                Vec3 p = from.lerp(to, (double)i / (double)n);
                if (i > 0 && i < n) {
                    p = p.add((rnd.nextDouble() - 0.5) * jag * 2.0, (rnd.nextDouble() - 0.5) * jag, (rnd.nextDouble() - 0.5) * jag * 2.0);
                }

                this.pts[i] = p;
            }

            this.width = width;
            this.r = (float)(color >> 16 & 0xFF) / 255.0F;
            this.g = (float)(color >> 8 & 0xFF) / 255.0F;
            this.b = (float)(color & 0xFF) / 255.0F;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float p = this.progress(pt);
            float flick = (this.age % 2 == 0 ? 1.0F : 0.55F) * (1.0F - p);
            float[] w = new float[this.pts.length];
            float[] a = new float[this.pts.length];
            float[] wc = new float[this.pts.length];
            float[] ac = new float[this.pts.length];

            for (int i = 0; i < this.pts.length; i++) {
                w[i] = this.width * 3.0F;
                a[i] = 0.35F * flick;
                wc[i] = this.width;
                ac[i] = flick;
            }

            d.ribbon(this.pts, w, this.r, this.g, this.b, a);
            d.ribbon(this.pts, wc, 1.0F, 1.0F, 1.0F, ac);
        }
    }

    public static final class Draw {
        final VertexConsumer vc;
        final Matrix4f m;
        public final Vec3 cam;

        Draw(VertexConsumer vc, Matrix4f m, Vec3 cam) {
            this.vc = vc;
            this.m = m;
            this.cam = cam;
        }

        public void v(double x, double y, double z, float r, float g, float b, float a) {
            this.vc.addVertex(this.m, (float)(x - this.cam.x), (float)(y - this.cam.y), (float)(z - this.cam.z)).setColor(r, g, b, Mth.clamp(a, 0.0F, 1.0F));
        }

        public void v(Vec3 p, float r, float g, float b, float a) {
            this.v(p.x, p.y, p.z, r, g, b, a);
        }

        public void strip(Vec3[] in, Vec3[] out, float[] rgb, float[] alphaIn, float[] alphaOut) {
            for (int i = 0; i + 1 < in.length; i++) {
                this.v(in[i], rgb[0], rgb[1], rgb[2], alphaIn[i]);
                this.v(out[i], rgb[0], rgb[1], rgb[2], alphaOut[i]);
                this.v(out[i + 1], rgb[0], rgb[1], rgb[2], alphaOut[i + 1]);
                this.v(in[i + 1], rgb[0], rgb[1], rgb[2], alphaIn[i + 1]);
            }
        }

        public void ribbon(Vec3[] pts, float[] width, float r, float g, float b, float[] alpha) {
            int n = pts.length;
            if (n >= 2) {
                Vec3[] left = new Vec3[n];
                Vec3[] right = new Vec3[n];

                for (int i = 0; i < n; i++) {
                    Vec3 dir = pts[Math.min(n - 1, i + 1)].subtract(pts[Math.max(0, i - 1)]);
                    Vec3 toCam = this.cam.subtract(pts[i]);
                    Vec3 side = dir.cross(toCam);
                    double l = side.length();
                    side = l < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : side.scale((double)width[i] * 0.5 / l);
                    left[i] = pts[i].add(side);
                    right[i] = pts[i].subtract(side);
                }

                for (int i = 0; i + 1 < n; i++) {
                    this.v(left[i], r, g, b, alpha[i]);
                    this.v(right[i], r, g, b, alpha[i]);
                    this.v(right[i + 1], r, g, b, alpha[i + 1]);
                    this.v(left[i + 1], r, g, b, alpha[i + 1]);
                }
            }
        }

        public void sphere(Vec3 c, double radius, float r, float g, float b, float a, float edge, int seg) {
            Vec3 view = this.cam.subtract(c).normalize();
            int rings = seg / 2;

            for (int i = 0; i < rings; i++) {
                double t0 = 3.141592653589793 * (double)i / (double)rings;
                double t1 = 3.141592653589793 * (double)(i + 1) / (double)rings;

                for (int j = 0; j < seg; j++) {
                    double p0 = (double)(6.2831855F * (float)j / (float)seg);
                    double p1 = (double)(6.2831855F * (float)(j + 1) / (float)seg);
                    Vec3 n00 = unit(t0, p0);
                    Vec3 n01 = unit(t0, p1);
                    Vec3 n11 = unit(t1, p1);
                    Vec3 n10 = unit(t1, p0);
                    this.sv(c, radius, n00, view, r, g, b, a, edge);
                    this.sv(c, radius, n10, view, r, g, b, a, edge);
                    this.sv(c, radius, n11, view, r, g, b, a, edge);
                    this.sv(c, radius, n01, view, r, g, b, a, edge);
                }
            }
        }

        private void sv(Vec3 c, double radius, Vec3 n, Vec3 view, float r, float g, float b, float a, float edge) {
            float facing = (float)Math.abs(n.dot(view));
            float alpha = a * Mth.lerp(facing, edge, 1.0F);
            this.v(c.x + n.x * radius, c.y + n.y * radius, c.z + n.z * radius, r, g, b, alpha);
        }

        private static Vec3 unit(double theta, double phi) {
            double s = Math.sin(theta);
            return new Vec3(s * Math.cos(phi), Math.cos(theta), s * Math.sin(phi));
        }

        public void ring(Vec3 c, double r0, double r1, float r, float g, float b, float a, int seg) {
            double mid = (r0 + r1) * 0.5;

            for (int j = 0; j < seg; j++) {
                double p0 = (double)(6.2831855F * (float)j / (float)seg);
                double p1 = (double)(6.2831855F * (float)(j + 1) / (float)seg);
                double c0 = Math.cos(p0);
                double s0 = Math.sin(p0);
                double c1 = Math.cos(p1);
                double s1 = Math.sin(p1);
                this.v(c.x + c0 * r0, c.y, c.z + s0 * r0, r, g, b, 0.0F);
                this.v(c.x + c0 * mid, c.y, c.z + s0 * mid, r, g, b, a);
                this.v(c.x + c1 * mid, c.y, c.z + s1 * mid, r, g, b, a);
                this.v(c.x + c1 * r0, c.y, c.z + s1 * r0, r, g, b, 0.0F);
                this.v(c.x + c0 * mid, c.y, c.z + s0 * mid, r, g, b, a);
                this.v(c.x + c0 * r1, c.y, c.z + s0 * r1, r, g, b, 0.0F);
                this.v(c.x + c1 * r1, c.y, c.z + s1 * r1, r, g, b, 0.0F);
                this.v(c.x + c1 * mid, c.y, c.z + s1 * mid, r, g, b, a);
            }
        }

        public void ringIn(Vec3 c, Vec3 u, Vec3 w, double r0, double r1, float r, float g, float b, float a, int seg) {
            double mid = (r0 + r1) * 0.5;

            for (int j = 0; j < seg; j++) {
                double p0 = (double)(6.2831855F * (float)j / (float)seg);
                double p1 = (double)(6.2831855F * (float)(j + 1) / (float)seg);
                Vec3 d0 = u.scale(Math.cos(p0)).add(w.scale(Math.sin(p0)));
                Vec3 d1 = u.scale(Math.cos(p1)).add(w.scale(Math.sin(p1)));
                this.v(c.add(d0.scale(r0)), r, g, b, 0.0F);
                this.v(c.add(d0.scale(mid)), r, g, b, a);
                this.v(c.add(d1.scale(mid)), r, g, b, a);
                this.v(c.add(d1.scale(r0)), r, g, b, 0.0F);
                this.v(c.add(d0.scale(mid)), r, g, b, a);
                this.v(c.add(d0.scale(r1)), r, g, b, 0.0F);
                this.v(c.add(d1.scale(r1)), r, g, b, 0.0F);
                this.v(c.add(d1.scale(mid)), r, g, b, a);
            }
        }

        public void wall(Vec3 c, double radius, double y0, double y1, float r, float g, float b, float a, int seg) {
            for (int j = 0; j < seg; j++) {
                double p0 = (double)(6.2831855F * (float)j / (float)seg);
                double p1 = (double)(6.2831855F * (float)(j + 1) / (float)seg);
                double c0 = Math.cos(p0);
                double s0 = Math.sin(p0);
                double c1 = Math.cos(p1);
                double s1 = Math.sin(p1);
                this.v(c.x + c0 * radius, y0, c.z + s0 * radius, r, g, b, a);
                this.v(c.x + c1 * radius, y0, c.z + s1 * radius, r, g, b, a);
                this.v(c.x + c1 * radius, y1, c.z + s1 * radius, r, g, b, 0.0F);
                this.v(c.x + c0 * radius, y1, c.z + s0 * radius, r, g, b, 0.0F);
            }
        }
    }

    /** Dark smoke that keeps rising where something blew up or burned, thinning out over its life. Draws nothing itself. */
    public static final class SmokeColumn extends WorldFx.Effect {
        final Vec3 c;
        final float radius;

        public SmokeColumn(Vec3 c, float radius, int life) {
            super(life);
            this.c = c;
            this.radius = radius;
        }

        @Override
        void tick() {
            net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }

            float k = 1.0F - (float)this.age / (float)this.life;
            int n = k > 0.6F ? 4 + (int)(this.radius * 0.3F) : (level.random.nextFloat() < k * 1.8F ? 2 : 0);
            for (int i = 0; i < n; i++) {
                double a = level.random.nextDouble() * Math.PI * 2.0;
                double r = level.random.nextDouble() * this.radius * 0.6;
                // FLARE_SMOKE takes its colour from the velocity arguments
                float g = 0.07F + level.random.nextFloat() * 0.08F;
                ClientFx.spawn(
                    level,
                    com.takem.titans.registry.ModParticles.FLARE_SMOKE.get(),
                    this.c.x + Math.cos(a) * r,
                    this.c.y + level.random.nextDouble() * this.radius * 0.4,
                    this.c.z + Math.sin(a) * r,
                    g,
                    g * 0.96F,
                    g * 0.92F,
                    2.4F + this.radius * 0.5F + level.random.nextFloat() * 2.0F
                );
            }
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
        }
    }

    public abstract static class Effect {
        int age;
        final int life;

        protected Effect(int life) {
            this.life = Math.max(1, life);
        }

        protected float progress(float pt) {
            return Mth.clamp(((float)this.age + pt) / (float)this.life, 0.0F, 1.0F);
        }

        void tick() {
        }

        abstract void render(WorldFx.Draw var1, float var2);
    }

    public static final class Fireball extends WorldFx.Effect {
        final Vec3 c;
        final float maxRadius;
        final int grow;

        public Fireball(Vec3 c, float maxRadius, int grow, int life) {
            super(life);
            this.c = c;
            this.maxRadius = maxRadius;
            this.grow = grow;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float t = (float)this.age + pt;
            float g = Mth.clamp(t / (float)this.grow, 0.0F, 1.0F);
            float swell = 1.0F - (1.0F - g) * (1.0F - g);
            float burn = Mth.clamp((t - (float)this.grow) / (float)(this.life - this.grow), 0.0F, 1.0F);
            float rad = this.maxRadius * (0.15F + 0.85F * swell) * (1.0F + 0.1F * burn);
            float a = 1.0F - burn;
            int seg = rad > 20.0F ? 40 : 24;
            d.sphere(this.c, (double)rad * 1.25, 1.0F, 0.42F, 0.08F, 0.35F * a, 0.0F, seg);
            d.sphere(this.c, (double)rad, 1.0F, 0.7F + 0.2F * (1.0F - burn), 0.25F, 0.55F * a, 0.1F, seg);
            d.sphere(this.c, (double)rad * 0.7, 1.0F, 0.95F, 0.8F - 0.5F * burn, 0.8F * a * a, 0.3F, seg);
        }
    }

    public static final class Flash extends WorldFx.Effect {
        final Vec3 c;
        final float maxRadius;

        public Flash(Vec3 c, float maxRadius, int life) {
            super(life);
            this.c = c;
            this.maxRadius = maxRadius;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float p = this.progress(pt);
            float rad = this.maxRadius * (0.3F + 0.7F * (1.0F - (1.0F - p) * (1.0F - p)));
            float a = (1.0F - p) * (1.0F - p);
            d.sphere(this.c, (double)rad, 1.0F, 0.97F, 0.9F, 0.55F * a, 0.0F, 40);
            d.sphere(this.c, (double)rad * 0.55, 1.0F, 1.0F, 1.0F, 0.8F * a, 0.2F, 32);
        }
    }

    public static final class Orb extends WorldFx.Effect {
        final Vec3 c;
        final float radius;
        final float r;
        final float g;
        final float b;
        final RandomSource rnd;

        public Orb(Vec3 c, float radius, int life, int color, RandomSource rnd) {
            super(life);
            this.c = c;
            this.radius = radius;
            this.r = (float)(color >> 16 & 0xFF) / 255.0F;
            this.g = (float)(color >> 8 & 0xFF) / 255.0F;
            this.b = (float)(color & 0xFF) / 255.0F;
            this.rnd = rnd;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float flick = 0.7F + 0.3F * this.rnd.nextFloat();
            float a = (1.0F - this.progress(pt) * 0.5F) * flick;
            d.sphere(this.c, (double)this.radius * 1.6, this.r, this.g, this.b, 0.25F * a, 0.0F, 20);
            d.sphere(this.c, (double)this.radius, Mth.lerp(0.4F, this.r, 1.0F), Mth.lerp(0.4F, this.g, 1.0F), Mth.lerp(0.4F, this.b, 1.0F), 0.6F * a, 0.1F, 20);
            d.sphere(this.c, (double)this.radius * 0.45, 1.0F, 1.0F, 1.0F, 0.9F * a, 0.4F, 16);
        }
    }

    public static final class Pillar extends WorldFx.Effect {
        final Vec3 c;
        final float radius;
        final float height;

        public Pillar(Vec3 c, float radius, float height, int life) {
            super(life);
            this.c = c;
            this.radius = radius;
            this.height = height;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float p = this.progress(pt);
            float rise = Mth.clamp(p * 4.0F, 0.0F, 1.0F);
            float a = (1.0F - p) * (1.0F - p);
            float rad = this.radius * (0.6F + 0.4F * rise) * (1.0F - 0.5F * p);
            d.wall(this.c, (double)rad * 1.6, this.c.y, this.c.y + (double)(this.height * rise), 1.0F, 0.55F, 0.15F, 0.3F * a, 32);
            d.wall(this.c, (double)rad, this.c.y, this.c.y + (double)(this.height * rise), 1.0F, 0.85F, 0.5F, 0.6F * a, 32);
            d.wall(this.c, (double)rad * 0.4, this.c.y, this.c.y + (double)(this.height * rise) * 1.1, 1.0F, 1.0F, 0.95F, 0.9F * a, 24);
        }
    }

    public static final class RushTrail extends WorldFx.Effect {
        final Vec3 from;
        final Vec3 to;
        final float width;
        final float r;
        final float g;
        final float b;

        public RushTrail(Vec3 from, Vec3 to, float width, int color) {
            super(16);
            this.from = from;
            this.to = to;
            this.width = width;
            this.r = (float)(color >> 16 & 0xFF) / 255.0F;
            this.g = (float)(color >> 8 & 0xFF) / 255.0F;
            this.b = (float)(color & 0xFF) / 255.0F;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float p = this.progress(pt);
            float fade = (1.0F - p) * (1.0F - p);
            int n = 12;
            Vec3[] pts = new Vec3[n + 1];
            float[] w = new float[n + 1];
            float[] a = new float[n + 1];
            float[] wc = new float[n + 1];
            float[] ac = new float[n + 1];

            for (int i = 0; i <= n; i++) {
                float f = (float)i / (float)n;
                pts[i] = this.from.lerp(this.to, (double)f);
                float shape = Mth.sin(f * 3.1415927F) * 0.7F + 0.3F * f;
                w[i] = this.width * shape * (1.0F + p * 1.5F);
                a[i] = 0.4F * fade;
                wc[i] = this.width * 0.25F * shape * (1.0F - p * 0.5F);
                ac[i] = 0.95F * fade;
            }

            d.ribbon(pts, w, this.r, this.g, this.b, a);
            d.ribbon(pts, wc, 1.0F, 1.0F, 1.0F, ac);
        }
    }

    public static final class ShockRing extends WorldFx.Effect {
        final Vec3 c;
        final float maxRadius;
        final float thickness;
        final float height;
        final float r;
        final float g;
        final float b;
        final float alpha;

        public ShockRing(Vec3 c, float maxRadius, int life, float thickness, float height, int color, float alpha) {
            super(life);
            this.c = c;
            this.maxRadius = maxRadius;
            this.thickness = thickness;
            this.height = height;
            this.r = (float)(color >> 16 & 0xFF) / 255.0F;
            this.g = (float)(color >> 8 & 0xFF) / 255.0F;
            this.b = (float)(color & 0xFF) / 255.0F;
            this.alpha = alpha;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float p = this.progress(pt);
            float e = 1.0F - (1.0F - p) * (1.0F - p) * (1.0F - p);
            double rad = (double)(this.maxRadius * e);
            float a = this.alpha * (1.0F - p);
            double th = (double)this.thickness * (0.4 + (double)p);
            int seg = Mth.clamp((int)(rad * 1.5), 24, 160);
            d.ring(this.c, Math.max(0.0, rad - th), rad + th * 0.3, this.r, this.g, this.b, a, seg);
            if (this.height > 0.0F) {
                d.wall(this.c, rad, this.c.y, this.c.y + (double)(this.height * (1.0F - p * 0.6F)), this.r, this.g, this.b, a * 0.6F, seg);
            }
        }
    }

    public static final class SlashArc extends WorldFx.Effect {
        final Vec3 c;
        final Quaternionf q;
        final float radius;
        final float span;
        final float r;
        final float g;
        final float b;
        final boolean big;

        public SlashArc(Vec3 c, float yaw, float pitch, float roll, float radius, int color, boolean big) {
            super(big ? 12 : 9);
            this.c = c;
            this.q = new Quaternionf().rotateY(-yaw * 0.017453292F).rotateX(pitch * 0.017453292F).rotateZ(roll * 0.017453292F);
            this.radius = radius;
            this.span = big ? 250.0F : 200.0F;
            this.r = (float)(color >> 16 & 0xFF) / 255.0F;
            this.g = (float)(color >> 8 & 0xFF) / 255.0F;
            this.b = (float)(color & 0xFF) / 255.0F;
            this.big = big;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float t = (float)this.age + pt;
            float sweep = Mth.clamp(t / 2.0F, 0.0F, 1.0F);
            float fade = 1.0F - Mth.clamp((t - 2.0F) / ((float)this.life - 2.0F), 0.0F, 1.0F);
            if (!(fade <= 0.0F)) {
                float grow = 1.0F + 0.12F * t / (float)this.life;
                int n = 28;
                float start = -this.span / 2.0F;
                float end = start + this.span * sweep;
                float rad = this.radius * grow;
                float maxW = this.radius * (this.big ? 0.32F : 0.24F);
                this.layer(d, n, start, end, rad, maxW * 2.6F, this.r, this.g, this.b, 0.35F * fade);
                this.layer(
                    d, n, start, end, rad, maxW * 1.4F, Mth.lerp(0.5F, this.r, 1.0F), Mth.lerp(0.5F, this.g, 1.0F), Mth.lerp(0.5F, this.b, 1.0F), 0.55F * fade
                );
                this.layer(d, n, start, end, rad, maxW * 0.45F, 1.0F, 1.0F, 1.0F, 0.95F * fade);
            }
        }

        private void layer(WorldFx.Draw d, int n, float a0, float a1, float rad, float width, float cr, float cg, float cb, float alpha) {
            Vec3[] in = new Vec3[n + 1];
            Vec3[] out = new Vec3[n + 1];
            float[] ai = new float[n + 1];
            float[] ao = new float[n + 1];
            Vector3f tmp = new Vector3f();

            for (int i = 0; i <= n; i++) {
                float f = (float)i / (float)n;
                float ang = Mth.lerp(f, a0, a1) * 0.017453292F;
                float w = width * Mth.sin(f * 3.1415927F) * (0.55F + 0.45F * f);
                float ri = rad - w * 0.5F;
                float ro = rad + w * 0.5F;
                tmp.set(Mth.cos(ang) * ri, Mth.sin(ang) * ri, 0.0F);
                this.q.transform(tmp);
                in[i] = this.c.add((double)tmp.x, (double)tmp.y, (double)tmp.z);
                tmp.set(Mth.cos(ang) * ro, Mth.sin(ang) * ro, 0.0F);
                this.q.transform(tmp);
                out[i] = this.c.add((double)tmp.x, (double)tmp.y, (double)tmp.z);
                float a = alpha * (0.35F + 0.65F * f);
                ai[i] = a;
                ao[i] = a * 0.25F;
            }

            d.strip(in, out, new float[]{cr, cg, cb}, ai, ao);
        }
    }

    public static final class SonicRing extends WorldFx.Effect {
        final Vec3 c;
        final Vec3 u;
        final Vec3 w;
        final Vec3 dir;
        final float maxRadius;
        final float r;
        final float g;
        final float b;

        public SonicRing(Vec3 c, Vec3 dir, float maxRadius, int life, int color) {
            super(life);
            this.c = c;
            this.dir = dir.normalize();
            Vec3 up = Math.abs(this.dir.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            this.u = this.dir.cross(up).normalize();
            this.w = this.u.cross(this.dir).normalize();
            this.maxRadius = maxRadius;
            this.r = (float)(color >> 16 & 0xFF) / 255.0F;
            this.g = (float)(color >> 8 & 0xFF) / 255.0F;
            this.b = (float)(color & 0xFF) / 255.0F;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float p = this.progress(pt);
            float e = 1.0F - (1.0F - p) * (1.0F - p);
            double rad = (double)this.maxRadius * (0.25 + 0.75 * (double)e);
            float a = 0.8F * (1.0F - p);
            Vec3 at = this.c.subtract(this.dir.scale((double)e * 1.5));
            d.ringIn(at, this.u, this.w, rad * 0.75, rad * 1.1, this.r, this.g, this.b, a, 40);
            d.ringIn(at.subtract(this.dir.scale(0.6)), this.u, this.w, rad * 0.5, rad * 0.8, 1.0F, 1.0F, 1.0F, a * 0.6F, 32);
        }
    }

    public static final class Starburst extends WorldFx.Effect {
        final Vec3 c;
        final Vec3[] rays;
        final float length;
        final float width;
        final float r;
        final float g;
        final float b;

        public Starburst(Vec3 c, int count, float length, float width, int life, int color, RandomSource rnd) {
            super(life);
            this.c = c;
            this.rays = new Vec3[count];

            for (int i = 0; i < count; i++) {
                double u = rnd.nextDouble() * 2.0 - 1.0;
                double a = rnd.nextDouble() * 6.2831854820251465;
                double s = Math.sqrt(1.0 - u * u);
                this.rays[i] = new Vec3(s * Math.cos(a), u, s * Math.sin(a)).scale(0.4 + rnd.nextDouble() * 0.6);
            }

            this.length = length;
            this.width = width;
            this.r = (float)(color >> 16 & 0xFF) / 255.0F;
            this.g = (float)(color >> 8 & 0xFF) / 255.0F;
            this.b = (float)(color & 0xFF) / 255.0F;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float p = this.progress(pt);
            float grow = Mth.clamp(p * 3.0F, 0.0F, 1.0F);
            float a = (1.0F - p) * (1.0F - p);

            for (Vec3 ray : this.rays) {
                Vec3 end = this.c.add(ray.scale((double)(this.length * grow)));
                Vec3[] pts = new Vec3[]{this.c, this.c.lerp(end, 0.5), end};
                d.ribbon(pts, new float[]{this.width, this.width * 0.6F, 0.0F}, this.r, this.g, this.b, new float[]{0.7F * a, 0.5F * a, 0.0F});
                d.ribbon(pts, new float[]{this.width * 0.3F, this.width * 0.15F, 0.0F}, 1.0F, 1.0F, 1.0F, new float[]{a, 0.7F * a, 0.0F});
            }

            d.sphere(this.c, (double)this.width * 2.5 * (1.0 - (double)p * 0.5), this.r, this.g, this.b, 0.5F * a, 0.0F, 16);
        }
    }

    public static final class WaveCrescent extends WorldFx.Effect {
        final Vec3 c;
        final Vec3 dir;
        final Vec3 edge;
        final Vec3 normal;
        final float half;
        final float depth;
        final boolean head;
        final boolean violet;

        public WaveCrescent(Vec3 c, Vec3 dir, Vec3 edge, float half, boolean head) {
            this(c, dir, edge, half, head, false);
        }

        public WaveCrescent(Vec3 c, Vec3 dir, Vec3 edge, float half, boolean head, boolean violet) {
            super(head ? 2 : (violet ? 11 : 8));
            this.violet = violet;
            this.c = c;
            this.dir = dir;
            this.edge = edge;
            this.normal = edge.cross(dir).normalize();
            this.half = half;
            this.depth = head ? 2.6F : 1.6F;
            this.head = head;
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float p = this.progress(pt);
            float fade = this.head ? 1.0F : (1.0F - p) * (1.0F - p) * 0.55F;
            int n = 24;
            float h = this.half * (1.0F + (this.head ? 0.0F : p * 0.35F));
            if (this.violet) {
                // the Founder's Edge: violet light with a gold rim
                this.layer(d, n, h * 1.04F, this.normal.scale((double)(this.depth * 1.5F)), 1.0F, 0.78F, 0.3F, 0.3F * fade, true);
                this.layer(d, n, h, this.dir.scale((double)(-this.depth * 2.4F)), 0.45F, 0.15F, 1.0F, 0.5F * fade, false);
                this.layer(d, n, h, this.dir.scale((double)(-this.depth)), 0.8F, 0.55F, 1.0F, 0.75F * fade, false);
                this.layer(d, n, h, this.normal.scale((double)(this.depth * 0.9F)), 0.6F, 0.25F, 1.0F, 0.6F * fade, true);
                this.layer(d, n, h, this.normal.scale((double)(this.depth * 0.35F)), 0.9F, 0.78F, 1.0F, 0.9F * fade, true);
                this.layer(d, n, h * 0.97F, this.normal.scale((double)(this.depth * 0.12F)), 1.0F, 1.0F, 1.0F, fade, true);
                return;
            }

            this.layer(d, n, h, this.dir.scale((double)(-this.depth * 1.8F)), 1.0F, 0.55F, 0.1F, 0.4F * fade, false);
            this.layer(d, n, h, this.dir.scale((double)(-this.depth)), 1.0F, 0.82F, 0.35F, 0.7F * fade, false);
            this.layer(d, n, h, this.normal.scale((double)(this.depth * 0.9F)), 1.0F, 0.6F, 0.12F, 0.5F * fade, true);
            this.layer(d, n, h, this.normal.scale((double)(this.depth * 0.35F)), 1.0F, 0.9F, 0.55F, 0.85F * fade, true);
            this.layer(d, n, h * 0.97F, this.normal.scale((double)(this.depth * 0.12F)), 1.0F, 1.0F, 1.0F, fade, true);
        }

        private void layer(WorldFx.Draw d, int n, float h, Vec3 thick, float cr, float cg, float cb, float alpha, boolean both) {
            Vec3[] a = new Vec3[n + 1];
            Vec3[] b = new Vec3[n + 1];
            float[] aa = new float[n + 1];
            float[] ab = new float[n + 1];

            for (int i = 0; i <= n; i++) {
                float f = (float)i / (float)n * 2.0F - 1.0F;
                double bow = (1.0 - (double)(f * f)) * (double)h * 0.32;
                float taper = 1.0F - f * f * 0.85F;
                Vec3 p = this.c.add(this.edge.scale((double)(f * h))).add(this.dir.scale(bow));
                a[i] = both ? p.subtract(thick.scale((double)taper)) : p;
                b[i] = p.add(thick.scale((double)taper));
                aa[i] = both ? 0.0F : alpha * taper;
                ab[i] = 0.0F;
            }

            if (both) {
                Vec3[] mid = new Vec3[n + 1];
                float[] am = new float[n + 1];

                for (int i = 0; i <= n; i++) {
                    mid[i] = a[i].add(b[i]).scale(0.5);
                    float f = (float)i / (float)n * 2.0F - 1.0F;
                    am[i] = alpha * (1.0F - f * f * 0.85F);
                }

                d.strip(mid, a, new float[]{cr, cg, cb}, am, aa);
                d.strip(mid, b, new float[]{cr, cg, cb}, am, ab);
            } else {
                d.strip(a, b, new float[]{cr, cg, cb}, aa, ab);
            }
        }
    }
}
