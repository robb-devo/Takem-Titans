package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.takem.titans.item.BladeItem;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class OffhandBladeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final ItemInHandRenderer itemInHandRenderer;

    public OffhandBladeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ItemInHandRenderer itemInHandRenderer) {
        super(parent);
        this.itemInHandRenderer = itemInHandRenderer;
    }

    public void render(
        PoseStack ps,
        MultiBufferSource buffers,
        int light,
        AbstractClientPlayer p,
        float limbSwing,
        float limbSwingAmount,
        float pt,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        ItemStack main = p.getMainHandItem();
        if (main.getItem() instanceof BladeItem && p.getOffhandItem().isEmpty()) {
            HumanoidArm arm = p.getMainArm().getOpposite();
            boolean left = arm == HumanoidArm.LEFT;
            ps.pushPose();
            this.getParentModel().translateToHand(arm, ps);
            ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
            ps.mulPose(Axis.YP.rotationDegrees(180.0F));
            ps.translate((float)(left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
            this.itemInHandRenderer
                .renderItem(p, main, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, ps, buffers, light);
            ps.popPose();
        }
    }
}
