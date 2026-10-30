package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.takem.titans.TakemTitans;
import com.takem.titans.item.ScoutCloakItem;
import com.takem.titans.item.VanguardArmorItem;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem.Type;

public class CloakLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEXTURE = TakemTitans.id("textures/entity/scout_cloak.png");
    private static ModelPart cloak;

    public CloakLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // wide enough to fall over the shoulders and arms, down to the knees; the hood lies bunched behind the neck
        // (texture painted to these boxes by tools/gear_art.py)
        root.addOrReplaceChild("cloak", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, 0.0F, -1.0F, 16.0F, 20.0F, 1.0F), PartPose.ZERO);
        root.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(36, 0).addBox(-5.0F, -1.8F, -2.5F, 10.0F, 3.5F, 2.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static ModelPart cloak() {
        if (cloak == null) {
            cloak = createLayer().bakeRoot();
        }

        return cloak;
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
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        if (!p.isInvisible() && (chest.getItem() instanceof ScoutCloakItem || chest.getItem() instanceof VanguardArmorItem v && v.getType() == Type.CHESTPLATE)
            )
         {
            ps.pushPose();
            this.getParentModel().body.translateAndRotate(ps);
            ps.translate(0.0F, 0.0F, 0.135F);
            double dx = Mth.lerp((double)pt, p.xCloakO, p.xCloak) - Mth.lerp((double)pt, p.xo, p.getX());
            double dy = Mth.lerp((double)pt, p.yCloakO, p.yCloak) - Mth.lerp((double)pt, p.yo, p.getY());
            double dz = Mth.lerp((double)pt, p.zCloakO, p.zCloak) - Mth.lerp((double)pt, p.zo, p.getZ());
            float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
            double sin = (double)Mth.sin(bodyYaw * 0.017453292F);
            double cos = (double)(-Mth.cos(bodyYaw * 0.017453292F));
            float lift = Mth.clamp((float)dy * 10.0F, -6.0F, 32.0F);
            float back = Mth.clamp((float)(dx * sin + dz * cos) * 100.0F, 0.0F, 165.0F);
            float sway = Mth.clamp((float)(dx * cos - dz * sin) * 100.0F, -25.0F, 25.0F);
            float bob = Mth.lerp(pt, p.oBob, p.bob);
            lift += Mth.sin(Mth.lerp(pt, p.walkDistO, p.walkDist) * 6.0F) * 32.0F * bob;
            if (p.isCrouching()) {
                lift += 25.0F;
            }

            float speed = (float)Math.min(1.0, Math.sqrt(dx * dx + dz * dz) * 0.6);
            float flutter = Mth.sin(ageInTicks * 1.3F) * 6.0F * speed;
            ps.mulPose(Axis.XP.rotationDegrees(6.0F + back / 2.0F + lift + flutter));
            ps.mulPose(Axis.ZP.rotationDegrees(sway / 2.0F));
            ps.mulPose(Axis.YP.rotationDegrees(180.0F - sway / 2.0F));
            cloak().render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }
}
