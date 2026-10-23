package com.takem.titans.item;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The two kits the story hands out beside the Vanguard's: Recon leathers (light, thrifty with gas, soft landings) and
 * the Hardened carapace grown from titan hearts (shrugs off titan blows and bursts into crystal when you're nearly
 * done for). Each piece is decent armour on its own; the set bonus needs all three.
 */
@EventBusSubscriber(modid = "takemtitans")
public class RegimentArmorItem extends ArmorItem {
    private static final String BURST_TAG = "takemtitans_crystal_burst";

    public RegimentArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    private static boolean wears(Player p, Item head, Item chest, Item feet) {
        return p.getItemBySlot(EquipmentSlot.HEAD).is(head) && p.getItemBySlot(EquipmentSlot.CHEST).is(chest) && p.getItemBySlot(EquipmentSlot.FEET).is(feet);
    }

    public static boolean recon(Player p) {
        return wears(p, ModItems.RECON_HOOD.get(), ModItems.RECON_JACKET.get(), ModItems.RECON_BOOTS.get());
    }

    public static boolean hardened(Player p) {
        return wears(p, ModItems.HARDENED_HELM.get(), ModItems.HARDENED_PLATE.get(), ModItems.HARDENED_BOOTS.get());
    }

    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }

        float amount = event.getAmount();
        AbstractTitan titan = event.getSource().getEntity() instanceof AbstractTitan t ? t : null;
        if (titan != null && !titan.isDummy()) {
            // titans past the Walls hit a lot harder than the stragglers near the training yard
            amount *= 1.0F + 0.22F * titan.threat();
        }

        if (recon(p) && event.getSource().is(DamageTypeTags.IS_FALL)) {
            amount *= 0.4F;
        }

        if (hardened(p)) {
            if (titan != null) {
                amount *= 0.6F;
            }

            long now = p.level().getGameTime();
            if (p.getHealth() - amount < p.getMaxHealth() * 0.3F && now >= p.getPersistentData().getLong(BURST_TAG)) {
                p.getPersistentData().putLong(BURST_TAG, now + 1800L);
                amount = Math.min(amount, Math.max(0.0F, p.getHealth() - 2.0F));
                p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 3));
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1));
                ServerLevel level = p.serverLevel();
                level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 60, 0.6, 1.0, 0.6, 0.25);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0F, 0.7F);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.5F, 0.6F);
                p.displayClientMessage(Component.translatable("message.takemtitans.crystal_burst").withStyle(ChatFormatting.AQUA), true);
            }
        }

        event.setAmount(amount);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean crystal = stack.is(ModItems.HARDENED_HELM.get()) || stack.is(ModItems.HARDENED_PLATE.get()) || stack.is(ModItems.HARDENED_BOOTS.get());
        tooltip.add(
            Component.translatable(crystal ? "tooltip.takemtitans.hardened" : "tooltip.takemtitans.recon")
                .withStyle(crystal ? ChatFormatting.AQUA : ChatFormatting.GOLD, ChatFormatting.ITALIC)
        );
    }
}
