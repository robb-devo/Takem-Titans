package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.takem.titans.TakemTitans;
import com.takem.titans.entity.CannonEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class CannonRenderer extends EntityRenderer<CannonEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(TakemTitans.id("cannon"), "main");
    private static final ResourceLocation[] TEX = new ResourceLocation[]{
        TakemTitans.id("textures/entity/cannon/cannon_1.png"),
        TakemTitans.id("textures/entity/cannon/cannon_2.png"),
        TakemTitans.id("textures/entity/cannon/cannon_3.png")
    };
    private final ModelPart root;
    private final ModelPart pivot;
    private final ModelPart wheelL;
    private final ModelPart wheelR;
    private final ModelPart[] barrels = new ModelPart[3];

    public CannonRenderer(Context ctx) {
        super(ctx);
        this.root = ctx.bakeLayer(LAYER);
        this.pivot = this.root.getChild("pivot");
        this.wheelL = this.root.getChild("wheel_l");
        this.wheelR = this.root.getChild("wheel_r");
        this.barrels[0] = this.pivot.getChild("barrel1");
        this.barrels[1] = this.pivot.getChild("barrel2");
        this.barrels[2] = this.pivot.getChild("battery");
        this.shadowRadius = 1.2F;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
            "carriage",
            CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -7.0F, -6.0F, 10.0F, 4.0F, 16.0F).texOffs(44, 20).addBox(-1.5F, -4.0F, 8.0F, 3.0F, 4.0F, 8.0F),
            PartPose.offset(0.0F, 24.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "cheek_l", CubeListBuilder.create().texOffs(22, 20).addBox(-1.0F, -6.0F, -4.0F, 2.0F, 6.0F, 9.0F), PartPose.offset(4.0F, 17.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "cheek_r", CubeListBuilder.create().texOffs(22, 20).addBox(-1.0F, -6.0F, -4.0F, 2.0F, 6.0F, 9.0F), PartPose.offset(-4.0F, 17.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "wheel_l", CubeListBuilder.create().texOffs(0, 20).addBox(-1.0F, -4.5F, -4.5F, 2.0F, 9.0F, 9.0F), PartPose.offset(6.5F, 19.5F, 0.0F)
        );
        root.addOrReplaceChild(
            "wheel_r", CubeListBuilder.create().texOffs(0, 20).addBox(-1.0F, -4.5F, -4.5F, 2.0F, 9.0F, 9.0F), PartPose.offset(-6.5F, 19.5F, 0.0F)
        );
        PartDefinition pivot = root.addOrReplaceChild("pivot", CubeListBuilder.create(), PartPose.offset(0.0F, 13.0F, 0.0F));
        pivot.addOrReplaceChild(
            "barrel1",
            CubeListBuilder.create()
                .texOffs(0, 40)
                .addBox(-2.5F, -2.5F, -16.0F, 5.0F, 5.0F, 22.0F)
                .texOffs(0, 80)
                .addBox(-3.0F, -3.0F, -17.0F, 6.0F, 6.0F, 3.0F),
            PartPose.ZERO
        );
        pivot.addOrReplaceChild(
            "barrel2",
            CubeListBuilder.create()
                .texOffs(0, 40)
                .addBox(-3.5F, -3.5F, -22.0F, 7.0F, 7.0F, 28.0F)
                .texOffs(0, 80)
                .addBox(-4.0F, -4.0F, -23.0F, 8.0F, 8.0F, 3.0F)
                .texOffs(0, 80)
                .addBox(-4.0F, -4.0F, -6.0F, 8.0F, 8.0F, 3.0F),
            PartPose.ZERO
        );
        pivot.addOrReplaceChild(
            "battery",
            CubeListBuilder.create()
                .texOffs(0, 40)
                .addBox(-7.0F, -2.0F, -20.0F, 4.0F, 4.0F, 24.0F)
                .texOffs(0, 40)
                .addBox(-2.0F, -2.5F, -21.0F, 4.0F, 5.0F, 25.0F)
                .texOffs(0, 40)
                .addBox(3.0F, -2.0F, -20.0F, 4.0F, 4.0F, 24.0F)
                .texOffs(0, 80)
                .addBox(-8.0F, -3.5F, -6.0F, 16.0F, 7.0F, 6.0F),
            PartPose.ZERO
        );
        return LayerDefinition.create(mesh, 128, 128);
    }

    public void render(CannonEntity c, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        int tier = Math.max(1, Math.min(3, c.tier()));

        for (int i = 0; i < 3; i++) {
            this.barrels[i].visible = i == tier - 1;
        }

        float recoil = c.recoil(pt);
        this.pivot.xRot = Mth.lerp(pt, c.xRotO, c.getXRot()) * 0.017453292F - recoil * 0.12F;
        this.pivot.z = recoil * 2.5F;
        this.wheelL.xRot = -recoil * 0.6F;
        this.wheelR.xRot = -recoil * 0.6F;
        ps.pushPose();
        float scale = tier == 1 ? 1.5F : (tier == 2 ? 1.75F : 1.85F);
        ps.scale(scale, scale, scale);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(pt, c.yRotO, c.getYRot())));
        ps.scale(-1.0F, -1.0F, 1.0F);
        ps.translate(0.0F, -1.501F, 0.0F);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX[tier - 1]));
        this.root.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(c, yaw, pt, ps, buffers, light);
    }

    public ResourceLocation getTextureLocation(CannonEntity c) {
        return TEX[Math.max(1, Math.min(3, c.tier())) - 1];
    }
}
