package com.takem.titans.item;

import com.takem.titans.combat.TitanShift;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Shifter Serum: inject it and the Titan Shift begins (see TitanShift). */
public class ShifterSerumItem extends Item {
    public ShifterSerumItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (TitanShift.isShifted(player)) {
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer sp) {
            if (!TitanShift.begin(sp)) {
                return InteractionResultHolder.fail(stack);
            }
            stack.consume(1, player);
            player.getCooldowns().addCooldown(this, 1800);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.takemtitans.shifter_serum").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.takemtitans.shifter_serum_controls").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.takemtitans.shifter_serum_warning").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
    }
}
