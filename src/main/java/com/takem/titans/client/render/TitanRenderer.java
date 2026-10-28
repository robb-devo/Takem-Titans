package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.takem.titans.TakemTitans;
import com.takem.titans.entity.titan.AbstractTitan;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.FastColor.ARGB32;

public class TitanRenderer extends MobRenderer<AbstractTitan, TitanModel> {
    private final ResourceLocation[] textures;
    private final String[] styles;
    private final int bellyMask;
    private final float widthScale;

    public TitanRenderer(Context context, String name, int variants, int bellyMask, float widthScale) {
        super(context, new TitanModel(context.bakeLayer(TitanModel.LAYER)), 1.0F);
        this.addLayer(new WoundLayer(this));
        this.addLayer(new TitanGlowLayer(this));
        this.bellyMask = bellyMask;
        this.widthScale = widthScale;
        this.textures = new ResourceLocation[variants];
        this.styles = new String[variants];

        for (int i = 0; i < variants; i++) {
            this.textures[i] = TakemTitans.id("textures/entity/titan/" + name + "_" + i + ".png");
            this.styles[i] = TitanModel.styleOf(name + "_" + i);
        }
    }

    public void render(AbstractTitan titan, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        this.model.tint = -1;
        this.model.style = this.styles[Math.floorMod(titan.getVariant(), this.textures.length)];
        this.model.showBelly = (this.bellyMask >> Math.floorMod(titan.getVariant(), this.textures.length) & 1) != 0;
        if (titan.dyingTicks > 40) {
            float e = Mth.clamp(((float)titan.dyingTicks + pt - 40.0F) / ((float)titan.evaporateTicks() - 40.0F), 0.0F, 1.0F);
            int shade = (int)(255.0F * (1.0F - 0.55F * e));
            int alpha = (int)(255.0F * (1.0F - Mth.clamp((e - 0.7F) / 0.3F, 0.0F, 1.0F)));
            this.model.tint = ARGB32.color(alpha, shade, (int)((float)shade * 0.92F), (int)((float)shade * 0.88F));
        }

        super.render(titan, yaw, pt, ps, buffers, light);
    }

    protected void scale(AbstractTitan titan, PoseStack ps, float pt) {
        float s = titan.getTitanScale();
        if (titan.dyingTicks > 40) {
            float e = Mth.clamp(((float)titan.dyingTicks + pt - 40.0F) / ((float)titan.evaporateTicks() - 40.0F), 0.0F, 1.0F);
            s *= 1.0F - 0.3F * e;
        }

        ps.scale(s * this.widthScale, s, s * this.widthScale);
    }

    protected float getShadowRadius(AbstractTitan titan) {
        return titan.getBbWidth() * 0.8F;
    }

    @Nullable
    protected RenderType getRenderType(AbstractTitan titan, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (titan.dyingTicks > 40 && bodyVisible) {
            return RenderType.entityTranslucent(this.getTextureLocation(titan));
        } else {
            return bodyVisible ? RenderType.entityCutoutNoCull(this.getTextureLocation(titan)) : super.getRenderType(titan, bodyVisible, translucent, glowing);
        }
    }

    protected float getFlipDegrees(AbstractTitan titan) {
        return 0.0F;
    }

    public ResourceLocation getTextureLocation(AbstractTitan titan) {
        return this.textures[Math.floorMod(titan.getVariant(), this.textures.length)];
    }

    protected boolean shouldShowName(AbstractTitan titan) {
        return titan.hasCustomName() && super.shouldShowName(titan);
    }
}
