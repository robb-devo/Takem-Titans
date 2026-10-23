package com.takem.titans.world;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class BreachData extends SavedData {
    public boolean breached;

    public static BreachData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(BreachData::new, BreachData::load, null), "takemtitans_breach");
    }

    private static BreachData load(CompoundTag tag, Provider registries) {
        BreachData d = new BreachData();
        d.breached = tag.getBoolean("Breached");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, Provider registries) {
        tag.putBoolean("Breached", this.breached);
        return tag;
    }

    public void setBreached(boolean value) {
        this.breached = value;
        this.setDirty();
    }
}
