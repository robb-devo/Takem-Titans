package com.takem.titans.item;

import com.takem.titans.TakemConfig;
import com.takem.titans.TakemTitans;
import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.registry.ModSounds;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemAttributeModifiers.Builder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.network.PacketDistributor;

public class BladeItem extends Item {
    private static final ItemAttributeModifiers BROKEN = modifiers(0.5, -1.7, 0.0);
    public final BladeTier tier;
    private final ItemAttributeModifiers sharp;

    public BladeItem(Properties properties) {
        this(properties, BladeTier.STANDARD);
    }

    public BladeItem(Properties properties, BladeTier tier) {
        super(properties);
        this.tier = tier;
        this.sharp = modifiers(tier.damage, -1.7, 1.75);
    }

    private static ItemAttributeModifiers modifiers(double damage, double speed, double reach) {
        Builder b = ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage, Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, speed, Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        if (reach > 0.0) {
            b.add(
                Attributes.ENTITY_INTERACTION_RANGE,
                new AttributeModifier(TakemTitans.id("blade_reach"), reach, Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND
            );
        }

        return b.build();
    }

    public static boolean isBroken(ItemStack stack) {
        return stack.getDamageValue() >= stack.getMaxDamage();
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        try {
            return TakemConfig.BLADE_DURABILITY.get() * this.tier.durability;
        } catch (IllegalStateException var3) {
            return super.getMaxDamage(stack);
        }
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        return isBroken(stack) ? BROKEN : this.sharp;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return !isBroken(stack);
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        wear(stack, attacker, 1);
    }

    public static void wear(ItemStack stack, LivingEntity user, int amount) {
        if (!isBroken(stack)) {
            if (user instanceof Player p && p.getAbilities().instabuild) {
                return;
            }

            int dmg = Math.min(stack.getMaxDamage(), stack.getDamageValue() + amount);
            stack.setDamageValue(dmg);
            if (dmg >= stack.getMaxDamage()) {
                user.level()
                    .playSound(
                        null,
                        user.getX(),
                        user.getY() + 1.0,
                        user.getZ(),
                        ModSounds.BLADE_BREAK.get(),
                        SoundSource.PLAYERS,
                        1.0F,
                        0.9F + user.getRandom().nextFloat() * 0.2F
                    );
                if (user instanceof ServerPlayer sp) {
                    PacketDistributor.sendToPlayer(sp, FeedbackPayload.of(6, 0, 0, 0));
                }
            }
        }
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (state.getDestroySpeed(level, pos) > 0.5F) {
            wear(stack, miner, 1);
        }

        return true;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        return !isBroken(stack) && (ability == ItemAbilities.SWORD_SWEEP || ability == ItemAbilities.SWORD_DIG);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // the Founder's Edge shimmers like nothing else in the armoury
        return this.tier == BladeTier.FOUNDER && !isBroken(stack) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (this.tier != BladeTier.STANDARD) {
            tooltip.add(
                Component.translatable("tooltip.takemtitans.blades_tier." + this.tier.name().toLowerCase(Locale.ROOT))
                    .withStyle(
                        switch (this.tier) {
                            case ACKERMAN, SERRATED -> ChatFormatting.RED;
                            case TITANBREAKER -> ChatFormatting.GOLD;
                            case FOUNDER -> ChatFormatting.LIGHT_PURPLE;
                            default -> ChatFormatting.AQUA;
                        }
                    )
            );
            if (this.tier == BladeTier.TITANBREAKER) {
                tooltip.add(Component.translatable("tooltip.takemtitans.titanbreaker").withStyle(ChatFormatting.YELLOW));
            } else if (this.tier == BladeTier.FOUNDER) {
                tooltip.add(Component.translatable("tooltip.takemtitans.founder").withStyle(ChatFormatting.YELLOW));
                tooltip.add(Component.translatable("tooltip.takemtitans.founder2").withStyle(ChatFormatting.YELLOW));
            } else if (this.tier == BladeTier.SERRATED) {
                tooltip.add(Component.translatable("tooltip.takemtitans.serrated").withStyle(ChatFormatting.YELLOW));
            }
        }

        if (isBroken(stack)) {
            tooltip.add(Component.translatable("tooltip.takemtitans.blades_broken").withStyle(ChatFormatting.RED));
        } else {
            int left = stack.getMaxDamage() - stack.getDamageValue();
            tooltip.add(Component.translatable("tooltip.takemtitans.blades_sharpness", left, stack.getMaxDamage()).withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.translatable("tooltip.takemtitans.blades_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
