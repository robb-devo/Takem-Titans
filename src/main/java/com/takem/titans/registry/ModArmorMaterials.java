package com.takem.titans.registry;

import com.takem.titans.TakemTitans;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.ArmorMaterial.Layer;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, "takemtitans");
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> ODM_HARNESS = MATERIALS.register(
        "odm_harness", () -> new ArmorMaterial(Util.make(new EnumMap<>(Type.class), m -> {
                m.put(Type.BOOTS, 1);
                m.put(Type.LEGGINGS, 3);
                m.put(Type.CHESTPLATE, 3);
                m.put(Type.HELMET, 1);
                m.put(Type.BODY, 3);
            }), 12, SoundEvents.ARMOR_EQUIP_CHAIN, () -> Ingredient.of(Items.LEATHER), List.of(new Layer(TakemTitans.id("odm_harness"))), 0.0F, 0.0F)
    );
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SCOUT_UNIFORM = MATERIALS.register(
        "scout_uniform", () -> new ArmorMaterial(Util.make(new EnumMap<>(Type.class), m -> {
                m.put(Type.BOOTS, 1);
                m.put(Type.LEGGINGS, 2);
                m.put(Type.CHESTPLATE, 3);
                m.put(Type.HELMET, 1);
                m.put(Type.BODY, 3);
            }), 15, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.LEATHER), List.of(new Layer(TakemTitans.id("scout_uniform"))), 0.0F, 0.0F)
    );
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> VANGUARD = MATERIALS.register(
        "vanguard", () -> new ArmorMaterial(Util.make(new EnumMap<>(Type.class), m -> {
                m.put(Type.BOOTS, 2);
                m.put(Type.LEGGINGS, 5);
                m.put(Type.CHESTPLATE, 6);
                m.put(Type.HELMET, 2);
                m.put(Type.BODY, 6);
            }), 18, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.LEATHER), List.of(new Layer(TakemTitans.id("vanguard"))), 1.0F, 0.0F)
    );

    /** Light field leathers for long rides outside the Walls. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> RECON = MATERIALS.register(
        "recon", () -> new ArmorMaterial(Util.make(new EnumMap<>(Type.class), m -> {
                m.put(Type.BOOTS, 2);
                m.put(Type.LEGGINGS, 3);
                m.put(Type.CHESTPLATE, 4);
                m.put(Type.HELMET, 2);
                m.put(Type.BODY, 4);
            }), 16, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.LEATHER), List.of(new Layer(TakemTitans.id("recon"))), 0.0F, 0.0F)
    );
    /** Crystal grown from titan hearts, as hard as the Armored Titan's own plates. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> HARDENED = MATERIALS.register(
        "hardened", () -> new ArmorMaterial(Util.make(new EnumMap<>(Type.class), m -> {
                m.put(Type.BOOTS, 4);
                m.put(Type.LEGGINGS, 6);
                m.put(Type.CHESTPLATE, 9);
                m.put(Type.HELMET, 4);
                m.put(Type.BODY, 9);
            }), 22, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(Items.AMETHYST_SHARD), List.of(new Layer(TakemTitans.id("hardened"))), 3.5F, 0.25F)
    );

    /** The Survey Corps saddle cloth: green barding with the Wings of Freedom on both flanks. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SCOUT_BARDING = MATERIALS.register(
        "scout_barding", () -> new ArmorMaterial(Util.make(new EnumMap<>(Type.class), m -> {
                m.put(Type.BOOTS, 0);
                m.put(Type.LEGGINGS, 0);
                m.put(Type.CHESTPLATE, 0);
                m.put(Type.HELMET, 0);
                m.put(Type.BODY, 7);
            }), 1, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.LEATHER), List.of(new Layer(TakemTitans.id("scout_barding"))), 0.0F, 0.0F)
    );

    private ModArmorMaterials() {
    }

    public static Holder<ArmorMaterial> odm() {
        return ODM_HARNESS;
    }
}
