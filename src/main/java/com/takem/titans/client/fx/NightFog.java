package com.takem.titans.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor;
import net.neoforged.neoforge.client.event.ViewportEvent.RenderFog;

/**
 * The fog of a night mission: it rolls in over a few seconds once you come near the place, thick enough that a titan is a
 * shape until it is forty blocks off, and thins again as you leave or when the mission is over.
 */
@EventBusSubscriber(modid = "takemtitans", value = Dist.CLIENT)
public final class NightFog {
    private static boolean on;
    private static double x;
    private static double z;
    private static double r;
    private static float k;
    private static final float[] MIST = {0.62F, 0.66F, 0.72F};
    private static float kO;
    private static boolean below;
    private static float kt;
    private static float ktO;

    /** Down in the tunnels: a close, rust-dark fog and ash drifting in the air. */
    public static void tunnel(boolean on) {
        below = on;
    }

    private NightFog() {
    }

    public static void handle(CompoundTag tag) {
        on = tag.getBoolean("on");
        if (on) {
            x = tag.getDouble("x");
            z = tag.getDouble("z");
            r = tag.getDouble("r");
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        kO = k;
        float target = 0.0F;
        if (on && mc.player != null) {
            double d = Math.sqrt(mc.player.distanceToSqr(x, mc.player.getY(), z));
            target = (float)Mth.clamp(1.0 - (d - r) / 120.0, 0.0, 1.0);
        }

        k += Mth.clamp(target - k, -0.012F, 0.008F);
        ktO = kt;
        kt += Mth.clamp((below ? 1.0F : 0.0F) - kt, -0.04F, 0.03F);
        if (kt > 0.2F && mc.level != null && mc.player != null && !mc.isPaused()) {
            RandomSource r = mc.level.random;
            for (int i = 0; i < 2; i++) {
                double px = mc.player.getX() + (r.nextDouble() - 0.5) * 16.0;
                double py = mc.player.getY() + (r.nextDouble() - 0.3) * 6.0;
                double pz = mc.player.getZ() + (r.nextDouble() - 0.5) * 16.0;
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_ASH, px, py, pz, 0.0, -0.01, 0.0);
            }
        }

        // low banks of mist drifting through the streets: they show with a shader pack on too, which keeps its own fog
        if (k > 0.15F && mc.level != null && mc.player != null && !mc.isPaused() && mc.level.getGameTime() % 15L == 0L) {
            RandomSource rnd = mc.level.random;
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double d = 6.0 + rnd.nextDouble() * 26.0;
            double px = mc.player.getX() + Math.cos(a) * d;
            double pz = mc.player.getZ() + Math.sin(a) * d;
            int y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(px), Mth.floor(pz));
            DebrisFx.haze(mc.level, new Vec3(px, y, pz), 8.0F * k, 1, MIST);
        }
    }

    private static float strength(float pt) {
        return Mth.lerp(pt, kO, k);
    }

    @SubscribeEvent
    public static void onFog(RenderFog event) {
        float t = Mth.lerp((float)event.getPartialTick(), ktO, kt);
        if (t > 0.002F && event.getMode() == FogMode.FOG_TERRAIN) {
            float far = event.getFarPlaneDistance();
            event.setFarPlaneDistance(Mth.lerp(t, far, Math.min(far, 34.0F)));
            event.setNearPlaneDistance(Mth.lerp(t, event.getNearPlaneDistance(), -4.0F));
            event.setCanceled(true);
        }

        float s = strength((float)event.getPartialTick());
        if (s > 0.002F && event.getMode() == FogMode.FOG_TERRAIN) {
            float far = event.getFarPlaneDistance();
            event.setFarPlaneDistance(Mth.lerp(s, far, Math.min(far, 46.0F)));
            event.setNearPlaneDistance(Mth.lerp(s, event.getNearPlaneDistance(), -8.0F));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFogColor(ComputeFogColor event) {
        float t = Mth.lerp((float)event.getPartialTick(), ktO, kt) * 0.9F;
        if (t > 0.002F) {
            event.setRed(Mth.lerp(t, event.getRed(), 0.11F));
            event.setGreen(Mth.lerp(t, event.getGreen(), 0.05F));
            event.setBlue(Mth.lerp(t, event.getBlue(), 0.04F));
        }

        float s = strength((float)event.getPartialTick()) * 0.85F;
        if (s > 0.002F) {
            event.setRed(Mth.lerp(s, event.getRed(), 0.13F));
            event.setGreen(Mth.lerp(s, event.getGreen(), 0.15F));
            event.setBlue(Mth.lerp(s, event.getBlue(), 0.19F));
        }
    }
}
