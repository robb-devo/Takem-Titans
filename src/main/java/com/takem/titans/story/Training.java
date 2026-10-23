package com.takem.titans.story;

import com.takem.titans.entity.npc.TownsfolkEntity;
import com.takem.titans.item.BladeItem;
import com.takem.titans.odm.OdmGear;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.world.TitanSpawner;
import com.takem.titans.world.TrainingGround;
import com.takem.titans.world.WorldEvents;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Training Corps tutorial. A new recruit arrives in Shiganshina with empty hands, reports to Instructor Brandt at
 * the training yard and works through eight short drills, one ODM skill each, before the story proper begins. Every
 * drill is explained three times over (the instructor in chat, a banner, the quest tracker) with the player's own key
 * bindings, and finishes on an action the player really performs, never on a timer.
 */
public final class Training {
    public static final int DONE = 99;
    public static final int REPORT = 0;
    public static final int HOOK = 1;
    public static final int REEL = 2;
    public static final int GAS = 3;
    public static final int SPIN = 4;
    public static final int CUT = 5;
    public static final int RUSH = 6;
    public static final int RELOAD = 7;
    public static final int SUPPLY = 8;
    public static final int GRADUATE = 9;
    static final String[] IDS = new String[]{"report", "hook", "reel", "gas", "spin", "cut", "rush", "reload", "supply", "graduate"};
    private static final int[] NEED = new int[]{1, 3, 1, 4, 1, 2, 1, 1, 1, 1};
    private static final int DRILLS = 8;
    private static final Set<UUID> GREET = new HashSet<>();
    private static final Set<UUID> CALLED = new HashSet<>();

    private Training() {
    }

    public static boolean active(ServerPlayer player, OdmPlayerData d) {
        return d.training < DONE && TitanSpawner.isShiganshina(player.level());
    }

    /** Login of a recruit who has not finished the drills: the welcome waits until the client is in the world. */
    public static void greet(ServerPlayer player) {
        GREET.add(player.getUUID());
        CALLED.remove(player.getUUID());
    }

    /** Every tick for players inside Shiganshina, so short climbs are not missed between the slower story ticks. */
    static void everyTick(ServerPlayer player, OdmPlayerData d) {
        if (d.training == REEL && player.getY() >= (double)(TrainingGround.GANTRY_Y - 8) && TrainingGround.inYard(player.getX(), player.getZ(), 14.0)) {
            on(player, REEL);
        }
    }

    /** Once a second for players inside Shiganshina. */
    static void tick(ServerLevel level, ServerPlayer player, OdmPlayerData d) {
        if (player.distanceToSqr(TrainingGround.CENTER.x, player.getY(), TrainingGround.CENTER.z) < 12544.0) {
            TrainingGround.upkeep(level);
        }

        if (d.training >= DONE) {
            return;
        }

        if (GREET.remove(player.getUUID())) {
            if (d.training == REPORT) {
                Story.banner(player, "chapter", Component.translatable("training.takemtitans.chapter"), Component.translatable("training.takemtitans.welcome.sub"));
                player.sendSystemMessage(Component.translatable("message.takemtitans.welcome_recruit").withStyle(ChatFormatting.GRAY));
            } else if (d.training < GRADUATE) {
                say(player, "training.takemtitans.say." + IDS[d.training], keys());
            }

            Story.sync(player);
        }

        if ((d.training == REPORT || d.training == GRADUATE)
            && player.distanceToSqr(TrainingGround.INSTRUCTOR) < 196.0
            && CALLED.add(player.getUUID())) {
            say(player, d.training == REPORT ? "training.takemtitans.call" : "training.takemtitans.call_back");
        }
    }

