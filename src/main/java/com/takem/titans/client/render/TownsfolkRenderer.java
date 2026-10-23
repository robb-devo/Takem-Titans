package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.takem.titans.TakemTitans;
import com.takem.titans.entity.npc.TownsfolkEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;

public class TownsfolkRenderer extends HumanoidMobRenderer<TownsfolkEntity, PlayerModel<TownsfolkEntity>> {
    private static final Map<Integer, ResourceLocation> CACHE = new HashMap<>();

    public TownsfolkRenderer(Context ctx) {
        super(ctx, new TownsfolkModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    public void render(TownsfolkEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        super.render(e, yaw, pt, ps, buffers, light);
        Hologram.render(e, this.entityRenderDispatcher, this.getFont(), ps, buffers, pt);
    }

    public ResourceLocation getTextureLocation(TownsfolkEntity e) {
        int role = e.getRole();
        int variant = e.getVariant();
        return CACHE.computeIfAbsent(role * 100 + variant, k -> {
            String name = TownsfolkEntity.ROLE_NAMES[Math.floorMod(role, TownsfolkEntity.ROLE_NAMES.length)];
            if (role == 0) {
                name = name + "_" + Math.floorMod(variant, 6);
            } else if (role == 1 || role == 6) {
                name = name + "_" + Math.floorMod(variant, 3);
            }

            return TakemTitans.id("textures/entity/npc/" + name + ".png");
        });
    }
}
