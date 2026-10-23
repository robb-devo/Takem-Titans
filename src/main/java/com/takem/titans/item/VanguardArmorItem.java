package com.takem.titans.item;

import com.takem.titans.odm.OdmGear;
import com.takem.titans.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;

public class VanguardArmorItem extends ArmorItem {
    public VanguardArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    public static boolean fullSet(Player p) {
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack feet = p.getItemBySlot(EquipmentSlot.FEET);
        return head.is(ModItems.VANGUARD_HELMET.get())
            && chest.is(ModItems.VANGUARD_COAT.get())
            && feet.is(ModItems.VANGUARD_BOOTS.get())
            && OdmGear.hasGear(p);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.takemtitans.vanguard").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
    }
}
