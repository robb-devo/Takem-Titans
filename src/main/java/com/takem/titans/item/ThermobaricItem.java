package com.takem.titans.item;

import com.takem.titans.entity.ThermobaricEntity;
import com.takem.titans.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

public class ThermobaricItem extends Item {
    public ThermobaricItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.EXPLOSIVE_THROW.get(), SoundSource.PLAYERS, 1.0F, 0.9F);
        if (!level.isClientSide) {
            ThermobaricEntity vial = new ThermobaricEntity(level, player);
            vial.setItem(stack.copyWithCount(1));
            vial.shootFromRotation(player, player.getXRot(), player.getYRot(), -8.0F, 1.6F, 0.8F);
            level.addFreshEntity(vial);
            stack.consume(1, player);
        }

        player.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.takemtitans.thermobaric").withStyle(ChatFormatting.GOLD));
    }
}
