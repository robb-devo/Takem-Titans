package com.takem.titans;

import com.mojang.logging.LogUtils;
import com.takem.titans.network.TakemNetwork;
import com.takem.titans.registry.ModArmorMaterials;
import com.takem.titans.registry.ModAttachments;
import com.takem.titans.registry.ModBlockEntities;
import com.takem.titans.registry.ModBlocks;
import com.takem.titans.registry.ModCreativeTabs;
import com.takem.titans.registry.ModDataComponents;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.ModWorldgen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import org.slf4j.Logger;

@Mod("takemtitans")
public final class TakemTitans {
    public static final String MODID = "takemtitans";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TakemTitans(IEventBus modBus, ModContainer container) {
        ModSounds.SOUNDS.register(modBus);
        ModDataComponents.COMPONENTS.register(modBus);
        ModArmorMaterials.MATERIALS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.TYPES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModWorldgen.register(modBus);
        container.registerConfig(Type.COMMON, TakemConfig.COMMON_SPEC);
        container.registerConfig(Type.CLIENT, TakemConfig.CLIENT_SPEC);
        modBus.addListener(TakemNetwork::register);
        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(ModEntities::registerSpawnPlacements);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("takemtitans", path);
    }
}
