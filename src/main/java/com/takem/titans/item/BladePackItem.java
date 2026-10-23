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

public class BladePackItem extends Item {
    public static final int BLADES_PER_PACK = 4;

    public BladePackItem(Properties properties) {
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
        } else if (OdmGear.getSpareBlades(gear) >= OdmGear.maxSpareBlades()) {
            return InteractionResultHolder.fail(stack);
        } else {
            if (!level.isClientSide) {
                OdmGear.setSpareBlades(gear, OdmGear.getSpareBlades(gear) + 4);
                stack.consume(1, player);
            }

            level.playSound(player, player.getX(), player.getY() + 1.0, player.getZ(), ModSounds.BLADE_RELOAD.get(), SoundSource.PLAYERS, 1.0F, 0.8F);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.takemtitans.blade_pack", 4).withStyle(ChatFormatting.GRAY));
    }
}
