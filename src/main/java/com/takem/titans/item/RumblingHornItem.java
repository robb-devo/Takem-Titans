package com.takem.titans.item;

import com.takem.titans.world.Rumbling;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;

public class RumblingHornItem extends Item {
    public RumblingHornItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl && !Rumbling.start(sl, player)) {
            player.displayClientMessage(Component.translatable("message.takemtitans.rumbling_busy").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }

        player.startUsingItem(hand);
        player.getCooldowns().addCooldown(this, 300);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 80;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.TOOT_HORN;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.takemtitans.rumbling_horn").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.takemtitans.rumbling_horn_warning").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
    }
}
