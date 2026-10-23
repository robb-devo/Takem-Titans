package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.takem.titans.TakemTitans;
import com.takem.titans.item.GearTier;
import com.takem.titans.odm.OdmGear;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class OdmGearLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEXTURE = TakemTitans.id("textures/entity/odm_gear.png");
    public static final ResourceLocation TEXTURE_ADVANCED = TakemTitans.id("textures/entity/odm_gear_advanced.png");
    public static final ResourceLocation TEXTURE_GODSPEED = TakemTitans.id("textures/entity/odm_gear_godspeed.png");
    private static ModelPart gear;

    public OdmGearLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("belt", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, 10.4F, -2.5F, 9.0F, 1.6F, 5.0F), PartPose.ZERO);
        root.addOrReplaceChild("chest_strap", CubeListBuilder.create().texOffs(0, 7).addBox(-4.3F, 4.0F, -2.3F, 8.6F, 0.9F, 4.6F), PartPose.ZERO);
        root.addOrReplaceChild("strap_fl", CubeListBuilder.create().texOffs(28, 0).addBox(1.2F, -0.1F, -2.35F, 1.1F, 10.6F, 0.5F), PartPose.ZERO);
        root.addOrReplaceChild("strap_fr", CubeListBuilder.create().texOffs(28, 0).addBox(-2.3F, -0.1F, -2.35F, 1.1F, 10.6F, 0.5F), PartPose.ZERO);
        root.addOrReplaceChild("strap_bl", CubeListBuilder.create().texOffs(28, 0).addBox(1.2F, -0.1F, 1.85F, 1.1F, 10.6F, 0.5F), PartPose.ZERO);
        root.addOrReplaceChild("strap_br", CubeListBuilder.create().texOffs(28, 0).addBox(-2.3F, -0.1F, 1.85F, 1.1F, 10.6F, 0.5F), PartPose.ZERO);
        root.addOrReplaceChild("motor", CubeListBuilder.create().texOffs(0, 13).addBox(-2.5F, 8.2F, 2.2F, 5.0F, 3.8F, 2.4F), PartPose.ZERO);
        root.addOrReplaceChild("nozzle", CubeListBuilder.create().texOffs(15, 13).addBox(-1.0F, 9.3F, 4.5F, 2.0F, 1.6F, 1.4F), PartPose.ZERO);
        root.addOrReplaceChild("reel_l", CubeListBuilder.create().texOffs(22, 13).addBox(2.4F, 8.9F, 2.6F, 1.2F, 2.4F, 2.4F), PartPose.ZERO);
        root.addOrReplaceChild("reel_r", CubeListBuilder.create().texOffs(22, 13).addBox(-3.6F, 8.9F, 2.6F, 1.2F, 2.4F, 2.4F), PartPose.ZERO);

        for (int s = -1; s <= 1; s += 2) {
            String n = s < 0 ? "_r" : "_l";
            float x = s < 0 ? -6.6F : 4.2F;
            root.addOrReplaceChild("tank" + n, CubeListBuilder.create().texOffs(0, 20).addBox(x + 0.1F, 9.0F, -1.8F, 2.3F, 2.3F, 7.0F), PartPose.ZERO);
            root.addOrReplaceChild("tank_cap" + n, CubeListBuilder.create().texOffs(18, 20).addBox(x + 0.45F, 9.35F, 5.2F, 1.6F, 1.6F, 0.8F), PartPose.ZERO);
            root.addOrReplaceChild("box" + n, CubeListBuilder.create().texOffs(30, 12).addBox(x, 11.4F, -3.2F, 2.4F, 3.6F, 8.4F), PartPose.ZERO);
            root.addOrReplaceChild("box_lid" + n, CubeListBuilder.create().texOffs(30, 24).addBox(x - 0.2F, 11.0F, -3.4F, 2.8F, 0.6F, 2.0F), PartPose.ZERO);
            root.addOrReplaceChild(
                "launcher" + n, CubeListBuilder.create().texOffs(48, 0).addBox(s < 0 ? -5.4F : 3.6F, 9.6F, -4.2F, 1.8F, 1.8F, 3.0F), PartPose.ZERO
            );
            root.addOrReplaceChild(
                "launcher_tip" + n, CubeListBuilder.create().texOffs(48, 5).addBox(s < 0 ? -5.1F : 3.9F, 9.9F, -5.0F, 1.2F, 1.2F, 0.8F), PartPose.ZERO
            );
        }

        return LayerDefinition.create(mesh, 64, 32);
    }

    static ModelPart gear() {
        if (gear == null) {
            gear = createLayer().bakeRoot();
        }

        return gear;
    }

    public void render(
        PoseStack ps,
        MultiBufferSource buffers,
        int light,
        AbstractClientPlayer player,
        float limbSwing,
        float limbSwingAmount,
        float partialTick,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        ItemStack worn = OdmGear.getGear(player);
        if (!player.isInvisible() && !worn.isEmpty()) {
            GearTier tier = OdmGear.tier(worn);
            ResourceLocation tex = tier == GearTier.ADVANCED ? TEXTURE_ADVANCED : (tier == GearTier.GODSPEED ? TEXTURE_GODSPEED : TEXTURE);
            ps.pushPose();
            this.getParentModel().body.translateAndRotate(ps);
            gear().render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }
}
