package com.takem.titans.story;

import com.takem.titans.TakemConfig;
import com.takem.titans.entity.npc.JaegerEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.network.StoryPayload;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.skill.Skill;
import com.takem.titans.skill.Skills;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class Squad {
    public static final int RECRUIT = 10;
    public static final int DISMISS = 11;
    public static final int HOLD = 12;
    public static final int FOLLOW = 13;

    private Squad() {
    }

    static String name(String entry) {
        int i = entry.indexOf(124);
        return i < 0 ? entry : entry.substring(0, i);
    }

    static int variant(String entry) {
        int i = entry.indexOf(124);

        try {
            return i < 0 ? 0 : Integer.parseInt(entry.substring(i + 1));
        } catch (NumberFormatException var3) {
            return 0;
        }
    }

    public static int capacity(ServerPlayer p) {
        return Skills.squadSize(p);
    }

    @SubscribeEvent
    public static void onPlayerTick(Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.isSpectator() && player.isAlive()) {
            ServerLevel level = player.serverLevel();
            long t = level.getGameTime();
            if (t % 40L == 13L) {
                ensure(level, player);
            }

            if (t % 300L == 101L) {
                patrols(level, player);
            }

            if (t % 20L == 7L) {
                sendHud(player);
            }
        }
    }

    private static List<JaegerEntity> mine(ServerLevel level, ServerPlayer player, double range) {
        UUID id = player.getUUID();
        return level.getEntitiesOfClass(JaegerEntity.class, player.getBoundingBox().inflate(range, 96.0, range), j -> id.equals(j.owner()));
    }

    static void ensure(ServerLevel level, ServerPlayer player) {
        OdmPlayerData d = OdmServer.data(player);
        int cap = capacity(player);

        while (d.squad.size() > cap) {
            d.squad.remove(d.squad.size() - 1);
        }

        List<JaegerEntity> present = mine(level, player, 160.0);
        boolean escort = Story.escortActive(d);

        for (JaegerEntity j : present) {
            if (j.isTemporary() ? !escort : !d.squad.contains(j.jaegerName() + "|" + j.getVariant())) {
                leave(level, j);
            }
        }

        if (!Story.inCutscene(player)) {
            for (int i = 0; i < d.squad.size(); i++) {
                String entry = d.squad.get(i);
                String nm = name(entry);
                boolean found = false;

                for (JaegerEntity jx : present) {
                    if (!jx.isTemporary() && jx.isAlive() && jx.jaegerName().equals(nm)) {
                        if (found) {
                            jx.discard();
                        }

                        found = true;
                    }
                }

                int lvl = stats(d, nm)[0];

                for (JaegerEntity jxx : present) {
                    if (!jxx.isTemporary() && jxx.isAlive() && jxx.jaegerName().equals(nm) && jxx.jaegerLevel() != lvl) {
                        jxx.setJaegerLevel(lvl);
                    }
                }

                if (!found) {
                    JaegerEntity spawned = spawnNear(level, player, nm, variant(entry), player.getUUID(), false, i);
                    if (spawned != null) {
                        spawned.setJaegerLevel(lvl);
                        spawned.setHealth(spawned.getMaxHealth());
                    }
                }
            }

            if (escort) {
                int temps = 0;

                for (JaegerEntity jxxx : present) {
                    if (jxxx.isTemporary() && jxxx.isAlive()) {
                        temps++;
                    }
                }

                for (int i = temps; i < 2; i++) {
                    RandomSource r = level.random;
                    spawnNear(level, player, JaegerEntity.NAMES[r.nextInt(JaegerEntity.NAMES.length)], r.nextInt(4), player.getUUID(), true, 4 + i);
                }
            }
        }
    }

    @Nullable
    static JaegerEntity spawnNear(ServerLevel level, ServerPlayer player, String name, int variant, @Nullable UUID owner, boolean temporary, int slot) {
        RandomSource r = level.random;

        for (int attempt = 0; attempt < 10; attempt++) {
            double a = r.nextDouble() * 3.141592653589793 * 2.0;
            double dist = 6.0 + r.nextDouble() * 6.0;
            int x = Mth.floor(player.getX() + Math.cos(a) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(a) * dist);
            if (level.hasChunk(x >> 4, z >> 4)) {
                int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs((double)y - player.getY()) < 24.0 || attempt > 6) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).isAir() && level.getFluidState(pos.below()).isEmpty()) {
                        JaegerEntity j = ModEntities.JAEGER.get().create(level);
                        if (j == null) {
                            return null;
                        }

                        j.moveTo((double)x + 0.5, (double)y, (double)z + 0.5, r.nextFloat() * 360.0F, 0.0F);
                        j.setup(name, variant, owner, temporary, slot);
                        level.addFreshEntity(j);
                        level.sendParticles(ModParticles.GAS.get(), j.getX(), j.getY() + 1.0, j.getZ(), 30, 0.4, 0.6, 0.4, 0.08);
                        level.playSound(null, j.getX(), j.getY(), j.getZ(), ModSounds.GAS_BURST.get(), SoundSource.NEUTRAL, 1.2F, 1.0F);
                        return j;
                    }
                }
            }
        }

        return null;
    }

    static void leave(ServerLevel level, JaegerEntity j) {
        level.sendParticles(ModParticles.GAS.get(), j.getX(), j.getY() + 1.0, j.getZ(), 30, 0.4, 0.6, 0.4, 0.08);
        level.playSound(null, j.getX(), j.getY(), j.getZ(), ModSounds.GAS_BURST.get(), SoundSource.NEUTRAL, 1.2F, 1.0F);
        j.discard();
    }

    public static void arrive(ServerLevel level, JaegerEntity j, ServerPlayer leader) {
        Vec3 back = leader.position().subtract(leader.getLookAngle().multiply(1.0, 0.0, 1.0).scale(5.0));
        int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(back.x), Mth.floor(back.z));
        j.teleportTo(back.x, Math.max((double)y, leader.getY()), back.z);
        level.sendParticles(ModParticles.GAS.get(), j.getX(), j.getY() + 1.0, j.getZ(), 30, 0.4, 0.6, 0.4, 0.08);
    }

    static void patrols(ServerLevel level, ServerPlayer player) {
        if (TakemConfig.JAEGER_PATROLS.get() && level.isDay() && level.dimension() == Level.OVERWORLD) {
            List<AbstractTitan> titans = level.getEntitiesOfClass(
                AbstractTitan.class, player.getBoundingBox().inflate(110.0, 60.0, 110.0), t -> t.isAlive() && !t.isDummy()
            );
            if (titans.isEmpty()) {
                garrison(level, player);
            } else {
                int lone = level.getEntitiesOfClass(JaegerEntity.class, player.getBoundingBox().inflate(140.0, 80.0, 140.0), jx -> jx.owner() == null).size();
                int cap = TakemConfig.JAEGER_CAP.get();
                if (lone < cap && level.random.nextInt(3) != 0) {
                    RandomSource r = level.random;
                    AbstractTitan t = titans.get(r.nextInt(titans.size()));
                    int n = Math.min(cap - lone, 1 + r.nextInt(3));

                    for (int i = 0; i < n; i++) {
                        double a = r.nextDouble() * 3.141592653589793 * 2.0;
                        double dist = 28.0 + r.nextDouble() * 24.0;
                        int x = Mth.floor(t.getX() + Math.cos(a) * dist);
                        int z = Mth.floor(t.getZ() + Math.sin(a) * dist);
                        if (level.hasChunk(x >> 4, z >> 4) && player.distanceToSqr((double)x, player.getY(), (double)z) > 400.0) {
                            int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                            BlockPos pos = new BlockPos(x, y, z);
                            if (level.getBlockState(pos).isAir() && level.getFluidState(pos.below()).isEmpty()) {
                                JaegerEntity j = ModEntities.JAEGER.get().create(level);
                                if (j != null) {
                                    j.moveTo((double)x + 0.5, (double)y, (double)z + 0.5, r.nextFloat() * 360.0F, 0.0F);
                                    j.setup(JaegerEntity.NAMES[r.nextInt(JaegerEntity.NAMES.length)], r.nextInt(4), null, false, i);
                                    level.addFreshEntity(j);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void garrison(ServerLevel level, ServerPlayer player) {
        if (StoryWorld.isTown(player.getX(), player.getZ())) {
            int lone = level.getEntitiesOfClass(JaegerEntity.class, player.getBoundingBox().inflate(96.0, 60.0, 96.0), jx -> jx.owner() == null).size();
            RandomSource r = level.random;
            if (lone < Math.min(2, TakemConfig.JAEGER_CAP.get()) && r.nextInt(2) == 0) {
                for (int attempt = 0; attempt < 8; attempt++) {
                    double a = r.nextDouble() * 3.141592653589793 * 2.0;
                    double dist = 22.0 + r.nextDouble() * 26.0;
                    int x = Mth.floor(player.getX() + Math.cos(a) * dist);
                    int z = Mth.floor(player.getZ() + Math.sin(a) * dist);
                    if (level.hasChunk(x >> 4, z >> 4) && StoryWorld.isTown((double)x + 0.5, (double)z + 0.5)) {
                        int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                        BlockPos pos = new BlockPos(x, y, z);
                        if (y <= 68 && level.getBlockState(pos).isAir() && level.getFluidState(pos.below()).isEmpty()) {
                            JaegerEntity j = ModEntities.JAEGER.get().create(level);
                            if (j != null) {
                                j.moveTo((double)x + 0.5, (double)y, (double)z + 0.5, r.nextFloat() * 360.0F, 0.0F);
                                j.setup(JaegerEntity.NAMES[r.nextInt(JaegerEntity.NAMES.length)], r.nextInt(4), null, false, 0);
                                level.addFreshEntity(j);
                            }

                            return;
                        }
                    }
                }
            }
        }
    }

    static int[] stats(OdmPlayerData d, String name) {
        return d.squadStats.computeIfAbsent(name, k -> new int[]{1, 0});
    }

    static int need(int level) {
        return 3 + level * 2;
    }

    public static void onKill(ServerPlayer leader, JaegerEntity j, AbstractTitan titan) {
        OdmPlayerData d = OdmServer.data(leader);
        if (!j.isTemporary() && d.squad.contains(j.jaegerName() + "|" + j.getVariant())) {
            int[] s = stats(d, j.jaegerName());
            s[1] += (1 + (int)(titan.getTitanHeight() / 6.0F)) * (Skills.has(leader, Skill.LEGION) ? 2 : 1);

            while (s[0] < 10 && s[1] >= need(s[0])) {
                s[1] -= need(s[0]);
                s[0]++;
                j.setJaegerLevel(s[0]);
                j.setHealth(j.getMaxHealth());
                leader.sendSystemMessage(Component.translatable("message.takemtitans.jaeger_level", j.jaegerName(), s[0]).withStyle(ChatFormatting.AQUA));
                ServerLevel level = leader.serverLevel();
                level.playSound(null, j.getX(), j.getY() + 1.0, j.getZ(), ModSounds.LEVEL_UP.get(), SoundSource.PLAYERS, 1.0F, 1.2F);
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, j.getX(), j.getY() + 1.0, j.getZ(), 30, 0.4, 0.8, 0.4, 0.3);
            }
        }
    }

    static int hireCost(OdmPlayerData d) {
        return 20 + 15 * d.squad.size();
    }

    public static void hire(ServerPlayer player) {
        OdmPlayerData d = OdmServer.data(player);
        int cap = capacity(player);
        if (d.squad.size() >= cap) {
            player.displayClientMessage(Component.translatable("dialogue.takemtitans.jaeger.full", cap).withStyle(ChatFormatting.RED), true);
        } else if (!Shop.spend(player, hireCost(d))) {
            player.displayClientMessage(Component.translatable("shop.takemtitans.too_poor", hireCost(d)).withStyle(ChatFormatting.RED), true);
        } else {
            RandomSource r = player.getRandom();
            String name = JaegerEntity.NAMES[r.nextInt(JaegerEntity.NAMES.length)];

            for (int i = 0; i < 40; i++) {
                String cand = JaegerEntity.NAMES[r.nextInt(JaegerEntity.NAMES.length)];
                if (d.squad.stream().noneMatch(e -> name(e).equals(cand))) {
                    name = cand;
                    break;
                }
            }

            int variant = r.nextInt(4);
            d.squad.add(name + "|" + variant);
            d.squadStats.put(name, new int[]{1, 0});
            JaegerEntity j = spawnNear(player.serverLevel(), player, name, variant, player.getUUID(), false, d.squad.size() - 1);
            if (j != null) {
                j.setJaegerLevel(1);
                j.say("join");
            }

            player.displayClientMessage(Component.translatable("message.takemtitans.jaeger_joined", name), true);
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.QUEST_COMPLETE.get(), SoundSource.PLAYERS, 0.8F, 1.2F);
            Story.sync(player);
        }
    }

    static void sendHud(ServerPlayer player) {
        OdmPlayerData d = OdmServer.data(player);
        List<JaegerEntity> present = mine(player.serverLevel(), player, 200.0);
        CompoundTag tag = new CompoundTag();
        tag.putString("t", "squad");
        ListTag list = new ListTag();

        for (String entry : d.squad) {
            String nm = name(entry);
            int[] s = stats(d, nm);
            CompoundTag m = new CompoundTag();
            m.putString("n", nm);
            m.putInt("l", s[0]);
            m.putFloat("x", s[0] >= 10 ? 1.0F : (float)s[1] / (float)need(s[0]));
            float hp = -1.0F;

            for (JaegerEntity j : present) {
                if (!j.isTemporary() && j.jaegerName().equals(nm)) {
                    hp = j.getHealth() / Math.max(1.0F, j.getMaxHealth());
                }
            }

            m.putFloat("h", hp);
            list.add(m);
        }

        tag.put("m", list);
        tag.putBoolean("hold", d.squadHold);
        PacketDistributor.sendToPlayer(player, new StoryPayload(tag));
    }

    public static void onDeath(ServerLevel level, JaegerEntity j) {
        if (j.owner() != null && level.getPlayerByUUID(j.owner()) instanceof ServerPlayer p) {
            OdmPlayerData d = OdmServer.data(p);
            if (!j.isTemporary() && d.squad.remove(j.jaegerName() + "|" + j.getVariant())) {
                d.squadStats.remove(j.jaegerName());
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
                p.sendSystemMessage(Component.translatable("message.takemtitans.jaeger_fallen", j.jaegerName()).withStyle(ChatFormatting.RED));
                Story.sync(p);
            }
        }
    }

    public static void talk(ServerPlayer player, JaegerEntity j) {
        OdmPlayerData d = OdmServer.data(player);
        RandomSource r = player.getRandom();
        List<Component> lines = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        List<Component> labels = new ArrayList<>();
        boolean mine = player.getUUID().equals(j.owner());
        if (mine) {
            lines.add(Component.translatable("dialogue.takemtitans.jaeger.squad." + r.nextInt(5), player.getName()));
            if (!j.isTemporary()) {
                int lvl = stats(d, j.jaegerName())[0];
                if (lvl < 10) {
                    ids.add(14);
                    labels.add(Component.translatable("dialogue.takemtitans.jaeger.train", lvl + 1, 10 * lvl));
                }

                ids.add(d.squadHold ? 13 : 12);
                labels.add(Component.translatable(d.squadHold ? "dialogue.takemtitans.jaeger.follow" : "dialogue.takemtitans.jaeger.hold"));
                ids.add(11);
                labels.add(Component.translatable("dialogue.takemtitans.jaeger.dismiss"));
            }
        } else if (j.owner() == null) {
            lines.add(Component.translatable("dialogue.takemtitans.jaeger.idle." + r.nextInt(6), player.getName()));
            int cap = capacity(player);
            if (cap <= 0) {
                lines.add(Component.translatable("dialogue.takemtitans.jaeger.no_skill"));
            } else if (d.squad.size() >= cap) {
                lines.add(Component.translatable("dialogue.takemtitans.jaeger.full", cap));
            } else {
                ids.add(10);
                labels.add(Component.translatable("dialogue.takemtitans.jaeger.recruit"));
            }
        } else {
            lines.add(Component.translatable("dialogue.takemtitans.jaeger.taken"));
        }

        ids.add(0);
        labels.add(Component.translatable("dialogue.takemtitans.option.leave"));
        CompoundTag tag = new CompoundTag();
        tag.putString("t", "dialogue");
        tag.putInt("npc", j.getId());
        tag.putString("speaker", Story.json(player, Component.literal(j.jaegerName())));
        tag.putInt("role", 1);
        ListTag ls = new ListTag();

        for (Component c : lines) {
            ls.add(StringTag.valueOf(Story.json(player, c)));
        }

        tag.put("lines", ls);
        ListTag os = new ListTag();

        for (int i = 0; i < ids.size(); i++) {
            CompoundTag o = new CompoundTag();
            o.putInt("id", ids.get(i));
            o.putString("label", Story.json(player, labels.get(i)));
            os.add(o);
        }

        tag.put("opts", os);
        PacketDistributor.sendToPlayer(player, new StoryPayload(tag));
    }

    public static void handle(ServerPlayer player, JaegerEntity j, int option) {
        OdmPlayerData d = OdmServer.data(player);
        ServerLevel level = player.serverLevel();
        switch (option) {
            case 10:
                if (j.owner() == null && d.squad.size() < capacity(player)) {
                    String entry = j.jaegerName() + "|" + j.getVariant();
                    if (!d.squad.contains(entry)) {
                        d.squad.add(entry);
                    }

                    j.setOwner(player.getUUID(), d.squad.size() - 1);
                    j.say("join");
                    player.displayClientMessage(Component.translatable("message.takemtitans.jaeger_joined", j.jaegerName()), true);
                    level.playSound(null, j.getX(), j.getY() + 1.0, j.getZ(), ModSounds.QUEST_COMPLETE.get(), SoundSource.PLAYERS, 0.6F, 1.3F);
                    Story.sync(player);
                }
                break;
            case 11:
                if (player.getUUID().equals(j.owner())) {
                    d.squad.remove(j.jaegerName() + "|" + j.getVariant());
                    j.setOwner(null, 0);
                    player.displayClientMessage(Component.translatable("message.takemtitans.jaeger_left", j.jaegerName()), true);
                    Story.sync(player);
                }
                break;
            case 12:
            case 13:
                d.squadHold = option == 12;
                player.displayClientMessage(Component.translatable(d.squadHold ? "message.takemtitans.squad_hold" : "message.takemtitans.squad_follow"), true);

                for (JaegerEntity m : mine(level, player, 128.0)) {
                    m.say(d.squadHold ? "hold" : "follow");
                }
                break;
            case 14:
                if (player.getUUID().equals(j.owner())) {
                    int[] s = stats(d, j.jaegerName());
                    if (s[0] < 10 && Shop.spend(player, 10 * s[0])) {
                        s[0]++;
                        s[1] = 0;
                        j.setJaegerLevel(s[0]);
                        j.setHealth(j.getMaxHealth());
                        player.sendSystemMessage(
                            Component.translatable("message.takemtitans.jaeger_level", j.jaegerName(), s[0]).withStyle(ChatFormatting.AQUA)
                        );
                        level.playSound(null, j.getX(), j.getY() + 1.0, j.getZ(), ModSounds.LEVEL_UP.get(), SoundSource.PLAYERS, 1.0F, 1.2F);
                    } else if (s[0] < 10) {
                        player.displayClientMessage(Component.translatable("shop.takemtitans.too_poor", 10 * s[0]).withStyle(ChatFormatting.RED), true);
                    }
                }
        }
    }

    static int squadCount(ServerLevel level, ServerPlayer player) {
        return mine(level, player, 128.0).size();
    }

    static AABB around(ServerPlayer p, double r) {
        return p.getBoundingBox().inflate(r);
    }
}
