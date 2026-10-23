package com.takem.titans.item;

import com.takem.titans.odm.OdmGear;
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

public class GasCanisterItem extends Item {
    public GasCanisterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack gear = OdmGear.getGear(player);
        if (gear.isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.takemtitans.need_gear"), true);
            }

            return InteractionResultHolder.fail(stack);
        } else if (OdmGear.getGas(gear) >= OdmGear.maxGas(gear)) {
            return InteractionResultHolder.fail(stack);
        } else {
            if (!level.isClientSide) {
                OdmGear.setGas(gear, OdmGear.getGas(gear) + OdmGear.maxGas(gear) / 2);
                stack.consume(1, player);
            }

            level.playSound(player, player.getX(), player.getY() + 1.0, player.getZ(), ModSounds.REFILL.get(), SoundSource.PLAYERS, 1.0F, 1.1F);
            player.getCooldowns().addCooldown(this, 10);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.takemtitans.gas_canister").withStyle(ChatFormatting.GRAY));
    }
}
