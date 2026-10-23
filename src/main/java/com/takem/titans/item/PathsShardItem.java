package com.takem.titans.item;

import com.takem.titans.entity.PathsShardEntity;
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
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;

public class PathsShardItem extends Item {
    public PathsShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0F, 0.7F);
        if (!level.isClientSide) {
            PathsShardEntity vial = new PathsShardEntity(level, player);
            vial.setItem(stack.copyWithCount(1));
            vial.shootFromRotation(player, player.getXRot(), player.getYRot(), -4.0F, 2.4F, 0.3F);
            level.addFreshEntity(vial);
            stack.consume(1, player);
        }

        player.getCooldowns().addCooldown(this, 200);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.takemtitans.paths_shard").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.takemtitans.paths_shard_warning").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
    }
}
