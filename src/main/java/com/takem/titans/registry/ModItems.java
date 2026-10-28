package com.takem.titans.registry;

import com.takem.titans.item.BeastBoulderItem;
import com.takem.titans.item.BladeItem;
import com.takem.titans.item.BladePackItem;
import com.takem.titans.item.HorseWhistleItem;
import com.takem.titans.item.BladeTier;
import com.takem.titans.item.FieldManualItem;
import com.takem.titans.item.GearMod;
import com.takem.titans.item.GearModItem;
import com.takem.titans.item.CannonItem;
import com.takem.titans.item.ClusterSpearItem;
import com.takem.titans.item.ColossalSerumItem;
import com.takem.titans.item.DemolitionChargeItem;
import com.takem.titans.item.FieldGear;
import com.takem.titans.item.FlareCartridgeItem;
import com.takem.titans.item.FlareColor;
import com.takem.titans.item.FlareGunItem;
import com.takem.titans.item.GasCanisterItem;
import com.takem.titans.item.GearTier;
import com.takem.titans.item.OdmGearItem;
import com.takem.titans.item.PathsShardItem;
import com.takem.titans.item.RegimentArmorItem;
import com.takem.titans.item.RumblingHornItem;
import com.takem.titans.item.ScoutCloakItem;
import com.takem.titans.item.ThermobaricItem;
import com.takem.titans.item.ThunderSpearItem;
import com.takem.titans.item.VanguardArmorItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

