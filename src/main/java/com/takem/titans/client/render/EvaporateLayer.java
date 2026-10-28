package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.takem.titans.TakemTitans;
import com.takem.titans.entity.titan.AbstractTitan;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.FastColor.ARGB32;

/**
 * A dead titan's skin boils away first: the raw muscle and bone underneath (one texture for every titan, they share
 * their UVs) shows through more and more while the steam pours off, before the whole body fades.
 */
public class EvaporateLayer extends RenderLayer<AbstractTitan, TitanModel> {
    private static final ResourceLocation FLESH = TakemTitans.id("textures/entity/titan/evaporate.png");

    public EvaporateLayer(RenderLayerParent<AbstractTitan, TitanModel> parent) {
        super(parent);
    }

    public void render(
        PoseStack ps, MultiBufferSource buffers, int light, AbstractTitan t, float limbSwing, float limbAmount, float pt, float age, float yaw, float pitch
    ) {
        if (t.dyingTicks > 24 && !t.isInvisible()) {
            float k = Mth.clamp(((float)t.dyingTicks + pt - 24.0F) / 90.0F, 0.0F, 1.0F);
            int a = (int)(255.0F * k * k * (3.0F - 2.0F * k));
            this.getParentModel().renderToBuffer(ps, buffers.getBuffer(RenderType.entityTranslucent(FLESH)), light, OverlayTexture.NO_OVERLAY, ARGB32.color(a, 255, 255, 255));
        }
    }
}
