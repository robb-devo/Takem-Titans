package com.takem.titans.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.takem.titans.TakemTitans;
import com.takem.titans.client.fx.ClientFx;
import com.takem.titans.client.gui.AbilityHud;
import com.takem.titans.client.gui.ScreenFx;
import com.takem.titans.client.gui.TTGui;
import com.takem.titans.client.hud.OdmHud;
import com.takem.titans.client.particle.TakemParticle;
import com.takem.titans.client.render.CannonRenderer;
import com.takem.titans.client.render.CloakLayer;
import com.takem.titans.client.render.ClusterSpearRenderer;
import com.takem.titans.client.render.DebrisRenderer;
import com.takem.titans.client.render.FallingSectionRenderer;
import com.takem.titans.client.render.FlareRenderer;
import com.takem.titans.client.render.GibRenderer;
import com.takem.titans.client.render.HeartRenderer;
import com.takem.titans.client.render.JaegerRenderer;
import com.takem.titans.client.render.OdmGearLayer;
import com.takem.titans.client.render.OffhandBladeLayer;
import com.takem.titans.client.render.ThunderSpearRenderer;
import com.takem.titans.client.render.TitanModel;
import com.takem.titans.client.render.TitanRenderer;
import com.takem.titans.client.render.TownsfolkRenderer;
import com.takem.titans.client.story.Cutscene;
import com.takem.titans.client.story.StoryClient;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.item.BladeItem;
import com.takem.titans.item.FlareGunItem;
import com.takem.titans.network.TakemNetwork;
import com.takem.titans.odm.OdmMovementHooks;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModParticles;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.PlayerSkin.Model;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT},
    bus = Bus.MOD
)
public final class TakemClient {
    private TakemClient() {
    }

