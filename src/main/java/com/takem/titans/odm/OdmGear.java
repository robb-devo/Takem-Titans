package com.takem.titans.odm;

import com.takem.titans.TakemConfig;
import com.takem.titans.item.BladeItem;
import com.takem.titans.item.GearTier;
import com.takem.titans.item.OdmGearItem;
import com.takem.titans.registry.ModDataComponents;
import com.takem.titans.registry.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class OdmGear {
    private OdmGear() {
    }

    public static int maxGas() {
        try {
            return TakemConfig.GAS_CAPACITY.get();
        } catch (IllegalStateException var1) {
            return 2400;
        }
    }

    public static int maxGas(ItemStack gear) {
        return (int)Math.round((double)maxGas() * tier(gear).gas);
    }

    public static GearTier tier(ItemStack gear) {
        return gear.getItem() instanceof OdmGearItem g ? g.tier : GearTier.STANDARD;
    }

    public static GearTier tier(Player player) {
        return tier(getGear(player));
    }

    public static int maxSpareBlades() {
        try {
            return TakemConfig.SPARE_BLADES.get();
        } catch (IllegalStateException var1) {
            return 8;
        }
    }

    public static int mods(ItemStack gear) {
        Integer m = gear.get(ModDataComponents.GEAR_MODS.get());
        return m == null ? 0 : m;
    }

    public static boolean hasMod(ItemStack gear, com.takem.titans.item.GearMod mod) {
        return (mods(gear) & mod.bit()) != 0;
    }

    public static boolean hasMod(Player player, com.takem.titans.item.GearMod mod) {
        return hasMod(getGear(player), mod);
    }

    /** Fits a mod; with every slot full the lowest one comes out and is returned. */
    @javax.annotation.Nullable
    public static com.takem.titans.item.GearMod fitMod(ItemStack gear, com.takem.titans.item.GearMod mod) {
        int m = mods(gear);
        com.takem.titans.item.GearMod removed = null;
        if (Integer.bitCount(m) >= com.takem.titans.item.GearMod.slots(tier(gear))) {
            for (com.takem.titans.item.GearMod g : com.takem.titans.item.GearMod.values()) {
                if ((m & g.bit()) != 0) {
                    removed = g;
                    m &= ~g.bit();
                    break;
                }
            }
        }

        gear.set(ModDataComponents.GEAR_MODS.get(), m | mod.bit());
        return removed;
    }

    /** Gas cost after the gas recycler, if one is fitted. */
    public static double gasCost(ItemStack gear, double base) {
        return hasMod(gear, com.takem.titans.item.GearMod.RECYCLER) ? base * 0.7 : base;
    }

    /** How far this player's hooks reach: the configured range, a quarter more with long cables. */
    public static double hookRange(Player player) {
        return hookRange() * (hasMod(player, com.takem.titans.item.GearMod.LONG_HOOKS) ? 1.25 : 1.0);
    }

    public static double hookRange() {
        try {
            return TakemConfig.HOOK_RANGE.get();
        } catch (IllegalStateException var1) {
            return 72.0;
        }
    }

    public static ItemStack getGear(Player player) {
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        return legs.getItem() instanceof OdmGearItem ? legs : ItemStack.EMPTY;
    }

    public static boolean hasGear(Player player) {
        return !getGear(player).isEmpty() && !player.isSpectator();
    }

    public static boolean holdingBlades(Player player) {
        return player.getMainHandItem().getItem() instanceof BladeItem;
    }

    public static int getGas(ItemStack gear) {
        return gear.getOrDefault(ModDataComponents.GAS.get(), Integer.valueOf(0));
    }

    public static void setGas(ItemStack gear, int gas) {
        gear.set(ModDataComponents.GAS.get(), Math.max(0, Math.min(maxGas(gear), gas)));
    }

    public static int getSpareBlades(ItemStack gear) {
        return gear.getOrDefault(ModDataComponents.SPARE_BLADES.get(), Integer.valueOf(0));
    }

    public static void setSpareBlades(ItemStack gear, int blades) {
        gear.set(ModDataComponents.SPARE_BLADES.get(), Math.max(0, Math.min(maxSpareBlades(), blades)));
    }

    public static float gasFraction(ItemStack gear) {
        return gear.isEmpty() ? 0.0F : Math.min(1.0F, (float)getGas(gear) / (float)maxGas(gear));
    }

    public static ItemStack createFullGear() {
        return createFullGear(ModItems.ODM_GEAR.get());
    }

    public static ItemStack createFullGear(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.GAS.get(), maxGas(stack));
        stack.set(ModDataComponents.SPARE_BLADES.get(), maxSpareBlades());
        return stack;
    }

    public static boolean resupply(Player player) {
        boolean changed = false;
        ItemStack gear = getGear(player);
        if (!gear.isEmpty()) {
            if (getGas(gear) < maxGas(gear)) {
                setGas(gear, maxGas(gear));
                changed = true;
            }

            if (getSpareBlades(gear) < maxSpareBlades()) {
                setSpareBlades(gear, maxSpareBlades());
                changed = true;
            }
        }

        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.getItem() instanceof BladeItem && stack.getDamageValue() > 0) {
                stack.setDamageValue(0);
                changed = true;
            }
        }

        for (ItemStack stackx : player.getInventory().items) {
            if (stackx.getItem() instanceof OdmGearItem && (getGas(stackx) < maxGas(stackx) || getSpareBlades(stackx) < maxSpareBlades())) {
                setGas(stackx, maxGas(stackx));
                setSpareBlades(stackx, maxSpareBlades());
                changed = true;
            }
        }

        return changed;
    }
}
