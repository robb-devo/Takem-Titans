package com.takem.titans.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;

public class FlareCartridgeItem extends Item {
    public final FlareColor color;

    public FlareCartridgeItem(FlareColor color, Properties properties) {
        super(properties);
        this.color = color;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.takemtitans.flare." + this.color.id()).withStyle(this.color.chat));
    }
}
