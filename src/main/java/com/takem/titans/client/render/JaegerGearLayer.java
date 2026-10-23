package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.takem.titans.entity.npc.JaegerEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class JaegerGearLayer extends RenderLayer<JaegerEntity, PlayerModel<JaegerEntity>> {
    public JaegerGearLayer(RenderLayerParent<JaegerEntity, PlayerModel<JaegerEntity>> parent) {
        super(parent);
    }

    public void render(
        PoseStack ps,
        MultiBufferSource buffers,
        int light,
        JaegerEntity entity,
        float limbSwing,
        float limbSwingAmount,
        float partialTick,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        if (!entity.isInvisible()) {
            ps.pushPose();
            this.getParentModel().body.translateAndRotate(ps);
            OdmGearLayer.gear().render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(OdmGearLayer.TEXTURE)), light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }
}
