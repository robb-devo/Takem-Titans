package com.takem.titans.item;

import com.takem.titans.odm.OdmGear;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A gear extra. Using it opens the Gear Workshop, where extras are fitted into (and taken out of) the worn rig;
 * nothing is lost, so extras can be swapped between rigs freely.
 */
public class GearModItem extends Item {
    public final GearMod mod;

    public GearModItem(Item.Properties properties, GearMod mod) {
        super(properties);
        this.mod = mod;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // the Gear Workshop does the fitting: it shows the rig's slots, what each extra does and how they pair up
        if (level.isClientSide) {
            com.takem.titans.client.gui.GearWorkshopScreen.open();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    /**
     * The workshop's buttons, on the server. Fitting needs a free slot and an extra of that kind in the pack (nothing is
     * ever pushed out); taking one out puts it back in the pack.
     */
    public static void workshop(net.minecraft.server.level.ServerPlayer player, boolean fit, int ordinal) {
        if (ordinal < 0 || ordinal >= GearMod.values().length) {
            return;
        }
        GearMod mod = GearMod.values()[ordinal];
        ItemStack gear = OdmGear.getGear(player);
        if (gear.isEmpty()) {
            return;
        }
        Level level = player.level();
        if (fit) {
            if (OdmGear.hasMod(gear, mod) || Integer.bitCount(OdmGear.mods(gear)) >= GearMod.slots(OdmGear.tier(gear))) {
                return;
            }
            net.minecraft.world.entity.player.Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.getItem() instanceof GearModItem gm && gm.mod == mod) {
                    s.shrink(1);
                    OdmGear.fitMod(gear, mod);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                    return;
                }
            }
        } else if (OdmGear.hasMod(gear, mod)) {
            gear.set(com.takem.titans.registry.ModDataComponents.GEAR_MODS.get(), OdmGear.mods(gear) & ~mod.bit());
            ItemStack back = new ItemStack(itemFor(mod));
            if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
        }
    }

    public static Item itemFor(GearMod mod) {
        return switch (mod) {
            case RECYCLER -> com.takem.titans.registry.ModItems.MOD_RECYCLER.get();
            case WINCH -> com.takem.titans.registry.ModItems.MOD_WINCH.get();
            case FRAME -> com.takem.titans.registry.ModItems.MOD_FRAME.get();
            case LONG_HOOKS -> com.takem.titans.registry.ModItems.MOD_LONG_HOOKS.get();
            case BARBS -> com.takem.titans.registry.ModItems.MOD_BARBS.get();
            case TWIN_CHAMBER -> com.takem.titans.registry.ModItems.MOD_TWIN_CHAMBER.get();
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.takemtitans.mod." + this.mod.id()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.takemtitans.mod_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
