package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.takem.titans.TakemTitans;
import com.takem.titans.entity.FlareEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class FlareRenderer extends EntityRenderer<FlareEntity> {
    private static final ResourceLocation TEXTURE = TakemTitans.id("textures/entity/flare.png");

    public FlareRenderer(Context context) {
        super(context);
    }

    public void render(FlareEntity flare, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float alpha = 1.0F;
        float size;
        if (flare.hasBurst()) {
            float t = ((float)(flare.age() - 46) + pt) / 40.0F;
            size = 1.2F + t * 3.0F;
            alpha = Mth.clamp(1.0F - t, 0.0F, 1.0F);
        } else {
            size = 0.55F + Mth.sin(((float)flare.tickCount + pt) * 1.7F) * 0.08F;
        }

        if (!(alpha <= 0.01F)) {
            int rgb = flare.color().rgb;
            int r = rgb >> 16 & 0xFF;
            int g = rgb >> 8 & 0xFF;
            int b = rgb & 0xFF;
            ps.pushPose();
            ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
            ps.scale(size, size, size);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
            Pose pose = ps.last();
            int a = (int)(alpha * 255.0F);
            quad(vc, pose, r, g, b, a);
            ps.scale(0.45F, 0.45F, 0.45F);
            quad(vc, ps.last(), 255, 255, 255, a);
            ps.popPose();
            super.render(flare, yaw, pt, ps, buffers, light);
        }
    }

    private static void quad(VertexConsumer vc, Pose pose, int r, int g, int b, int a) {
        v(vc, pose, -0.5F, -0.5F, 0.0F, 1.0F, r, g, b, a);
        v(vc, pose, 0.5F, -0.5F, 1.0F, 1.0F, r, g, b, a);
        v(vc, pose, 0.5F, 0.5F, 1.0F, 0.0F, r, g, b, a);
        v(vc, pose, -0.5F, 0.5F, 0.0F, 0.0F, r, g, b, a);
    }

    private static void v(VertexConsumer vc, Pose pose, float x, float y, float u, float v, int r, int g, int b, int a) {
        vc.addVertex(pose, x, y, 0.0F)
            .setColor(r, g, b, a)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(15728880)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    public ResourceLocation getTextureLocation(FlareEntity entity) {
        return TEXTURE;
    }
}
