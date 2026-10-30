package com.takem.titans.registry;

import com.takem.titans.odm.OdmGear;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "takemtitans");
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
        "main",
        () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.takemtitans"))
                .icon(() -> new ItemStack(ModItems.ODM_BLADES.get()))
                .displayItems((params, out) -> {
                    out.accept(ModItems.FIELD_MANUAL.get());
                    out.accept(ModItems.WAYSTONE.get());
                    out.accept(ModItems.MOD_RECYCLER.get());
                    out.accept(ModItems.MOD_WINCH.get());
                    out.accept(ModItems.MOD_FRAME.get());
                    out.accept(ModItems.MOD_LONG_HOOKS.get());
                    out.accept(ModItems.MOD_BARBS.get());
                    out.accept(ModItems.MOD_TWIN_CHAMBER.get());
                    out.accept(OdmGear.createFullGear());
                    out.accept(OdmGear.createFullGear(ModItems.ADVANCED_ODM_GEAR.get()));
                    out.accept(OdmGear.createFullGear(ModItems.PHANTOM_ODM_GEAR.get()));
                    out.accept(OdmGear.createFullGear(ModItems.GODSPEED_ODM_GEAR.get()));
                    out.accept(OdmGear.createFullGear(ModItems.EVENT_HORIZON_ODM_GEAR.get()));
                    out.accept(ModItems.ODM_BLADES.get());
                    out.accept(ModItems.VETERAN_BLADES.get());
                    out.accept(ModItems.SERRATED_BLADES.get());
                    out.accept(ModItems.ACKERMAN_BLADES.get());
                    out.accept(ModItems.TITANBREAKER_BLADES.get());
                    out.accept(ModItems.FOUNDER_BLADES.get());
                    out.accept(ModItems.GAS_CANISTER.get());
                    out.accept(ModItems.BLADE_PACK.get());
                    out.accept(ModItems.SCOUT_CLOAK.get());
                    out.accept(ModItems.FLARE_GUN.get());
                    out.accept(ModItems.FLARE_RED.get());
                    out.accept(ModItems.FLARE_GREEN.get());
                    out.accept(ModItems.FLARE_BLACK.get());
                    out.accept(ModItems.FLARE_YELLOW.get());
                    out.accept(ModItems.FLARE_PURPLE.get());
                    out.accept(ModItems.THUNDER_SPEAR.get());
                    out.accept(ModItems.CLUSTER_SPEAR.get());
                    out.accept(ModItems.THERMOBARIC_CANISTER.get());
                    out.accept(ModItems.DEMOLITION_CHARGE.get());
                    out.accept(ModItems.VANGUARD_HELMET.get());
                    out.accept(ModItems.VANGUARD_COAT.get());
                    out.accept(ModItems.VANGUARD_BOOTS.get());
                    out.accept(ModItems.RECON_HOOD.get());
                    out.accept(ModItems.RECON_JACKET.get());
                    out.accept(ModItems.RECON_BOOTS.get());
                    out.accept(ModItems.HARDENED_HELM.get());
                    out.accept(ModItems.HARDENED_PLATE.get());
                    out.accept(ModItems.HARDENED_BOOTS.get());
                    out.accept(ModItems.COLOSSAL_SERUM.get());
                    out.accept(ModItems.SHIFTER_SERUM.get());
                    out.accept(ModItems.ODM_PISTOLS.get());
                    out.accept(ModItems.RUMBLING_HORN.get());
                    out.accept(ModItems.BEAST_BOULDER.get());
                    out.accept(ModItems.PATHS_SHARD.get());
                    out.accept(ModItems.MERIT_BADGE.get());
                    out.accept(ModItems.ULTRAHARD_STEEL_INGOT.get());
                    out.accept(ModItems.ICE_BURST_SHARD.get());
                    out.accept(ModItems.SUPPLY_STATION.get());
                    out.accept(ModItems.GATE_CONTROL.get());
                    out.accept(ModItems.WALL_GATE.get());
                    out.accept(ModItems.WALL_STONE.get());
                    out.accept(ModItems.WALL_STONE_BRICKS.get());
                    out.accept(ModItems.WALL_STONE_TILES.get());
                    out.accept(ModItems.CHISELED_WALL_STONE.get());
                    out.accept(ModItems.WALL_STONE_STAIRS.get());
                    out.accept(ModItems.WALL_STONE_SLAB.get());
                    out.accept(ModItems.PLASTER.get());
                    out.accept(ModItems.PLASTER_BRACE.get());
                    out.accept(ModItems.PLASTER_BRACE_MIRRORED.get());
                    out.accept(ModItems.PLASTER_CREAM.get());
                    out.accept(ModItems.PLASTER_CREAM_BRACE.get());
                    out.accept(ModItems.PLASTER_CREAM_BRACE_MIRRORED.get());
                    out.accept(ModItems.PLASTER_OCHRE.get());
                    out.accept(ModItems.ROOF_TILES.get());
                    out.accept(ModItems.ROOF_TILES_STAIRS.get());
                    out.accept(ModItems.ROOF_TILES_SLAB.get());
                    out.accept(ModItems.ROOF_TILES_DARK.get());
                    out.accept(ModItems.ROOF_TILES_DARK_STAIRS.get());
                    out.accept(ModItems.ROOF_TILES_DARK_SLAB.get());
                    out.accept(ModItems.ROOF_TILES_SLATE.get());
                    out.accept(ModItems.ROOF_TILES_SLATE_STAIRS.get());
                    out.accept(ModItems.ROOF_TILES_SLATE_SLAB.get());
                    out.accept(ModItems.ICE_BURST_ORE.get());
                    out.accept(ModItems.DEEPSLATE_ICE_BURST_ORE.get());
                    out.accept(ModItems.PURE_TITAN_SPAWN_EGG.get());
                    out.accept(ModItems.ABNORMAL_TITAN_SPAWN_EGG.get());
                    out.accept(ModItems.CRAWLER_TITAN_SPAWN_EGG.get());
                    out.accept(ModItems.GHOUL_TITAN_SPAWN_EGG.get());
                    out.accept(ModItems.COLOSSAL_TITAN_SPAWN_EGG.get());
                    out.accept(ModItems.ARMORED_TITAN_SPAWN_EGG.get());
                    out.accept(ModItems.FEMALE_TITAN_SPAWN_EGG.get());
                    out.accept(ModItems.WAR_HAMMER_TITAN_SPAWN_EGG.get());
                    out.accept(ModItems.CART_TITAN_SPAWN_EGG.get());
                    out.accept(ModItems.BEAST_TITAN_SPAWN_EGG.get());
                    out.accept(ModItems.RAVAGER_TITAN_SPAWN_EGG.get());
                    out.accept(ModItems.JAEGER_SPAWN_EGG.get());
                    out.accept(ModItems.WALL_TITAN_SPAWN_EGG.get());
                    out.accept(ModItems.JAW_TITAN_SPAWN_EGG.get());
                    out.accept(ModItems.TITAN_HEART_SHARD.get());
                    out.accept(ModItems.MED_KIT.get());
                    out.accept(ModItems.WHETSTONE.get());
                    out.accept(ModItems.ADRENALINE.get());
                    out.accept(ModItems.SQUAD_HORN.get());
                    out.accept(ModItems.HORSE_WHISTLE.get());
                    out.accept(ModItems.SCOUT_BARDING.get());
                    out.accept(ModItems.SMOKE_BOMB.get());
                    out.accept(ModItems.NET_LAUNCHER.get());
                    out.accept(ModItems.FIELD_CANNON.get());
                    out.accept(ModItems.SIEGE_CANNON.get());
                    out.accept(ModItems.THUNDER_BATTERY.get());
                })
                .build()
    );

    private ModCreativeTabs() {
    }
}
