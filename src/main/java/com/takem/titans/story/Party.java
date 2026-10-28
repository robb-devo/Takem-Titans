package com.takem.titans.story;

import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.odm.OdmServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Playing the story together. One player leads a party; the others join it with {@code /party accept} after an
 * {@code /party invite}. The leader's story is the party's story: whatever a member does that would advance it -
 * kills, objectives reached - advances the leader's, every member is kept on the leader's chapter, and the rewards of
 * a finished objective go to everyone online. Each still earns their own badges and XP for what they kill.
 */
@EventBusSubscriber(modid = "takemtitans")
public final class Party {
    private static final Map<UUID, UUID> INVITES = new HashMap<>();

    private Party() {
    }

    static final class Data extends SavedData {
        /** member -> leader; leaders are not in here. */
        final Map<UUID, UUID> leaderOf = new HashMap<>();

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Data::new, Data::load, null), "takemtitans_parties");
        }

        private static Data load(CompoundTag tag, Provider registries) {
            Data d = new Data();
            for (String k : tag.getAllKeys()) {
                d.leaderOf.put(UUID.fromString(k), tag.getUUID(k));
            }

            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, Provider registries) {
            this.leaderOf.forEach((m, l) -> tag.putUUID(m.toString(), l));
            return tag;
        }
    }

    public static boolean isMember(ServerPlayer p) {
        return Data.get(p.server).leaderOf.containsKey(p.getUUID());
    }

    /** Whose story counts for this player: their party leader's (null while the leader is offline), or their own. */
    @Nullable
    public static ServerPlayer lead(ServerPlayer p) {
        UUID l = Data.get(p.server).leaderOf.get(p.getUUID());
        return l == null ? p : p.server.getPlayerList().getPlayer(l);
    }

    /** The members of this player's party who are online, not counting the leader. */
    public static List<ServerPlayer> members(ServerPlayer leader) {
        List<ServerPlayer> out = new ArrayList<>();
        Data.get(leader.server).leaderOf.forEach((m, l) -> {
            if (l.equals(leader.getUUID())) {
                ServerPlayer p = leader.server.getPlayerList().getPlayer(m);
                if (p != null) {
                    out.add(p);
                }
            }
        });
        return out;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 40 != 17) {
            return;
        }

        Data d = Data.get(server);
        d.leaderOf.forEach((m, l) -> {
            ServerPlayer member = server.getPlayerList().getPlayer(m);
            ServerPlayer leader = server.getPlayerList().getPlayer(l);
            if (member == null || leader == null) {
                return;
            }

            OdmPlayerData md = OdmServer.data(member);
            OdmPlayerData ld = OdmServer.data(leader);
            boolean changed = md.stage != ld.stage || md.stageCount != ld.stageCount;
            md.stage = ld.stage;
            md.stageCount = ld.stageCount;
            // the waystones either of them has woken are open to both
            changed |= md.waystones.addAll(ld.waystones);
            ld.waystones.addAll(md.waystones);
            if (changed) {
                Story.sync(member);
            }
        });
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("party")
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer me = c.getSource().getPlayerOrException();
                    ServerPlayer other = EntityArgument.getPlayer(c, "player");
                    if (other == me || isMember(me)) {
                        me.sendSystemMessage(Component.translatable("party.takemtitans.cannot").withStyle(ChatFormatting.RED));
                        return 0;
                    }

                    INVITES.put(other.getUUID(), me.getUUID());
                    me.sendSystemMessage(Component.translatable("party.takemtitans.invited", other.getDisplayName()).withStyle(ChatFormatting.GOLD));
                    other.sendSystemMessage(Component.translatable("party.takemtitans.invite", me.getDisplayName()).withStyle(ChatFormatting.GOLD));
                    return 1;
                })))
                .then(Commands.literal("accept").executes(c -> {
                    ServerPlayer me = c.getSource().getPlayerOrException();
                    UUID leader = INVITES.remove(me.getUUID());
                    Data d = Data.get(me.server);
                    if (leader == null || d.leaderOf.containsKey(leader)) {
                        me.sendSystemMessage(Component.translatable("party.takemtitans.no_invite").withStyle(ChatFormatting.RED));
                        return 0;
                    }

                    // a leader who joins someone else hands their own party over
                    d.leaderOf.replaceAll((m, l) -> l.equals(me.getUUID()) ? leader : l);
                    d.leaderOf.put(me.getUUID(), leader);
                    d.setDirty();
                    ServerPlayer l = me.server.getPlayerList().getPlayer(leader);
                    Component name = l != null ? l.getDisplayName() : Component.literal("?");
                    me.sendSystemMessage(Component.translatable("party.takemtitans.joined", name).withStyle(ChatFormatting.GREEN));
                    if (l != null) {
                        l.sendSystemMessage(Component.translatable("party.takemtitans.member_joined", me.getDisplayName()).withStyle(ChatFormatting.GREEN));
                    }

                    return 1;
                }))
                .then(Commands.literal("leave").executes(c -> {
                    ServerPlayer me = c.getSource().getPlayerOrException();
                    Data d = Data.get(me.server);
                    boolean any = d.leaderOf.remove(me.getUUID()) != null;
                    // a leader leaving breaks the party up
                    any |= d.leaderOf.values().removeIf(l -> l.equals(me.getUUID()));
                    d.setDirty();
                    me.sendSystemMessage(Component.translatable(any ? "party.takemtitans.left" : "party.takemtitans.none").withStyle(ChatFormatting.GRAY));
                    return 1;
                }))
                .then(Commands.literal("list").executes(c -> {
                    ServerPlayer me = c.getSource().getPlayerOrException();
                    ServerPlayer leader = lead(me);
                    List<String> names = new ArrayList<>();
                    if (leader != null) {
                        names.add(leader.getGameProfile().getName() + " *");
                        for (ServerPlayer m : members(leader)) {
                            names.add(m.getGameProfile().getName());
                        }
                    }

                    me.sendSystemMessage(Component.translatable("party.takemtitans.list", String.join(", ", names)).withStyle(ChatFormatting.GOLD));
                    return 1;
                }))
        );
    }
}
