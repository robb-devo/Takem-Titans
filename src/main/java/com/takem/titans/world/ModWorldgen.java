package com.takem.titans.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModWorldgen {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(
        Registries.CHUNK_GENERATOR, "takemtitans"
    );
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<ShiganshinaChunkGenerator>> SHIGANSHINA = CHUNK_GENERATORS.register(
        "shiganshina", () -> ShiganshinaChunkGenerator.CODEC
    );

    private ModWorldgen() {
    }

    public static void register(IEventBus modBus) {
        CHUNK_GENERATORS.register(modBus);
    }
}
