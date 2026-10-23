package com.takem.titans.item;

import com.takem.titans.entity.ClusterSpearEntity;
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

public class ClusterSpearItem extends Item {
    public ClusterSpearItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            ClusterSpearEntity spear = new ClusterSpearEntity(level, player);
            Vec3 look = player.getLookAngle();
            spear.setPos(player.getX() + look.x * 0.5, player.getEyeY() - 0.25, player.getZ() + look.z * 0.5);
            spear.shoot(look.x, look.y, look.z, 3.2F, 0.6F);
            level.addFreshEntity(spear);
            stack.consume(1, player);
        }

        level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), ModSounds.THUNDER_SPEAR_LAUNCH.get(), SoundSource.PLAYERS, 1.3F, 0.85F);
        player.getCooldowns().addCooldown(this, 24);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.takemtitans.cluster_spear").withStyle(ChatFormatting.GRAY));
    }
}
