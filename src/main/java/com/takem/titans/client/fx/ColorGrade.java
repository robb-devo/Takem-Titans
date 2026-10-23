package com.takem.titans.client.fx;

import com.mojang.logging.LogUtils;
import com.takem.titans.TakemConfig;
import com.takem.titans.TakemTitans;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * A subtle cinematic grade over the rendered world, drawn at the very end of level rendering so the HUD, hand and
 * menus stay true. Steps aside when a shader-pack loader is installed, and switches itself off for good if the
 * graphics driver refuses the shader.
 */
@EventBusSubscriber(modid = "takemtitans", value = Dist.CLIENT)
public final class ColorGrade {
    private static final ResourceLocation SHADER = TakemTitans.id("shaders/post/grade.json");
    private static PostChain chain;
    private static boolean failed;
    private static int width;
    private static int height;
    private static Boolean shaderPacks;

    private ColorGrade() {
    }

    /** Resource reloads and window recreation start from a fresh chain. */
    public static void reset() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || failed) {
            return;
        }

        float strength = TakemConfig.COLOR_GRADE.get() ? TakemConfig.COLOR_GRADE_STRENGTH.get().floatValue() : 0.0F;
        if (strength <= 0.0F) {
            reset();
            return;
        }

        if (shaderPacks == null) {
            shaderPacks = ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus");
        }

        if (shaderPacks) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();

        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), SHADER);
                width = -1;
            }

            if (w != width || h != height) {
                chain.resize(w, h);
                width = w;
                height = h;
            }

            chain.setUniform("Strength", Math.min(1.0F, strength));
            chain.process(event.getPartialTick().getGameTimeDeltaTicks());
        } catch (Exception e) {
            failed = true;
            reset();
            LogUtils.getLogger().warn("Takem Titans colour grade disabled: {}", e.toString());
        }

        mc.getMainRenderTarget().bindWrite(true);
    }
}