public final class ModItems {
    public static final Items ITEMS = DeferredRegister.createItems("takemtitans");
    public static final DeferredItem<OdmGearItem> ODM_GEAR = ITEMS.register(
        "odm_gear", () -> new OdmGearItem(ModArmorMaterials.ODM_HARNESS, Type.LEGGINGS, new Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<BladeItem> ODM_BLADES = ITEMS.register("odm_blades", () -> new BladeItem(new Properties().stacksTo(1).durability(40)));
    public static final DeferredItem<OdmGearItem> ADVANCED_ODM_GEAR = ITEMS.register(
        "advanced_odm_gear",
        () -> new OdmGearItem(ModArmorMaterials.ODM_HARNESS, Type.LEGGINGS, new Properties().stacksTo(1).rarity(Rarity.RARE), GearTier.ADVANCED)
    );
    public static final DeferredItem<BladeItem> VETERAN_BLADES = ITEMS.register(
        "veteran_blades", () -> new BladeItem(new Properties().stacksTo(1).durability(80).rarity(Rarity.UNCOMMON), BladeTier.VETERAN)
    );
    public static final DeferredItem<BladeItem> ACKERMAN_BLADES = ITEMS.register(
        "ackerman_blades", () -> new BladeItem(new Properties().stacksTo(1).durability(120).rarity(Rarity.EPIC), BladeTier.ACKERMAN)
    );
    public static final DeferredItem<FieldManualItem> FIELD_MANUAL = ITEMS.register(
        "field_manual", () -> new FieldManualItem(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<GearModItem> MOD_RECYCLER = gearMod(GearMod.RECYCLER);
    public static final DeferredItem<GearModItem> MOD_WINCH = gearMod(GearMod.WINCH);
    public static final DeferredItem<GearModItem> MOD_FRAME = gearMod(GearMod.FRAME);
    public static final DeferredItem<GearModItem> MOD_LONG_HOOKS = gearMod(GearMod.LONG_HOOKS);
    public static final DeferredItem<GearModItem> MOD_BARBS = gearMod(GearMod.BARBS);
    public static final DeferredItem<GearModItem> MOD_TWIN_CHAMBER = gearMod(GearMod.TWIN_CHAMBER);
    public static final DeferredItem<OdmGearItem> PHANTOM_ODM_GEAR = ITEMS.register(
        "phantom_odm_gear",
        () -> new OdmGearItem(ModArmorMaterials.ODM_HARNESS, Type.LEGGINGS, new Properties().stacksTo(1).rarity(Rarity.RARE), GearTier.PHANTOM)
    );
    public static final DeferredItem<OdmGearItem> GODSPEED_ODM_GEAR = ITEMS.register(
        "godspeed_odm_gear",
        () -> new OdmGearItem(ModArmorMaterials.ODM_HARNESS, Type.LEGGINGS, new Properties().stacksTo(1).rarity(Rarity.EPIC), GearTier.GODSPEED)
    );
    public static final DeferredItem<OdmGearItem> EVENT_HORIZON_ODM_GEAR = ITEMS.register(
        "event_horizon_odm_gear",
        () -> new OdmGearItem(
            ModArmorMaterials.ODM_HARNESS, Type.LEGGINGS, new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), GearTier.EVENT_HORIZON
        )
    );
    public static final DeferredItem<BladeItem> TITANBREAKER_BLADES = ITEMS.register(
        "titanbreaker_blades", () -> new BladeItem(new Properties().stacksTo(1).durability(300).rarity(Rarity.EPIC), BladeTier.TITANBREAKER)
    );
    public static final DeferredItem<BladeItem> SERRATED_BLADES = ITEMS.register(
        "serrated_blades", () -> new BladeItem(new Properties().stacksTo(1).durability(100).rarity(Rarity.RARE), BladeTier.SERRATED)
    );
    public static final DeferredItem<BladeItem> FOUNDER_BLADES = ITEMS.register(
        "founder_blades", () -> new BladeItem(new Properties().stacksTo(1).durability(600).rarity(Rarity.EPIC).fireResistant(), BladeTier.FOUNDER)
    );
    public static final DeferredItem<GasCanisterItem> GAS_CANISTER = ITEMS.register("gas_canister", () -> new GasCanisterItem(new Properties().stacksTo(16)));
    public static final DeferredItem<BladePackItem> BLADE_PACK = ITEMS.register("blade_pack", () -> new BladePackItem(new Properties().stacksTo(16)));
    public static final DeferredItem<Item> ULTRAHARD_STEEL_INGOT = ITEMS.registerSimpleItem("ultrahard_steel_ingot");
    public static final DeferredItem<Item> ICE_BURST_SHARD = ITEMS.registerSimpleItem("ice_burst_shard");
    public static final DeferredItem<ScoutCloakItem> SCOUT_CLOAK = ITEMS.register(
        "scout_cloak", () -> new ScoutCloakItem(ModArmorMaterials.SCOUT_UNIFORM, Type.CHESTPLATE, new Properties().stacksTo(1))
    );
    public static final DeferredItem<FlareGunItem> FLARE_GUN = ITEMS.register("flare_gun", () -> new FlareGunItem(new Properties().stacksTo(1)));
    public static final DeferredItem<FlareCartridgeItem> FLARE_RED = cartridge(FlareColor.RED);
    public static final DeferredItem<FlareCartridgeItem> FLARE_GREEN = cartridge(FlareColor.GREEN);
    public static final DeferredItem<FlareCartridgeItem> FLARE_BLACK = cartridge(FlareColor.BLACK);
    public static final DeferredItem<FlareCartridgeItem> FLARE_YELLOW = cartridge(FlareColor.YELLOW);
    public static final DeferredItem<FlareCartridgeItem> FLARE_PURPLE = cartridge(FlareColor.PURPLE);
    public static final DeferredItem<ThunderSpearItem> THUNDER_SPEAR = ITEMS.register(
        "thunder_spear", () -> new ThunderSpearItem(new Properties().stacksTo(4).rarity(Rarity.RARE))
    );
    public static final DeferredItem<ColossalSerumItem> COLOSSAL_SERUM = ITEMS.register(
        "colossal_serum", () -> new ColossalSerumItem(new Properties().stacksTo(16).rarity(Rarity.EPIC))
    );
    public static final DeferredItem<HorseWhistleItem> HORSE_WHISTLE = ITEMS.register(
        "horse_whistle", () -> new HorseWhistleItem(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<net.minecraft.world.item.AnimalArmorItem> SCOUT_BARDING = ITEMS.register(
        "scout_barding",
        () -> new net.minecraft.world.item.AnimalArmorItem(
            ModArmorMaterials.SCOUT_BARDING, net.minecraft.world.item.AnimalArmorItem.BodyType.EQUESTRIAN, false, new Properties().stacksTo(1)
        )
    );
    public static final DeferredItem<RumblingHornItem> RUMBLING_HORN = ITEMS.register(
        "rumbling_horn", () -> new RumblingHornItem(new Properties().stacksTo(1).rarity(Rarity.EPIC))
    );
    public static final DeferredItem<BeastBoulderItem> BEAST_BOULDER = ITEMS.register(
        "beast_boulder", () -> new BeastBoulderItem(new Properties().stacksTo(16).rarity(Rarity.EPIC))
    );
    public static final DeferredItem<PathsShardItem> PATHS_SHARD = ITEMS.register(
        "paths_shard", () -> new PathsShardItem(new Properties().stacksTo(16).rarity(Rarity.EPIC))
    );
    public static final DeferredItem<Item> MERIT_BADGE = ITEMS.register("merit_badge", () -> new Item(new Properties().stacksTo(64).rarity(Rarity.UNCOMMON)) {
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                tooltip.add(Component.translatable("tooltip.takemtitans.merit_badge").withStyle(ChatFormatting.GOLD));
            }
        });
    public static final DeferredItem<DeferredSpawnEggItem> PURE_TITAN_SPAWN_EGG = ITEMS.register(
        "pure_titan_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.PURE_TITAN, 14262398, 9194298, new Properties())
    );
    public static final DeferredItem<DeferredSpawnEggItem> ABNORMAL_TITAN_SPAWN_EGG = ITEMS.register(
        "abnormal_titan_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.ABNORMAL_TITAN, 13208683, 2829099, new Properties())
    );
    public static final DeferredItem<DeferredSpawnEggItem> GHOUL_TITAN_SPAWN_EGG = ITEMS.register(
        "ghoul_titan_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.GHOUL_TITAN, 14408667, 2631720, new Properties())
    );
    public static final DeferredItem<DeferredSpawnEggItem> CRAWLER_TITAN_SPAWN_EGG = ITEMS.register(
        "crawler_titan_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.CRAWLER_TITAN, 14725264, 6962474, new Properties())
    );
    public static final DeferredItem<DeferredSpawnEggItem> COLOSSAL_TITAN_SPAWN_EGG = ITEMS.register(
        "colossal_titan_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.COLOSSAL_TITAN, 11023659, 15786192, new Properties().rarity(Rarity.EPIC))
    );
    public static final DeferredItem<DeferredSpawnEggItem> ARMORED_TITAN_SPAWN_EGG = ITEMS.register(
        "armored_titan_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.ARMORED_TITAN, 15262418, 11560494, new Properties().rarity(Rarity.EPIC))
    );
    public static final DeferredItem<DeferredSpawnEggItem> FEMALE_TITAN_SPAWN_EGG = ITEMS.register(
        "female_titan_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.FEMALE_TITAN, 13532544, 15912810, new Properties().rarity(Rarity.EPIC))
    );
    public static final DeferredItem<DeferredSpawnEggItem> WAR_HAMMER_TITAN_SPAWN_EGG = ITEMS.register(
        "war_hammer_titan_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.WAR_HAMMER_TITAN, 15132400, 13691903, new Properties().rarity(Rarity.EPIC))
    );
    public static final DeferredItem<DeferredSpawnEggItem> CART_TITAN_SPAWN_EGG = ITEMS.register(
        "cart_titan_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.CART_TITAN, 6052956, 9139029, new Properties().rarity(Rarity.EPIC))
    );
    public static final DeferredItem<DeferredSpawnEggItem> BEAST_TITAN_SPAWN_EGG = ITEMS.register(
        "beast_titan_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.BEAST_TITAN, 7231040, 14207144, new Properties().rarity(Rarity.EPIC))
    );
    public static final DeferredItem<VanguardArmorItem> VANGUARD_HELMET = ITEMS.register(
        "vanguard_helmet",
        () -> new VanguardArmorItem(ModArmorMaterials.VANGUARD, Type.HELMET, new Properties().stacksTo(1).durability(400).rarity(Rarity.RARE))
    );
    public static final DeferredItem<VanguardArmorItem> VANGUARD_COAT = ITEMS.register(
        "vanguard_coat",
        () -> new VanguardArmorItem(ModArmorMaterials.VANGUARD, Type.CHESTPLATE, new Properties().stacksTo(1).durability(560).rarity(Rarity.RARE))
    );
    public static final DeferredItem<VanguardArmorItem> VANGUARD_BOOTS = ITEMS.register(
        "vanguard_boots", () -> new VanguardArmorItem(ModArmorMaterials.VANGUARD, Type.BOOTS, new Properties().stacksTo(1).durability(450).rarity(Rarity.RARE))
    );
    public static final DeferredItem<RegimentArmorItem> RECON_HOOD = ITEMS.register(
        "recon_hood", () -> new RegimentArmorItem(ModArmorMaterials.RECON, Type.HELMET, new Properties().stacksTo(1).durability(300).rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<RegimentArmorItem> RECON_JACKET = ITEMS.register(
        "recon_jacket",
        () -> new RegimentArmorItem(ModArmorMaterials.RECON, Type.CHESTPLATE, new Properties().stacksTo(1).durability(420).rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<RegimentArmorItem> RECON_BOOTS = ITEMS.register(
        "recon_boots", () -> new RegimentArmorItem(ModArmorMaterials.RECON, Type.BOOTS, new Properties().stacksTo(1).durability(340).rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<RegimentArmorItem> HARDENED_HELM = ITEMS.register(
        "hardened_helm",
        () -> new RegimentArmorItem(ModArmorMaterials.HARDENED, Type.HELMET, new Properties().stacksTo(1).durability(900).rarity(Rarity.EPIC).fireResistant())
    );
    public static final DeferredItem<RegimentArmorItem> HARDENED_PLATE = ITEMS.register(
        "hardened_plate",
        () -> new RegimentArmorItem(ModArmorMaterials.HARDENED, Type.CHESTPLATE, new Properties().stacksTo(1).durability(1300).rarity(Rarity.EPIC).fireResistant())
    );
    public static final DeferredItem<RegimentArmorItem> HARDENED_BOOTS = ITEMS.register(
        "hardened_boots",
        () -> new RegimentArmorItem(ModArmorMaterials.HARDENED, Type.BOOTS, new Properties().stacksTo(1).durability(1000).rarity(Rarity.EPIC).fireResistant())
    );
    public static final DeferredItem<ThermobaricItem> THERMOBARIC_CANISTER = ITEMS.register(
        "thermobaric_canister", () -> new ThermobaricItem(new Properties().stacksTo(8).rarity(Rarity.RARE))
    );
    public static final DeferredItem<ClusterSpearItem> CLUSTER_SPEAR = ITEMS.register(
        "cluster_spear", () -> new ClusterSpearItem(new Properties().stacksTo(4).rarity(Rarity.EPIC))
    );
    public static final DeferredItem<DemolitionChargeItem> DEMOLITION_CHARGE = ITEMS.register(
        "demolition_charge", () -> new DemolitionChargeItem(new Properties().stacksTo(16).rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<DeferredSpawnEggItem> RAVAGER_TITAN_SPAWN_EGG = ITEMS.register(
        "ravager_titan_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.RAVAGER_TITAN, 5904914, 16738858, new Properties().rarity(Rarity.EPIC))
    );
    public static final DeferredItem<DeferredSpawnEggItem> JAEGER_SPAWN_EGG = ITEMS.register(
        "jaeger_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.JAEGER, 3099178, 15262416, new Properties())
    );
    public static final DeferredItem<CannonItem> FIELD_CANNON = ITEMS.register(
        "field_cannon", () -> new CannonItem(1, new Properties().stacksTo(4).rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<CannonItem> SIEGE_CANNON = ITEMS.register(
        "siege_cannon", () -> new CannonItem(2, new Properties().stacksTo(4).rarity(Rarity.RARE))
    );
    public static final DeferredItem<CannonItem> THUNDER_BATTERY = ITEMS.register(
        "thunder_battery", () -> new CannonItem(3, new Properties().stacksTo(4).rarity(Rarity.EPIC))
    );
    public static final DeferredItem<Item> CANNON_SHELL = ITEMS.registerSimpleItem("cannon_shell");
    public static final DeferredItem<DeferredSpawnEggItem> JAW_TITAN_SPAWN_EGG = ITEMS.register(
        "jaw_titan_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.JAW_TITAN, 2761760, 15261904, new Properties().rarity(Rarity.EPIC))
    );
    public static final DeferredItem<Item> MED_KIT = ITEMS.register("med_kit", () -> new FieldGear.MedKit(new Properties().stacksTo(8)));
    public static final DeferredItem<Item> WHETSTONE = ITEMS.register("whetstone", () -> new FieldGear.Whetstone(new Properties().durability(8)));
    public static final DeferredItem<Item> ADRENALINE = ITEMS.register("adrenaline", () -> new FieldGear.Stim(new Properties().stacksTo(4).rarity(Rarity.RARE)));
    public static final DeferredItem<Item> SQUAD_HORN = ITEMS.register(
        "squad_horn", () -> new FieldGear.SquadHorn(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<Item> SMOKE_BOMB = ITEMS.register("smoke_bomb", () -> new FieldGear.SmokeBombItem(new Properties().stacksTo(16)));
    public static final DeferredItem<Item> NET_LAUNCHER = ITEMS.register(
        "net_launcher", () -> new FieldGear.NetLauncher(new Properties().durability(16).rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<Item> TITAN_HEART_SHARD = ITEMS.register(
        "titan_heart_shard", () -> new Item(new Properties().stacksTo(64).rarity(Rarity.EPIC))
    );
    public static final DeferredItem<DeferredSpawnEggItem> WALL_TITAN_SPAWN_EGG = ITEMS.register(
        "wall_titan_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.WALL_TITAN, 12103060, 16757575, new Properties().rarity(Rarity.EPIC))
    );
    public static final DeferredItem<BlockItem> WALL_STONE = ITEMS.registerSimpleBlockItem(ModBlocks.WALL_STONE);
    public static final DeferredItem<BlockItem> WALL_STONE_BRICKS = ITEMS.registerSimpleBlockItem(ModBlocks.WALL_STONE_BRICKS);
    public static final DeferredItem<BlockItem> WALL_STONE_TILES = ITEMS.registerSimpleBlockItem(ModBlocks.WALL_STONE_TILES);
    public static final DeferredItem<BlockItem> CHISELED_WALL_STONE = ITEMS.registerSimpleBlockItem(ModBlocks.CHISELED_WALL_STONE);
    public static final DeferredItem<BlockItem> WALL_STONE_STAIRS = ITEMS.registerSimpleBlockItem(ModBlocks.WALL_STONE_STAIRS);
    public static final DeferredItem<BlockItem> WALL_STONE_SLAB = ITEMS.registerSimpleBlockItem(ModBlocks.WALL_STONE_SLAB);
    public static final DeferredItem<BlockItem> ICE_BURST_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.ICE_BURST_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_ICE_BURST_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_ICE_BURST_ORE);
    public static final DeferredItem<BlockItem> SUPPLY_STATION = ITEMS.registerSimpleBlockItem(ModBlocks.SUPPLY_STATION);
    public static final DeferredItem<BlockItem> WALL_GATE = ITEMS.registerSimpleBlockItem(ModBlocks.WALL_GATE);
    public static final DeferredItem<BlockItem> GATE_CONTROL = ITEMS.registerSimpleBlockItem(ModBlocks.GATE_CONTROL);
    public static final DeferredItem<BlockItem> WAYSTONE = ITEMS.registerSimpleBlockItem(ModBlocks.WAYSTONE);

    private static DeferredItem<GearModItem> gearMod(GearMod mod) {
        return ITEMS.register("mod_" + mod.id(), () -> new GearModItem(new Properties().stacksTo(1).rarity(Rarity.RARE), mod));
    }

    private ModItems() {
    }

    private static DeferredItem<FlareCartridgeItem> cartridge(FlareColor color) {
        return ITEMS.register("flare_" + color.id(), () -> new FlareCartridgeItem(color, new Properties().stacksTo(16)));
    }
}