    /** A drill action happened: count it if it is the one the recruit is on. */
    public static void on(ServerPlayer player, int step) {
        OdmPlayerData d = OdmServer.data(player);
        if (d.training != step || !TitanSpawner.isShiganshina(player.level())) {
            return;
        }

        if (++d.trainingCount >= NEED[step]) {
            advance(player, d);
        } else {
            if (step != CUT) {
                player.displayClientMessage(Component.translatable("training.takemtitans.rep", d.trainingCount, NEED[step]).withStyle(ChatFormatting.GREEN), true);
            }

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.2F + 0.15F * (float)d.trainingCount);
            Story.sync(player);
        }
    }

    public static void onDummyCut(ServerPlayer player) {
        on(player, CUT);
    }

    private static void advance(ServerPlayer player, OdmPlayerData d) {
        d.trainingCount = 0;
        d.training++;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        if (d.training >= GRADUATE) {
            d.training = GRADUATE;
            CALLED.remove(player.getUUID());
            Story.banner(player, "stage", Component.translatable("training.takemtitans.drills_done"), Component.translatable("training.takemtitans.drills_done.sub"));
            say(player, "training.takemtitans.say.graduate");
        } else {
            prepare(player, d.training);
            Story.banner(
                player,
                "stage",
                Component.translatable("training.takemtitans.drill", d.training, DRILLS),
                Component.translatable("training.takemtitans.name." + IDS[d.training])
            );
            say(player, "training.takemtitans.say." + IDS[d.training], keys());
        }

        Story.addXp(player, 15);
    }

    /** Sets the stage for a drill so it can always be done: dull blades to swap, half-empty tanks to refill. */
    private static void prepare(ServerPlayer player, int step) {
        ItemStack gear = OdmGear.getGear(player);
        if (step == RELOAD) {
            ItemStack blades = player.getMainHandItem();
            if (blades.getItem() instanceof BladeItem && blades.isDamageableItem() && blades.getDamageValue() < blades.getMaxDamage() / 2) {
                blades.setDamageValue(blades.getMaxDamage() * 2 / 3);
            }

            if (!gear.isEmpty() && OdmGear.getSpareBlades(gear) <= 0) {
                OdmGear.setSpareBlades(gear, 1);
            }
        } else if (step == SUPPLY && !gear.isEmpty()) {
            int max = OdmGear.maxGas(gear);
            if (OdmGear.getGas(gear) > max * 2 / 5) {
                OdmGear.setGas(gear, max * 2 / 5);
            }
        }
    }

    static void talk(ServerPlayer player, OdmPlayerData d, TownsfolkEntity npc, List<Component> lines, List<Integer> ids, List<Component> labels) {
        if (d.training == REPORT) {
            for (int i = 0; i < 4; i++) {
                lines.add(Component.translatable("dialogue.takemtitans.training.report." + i, player.getName()));
            }

            ids.add(10);
            labels.add(Component.translatable("dialogue.takemtitans.training.report.accept"));
            ids.add(11);
            labels.add(Component.translatable("dialogue.takemtitans.training.skip"));
        } else if (d.training < GRADUATE) {
            if (!OdmGear.hasGear(player)) {
                WorldEvents.giveStarterGear(player);
                lines.add(Component.translatable("dialogue.takemtitans.training.reissue"));
            }

            lines.add(Component.translatable("training.takemtitans.say." + IDS[d.training], keys()));
            ids.add(11);
            labels.add(Component.translatable("dialogue.takemtitans.training.skip"));
        } else if (d.training == GRADUATE) {
            for (int i = 0; i < 3; i++) {
                lines.add(Component.translatable("dialogue.takemtitans.training.graduate." + i, player.getName()));
            }

            ids.add(10);
            labels.add(Component.translatable("dialogue.takemtitans.training.graduate.accept"));
        } else {
            lines.add(Component.translatable("dialogue.takemtitans.idle.instructor." + npc.getRandom().nextInt(Story.idleCount(7)), player.getName()));
            ids.add(12);
            labels.add(Component.translatable("dialogue.takemtitans.training.again"));
        }
    }

    static void handle(ServerPlayer player, int option) {
        OdmPlayerData d = OdmServer.data(player);
        if (option == 10 && d.training == REPORT) {
            WorldEvents.giveStarterGear(player);
            d.training = HOOK;
            d.trainingCount = 0;
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            Story.banner(
                player, "stage", Component.translatable("training.takemtitans.drill", HOOK, DRILLS), Component.translatable("training.takemtitans.name." + IDS[HOOK])
            );
            say(player, "training.takemtitans.say.hook", keys());
            Story.sync(player);
        } else if (option == 10 && d.training == GRADUATE) {
            say(player, "training.takemtitans.say.farewell");
            Story.addXp(player, 50);
            finish(player, d);
        } else if (option == 11 && d.training < GRADUATE) {
            skip(player);
        } else if (option == 12 && d.training >= DONE) {
            d.training = HOOK;
            d.trainingCount = 0;
            if (!OdmGear.hasGear(player)) {
                WorldEvents.giveStarterGear(player);
            }

            Story.banner(
                player, "stage", Component.translatable("training.takemtitans.drill", HOOK, DRILLS), Component.translatable("training.takemtitans.name." + IDS[HOOK])
            );
            say(player, "training.takemtitans.say.hook", keys());
            Story.sync(player);
        }
    }

    public static void skip(ServerPlayer player) {
        OdmPlayerData d = OdmServer.data(player);
        if (d.training < DONE) {
            if (d.training == REPORT) {
                WorldEvents.giveStarterGear(player);
            }

            say(player, "training.takemtitans.say.skipped");
            finish(player, d);
        }
    }

    public static void restart(ServerPlayer player) {
        OdmPlayerData d = OdmServer.data(player);
        d.training = REPORT;
        d.trainingCount = 0;
        greet(player);
        Story.sync(player);
    }

    private static void finish(ServerPlayer player, OdmPlayerData d) {
        if (!d.starterKitGiven) {
            d.starterKitGiven = true;
            WorldEvents.giveFieldKit(player);
        }

        d.training = DONE;
        d.trainingCount = 0;
        if (d.introSeen || d.stage > 0) {
            // a first graduation goes straight into the prologue cutscene, which brings its own banner
            Story.banner(
                player,
                "chapter",
                Component.translatable("training.takemtitans.graduated"),
                d.stage == 0 ? Component.translatable("training.takemtitans.graduated.sub") : Component.empty()
            );
        }

        Story.sync(player);
    }

    static void fill(CompoundTag tag, OdmPlayerData d) {
        int step = Math.min(Math.max(d.training, 0), GRADUATE);
        tag.putString("chapter", "training.takemtitans.chapter");
        tag.putString("obj", "training.takemtitans.obj." + IDS[step]);
        tag.putInt("count", d.trainingCount);
        tag.putInt("need", NEED[step]);
    }

    @Nullable
    static Vec3 target(ServerPlayer player, OdmPlayerData d) {
        if (d.training == REPORT || d.training == GRADUATE) {
            List<TownsfolkEntity> found = player.level()
                .getEntitiesOfClass(TownsfolkEntity.class, player.getBoundingBox().inflate(96.0), e -> e.getRole() == 7);
            return found.isEmpty() ? TrainingGround.INSTRUCTOR : found.get(0).position();
        } else if (d.training == SUPPLY) {
            return TrainingGround.SUPPLY.getCenter();
        } else {
            // inside the yard the marker would only point at the ground under your feet
            return TrainingGround.inYard(player.getX(), player.getZ(), 4.0) ? null : TrainingGround.CENTER;
        }
    }

    /** The player's own bindings, in the order the training lines use them (%1$s to %8$s). */
    static Object[] keys() {
        return new Object[]{
            key("key.takemtitans.hook_left"),
            key("key.takemtitans.hook_right"),
            key("key.takemtitans.boost"),
            key("key.takemtitans.dash"),
            key("key.attack"),
            key("key.takemtitans.reload"),
            key("key.use"),
            key("key.sneak")
        };
    }

    private static Component key(String name) {
        return Component.literal("[").append(Component.keybind(name)).append("]").withStyle(ChatFormatting.YELLOW);
    }

    static void say(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(
            Component.translatable(
                "training.takemtitans.speech", Component.translatable("npc.takemtitans.instructor").withStyle(ChatFormatting.GOLD), Component.translatable(key, args)
            )
        );
    }
}