    @SubscribeEvent
    public static void onReloadListeners(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)rm -> com.takem.titans.client.fx.ColorGrade.reset());
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        OdmMovementHooks.setController(ClientOdm.INSTANCE);
        TakemNetwork.hookSyncHandler = RemoteHooks::handle;
        TakemNetwork.feedbackHandler = FeedbackHandler::handle;
        TakemNetwork.fxHandler = ClientFx::handle;
        TakemNetwork.detachHandler = ClientFx::handleDetach;
        AbstractTitan.clientFootstep = TitanGrabClient::footstep;
        GodGearClient.init();
        TakemNetwork.storyHandler = StoryClient::handle;
        event.enqueueWork(
            () -> {
                for (DeferredItem<BladeItem> blades : List.of(
                    ModItems.ODM_BLADES, ModItems.VETERAN_BLADES, ModItems.SERRATED_BLADES, ModItems.ACKERMAN_BLADES, ModItems.TITANBREAKER_BLADES, ModItems.FOUNDER_BLADES
                )) {
                    ItemProperties.register(blades.get(), TakemTitans.id("broken"), (stack, level, entity, seed) -> BladeItem.isBroken(stack) ? 1.0F : 0.0F);
                }

                ItemProperties.register(
                    ModItems.FLARE_GUN.get(), TakemTitans.id("color"), (stack, level, entity, seed) -> (float)FlareGunItem.selected(stack).ordinal() / 10.0F
                );
            }
        );
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : OdmKeys.ALL) {
            event.register(key);
        }
    }

    @SubscribeEvent
    public static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ScreenFx.LAYER, ScreenFx::render);
        event.registerAbove(ScreenFx.LAYER, OdmHud.SPEED_LINES, OdmHud::renderSpeedLines);
        event.registerAbove(OdmHud.SPEED_LINES, OdmHud.HIT_FX, OdmHud::renderHitFx);
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, OdmHud.SIGHTS, OdmHud::renderSights);
        event.registerAbove(VanillaGuiLayers.HOTBAR, OdmHud.PANEL, OdmHud::renderPanel);
        event.registerAbove(OdmHud.PANEL, StoryClient.HUD, StoryClient::renderHud);
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, CannonClient.HUD, CannonClient::render);
        event.registerAbove(VanillaGuiLayers.HOTBAR, AbilityHud.LAYER, AbilityHud::render);
        event.registerAboveAll(Cutscene.LAYER, Cutscene::render);
    }

    @SubscribeEvent
    public static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.GAS.get(), TakemParticle.factory(TakemParticle.Style.GAS));
        event.registerSpriteSet(ModParticles.STEAM.get(), TakemParticle.factory(TakemParticle.Style.STEAM));
        event.registerSpriteSet(ModParticles.BIG_STEAM.get(), TakemParticle.factory(TakemParticle.Style.BIG_STEAM));
        event.registerSpriteSet(ModParticles.BLOOD.get(), TakemParticle.factory(TakemParticle.Style.BLOOD));
        event.registerSpriteSet(ModParticles.SPARK.get(), TakemParticle.factory(TakemParticle.Style.SPARK));
        event.registerSpriteSet(ModParticles.FLARE_SMOKE.get(), TakemParticle.factory(TakemParticle.Style.FLARE_SMOKE));
        event.registerSpriteSet(ModParticles.WIND_STREAK.get(), TakemParticle.factory(TakemParticle.Style.WIND_STREAK));
        event.registerSpriteSet(ModParticles.DUST_CLOUD.get(), TakemParticle.factory(TakemParticle.Style.DUST_CLOUD));
        event.registerSpriteSet(ModParticles.FIREBALL.get(), TakemParticle.factory(TakemParticle.Style.FIREBALL));
        event.registerSpriteSet(ModParticles.EMBER.get(), TakemParticle.factory(TakemParticle.Style.EMBER));
        event.registerSpriteSet(ModParticles.MUSHROOM.get(), TakemParticle.factory(TakemParticle.Style.MUSHROOM));
    }

    @SubscribeEvent
    public static void onRenderers(RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FLARE.get(), FlareRenderer::new);
        event.registerEntityRenderer(ModEntities.THUNDER_SPEAR.get(), ThunderSpearRenderer::new);
        event.registerEntityRenderer(ModEntities.COLOSSAL_SERUM.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.2F, true));
        event.registerEntityRenderer(ModEntities.BEAST_MARKER.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.5F, true));
        event.registerEntityRenderer(ModEntities.PATHS_SHARD.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.2F, true));
        event.registerEntityRenderer(ModEntities.SLASH_WAVE.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.DEBRIS.get(), DebrisRenderer::new);
        event.registerEntityRenderer(ModEntities.FALLING_SECTION.get(), FallingSectionRenderer::new);
        event.registerEntityRenderer(ModEntities.PURE_TITAN.get(), ctx -> new TitanRenderer(ctx, "pure_titan", 6, 27, 1.0F));
        event.registerEntityRenderer(ModEntities.TRAINING_DUMMY.get(), ctx -> new TitanRenderer(ctx, "training_dummy", 1, 0, 1.0F));
        event.registerEntityRenderer(ModEntities.ABNORMAL_TITAN.get(), ctx -> new TitanRenderer(ctx, "abnormal_titan", 3, 0, 0.88F));
        event.registerEntityRenderer(ModEntities.CRAWLER_TITAN.get(), ctx -> new TitanRenderer(ctx, "crawler_titan", 2, 2, 1.0F));
        event.registerEntityRenderer(ModEntities.COLOSSAL_TITAN.get(), ctx -> new TitanRenderer(ctx, "colossal_titan", 1, 0, 1.0F));
        event.registerEntityRenderer(ModEntities.RUMBLING_TITAN.get(), ctx -> new TitanRenderer(ctx, "wall_titan", 3, 0, 1.0F));
        event.registerEntityRenderer(ModEntities.ARMORED_TITAN.get(), ctx -> new TitanRenderer(ctx, "armored_titan", 2, 0, 1.12F));
        event.registerEntityRenderer(ModEntities.BEAST_TITAN.get(), ctx -> new TitanRenderer(ctx, "beast_titan", 1, 1, 1.18F));
        event.registerEntityRenderer(ModEntities.TOWNSFOLK.get(), TownsfolkRenderer::new);
        event.registerEntityRenderer(ModEntities.WALL_TITAN.get(), ctx -> new TitanRenderer(ctx, "wall_titan", 3, 0, 1.0F));
        event.registerEntityRenderer(ModEntities.JAW_TITAN.get(), ctx -> new TitanRenderer(ctx, "jaw_titan", 1, 0, 0.9F));
        event.registerEntityRenderer(ModEntities.TITAN_HEART.get(), HeartRenderer::new);
        event.registerEntityRenderer(ModEntities.TITAN_GIB.get(), GibRenderer::new);
        event.registerEntityRenderer(ModEntities.SMOKE_BOMB.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.0F, true));
        event.registerEntityRenderer(ModEntities.NET.get(), ctx -> new ThrownItemRenderer<>(ctx, 2.0F, true));
        event.registerEntityRenderer(ModEntities.CANNON.get(), CannonRenderer::new);
        event.registerEntityRenderer(ModEntities.CANNONBALL.get(), ctx -> new ThrownItemRenderer<>(ctx, 2.2F, true));
        event.registerEntityRenderer(ModEntities.JAEGER.get(), JaegerRenderer::new);
        event.registerEntityRenderer(ModEntities.RAVAGER_TITAN.get(), ctx -> new TitanRenderer(ctx, "ravager_titan", 1, 0, 1.05F));
        event.registerEntityRenderer(ModEntities.THERMOBARIC.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.0F, true));
        event.registerEntityRenderer(ModEntities.CLUSTER_SPEAR.get(), ClusterSpearRenderer::new);
        event.registerEntityRenderer(ModEntities.BOMBLET.get(), ctx -> new ThrownItemRenderer<>(ctx, 0.6F, true));
        event.registerEntityRenderer(ModEntities.DEMOLITION_CHARGE.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.0F, true));
        event.registerEntityRenderer(ModEntities.SCOUT_HORSE.get(), net.minecraft.client.renderer.entity.HorseRenderer::new);
    }

    @SubscribeEvent
    public static void onShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                new ShaderInstance(event.getResourceProvider(), TakemTitans.id("skill_aura"), DefaultVertexFormat.POSITION_TEX), s -> TTGui.skillAura = s
            );
        } catch (Exception var3) {
            TakemTitans.LOGGER.warn("Takem Titans: skill tree shader unavailable, using a flat background", var3);
        }

        try {
            event.registerShader(
                new ShaderInstance(event.getResourceProvider(), TakemTitans.id("screen_fx"), DefaultVertexFormat.POSITION_TEX), s -> TTGui.screenFx = s
            );
        } catch (Exception var2) {
            TakemTitans.LOGGER.warn("Takem Titans: screen effect shader unavailable", var2);
        }
    }

    @SubscribeEvent
    public static void onLayerDefinitions(RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TitanModel.LAYER, TitanModel::createLayer);
        event.registerLayerDefinition(CannonRenderer.LAYER, CannonRenderer::createLayer);
    }

    @SubscribeEvent
    public static void onAddLayers(AddLayers event) {
        for (Model model : event.getSkins()) {
            if (event.getSkin(model) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new OdmGearLayer(renderer));
                renderer.addLayer(new CloakLayer(renderer));
                renderer.addLayer(new OffhandBladeLayer(renderer, event.getContext().getItemInHandRenderer()));
            }
        }
    }
}
