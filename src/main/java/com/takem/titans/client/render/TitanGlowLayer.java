package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.takem.titans.entity.titan.AbstractTitan;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.FastColor.ARGB32;

/**
 * Burning eyes and glowing cracks: a skin's {@code _glow} mask drawn full bright on top, the way spiders' eyes are.
 * Skins without one simply have no mask. The glow gutters out as a dying titan evaporates.
 */
public class TitanGlowLayer extends RenderLayer<AbstractTitan, TitanModel> {
    private static final Map<ResourceLocation, ResourceLocation> MASKS = new HashMap<>();

    public TitanGlowLayer(RenderLayerParent<AbstractTitan, TitanModel> parent) {
        super(parent);
    }

    public void render(
        PoseStack ps, MultiBufferSource buffers, int light, AbstractTitan t, float limbSwing, float limbAmount, float pt, float age, float yaw, float pitch
    ) {
        if (t.isInvisible() || t.dyingTicks > 60) {
            return;
        }

        ResourceLocation mask = MASKS.computeIfAbsent(this.getTextureLocation(t), skin -> {
            ResourceLocation m = ResourceLocation.fromNamespaceAndPath(skin.getNamespace(), skin.getPath().replace(".png", "_glow.png"));
            return Minecraft.getInstance().getResourceManager().getResource(m).isPresent() ? m : null;
        });
        if (mask != null) {
            float fade = t.dyingTicks > 0 ? 1.0F - Mth.clamp((float)t.dyingTicks / 60.0F, 0.0F, 1.0F) : 1.0F;
            // a slow flicker, like coals breathing
            float pulse = 0.85F + 0.15F * Mth.sin(((float)t.tickCount + pt) * 0.15F + (float)t.getId());
            // the eyes render type adds its colour onto what is behind, so fade by darkening, not by alpha
            int v = (int)(255.0F * fade * pulse);
            this.getParentModel().renderToBuffer(ps, buffers.getBuffer(RenderType.eyes(mask)), 15728880, OverlayTexture.NO_OVERLAY, ARGB32.color(255, v, v, v));
        }
    }
}
