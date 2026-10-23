package com.takem.titans.client.render;

import com.takem.titans.entity.npc.TownsfolkEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public class TownsfolkModel extends PlayerModel<TownsfolkEntity> {
    public TownsfolkModel(ModelPart root) {
        super(root, false);
    }

    public void setupAnim(TownsfolkEntity e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
        float t = age + (float)e.getId() * 7.0F;
        switch (e.activity()) {
            case 1:
            case 8: {
                float k = Mth.sin(t * 0.45F);
                this.rightArm.xRot = -1.9F + k * 0.95F;
                this.rightArm.zRot = -0.1F;
                this.leftArm.xRot = -0.6F;
                this.leftArm.zRot = 0.2F;
                this.head.xRot = 0.35F;
                this.body.xRot = 0.12F + k * 0.04F;
                break;
            }
            case 2:
                this.rightArm.xRot = -0.55F + Mth.sin(t * 0.22F) * 0.45F;
                this.rightArm.zRot = -0.25F + Mth.cos(t * 0.17F) * 0.15F;
                this.leftArm.xRot = -0.25F + Mth.cos(t * 0.19F) * 0.3F;
                this.leftArm.zRot = 0.15F;
                this.head.xRot = this.head.xRot + Mth.sin(t * 0.33F) * 0.12F;
                this.head.yRot = this.head.yRot + Mth.sin(t * 0.09F) * 0.25F;
                break;
            case 3:
                this.rightArm.xRot = -1.15F + Mth.cos(limbSwing * 0.66F) * 0.08F * limbAmount;
                this.leftArm.xRot = -1.15F - Mth.cos(limbSwing * 0.66F) * 0.08F * limbAmount;
                this.rightArm.zRot = 0.18F;
                this.leftArm.zRot = -0.18F;
                this.body.xRot = -0.08F;
                break;
            case 4: {
                float k = Mth.sin(t * 0.3F);
                this.rightArm.xRot = -0.75F;
                this.leftArm.xRot = -0.65F;
                this.rightArm.yRot = k * 0.55F;
                this.leftArm.yRot = k * 0.55F;
                this.body.yRot = k * 0.22F;
                this.head.xRot = 0.4F;
                break;
            }
            case 5:
                this.rightArm.xRot = -2.9F + Mth.sin(t * 0.9F) * 0.25F;
                this.leftArm.xRot = -2.9F + Mth.cos(t * 0.9F) * 0.25F;
                this.rightArm.zRot = 0.35F + Mth.sin(t * 0.9F) * 0.2F;
                this.leftArm.zRot = -0.35F - Mth.sin(t * 0.9F) * 0.2F;
                this.head.xRot = -0.35F;
                break;
            case 6:
                this.rightArm.xRot = -2.5F;
                this.leftArm.xRot = -2.5F;
                this.rightArm.zRot = -0.55F + Mth.sin(t * 2.5F) * 0.05F;
                this.leftArm.zRot = 0.55F - Mth.sin(t * 2.5F) * 0.05F;
                this.head.xRot = 0.7F;
                this.body.xRot = 0.45F;
                break;
            case 7:
                this.rightArm.xRot = -2.2F;
                this.rightArm.yRot = -0.55F;
                this.rightArm.zRot = 0.45F;
        }

        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
    }
}
