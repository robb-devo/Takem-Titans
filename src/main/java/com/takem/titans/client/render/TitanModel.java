package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.takem.titans.TakemTitans;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.TitanAnimator;
import com.takem.titans.entity.titan.TitanSkeleton;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.FastColor.ARGB32;

public class TitanModel extends HierarchicalModel<AbstractTitan> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(TakemTitans.id("titan"), "main");
    private final ModelPart root;
    private final ModelPart[] joints = new ModelPart[15];
    private final ModelPart hair;
    private final ModelPart belly;
    private final ModelPart rightStump;
    private final ModelPart leftStump;
    private final TitanSkeleton.Pose pose = new TitanSkeleton.Pose();
    private final TitanSkeleton.Frames frames = new TitanSkeleton.Frames();
    public int tint = -1;
    public boolean showBelly = true;

    public TitanModel(ModelPart root) {
        this.root = root;
        ModelPart base = root.getChild("root");
        this.joints[0] = base;
        this.joints[1] = base.getChild("hips");
        this.joints[2] = this.joints[1].getChild("torso");
        this.joints[3] = this.joints[2].getChild("head");
        this.joints[4] = this.joints[3].getChild("jaw");
        this.hair = this.joints[3].getChild("hair");
        this.belly = this.joints[2].getChild("belly");
        this.joints[5] = this.joints[2].getChild("right_arm");
        this.joints[6] = this.joints[5].getChild("right_forearm");
        this.joints[7] = this.joints[6].getChild("right_hand");
        this.joints[8] = this.joints[2].getChild("left_arm");
        this.joints[9] = this.joints[8].getChild("left_forearm");
        this.joints[10] = this.joints[9].getChild("left_hand");
        this.joints[11] = this.joints[1].getChild("right_leg");
        this.joints[12] = this.joints[11].getChild("right_shin");
        this.joints[13] = this.joints[1].getChild("left_leg");
        this.joints[14] = this.joints[13].getChild("left_shin");
        this.rightStump = this.joints[5].getChild("right_stump");
        this.leftStump = this.joints[8].getChild("left_stump");
    }

    private static PartPose at(int joint) {
        return PartPose.offset(TitanSkeleton.PIVOT[joint][0], TitanSkeleton.PIVOT[joint][1], TitanSkeleton.PIVOT[joint][2]);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition top = mesh.getRoot();
        PartDefinition root = top.addOrReplaceChild("root", CubeListBuilder.create(), at(0));
        PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create(), at(1));
        PartDefinition torso = hips.addOrReplaceChild(
            "torso",
            CubeListBuilder.create()
                .texOffs(0, 32)
                .addBox(-10.0F, -26.0F, -6.0F, 20.0F, 26.0F, 12.0F)
                .texOffs(100, 32)
                .addBox(-4.0F, -32.0F, -1.0F, 8.0F, 6.0F, 8.0F),
            at(2)
        );
        torso.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(64, 32).addBox(-8.0F, -13.0F, -8.0F, 16.0F, 11.0F, 2.0F), PartPose.ZERO);
        PartDefinition head = torso.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-9.0F, -18.0F, -9.0F, 18.0F, 12.0F, 18.0F)
                .texOffs(208, 0)
                .addBox(-10.5F, -13.0F, -1.0F, 2.0F, 5.0F, 3.0F)
                .texOffs(208, 0)
                .mirror()
                .addBox(8.5F, -13.0F, -1.0F, 2.0F, 5.0F, 3.0F)
                .mirror(false)
                .texOffs(208, 10)
                .addBox(-1.5F, -11.0F, -11.0F, 3.0F, 4.0F, 2.0F),
            at(3)
        );
        head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(72, 0).addBox(-8.0F, 0.0F, -12.0F, 16.0F, 6.0F, 15.0F), at(4));
        head.addOrReplaceChild(
            "hair", CubeListBuilder.create().texOffs(136, 0).addBox(-9.0F, -18.0F, -9.0F, 18.0F, 12.0F, 18.0F, new CubeDeformation(0.6F)), PartPose.ZERO
        );
        PartDefinition rArm = torso.addOrReplaceChild(
            "right_arm", CubeListBuilder.create().texOffs(0, 72).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 16.0F, 6.0F), at(5)
        );
        rArm.addOrReplaceChild(
            "right_stump", CubeListBuilder.create().texOffs(72, 72).addBox(-3.0F, 13.0F, -3.0F, 6.0F, 2.0F, 6.0F, new CubeDeformation(0.15F)), PartPose.ZERO
        );
        PartDefinition rFore = rArm.addOrReplaceChild(
            "right_forearm", CubeListBuilder.create().texOffs(24, 72).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 15.0F, 6.0F), at(6)
        );
        rFore.addOrReplaceChild("right_hand", CubeListBuilder.create().texOffs(48, 72).addBox(-3.5F, 0.0F, -2.5F, 7.0F, 9.0F, 5.0F), at(7));
        PartDefinition lArm = torso.addOrReplaceChild(
            "left_arm", CubeListBuilder.create().mirror().texOffs(0, 72).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 16.0F, 6.0F), at(8)
        );
        lArm.addOrReplaceChild(
            "left_stump",
            CubeListBuilder.create().mirror().texOffs(72, 72).addBox(-3.0F, 13.0F, -3.0F, 6.0F, 2.0F, 6.0F, new CubeDeformation(0.15F)),
            PartPose.ZERO
        );
        PartDefinition lFore = lArm.addOrReplaceChild(
            "left_forearm", CubeListBuilder.create().mirror().texOffs(24, 72).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 15.0F, 6.0F), at(9)
        );
        lFore.addOrReplaceChild("left_hand", CubeListBuilder.create().mirror().texOffs(48, 72).addBox(-3.5F, 0.0F, -2.5F, 7.0F, 9.0F, 5.0F), at(10));
        PartDefinition rLeg = hips.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 96).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 10.0F, 8.0F), at(11));
        rLeg.addOrReplaceChild(
            "right_shin",
            CubeListBuilder.create().texOffs(32, 96).addBox(-3.5F, 0.0F, -3.5F, 7.0F, 10.0F, 7.0F).texOffs(60, 96).addBox(-3.5F, 7.0F, -6.5F, 7.0F, 3.0F, 3.0F),
            at(12)
        );
        PartDefinition lLeg = hips.addOrReplaceChild(
            "left_leg", CubeListBuilder.create().mirror().texOffs(0, 96).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 10.0F, 8.0F), at(13)
        );
        lLeg.addOrReplaceChild(
            "left_shin",
            CubeListBuilder.create()
                .mirror()
                .texOffs(32, 96)
                .addBox(-3.5F, 0.0F, -3.5F, 7.0F, 10.0F, 7.0F)
                .texOffs(60, 96)
                .addBox(-3.5F, 7.0F, -6.5F, 7.0F, 3.0F, 3.0F),
            at(14)
        );
        return LayerDefinition.create(mesh, 256, 128);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    public void prepareMobModel(AbstractTitan titan, float limbSwing, float limbSwingAmount, float partialTick) {
        TitanAnimator.pose(titan, partialTick, this.pose, this.frames);

        for (int i = 0; i < 15; i++) {
            ModelPart part = this.joints[i];
            part.x = TitanSkeleton.PIVOT[i][0];
            part.y = TitanSkeleton.PIVOT[i][1];
            part.z = TitanSkeleton.PIVOT[i][2];
            part.xRot = this.pose.xRot[i];
            part.yRot = this.pose.yRot[i];
            part.zRot = this.pose.zRot[i];
        }

        this.joints[0].x = this.joints[0].x + this.pose.rootX;
        this.joints[0].y = this.joints[0].y + this.pose.rootY;
        this.joints[0].z = this.joints[0].z + this.pose.rootZ;
        ModelPart head = this.joints[3];
        head.xScale = head.yScale = head.zScale = this.pose.headScale;
        boolean rightCut = titan.isArmCut(-1);
        boolean leftCut = titan.isArmCut(1);
        this.joints[6].visible = !rightCut;
        this.joints[9].visible = !leftCut;
        this.rightStump.visible = rightCut;
        this.leftStump.visible = leftCut;
        this.belly.visible = this.showBelly;
        this.joints[3].visible = !titan.isHeadless();
        this.joints[12].visible = !titan.isLegCut(-1);
        this.joints[14].visible = !titan.isLegCut(1);
        this.hair.visible = true;
    }

    public void setupAnim(AbstractTitan titan, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        int c = color;
        if (this.tint != -1) {
            c = ARGB32.multiply(color, this.tint);
        }

        super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, c);
    }
}
