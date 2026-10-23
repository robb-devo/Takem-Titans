package com.takem.titans.item;

import com.takem.titans.entity.ScoutHorseEntity;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.odm.OdmServer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Calls the player's horse. The first blow brings a new one from the stables; if it falls, another comes after a while. */
public class HorseWhistleItem extends Item {
    public HorseWhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.getCooldowns().addCooldown(this, 40);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.6F, 1.9F);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.2F, 1.5F);
        if (player instanceof ServerPlayer sp) {
            call(sp);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static void call(ServerPlayer player) {
        OdmPlayerData d = OdmServer.data(player);
        ScoutHorseEntity horse = find(player, d);
        if (horse != null) {
            horse.callTo(player);
            player.displayClientMessage(Component.translatable("message.takemtitans.horse_coming"), true);
            return;
        }

        long now = player.level().getGameTime();
        if (now < d.horseReadyAt) {
            int seconds = (int) ((d.horseReadyAt - now) / 20L) + 1;
            player.displayClientMessage(Component.translatable("message.takemtitans.horse_wait", seconds).withStyle(ChatFormatting.GRAY), true);
            return;
        }

        ScoutHorseEntity fresh = ScoutHorseEntity.fromStables(player);
        if (fresh != null) {
            fresh.callTo(player);
            player.displayClientMessage(Component.translatable("message.takemtitans.horse_new"), true);
        }
    }

    private static ScoutHorseEntity find(ServerPlayer player, OdmPlayerData d) {
        if (d.horse == null) {
            return null;
        }

        for (ServerLevel level : player.server.getAllLevels()) {
            Entity e = level.getEntity(d.horse);
            if (e instanceof ScoutHorseEntity h && h.isAlive()) {
                return h;
            }
        }

        // Unloaded far away: the stables send it back (the old one stays wherever it was left)
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.takemtitans.horse_whistle").withStyle(ChatFormatting.GRAY));
    }
}
