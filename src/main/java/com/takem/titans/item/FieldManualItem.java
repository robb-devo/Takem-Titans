package com.takem.titans.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The Survey Corps field manual: everything the mod can do, to look up at any time. Opens on use (or with its key). */
public class FieldManualItem extends Item {
    /** Set by the client at startup; opens the manual screen. */
    public static Runnable opener = () -> {
    };

    public FieldManualItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
            opener.run();
        }

        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
