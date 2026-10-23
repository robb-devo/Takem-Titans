package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.takem.titans.TakemTitans;
import com.takem.titans.entity.TitanGibEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class GibRenderer extends EntityRenderer<TitanGibEntity> {
    private final Map<Integer, ModelPart> parts = new HashMap<>();
    private final Map<Integer, Float> centres = new HashMap<>();
    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    public GibRenderer(Context ctx) {
        super(ctx);
        this.shadowRadius = 0.8F;
        this.add(ctx, 2, -13.0F, "hips", "torso");
        this.add(ctx, 3, -9.0F, "hips", "torso", "head");
        this.add(ctx, 5, 20.0F, "hips", "torso", "right_arm");
        this.add(ctx, 6, 12.0F, "hips", "torso", "right_arm", "right_forearm");
        this.add(ctx, 8, 20.0F, "hips", "torso", "left_arm");
        this.add(ctx, 9, 12.0F, "hips", "torso", "left_arm", "left_forearm");
        this.add(ctx, 11, 12.0F, "hips", "right_leg");
        this.add(ctx, 12, 7.0F, "hips", "right_leg", "right_shin");
        this.add(ctx, 13, 12.0F, "hips", "left_leg");
        this.add(ctx, 14, 7.0F, "hips", "left_leg", "left_shin");
    }

    private void add(Context ctx, int joint, float centre, String... path) {
        ModelPart p = ctx.bakeLayer(TitanModel.LAYER).getChild("root");

        for (String s : path) {
            p = p.getChild(s);
        }

        p.x = p.y = p.z = 0.0F;
        p.xRot = p.yRot = p.zRot = 0.0F;
        if (joint == 2) {
            for (String child : new String[]{"head", "right_arm", "left_arm"}) {
                p.getChild(child).visible = false;
            }
        }

        for (String stump : new String[]{"right_stump", "left_stump"}) {
            if (p.hasChild(stump)) {
                p.getChild(stump).visible = false;
            }
        }

        this.parts.put(joint, p);
        this.centres.put(joint, centre);
    }

    public void render(TitanGibEntity g, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        ModelPart part = this.parts.get(g.joint());
        if (part != null) {
            float s = g.scale() * g.fade(pt);
            if (s > 0.01F) {
                ps.pushPose();
                ps.translate(0.0F, g.scale() * 0.25F, 0.0F);
                ps.mulPose(Axis.YP.rotationDegrees(-g.getYRot()));
                ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, g.pitchO, g.pitch)));
                ps.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pt, g.rollO, g.roll)));
                ps.scale(s, s, s);
                ps.scale(-1.0F, -1.0F, 1.0F);
                ps.translate(0.0F, -this.centres.get(g.joint()) / 16.0F, 0.0F);
                part.render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(this.getTextureLocation(g))), light, OverlayTexture.NO_OVERLAY);
                part.render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(WoundLayer.WOUNDS[g.joint() == 2 ? 2 : 1])), light, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
        }

        super.render(g, yaw, pt, ps, buffers, light);
    }

    public ResourceLocation getTextureLocation(TitanGibEntity g) {
        return SKINS.computeIfAbsent(g.skin(), k -> TakemTitans.id("textures/entity/titan/" + k + ".png"));
    }
}
