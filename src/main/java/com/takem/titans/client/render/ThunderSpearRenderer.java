package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.takem.titans.entity.ThunderSpearEntity;
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

public class ThunderSpearRenderer extends EntityRenderer<ThunderSpearEntity> {
    private final ItemRenderer itemRenderer;
    private ItemStack stack;

    public ThunderSpearRenderer(Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    public void render(ThunderSpearEntity spear, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        if (this.stack == null) {
            this.stack = new ItemStack(ModItems.THUNDER_SPEAR.get());
        }

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(Mth.lerp(pt, spear.yRotO, spear.getYRot()) - 90.0F));
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pt, spear.xRotO, spear.getXRot()) - 45.0F));
        ps.scale(1.4F, 1.4F, 1.4F);
        this.itemRenderer.renderStatic(this.stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buffers, spear.level(), spear.getId());
        ps.popPose();
        super.render(spear, yaw, pt, ps, buffers, light);
    }

    public ResourceLocation getTextureLocation(ThunderSpearEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
