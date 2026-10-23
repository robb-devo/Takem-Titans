package com.takem.titans.client.render;

import com.takem.titans.TakemTitans;
import com.takem.titans.entity.npc.JaegerEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;

public class JaegerRenderer extends HumanoidMobRenderer<JaegerEntity, PlayerModel<JaegerEntity>> {
    private static final Map<Integer, ResourceLocation> CACHE = new HashMap<>();

    public JaegerRenderer(Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.addLayer(new JaegerGearLayer(this));
    }

    public ResourceLocation getTextureLocation(JaegerEntity e) {
        int v = Math.floorMod(e.getVariant(), 4);
        return CACHE.computeIfAbsent(v, k -> TakemTitans.id("textures/entity/npc/jaeger_" + k + ".png"));
    }
}
