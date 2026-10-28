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
 * A gear mod: use it while wearing ODM gear to fit it. With every slot taken, the oldest mod comes out and goes back
 * into your pack, so mods can be swapped between rigs freely.
 */
public class GearModItem extends Item {
    public final GearMod mod;

    public GearModItem(Item.Properties properties, GearMod mod) {
        super(properties);
        this.mod = mod;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack gear = OdmGear.getGear(player);
        if (gear.isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.takemtitans.mod_no_gear").withStyle(ChatFormatting.GRAY), true);
            }

            return InteractionResultHolder.fail(held);
        } else if (OdmGear.hasMod(gear, this.mod)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.takemtitans.mod_already").withStyle(ChatFormatting.GRAY), true);
            }

            return InteractionResultHolder.fail(held);
        } else {
            if (!level.isClientSide) {
                GearMod removed = OdmGear.fitMod(gear, this.mod);
                if (removed != null) {
                    ItemStack back = new ItemStack(GearModItem.itemFor(removed));
                    if (!player.getInventory().add(back)) {
                        player.drop(back, false);
                    }
                }

                held.consume(1, player);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                player.displayClientMessage(
                    Component.translatable("message.takemtitans.mod_fitted", Component.translatable(this.getDescriptionId())).withStyle(ChatFormatting.GOLD), true
                );
            }

            return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
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
