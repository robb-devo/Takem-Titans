package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.takem.titans.entity.TitanHeartEntity;
import com.takem.titans.registry.ModItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class HeartRenderer extends EntityRenderer<TitanHeartEntity> {
    private final ItemRenderer items;
    private ItemStack stack;

    public HeartRenderer(Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
    }

    public void render(TitanHeartEntity h, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        if (this.stack == null) {
            this.stack = new ItemStack(ModItems.TITAN_HEART_SHARD.get());
        }

        float t = (float)h.tickCount + pt;
        float beat = (float)Math.pow(Math.max(0.0, Math.sin((double)t * 0.26)), 6.0) * 0.12F;
        float s = h.size() * (1.0F + beat + (h.flash > 0 ? 0.08F : 0.0F));
        ps.pushPose();
        ps.translate(0.0F, h.size() * 0.5F + Mth.sin(t * 0.05F) * 0.15F, 0.0F);
        ps.mulPose(Axis.YP.rotationDegrees(t * 1.5F));

        for (int i = 0; i < 2; i++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees((float)i * 90.0F));
            ps.scale(s, s, s);
            this.items.renderStatic(this.stack, ItemDisplayContext.FIXED, 15728880, OverlayTexture.NO_OVERLAY, ps, buffers, h.level(), h.getId() + i);
            ps.popPose();
        }

        ps.popPose();
        super.render(h, yaw, pt, ps, buffers, light);
    }

    public ResourceLocation getTextureLocation(TitanHeartEntity h) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
