package com.takem.titans.item;

import com.takem.titans.entity.ThunderSpearEntity;
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
import net.minecraft.world.phys.Vec3;

public class ThunderSpearItem extends Item {
    public ThunderSpearItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            ThunderSpearEntity spear = new ThunderSpearEntity(level, player);
            Vec3 look = player.getLookAngle();
            spear.setPos(player.getX() + look.x * 0.5, player.getEyeY() - 0.25, player.getZ() + look.z * 0.5);
            spear.shoot(look.x, look.y, look.z, 3.6F, 0.4F);
            level.addFreshEntity(spear);
            stack.consume(1, player);
        }

        level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), ModSounds.THUNDER_SPEAR_LAUNCH.get(), SoundSource.PLAYERS, 1.3F, 1.0F);
        player.getCooldowns().addCooldown(this, 16);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.takemtitans.thunder_spear").withStyle(ChatFormatting.GRAY));
    }
}
