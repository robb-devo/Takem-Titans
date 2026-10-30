package com.takem.titans.client.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.takem.titans.TakemTitans;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;

/**
 * The combat post pass: one full-screen shader (shaders/program/combat.fsh) for the chromatic split and radial smear of a
 * big blow, the shock ripple racing out from where it landed, and the heat haze, warm grade and vignette while in a
 * titan's body. It runs only while one of those is live, and switches itself off for good if the shader won't load.
 */
@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class PostFx {
    private static final Logger LOG = LogUtils.getLogger();
    private static final ResourceLocation CHAIN = TakemTitans.id("shaders/post/combat.json");
    private static PostChain chain;
    private static boolean failed;
    private static int width = -1;
    private static int height = -1;
    private static long last;
    private static float clock;
    private static float aberration;
    private static float zoom;
    private static float centerX = 0.5F;
    private static float centerY = 0.5F;
    private static float rippleT = 1.0F;
    private static float rippleS;
    private static float rippleX = 0.5F;
    private static float rippleY = 0.5F;
    private static float flash;
    private static float heat;
    private static float tint;
    private static float vignette;
    private static float heatTo;
    private static float tintTo;
    private static float vignetteTo;
    private static float tr = 1.0F;
    private static float tg = 0.75F;
    private static float tb = 0.5F;
    private static int held;

    private PostFx() {
    }

    /** A blow felt through the screen: the colours split and the picture smears outward from the middle. */
    public static void kick(float aberr, float smear) {
        aberration = Math.max(aberration, aberr);
        if (smear > zoom) {
            zoom = smear;
            centerX = 0.5F;
            centerY = 0.5F;
        }
    }

    /** A blast: the smear centred on it, a ripple from it, a flash. */
    public static void burst(Vec3 at, float aberr, float smear) {
        kick(aberr, smear);
        float[] s = screen(at);
        if (s != null) {
            centerX = Mth.clamp(s[0], 0.0F, 1.0F);
            centerY = Mth.clamp(s[1], 0.0F, 1.0F);
        }
        ripple(at, 1.4F);
        flash = Math.max(flash, 0.3F);
    }

    /** A shock ring racing across the screen from where something hit. */
    public static void ripple(Vec3 at, float strength) {
        if (rippleT < 0.25F && rippleS > strength) {
            return;
        }
        float[] s = screen(at);
        rippleX = s != null ? s[0] : 0.5F;
        rippleY = s != null ? s[1] : 0.5F;
        rippleT = 0.0F;
        rippleS = strength;
    }

    /** A held look, asked for again every tick while it lasts; it fades out once nobody asks. */
    public static void hold(float heatAmount, float tintAmount, float vignetteAmount, float r, float g, float b) {
        heatTo = heatAmount;
        tintTo = tintAmount;
        vignetteTo = vignetteAmount;
        tr = r;
        tg = g;
        tb = b;
        held = 3;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (held > 0 && --held == 0) {
            heatTo = 0.0F;
            tintTo = 0.0F;
            vignetteTo = 0.0F;
        }
    }

    /** Where a point falls on the screen (0..1, y up as the shader sees it), or null if it's behind the camera. */
    public static float[] screen(Vec3 p) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 d = p.subtract(cam.getPosition());
        Vec3 f = Vec3.directionFromRotation(cam.getXRot(), cam.getYRot());
        Vec3 r = f.cross(new Vec3(0.0, 1.0, 0.0));
        if (r.lengthSqr() < 1.0E-6) {
            return null;
        }
        r = r.normalize();
        Vec3 u = r.cross(f);
        double z = d.dot(f);
        if (z < 0.5) {
            return null;
        }
        double tan = Math.tan(Math.toRadians((double)mc.options.fov().get().intValue()) * 0.5);
        double aspect = (double)mc.getWindow().getWidth() / Math.max(1.0, (double)mc.getWindow().getHeight());
        float sx = (float)(0.5 + d.dot(r) / z / (2.0 * tan * aspect));
        float sy = (float)(0.5 + d.dot(u) / z / (2.0 * tan));
        return new float[]{Mth.clamp(sx, -0.2F, 1.2F), Mth.clamp(sy, -0.2F, 1.2F)};
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        long now = Util.getMillis();
        float dt = last == 0L ? 0.016F : Mth.clamp((float)(now - last) / 1000.0F, 0.0F, 0.1F);
        last = now;
        clock = (clock + dt) % 600.0F;
        aberration *= (float)Math.exp((double)(-dt * 4.5F));
        zoom *= (float)Math.exp((double)(-dt * 5.5F));
        flash *= (float)Math.exp((double)(-dt * 6.0F));
        rippleT = Math.min(1.0F, rippleT + dt * 1.5F);
        float follow = Math.min(1.0F, dt * 3.0F);
        heat += (heatTo - heat) * follow;
        tint += (tintTo - tint) * follow;
        vignette += (vignetteTo - vignette) * follow;
        boolean ripple = rippleT < 1.0F && rippleS > 0.01F;
        if (failed || aberration < 0.004F && zoom < 0.004F && flash < 0.004F && !ripple && heat < 0.004F && tint < 0.004F && vignette < 0.004F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, CHAIN);
                width = -1;
            }
            if (main.width != width || main.height != height) {
                width = main.width;
                height = main.height;
                chain.resize(width, height);
            }
            chain.setUniform("Clock", clock);
            chain.setUniform("Aberration", aberration);
            chain.setUniform("Zoom", zoom);
            chain.setUniform("CenterX", centerX);
            chain.setUniform("CenterY", centerY);
            chain.setUniform("RippleT", rippleT);
            chain.setUniform("RippleS", ripple ? rippleS : 0.0F);
            chain.setUniform("RippleX", rippleX);
            chain.setUniform("RippleY", rippleY);
            chain.setUniform("Heat", heat);
            chain.setUniform("Tint", tint);
            chain.setUniform("TintR", tr);
            chain.setUniform("TintG", tg);
            chain.setUniform("TintB", tb);
            chain.setUniform("Vignette", vignette);
            chain.setUniform("Flash", flash);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.resetTextureMatrix();
            chain.process(event.getPartialTick().getGameTimeDeltaTicks());
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            main.bindWrite(true);
        } catch (Exception e) {
            failed = true;
            LOG.warn("Takem Titans: the combat post effect could not run and is switched off", e);
        }
    }
}
