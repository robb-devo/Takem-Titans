package com.takem.titans.item;

import com.takem.titans.entity.CannonEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CannonItem extends Item {
    public final int tier;

    public CannonItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        if (!level.isClientSide) {
            float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0.0F;
            CannonEntity c = CannonEntity.create(level, Vec3.atBottomCenterOf(pos), yaw, this.tier, false);
            if (!level.noCollision(c, c.getBoundingBox())) {
                return InteractionResult.FAIL;
            }

            level.addFreshEntity(c);
            level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.7F, 0.7F);
            ctx.getItemInHand().consume(1, ctx.getPlayer());
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.takemtitans.cannon_" + this.tier).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.takemtitans.cannon_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
