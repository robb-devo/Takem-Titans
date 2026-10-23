package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.takem.titans.TakemTitans;
import com.takem.titans.entity.titan.AbstractTitan;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public class WoundLayer extends RenderLayer<AbstractTitan, TitanModel> {
    public static final ResourceLocation[] WOUNDS = new ResourceLocation[]{
        TakemTitans.id("textures/entity/titan/wounds_1.png"),
        TakemTitans.id("textures/entity/titan/wounds_2.png"),
        TakemTitans.id("textures/entity/titan/wounds_3.png")
    };

    public WoundLayer(RenderLayerParent<AbstractTitan, TitanModel> parent) {
        super(parent);
    }

    public void render(
        PoseStack ps, MultiBufferSource buffers, int light, AbstractTitan t, float limbSwing, float limbAmount, float pt, float age, float yaw, float pitch
    ) {
        int g = t.goreLevel();
        if (g > 0 && !t.isInvisible()) {
            this.getParentModel()
                .renderToBuffer(
                    ps, buffers.getBuffer(RenderType.entityCutoutNoCull(WOUNDS[Math.min(3, g) - 1])), light, LivingEntityRenderer.getOverlayCoords(t, 0.0F), -1
                );
        }
    }
}
