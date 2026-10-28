package com.takem.titans.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, "takemtitans");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GAS = PARTICLES.register("gas", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STEAM = PARTICLES.register("steam", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BIG_STEAM = PARTICLES.register("big_steam", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD = PARTICLES.register("blood", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK = PARTICLES.register("spark", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLARE_SMOKE = PARTICLES.register("flare_smoke", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WIND_STREAK = PARTICLES.register("wind_streak", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUST_CLOUD = PARTICLES.register("dust_cloud", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIREBALL = PARTICLES.register("fireball", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMBER = PARTICLES.register("ember", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MUSHROOM = PARTICLES.register("mushroom", () -> new SimpleParticleType(true));
    /** Dust that rolls out along the ground from a collapse and piles up against walls. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUST_WAVE = PARTICLES.register("dust_wave", () -> new SimpleParticleType(true));
    /** The faint pall that hangs over a ruin long after the crash. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HAZE = PARTICLES.register("haze", () -> new SimpleParticleType(true));
    /** A tumbling 3D fragment of a block: a chip of stone, a splinter of timber, a shard of glass. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<BlockParticleOption>> RUBBLE = PARTICLES.register(
        "rubble",
        () -> new ParticleType<BlockParticleOption>(true) {
            @Override
            public MapCodec<BlockParticleOption> codec() {
                return BlockParticleOption.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, BlockParticleOption> streamCodec() {
                return BlockParticleOption.streamCodec(this);
            }
        }
    );

    private ModParticles() {
    }
}
