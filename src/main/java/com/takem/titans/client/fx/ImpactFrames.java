package com.takem.titans.client.fx;

import com.takem.titans.TakemConfig;
import com.takem.titans.TakemTitans;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent.Pre;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class ImpactFrames {
    private static final ResourceLocation SHADER = TakemTitans.id("shaders/post/impact_frame.json");
    private static final int FLIP_MS = 75;
    private static long start = -1L;
    private static long length;
    private static float strength;
    private static float heat;
    private static boolean loaded;

    private ImpactFrames() {
    }

    public static void trigger(float strength, int ms) {
        trigger(strength, ms, 0.0F);
    }

    public static void trigger(float s, int ms, float h) {
        if (TakemConfig.IMPACT_FRAMES.get()) {
            long now = Util.getMillis();
            if (start < 0L || now - start >= length || !(s < strength)) {
                start = now;
                length = (long)Math.max(30, ms);
                strength = Math.min(1.0F, s);
                heat = h;
            }
        }
    }

    public static boolean active() {
        return start >= 0L;
    }

    @SubscribeEvent
    public static void onFrame(Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (start < 0L) {
            if (loaded) {
                stop(mc);
            }
        } else {
            long t = Util.getMillis() - start;
            if (t < length && mc.level != null) {
                PostChain chain = mc.gameRenderer.currentEffect();
                if (!loaded || chain == null || !SHADER.toString().equals(chain.getName())) {
                    mc.gameRenderer.loadEffect(SHADER);
                    chain = mc.gameRenderer.currentEffect();
                    loaded = chain != null && SHADER.toString().equals(chain.getName());
                    if (!loaded) {
                        start = -1L;
                        return;
                    }
                }

                int flip = (int)(t / 75L);
                float phase = length <= 190L ? ((float)t < (float)length * 0.45F ? 0.0F : 1.0F) : (float)(flip % 2);
                float fadeOut = Math.min(1.0F, (float)(length - t) / 40.0F);
                chain.setUniform("Phase", phase);
                chain.setUniform("Strength", strength * Math.max(0.35F, fadeOut));
                chain.setUniform("Seed", (float)flip);
                chain.setUniform("Heat", heat);
            } else {
                start = -1L;
                stop(mc);
            }
        }
    }

    private static void stop(Minecraft mc) {
        loaded = false;
        PostChain chain = mc.gameRenderer.currentEffect();
        if (chain != null && SHADER.toString().equals(chain.getName())) {
            mc.gameRenderer.shutdownEffect();
            if (mc.getCameraEntity() != null) {
                mc.gameRenderer.checkEntityPostEffect(mc.getCameraEntity());
            }
        }
    }
}
