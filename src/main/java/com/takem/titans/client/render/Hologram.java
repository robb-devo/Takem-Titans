package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.takem.titans.client.story.StoryClient;
import com.takem.titans.entity.npc.TownsfolkEntity;
import com.takem.titans.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

public final class Hologram {
    private static final int FULL_BRIGHT = 15728880;
    private static ItemStack badge;

    private Hologram() {
    }

    public static void render(TownsfolkEntity e, EntityRenderDispatcher dispatcher, Font font, PoseStack ps, MultiBufferSource buffers, float pt) {
        boolean shop = e.getRole() == 3;
        boolean quest = e.isStoryCharacter() && StoryClient.questTargetNear(e.getX(), e.getZ(), 7.0);
        if (shop || quest) {
            Minecraft mc = Minecraft.getInstance();
            double dist = mc.player != null ? Math.sqrt(mc.player.distanceToSqr(e)) : 0.0;
            float grow = (float)Math.max(1.0, dist / 18.0);
            float t = (float)e.tickCount + pt;
            ps.pushPose();
            ps.translate(0.0F, e.getBbHeight() + 0.75F + Mth.sin(t * 0.08F) * 0.06F, 0.0F);
            if (shop) {
                if (badge == null) {
                    badge = new ItemStack(ModItems.MERIT_BADGE.get());
                }

                ps.pushPose();
                ps.translate(0.0F, 0.85F * grow, 0.0F);
                ps.mulPose(Axis.YP.rotationDegrees(t * 4.0F));
                ps.scale(0.7F * grow, 0.7F * grow, 0.7F * grow);
                mc.getItemRenderer().renderStatic(badge, ItemDisplayContext.FIXED, 15728880, OverlayTexture.NO_OVERLAY, ps, buffers, e.level(), e.getId());
                ps.popPose();
            }

            ps.mulPose(dispatcher.cameraOrientation());
            float s = 0.026F * grow;
            ps.scale(s, -s, s);
            if (quest) {
                ps.pushPose();
                ps.translate(0.0F, -16.0F, 0.0F);
                ps.scale(3.0F, 3.0F, 3.0F);
                line(font, ps, buffers, Component.literal("!"), 0, -14288);
                ps.popPose();
            }

            if (shop) {
                line(font, ps, buffers, Component.translatable("npc.takemtitans.holo.shop"), 0, -10134);
                line(font, ps, buffers, Component.translatable("npc.takemtitans.holo.shop_hint"), 11, -4200193);
            }

            ps.popPose();
        }
    }

    private static void line(Font font, PoseStack ps, MultiBufferSource buffers, Component text, int y, int color) {
        Matrix4f m = ps.last().pose();
        float x = (float)(-font.width(text) / 2);
        font.drawInBatch(text, x, (float)y, 1358954495 & color, false, m, buffers, DisplayMode.SEE_THROUGH, 1426063360, 15728880);
        font.drawInBatch(text, x, (float)y, color, true, m, buffers, DisplayMode.NORMAL, 0, 15728880);
    }
}
