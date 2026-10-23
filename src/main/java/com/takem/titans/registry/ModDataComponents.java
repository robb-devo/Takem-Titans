package com.takem.titans.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.DataComponents;

public final class ModDataComponents {
    public static final DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "takemtitans");
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> GAS = COMPONENTS.registerComponentType(
        "gas", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT)
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SPARE_BLADES = COMPONENTS.registerComponentType(
        "spare_blades", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT)
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FLARE_COLOR = COMPONENTS.registerComponentType(
        "flare_color", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT)
    );

    private ModDataComponents() {
    }
}
