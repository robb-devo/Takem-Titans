package com.takem.titans.item;

import com.takem.titans.odm.OdmGear;
import com.takem.titans.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;

public class OdmGearItem extends ArmorItem {
    public final GearTier tier;

    public OdmGearItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        this(material, type, properties, GearTier.STANDARD);
    }

    public OdmGearItem(Holder<ArmorMaterial> material, Type type, Properties properties, GearTier tier) {
        super(
            material,
            type,
            properties.component(ModDataComponents.GAS.get(), (int)Math.round(2400.0 * tier.gas)).component(ModDataComponents.SPARE_BLADES.get(), 8)
        );
        this.tier = tier;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * OdmGear.gasFraction(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float f = OdmGear.gasFraction(stack);
        return f < 0.15F ? 14699322 : Mth.hsvToRgb(0.52F, 0.55F, 0.6F + 0.4F * f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (this.tier == GearTier.ADVANCED) {
            tooltip.add(Component.translatable("tooltip.takemtitans.advanced_gear").withStyle(ChatFormatting.GOLD));
        } else if (this.tier == GearTier.GODSPEED) {
            tooltip.add(Component.translatable("tooltip.takemtitans.godspeed").withStyle(ChatFormatting.AQUA));
        }

        int gas = OdmGear.getGas(stack);
        int pct = Math.round(100.0F * OdmGear.gasFraction(stack));
        tooltip.add(Component.translatable("tooltip.takemtitans.gas", pct).withStyle(pct < 15 ? ChatFormatting.RED : ChatFormatting.AQUA));
        tooltip.add(
            Component.translatable("tooltip.takemtitans.spare_blades", OdmGear.getSpareBlades(stack), OdmGear.maxSpareBlades()).withStyle(ChatFormatting.GRAY)
        );
        if (flag.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.takemtitans.odm_gear.controls1").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.takemtitans.odm_gear.controls2").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.takemtitans.odm_gear.controls3").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.takemtitans.odm_gear.controls4").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.takemtitans.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }

        if (gas <= 0) {
            tooltip.add(Component.translatable("tooltip.takemtitans.empty").withStyle(ChatFormatting.RED));
        }
    }
}
