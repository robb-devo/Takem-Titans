package com.takem.titans.registry;

import com.takem.titans.entity.BeastMarkerEntity;
import com.takem.titans.entity.BombletEntity;
import com.takem.titans.entity.CannonEntity;
import com.takem.titans.entity.CannonballEntity;
import com.takem.titans.entity.ClusterSpearEntity;
import com.takem.titans.entity.ColossalSerumEntity;
import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.DemolitionChargeEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.entity.FlareEntity;
import com.takem.titans.entity.PathsShardEntity;
import com.takem.titans.entity.SlashWaveEntity;
import com.takem.titans.entity.ThermobaricEntity;
import com.takem.titans.entity.ThunderSpearEntity;
import com.takem.titans.entity.TitanGibEntity;
import com.takem.titans.entity.TitanHeartEntity;
import com.takem.titans.entity.npc.JaegerEntity;
import com.takem.titans.entity.npc.TownsfolkEntity;
import com.takem.titans.entity.titan.AbnormalTitan;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.ArmoredTitan;
import com.takem.titans.entity.titan.BeastTitan;
import com.takem.titans.entity.titan.ColossalTitan;
import com.takem.titans.entity.titan.CrawlerTitan;
import com.takem.titans.entity.titan.JawTitan;
import com.takem.titans.entity.titan.PureTitan;
import com.takem.titans.entity.titan.RavagerTitan;
import com.takem.titans.entity.titan.RumblingTitan;
import com.takem.titans.entity.titan.WallTitan;
import com.takem.titans.item.FieldGear;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "takemtitans");
    public static final DeferredHolder<EntityType<?>, EntityType<PureTitan>> PURE_TITAN = ENTITIES.register(
        "pure_titan",
        () -> Builder.of(PureTitan::new, MobCategory.MONSTER).sized(2.0F, 8.0F).eyeHeight(7.2F).clientTrackingRange(16).updateInterval(2).build("pure_titan")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<AbnormalTitan>> ABNORMAL_TITAN = ENTITIES.register(
        "abnormal_titan",
        () -> Builder.of(AbnormalTitan::new, MobCategory.MONSTER)
                .sized(2.0F, 9.0F)
                .eyeHeight(8.1F)
                .clientTrackingRange(16)
                .updateInterval(2)
                .build("abnormal_titan")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<CrawlerTitan>> CRAWLER_TITAN = ENTITIES.register(
        "crawler_titan",
        () -> Builder.of(CrawlerTitan::new, MobCategory.MONSTER)
                .sized(3.0F, 2.6F)
                .eyeHeight(2.0F)
                .clientTrackingRange(16)
                .updateInterval(2)
                .build("crawler_titan")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<ColossalTitan>> COLOSSAL_TITAN = ENTITIES.register(
        "colossal_titan",
        () -> Builder.of(ColossalTitan::new, MobCategory.MONSTER)
                .sized(12.0F, 60.0F)
                .eyeHeight(56.0F)
                .fireImmune()
                .clientTrackingRange(32)
                .updateInterval(2)
                .build("colossal_titan")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<RumblingTitan>> RUMBLING_TITAN = ENTITIES.register(
        "rumbling_titan",
        () -> Builder.of(RumblingTitan::new, MobCategory.MISC)
                .sized(12.0F, 60.0F)
                .eyeHeight(56.0F)
                .fireImmune()
                .clientTrackingRange(32)
                .updateInterval(2)
                .build("rumbling_titan")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<ArmoredTitan>> ARMORED_TITAN = ENTITIES.register(
        "armored_titan",
        () -> Builder.of(ArmoredTitan::new, MobCategory.MONSTER)
                .sized(2.0F, 8.0F)
                .eyeHeight(7.2F)
                .fireImmune()
                .clientTrackingRange(20)
                .updateInterval(2)
                .build("armored_titan")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<BeastTitan>> BEAST_TITAN = ENTITIES.register(
        "beast_titan",
        () -> Builder.of(BeastTitan::new, MobCategory.MONSTER).sized(2.0F, 8.0F).eyeHeight(7.2F).clientTrackingRange(20).updateInterval(2).build("beast_titan")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<TownsfolkEntity>> TOWNSFOLK = ENTITIES.register(
        "townsfolk",
        () -> Builder.of(TownsfolkEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F).noSave().clientTrackingRange(10).build("townsfolk")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<FlareEntity>> FLARE = ENTITIES.register(
        "flare", () -> Builder.<FlareEntity>of(FlareEntity::new, MobCategory.MISC).sized(0.3F, 0.3F).clientTrackingRange(16).updateInterval(1).build("flare")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<ThunderSpearEntity>> THUNDER_SPEAR = ENTITIES.register(
        "thunder_spear",
        () -> Builder.<ThunderSpearEntity>of(ThunderSpearEntity::new, MobCategory.MISC)
                .sized(0.4F, 0.4F)
                .clientTrackingRange(8)
                .updateInterval(1)
                .build("thunder_spear")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<ColossalSerumEntity>> COLOSSAL_SERUM = ENTITIES.register(
        "colossal_serum",
        () -> Builder.<ColossalSerumEntity>of(ColossalSerumEntity::new, MobCategory.MISC)
                .sized(0.3F, 0.3F)
                .clientTrackingRange(10)
                .updateInterval(1)
                .build("colossal_serum")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<BeastMarkerEntity>> BEAST_MARKER = ENTITIES.register(
        "beast_marker",
        () -> Builder.<BeastMarkerEntity>of(BeastMarkerEntity::new, MobCategory.MISC)
                .sized(0.4F, 0.4F)
                .clientTrackingRange(10)
                .updateInterval(1)
                .build("beast_marker")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<PathsShardEntity>> PATHS_SHARD = ENTITIES.register(
        "paths_shard",
        () -> Builder.<PathsShardEntity>of(PathsShardEntity::new, MobCategory.MISC)
                .sized(0.3F, 0.3F)
                .clientTrackingRange(10)
                .updateInterval(1)
                .build("paths_shard")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<SlashWaveEntity>> SLASH_WAVE = ENTITIES.register(
        "slash_wave",
        () -> Builder.of(SlashWaveEntity::new, MobCategory.MISC).sized(1.0F, 1.0F).noSave().clientTrackingRange(16).updateInterval(1).build("slash_wave")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<DebrisEntity>> DEBRIS = ENTITIES.register(
        "debris", () -> Builder.of(DebrisEntity::new, MobCategory.MISC).sized(0.98F, 0.98F).noSave().clientTrackingRange(10).updateInterval(3).build("debris")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<FallingSectionEntity>> FALLING_SECTION = ENTITIES.register(
        "falling_section",
        () -> Builder.of(FallingSectionEntity::new, MobCategory.MISC)
                .sized(1.0F, 1.0F)
                .noSave()
                .clientTrackingRange(20)
                .updateInterval(1)
                .build("falling_section")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<RavagerTitan>> RAVAGER_TITAN = ENTITIES.register(
        "ravager_titan",
        () -> Builder.of(RavagerTitan::new, MobCategory.MONSTER)
                .sized(2.6F, 11.0F)
                .eyeHeight(9.9F)
                .fireImmune()
                .clientTrackingRange(24)
                .updateInterval(2)
                .build("ravager_titan")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<com.takem.titans.entity.ScoutHorseEntity>> SCOUT_HORSE = ENTITIES.register(
        "scout_horse",
        () -> Builder.of(com.takem.titans.entity.ScoutHorseEntity::new, MobCategory.CREATURE)
                .sized(1.3964844F, 1.6F)
                .eyeHeight(1.52F)
                .passengerAttachments(1.44375F)
                .clientTrackingRange(10)
                .build("scout_horse")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<JaegerEntity>> JAEGER = ENTITIES.register(
        "jaeger",
        () -> Builder.of(JaegerEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(12).updateInterval(1).build("jaeger")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<ThermobaricEntity>> THERMOBARIC = ENTITIES.register(
        "thermobaric",
        () -> Builder.<ThermobaricEntity>of(ThermobaricEntity::new, MobCategory.MISC)
                .sized(0.3F, 0.3F)
                .clientTrackingRange(10)
                .updateInterval(1)
                .build("thermobaric")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<ClusterSpearEntity>> CLUSTER_SPEAR = ENTITIES.register(
        "cluster_spear",
        () -> Builder.<ClusterSpearEntity>of(ClusterSpearEntity::new, MobCategory.MISC)
                .sized(0.4F, 0.4F)
                .clientTrackingRange(8)
                .updateInterval(1)
                .build("cluster_spear")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<BombletEntity>> BOMBLET = ENTITIES.register(
        "bomblet",
        () -> Builder.<BombletEntity>of(BombletEntity::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .noSave()
                .clientTrackingRange(8)
                .updateInterval(1)
                .build("bomblet")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<DemolitionChargeEntity>> DEMOLITION_CHARGE = ENTITIES.register(
        "demolition_charge",
        () -> Builder.<DemolitionChargeEntity>of(DemolitionChargeEntity::new, MobCategory.MISC)
                .sized(0.3F, 0.3F)
                .clientTrackingRange(10)
                .updateInterval(1)
                .build("demolition_charge")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<WallTitan>> WALL_TITAN = ENTITIES.register(
        "wall_titan",
        () -> Builder.of(WallTitan::new, MobCategory.MONSTER)
                .sized(12.0F, 58.0F)
                .eyeHeight(54.0F)
                .fireImmune()
                .clientTrackingRange(32)
                .updateInterval(2)
                .build("wall_titan")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<TitanHeartEntity>> TITAN_HEART = ENTITIES.register(
        "titan_heart",
        () -> Builder.of(TitanHeartEntity::new, MobCategory.MISC)
                .sized(2.5F, 3.0F)
                .noSave()
                .fireImmune()
                .clientTrackingRange(16)
                .updateInterval(1)
                .build("titan_heart")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<CannonEntity>> CANNON = ENTITIES.register(
        "cannon",
        () -> Builder.of(CannonEntity::new, MobCategory.MISC).sized(1.8F, 1.6F).fireImmune().clientTrackingRange(12).updateInterval(1).build("cannon")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<CannonballEntity>> CANNONBALL = ENTITIES.register(
        "cannonball",
        () -> Builder.<CannonballEntity>of(CannonballEntity::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .clientTrackingRange(12)
                .updateInterval(1)
                .build("cannonball")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<TitanGibEntity>> TITAN_GIB = ENTITIES.register(
        "titan_gib",
        () -> Builder.of(TitanGibEntity::new, MobCategory.MISC).sized(1.0F, 1.0F).noSave().clientTrackingRange(12).updateInterval(1).build("titan_gib")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<FieldGear.SmokeBomb>> SMOKE_BOMB = ENTITIES.register(
        "smoke_bomb",
        () -> Builder.<FieldGear.SmokeBomb>of(FieldGear.SmokeBomb::new, MobCategory.MISC)
                .sized(0.3F, 0.3F)
                .clientTrackingRange(10)
                .updateInterval(1)
                .build("smoke_bomb")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<FieldGear.Net>> NET = ENTITIES.register(
        "net", () -> Builder.<FieldGear.Net>of(FieldGear.Net::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(1).build("net")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<JawTitan>> JAW_TITAN = ENTITIES.register(
        "jaw_titan",
        () -> Builder.of(JawTitan::new, MobCategory.MONSTER).sized(1.8F, 5.5F).eyeHeight(4.8F).clientTrackingRange(20).updateInterval(2).build("jaw_titan")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<com.takem.titans.entity.titan.TrainingDummy>> TRAINING_DUMMY = ENTITIES.register(
        "training_dummy",
        () -> Builder.of(com.takem.titans.entity.titan.TrainingDummy::new, MobCategory.MISC)
                .sized(2.0F, 8.0F)
                .eyeHeight(7.2F)
                .clientTrackingRange(10)
                .updateInterval(4)
                .build("training_dummy")
    );

    private ModEntities() {
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(PURE_TITAN.get(), AbstractTitan.createTitanAttributes().build());
        event.put(ABNORMAL_TITAN.get(), AbstractTitan.createTitanAttributes().build());
        event.put(CRAWLER_TITAN.get(), AbstractTitan.createTitanAttributes().build());
        event.put(COLOSSAL_TITAN.get(), ColossalTitan.createColossalAttributes().build());
        event.put(RUMBLING_TITAN.get(), ColossalTitan.createColossalAttributes().build());
        event.put(ARMORED_TITAN.get(), AbstractTitan.createTitanAttributes().build());
        event.put(BEAST_TITAN.get(), AbstractTitan.createTitanAttributes().build());
        event.put(TOWNSFOLK.get(), TownsfolkEntity.createAttributes().build());
        event.put(RAVAGER_TITAN.get(), AbstractTitan.createTitanAttributes().build());
        event.put(JAEGER.get(), JaegerEntity.createAttributes().build());
        event.put(SCOUT_HORSE.get(), com.takem.titans.entity.ScoutHorseEntity.createAttributes().build());
        event.put(WALL_TITAN.get(), ColossalTitan.createColossalAttributes().build());
        event.put(JAW_TITAN.get(), AbstractTitan.createTitanAttributes().build());
        event.put(TRAINING_DUMMY.get(), com.takem.titans.entity.titan.TrainingDummy.createDummyAttributes().build());
    }

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(PURE_TITAN.get(), SpawnPlacementTypes.ON_GROUND, Types.MOTION_BLOCKING_NO_LEAVES, AbstractTitan::checkTitanSpawnRules, Operation.REPLACE);
        event.register(
            ABNORMAL_TITAN.get(), SpawnPlacementTypes.ON_GROUND, Types.MOTION_BLOCKING_NO_LEAVES, AbstractTitan::checkTitanSpawnRules, Operation.REPLACE
        );
        event.register(
            CRAWLER_TITAN.get(), SpawnPlacementTypes.ON_GROUND, Types.MOTION_BLOCKING_NO_LEAVES, AbstractTitan::checkTitanSpawnRules, Operation.REPLACE
        );
    }
}
