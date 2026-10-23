package com.takem.titans.item;

import com.takem.titans.entity.FlareEntity;
import com.takem.titans.registry.ModDataComponents;
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

public class FlareGunItem extends Item {
    public FlareGunItem(Properties properties) {
        super(properties);
    }

    public static FlareColor selected(ItemStack gun) {
        return FlareColor.byIndex(gun.getOrDefault(ModDataComponents.FLARE_COLOR.get(), Integer.valueOf(0)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            FlareColor next = FlareColor.byIndex(selected(gun).ordinal() + 1);
            gun.set(ModDataComponents.FLARE_COLOR.get(), next.ordinal());
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.takemtitans.flare_selected", Component.translatable("color.takemtitans." + next.id()).withStyle(next.chat)),
                    true
                );
            }

            return InteractionResultHolder.sidedSuccess(gun, level.isClientSide);
        } else {
            FlareColor color = selected(gun);
            ItemStack ammo = findAmmo(player, color);
            if (ammo.isEmpty() && !player.getAbilities().instabuild) {
                if (!level.isClientSide) {
                    player.displayClientMessage(
                        Component.translatable("message.takemtitans.no_flares", Component.translatable("color.takemtitans." + color.id()).withStyle(color.chat)),
                        true
                    );
                }

                return InteractionResultHolder.fail(gun);
            } else {
                if (!level.isClientSide) {
                    FlareEntity flare = new FlareEntity(level, player, color);
                    Vec3 look = player.getLookAngle();
                    flare.setPos(player.getX() + look.x * 0.6, player.getEyeY() - 0.1 + look.y * 0.6, player.getZ() + look.z * 0.6);
                    flare.shoot(look.x, look.y, look.z, 2.4F, 0.6F);
                    level.addFreshEntity(flare);
                    if (!player.getAbilities().instabuild) {
                        ammo.shrink(1);
                    }
                }

                level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), ModSounds.FLARE_SHOOT.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
                player.getCooldowns().addCooldown(this, 30);
                return InteractionResultHolder.sidedSuccess(gun, level.isClientSide);
            }
        }
    }

    private static ItemStack findAmmo(Player player, FlareColor color) {
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof FlareCartridgeItem c && c.color == color) {
            return off;
        }

        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof FlareCartridgeItem c && c.color == color) {
                return s;
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FlareColor c = selected(stack);
        tooltip.add(
            Component.translatable("tooltip.takemtitans.flare_gun.selected", Component.translatable("color.takemtitans." + c.id()).withStyle(c.chat))
                .withStyle(ChatFormatting.GRAY)
        );
        tooltip.add(Component.translatable("tooltip.takemtitans.flare." + c.id()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.takemtitans.flare_gun.cycle").withStyle(ChatFormatting.DARK_GRAY));
    }
}
