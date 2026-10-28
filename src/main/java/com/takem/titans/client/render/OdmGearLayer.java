package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.takem.titans.TakemTitans;
import com.takem.titans.client.ClientOdm;
import com.takem.titans.client.RemoteHooks;
import com.takem.titans.item.GearTier;
import com.takem.titans.odm.OdmGear;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.item.ItemStack;

public class OdmGearLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** An opaque grey of brightness {@code f}: the glow layers are additive, so this scales how hard they shine. */
    private static int grey(float f) {
        int c = (int)(Mth.clamp(f, 0.0F, 1.0F) * 255.0F);
        return -16777216 | c << 16 | c << 8 | c;
    }

    public static final ResourceLocation TEXTURE = TakemTitans.id("textures/entity/odm_gear.png");
    public static final ResourceLocation TEXTURE_ADVANCED = TakemTitans.id("textures/entity/odm_gear_advanced.png");
    public static final ResourceLocation TEXTURE_GODSPEED = TakemTitans.id("textures/entity/odm_gear_godspeed.png");
    public static final ResourceLocation TEXTURE_PHANTOM = TakemTitans.id("textures/entity/odm_gear_phantom.png");
    public static final ResourceLocation TEXTURE_EVENT_HORIZON = TakemTitans.id("textures/entity/odm_gear_event_horizon.png");
    private static final ResourceLocation GLOW_PHANTOM = TakemTitans.id("textures/entity/odm_gear_phantom_glow.png");
    private static final ResourceLocation GLOW_EVENT_HORIZON = TakemTitans.id("textures/entity/odm_gear_event_horizon_glow.png");
    private static ModelPart gear;
    private static ModelPart ring;

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

    /**
     * The Event Horizon rig's halo: a ring of glowing segments standing behind the motor, sampled from a corner of the
     * glow texture no other part uses.
     */
    public static LayerDefinition createRingLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        int n = 14;
        float r = 3.4F;

        for (int i = 0; i < n; i++) {
            float a = (float)i / (float)n * 6.2831855F;
            root.addOrReplaceChild(
                "seg" + i,
                CubeListBuilder.create().texOffs(56, 24).addBox(-0.75F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F),
                PartPose.offsetAndRotation(Mth.cos(a) * r, Mth.sin(a) * r, 0.0F, 0.0F, 0.0F, a + 1.5707964F)
            );
        }

        return LayerDefinition.create(mesh, 64, 32);
    }

    static ModelPart ring() {
        if (ring == null) {
            ring = createRingLayer().bakeRoot();
        }

        return ring;
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
            ResourceLocation tex = switch (tier) {
                case ADVANCED -> TEXTURE_ADVANCED;
                case PHANTOM -> TEXTURE_PHANTOM;
                case GODSPEED -> TEXTURE_GODSPEED;
                case EVENT_HORIZON -> TEXTURE_EVENT_HORIZON;
                default -> TEXTURE;
            };
            ps.pushPose();
            this.getParentModel().body.translateAndRotate(ps);
            gear().render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY);
            if (tier == GearTier.PHANTOM) {
                // the accents breathe, slow and cold
                float pulse = 0.55F + 0.45F * Mth.sin(ageInTicks * 0.12F);
                gear().render(ps, buffers.getBuffer(RenderType.eyes(GLOW_PHANTOM)), light, OverlayTexture.NO_OVERLAY, grey(pulse));
            } else if (tier == GearTier.EVENT_HORIZON) {
                // the accents and the stars in the casing flicker like something far away, and the halo turns
                float pulse = 0.65F + 0.35F * Mth.sin(ageInTicks * 0.31F) * Mth.sin(ageInTicks * 0.07F + 1.3F);
                VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW_EVENT_HORIZON));
                gear().render(ps, glow, light, OverlayTexture.NO_OVERLAY, grey(pulse));
                boolean boosting = player == Minecraft.getInstance().player ? ClientOdm.INSTANCE.boosting : RemoteHooks.isBoosting(player.getId());
                float spin = ageInTicks * (boosting ? 0.45F : 0.12F);
                ps.pushPose();
                ps.translate(0.0F, 10.1F / 16.0F, 6.9F / 16.0F);
                ps.mulPose(Axis.ZP.rotation(spin));
                ring().render(ps, glow, light, OverlayTexture.NO_OVERLAY, grey(boosting ? 1.0F : 0.6F));
                ps.mulPose(Axis.ZP.rotation(-spin * 2.3F));
                ps.scale(0.62F, 0.62F, 0.62F);
                ring().render(ps, glow, light, OverlayTexture.NO_OVERLAY, grey(boosting ? 0.9F : 0.45F));
                ps.popPose();
            }

            ps.popPose();
        }
    }
}
