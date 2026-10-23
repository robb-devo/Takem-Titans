package com.takem.titans.story;

import com.takem.titans.entity.npc.TownsfolkEntity;
import com.takem.titans.network.ShopBuyPayload;
import com.takem.titans.network.StoryPayload;
import com.takem.titans.odm.OdmGear;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModSounds;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class Shop {
    public static final String[] CATEGORIES = new String[]{"supplies", "explosives", "arms", "garrison", "legendary"};
    /** category, price in badges, story stage that unlocks it, item */
    public static final List<Shop.Offer> OFFERS = List.of(
        new Shop.Offer(0, 2, 0, () -> new ItemStack(ModItems.GAS_CANISTER.get(), 4)),
        new Shop.Offer(0, 2, 0, () -> new ItemStack(ModItems.BLADE_PACK.get(), 2)),
        new Shop.Offer(0, 3, 0, () -> new ItemStack(ModItems.FLARE_RED.get(), 4)),
        new Shop.Offer(0, 3, 0, () -> new ItemStack(ModItems.FLARE_GREEN.get(), 4)),
        new Shop.Offer(0, 3, 0, () -> new ItemStack(ModItems.FLARE_BLACK.get(), 4)),
        new Shop.Offer(0, 3, 0, () -> new ItemStack(ModItems.FLARE_YELLOW.get(), 4)),
        new Shop.Offer(0, 4, 3, () -> new ItemStack(ModItems.FLARE_PURPLE.get(), 2)),
        new Shop.Offer(0, 1, 0, () -> new ItemStack(Items.BREAD, 8)),
        new Shop.Offer(0, 4, 0, () -> new ItemStack(ModItems.MED_KIT.get(), 2)),
        new Shop.Offer(0, 6, 3, () -> new ItemStack(ModItems.WHETSTONE.get())),
        new Shop.Offer(0, 10, 3, () -> new ItemStack(ModItems.HORSE_WHISTLE.get())),
        new Shop.Offer(0, 8, 3, () -> new ItemStack(ModItems.SCOUT_BARDING.get())),
        new Shop.Offer(0, 12, 14, () -> new ItemStack(ModItems.SQUAD_HORN.get())),
        new Shop.Offer(1, 5, 8, () -> new ItemStack(ModItems.THUNDER_SPEAR.get(), 2)),
        new Shop.Offer(1, 4, 3, () -> new ItemStack(ModItems.SMOKE_BOMB.get(), 3)),
        new Shop.Offer(1, 16, 6, () -> new ItemStack(ModItems.NET_LAUNCHER.get())),
        new Shop.Offer(1, 15, 6, () -> new ItemStack(ModItems.ADRENALINE.get())),
        new Shop.Offer(1, 9, 16, () -> new ItemStack(ModItems.CLUSTER_SPEAR.get(), 1)),
        new Shop.Offer(1, 8, 15, () -> new ItemStack(ModItems.THERMOBARIC_CANISTER.get(), 1)),
        new Shop.Offer(1, 6, 18, () -> new ItemStack(ModItems.DEMOLITION_CHARGE.get(), 2)),
        new Shop.Offer(1, 20, 12, () -> new ItemStack(ModItems.BEAST_BOULDER.get(), 2)),
        new Shop.Offer(1, 20, 21, () -> new ItemStack(ModItems.PATHS_SHARD.get(), 1)),
        new Shop.Offer(1, 24, 13, () -> new ItemStack(ModItems.COLOSSAL_SERUM.get(), 1)),
        new Shop.Offer(2, 6, 0, () -> new ItemStack(ModItems.SCOUT_CLOAK.get())),
        // the gear ladder: nothing is handed out, every step up is bought once the story reaches it
        new Shop.Offer(2, 20, 3, () -> new ItemStack(ModItems.VETERAN_BLADES.get())),
        new Shop.Offer(2, 35, 6, () -> OdmGear.createFullGear(ModItems.ADVANCED_ODM_GEAR.get())),
        new Shop.Offer(2, 10, 7, () -> new ItemStack(ModItems.RECON_HOOD.get())),
        new Shop.Offer(2, 14, 7, () -> new ItemStack(ModItems.RECON_JACKET.get())),
        new Shop.Offer(2, 10, 7, () -> new ItemStack(ModItems.RECON_BOOTS.get())),
        new Shop.Offer(2, 45, 9, () -> new ItemStack(ModItems.SERRATED_BLADES.get())),
        new Shop.Offer(2, 60, 11, () -> new ItemStack(ModItems.ACKERMAN_BLADES.get())),
        new Shop.Offer(2, 16, 13, () -> new ItemStack(ModItems.VANGUARD_HELMET.get())),
        new Shop.Offer(2, 24, 13, () -> new ItemStack(ModItems.VANGUARD_COAT.get())),
        new Shop.Offer(2, 18, 13, () -> new ItemStack(ModItems.VANGUARD_BOOTS.get())),
        new Shop.Offer(3, 30, 7, () -> new ItemStack(ModItems.FIELD_CANNON.get())),
        new Shop.Offer(3, 70, 19, () -> new ItemStack(ModItems.SIEGE_CANNON.get())),
        new Shop.Offer(3, 140, 24, () -> new ItemStack(ModItems.THUNDER_BATTERY.get())),
        new Shop.Offer(3, 8, 0, () -> new ItemStack(ModItems.ULTRAHARD_STEEL_INGOT.get(), 4)),
        new Shop.Offer(3, 6, 3, () -> new ItemStack(ModItems.SUPPLY_STATION.get())),
        new Shop.Offer(4, 120, 18, () -> OdmGear.createFullGear(ModItems.GODSPEED_ODM_GEAR.get())),
        new Shop.Offer(4, 150, 22, () -> new ItemStack(ModItems.TITANBREAKER_BLADES.get())),
        new Shop.Offer(4, 50, 27, () -> new ItemStack(ModItems.HARDENED_HELM.get())),
        new Shop.Offer(4, 70, 27, () -> new ItemStack(ModItems.HARDENED_PLATE.get())),
        new Shop.Offer(4, 55, 27, () -> new ItemStack(ModItems.HARDENED_BOOTS.get())),
        new Shop.Offer(4, 350, 29, () -> new ItemStack(ModItems.FOUNDER_BLADES.get())),
        new Shop.Offer(4, 56, 21, () -> new ItemStack(ModItems.RUMBLING_HORN.get())),
        new Shop.Offer(4, 45, 31, () -> new ItemStack(ModItems.TITAN_HEART_SHARD.get(), 2))
    );

    private Shop() {
    }

    public static void addBadges(ServerPlayer player, int n) {
        if (n > 0) {
            OdmPlayerData d = OdmServer.data(player);
            d.wallet += n;
            CompoundTag tag = new CompoundTag();
            tag.putString("t", "wallet");
            tag.putInt("wallet", d.wallet);
            tag.putInt("gain", n);
            PacketDistributor.sendToPlayer(player, new StoryPayload(tag));
        }
    }

    static void sendWallet(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putString("t", "wallet");
        tag.putInt("wallet", OdmServer.data(player).wallet);
        tag.putInt("gain", 0);
        PacketDistributor.sendToPlayer(player, new StoryPayload(tag));
    }

    public static boolean spend(ServerPlayer player, int n) {
        OdmPlayerData d = OdmServer.data(player);
        if (d.wallet < n) {
            return false;
        } else {
            d.wallet -= n;
            sendWallet(player);
            return true;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 10 == 3) {
            int found = 0;

            for (ItemStack st : player.getInventory().items) {
                if (st.is(ModItems.MERIT_BADGE.get())) {
                    found += st.getCount();
                    st.setCount(0);
                }
            }

            if (found > 0) {
                addBadges(player, found);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
            }

            if (player.tickCount % 200 == 3) {
                sendWallet(player);
            }
        }
    }

    public static void open(ServerPlayer player, TownsfolkEntity npc) {
        CompoundTag tag = new CompoundTag();
        tag.putString("t", "shop");
        tag.putInt("npc", npc.getId());
        tag.putString("name", Story.json(player, npc.getDisplayName()));
        tag.putInt("wallet", OdmServer.data(player).wallet);
        PacketDistributor.sendToPlayer(player, new StoryPayload(tag));
        player.level().playSound(null, npc.getX(), npc.getY() + 1.0, npc.getZ(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.NEUTRAL, 0.8F, 0.9F);
    }

    public static void handleBuy(ShopBuyPayload msg, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player
            && player.level().getEntity(msg.npc()) instanceof TownsfolkEntity npc
            && npc.getRole() == 3
            && npc.distanceToSqr(player) < 144.0
            && msg.offer() >= 0
            && msg.offer() < OFFERS.size()) {
            Shop.Offer o = OFFERS.get(msg.offer());
            if (OdmServer.data(player).stage < o.unlock) {
                player.displayClientMessage(
                    Component.translatable("shop.takemtitans.locked", Component.translatable(Story.chapterKey(o.unlock))).withStyle(ChatFormatting.RED), true
                );
                return;
            }

            int count = Math.max(1, Math.min(10, msg.count()));
            int total = o.price * count;
            if (!spend(player, total)) {
                player.displayClientMessage(Component.translatable("shop.takemtitans.too_poor", total).withStyle(ChatFormatting.RED), true);
            } else {
                for (int i = 0; i < count; i++) {
                    Story.give(player, o.item.get());
                }

                player.level().playSound(null, npc.getX(), npc.getY() + 1.0, npc.getZ(), ModSounds.REFILL.get(), SoundSource.NEUTRAL, 0.9F, 1.1F);
                player.level()
                    .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 1.4F);
                npc.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    public static record Offer(int category, int price, int unlock, Supplier<ItemStack> item) {
    }
}
