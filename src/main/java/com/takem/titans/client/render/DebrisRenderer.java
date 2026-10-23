package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.takem.titans.entity.DebrisEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class DebrisRenderer extends EntityRenderer<DebrisEntity> {
    public DebrisRenderer(Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    public void render(DebrisEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        BlockState state = e.getBlockState();
        if (state.getRenderShape() == RenderShape.MODEL) {
            pose.pushPose();
            pose.translate(0.0, 0.5, 0.0);
            float angle = Mth.lerp(partial, e.spinO, e.spin);
            if (angle != 0.0F && (e.axisX != 0.0F || e.axisY != 0.0F || e.axisZ != 0.0F)) {
                pose.mulPose(new Quaternionf().rotateAxis(angle * 0.017453292F, new Vector3f(e.axisX, e.axisY, e.axisZ)));
            }

            pose.translate(-0.5, -0.5, -0.5);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
            super.render(e, yaw, partial, pose, buffers, light);
        }
    }

    public ResourceLocation getTextureLocation(DebrisEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
