package com.takem.titans.skill;

import com.takem.titans.item.VanguardArmorItem;
import com.takem.titans.network.SkillActionPayload;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.story.Shop;
import com.takem.titans.story.Story;
import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class Skills {
    public static final int RESET_COST = 5;

    private Skills() {
    }

    public static int rank(@Nullable Player p, Skill s) {
        if (p == null) {
            return 0;
        } else {
            return p.level().isClientSide ? ClientSkills.rank(s) : OdmServer.data(p).skills[s.ordinal()];
        }
    }

    public static boolean has(@Nullable Player p, Skill s) {
        return rank(p, s) > 0;
    }

    public static int earned(OdmPlayerData d) {
        return Math.max(0, Math.min(d.level, 40) - 1) + d.bonusPoints;
    }

    public static int spent(OdmPlayerData d) {
        int n = 0;

        for (byte b : d.skills) {
            n += b;
        }

        return n;
    }

    public static int available(OdmPlayerData d) {
        return Math.max(0, earned(d) - spent(d));
    }

    @Nullable
    public static String blocker(OdmPlayerData d, Skill s) {
        int r = d.skills[s.ordinal()];
        if (r >= s.max) {
            return "skill.takemtitans.why.maxed";
        } else if (d.level < s.minLevel) {
            return "skill.takemtitans.why.level";
        } else if (!requirementsMet(d.skills, s)) {
            return "skill.takemtitans.why.requires";
        } else {
            return available(d) <= 0 ? "skill.takemtitans.why.points" : null;
        }
    }

    public static boolean requirementsMet(byte[] ranks, Skill s) {
        if (s.requires.length == 0) {
            return true;
        } else {
            for (Skill r : s.requires) {
                if (ranks[r.ordinal()] > 0) {
                    return true;
                }
            }

            return false;
        }
    }

    public static void handle(SkillActionPayload msg, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            OdmPlayerData d = OdmServer.data(player);
            if (msg.action() == 0 && msg.skill() >= 0 && msg.skill() < Skill.COUNT) {
                Skill s = Skill.ALL[msg.skill()];
                String why = blocker(d, s);
                if (why != null) {
                    player.displayClientMessage(Component.translatable(why), true);
                    return;
                }

                d.skills[s.ordinal()]++;
                player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), ModSounds.LEVEL_UP.get(), SoundSource.PLAYERS, 0.7F, 1.4F);
                player.level()
                    .playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
                player.displayClientMessage(
                    Component.translatable("skill.takemtitans.msg.learned", Component.translatable(s.nameKey()), d.skills[s.ordinal()], s.max), true
                );
                Story.applyPerks(player);
                Story.sync(player);
            } else if (msg.action() == 1) {
                reset(player, player.getAbilities().instabuild);
            }
        }
    }

    public static void reset(ServerPlayer player, boolean free) {
        OdmPlayerData d = OdmServer.data(player);
        if (spent(d) != 0) {
            if (!free && !Shop.spend(player, 5)) {
                player.displayClientMessage(Component.translatable("skill.takemtitans.msg.reset_cost", 5), true);
                return;
            }

            Arrays.fill(d.skills, (byte)0);
            player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
            player.displayClientMessage(Component.translatable("skill.takemtitans.msg.reset"), true);
            Story.applyPerks(player);
            Story.sync(player);
        }
    }

    public static void grantBonus(ServerPlayer player, int points) {
        OdmPlayerData d = OdmServer.data(player);
        d.bonusPoints += points;
        Story.sync(player);
    }

    public static float bladeMul(Player p) {
        float m = 1.0F + 0.08F * (float)rank(p, Skill.SHARPENED_STEEL);
        if (has(p, Skill.BLADE_DANCER)) {
            m *= 1.2F;
        }

        if (VanguardArmorItem.fullSet(p)) {
            m *= 1.1F;
        }

        if (!p.level().isClientSide && OdmServer.data(p).rallyTicks > 0) {
            m *= 1.15F;
        }

        return m;
    }

    public static float comboArtsMul(Player p, @Nullable OdmPlayerData d) {
        float m = 1.0F;
        int sky = rank(p, Skill.SKYFALL_STRIKE);
        if (sky > 0) {
            double fall = d != null ? -d.velocity.y : -p.getDeltaMovement().y;
            if (fall > 0.6) {
                m *= 1.0F + 0.15F * (float)sky * (fall > 1.5 ? 2.0F : 1.0F);
            }
        }

        if (d != null && d.killChain > 0 && p.level().getGameTime() < d.killChainUntil) {
            m *= 1.0F + (rank(p, Skill.KILL_CHAIN) >= 2 ? 0.25F : 0.15F) * (float)Math.min(5, d.killChain);
        }

        return m;
    }

    public static float napeMul(Player p) {
        return (1.0F + 0.12F * (float)rank(p, Skill.NAPE_SPECIALIST)) * (has(p, Skill.GODSLAYER) ? 1.25F : 1.0F);
    }

    public static double gasMul(Player p) {
        double m = (1.0 - 0.12 * (double)rank(p, Skill.GAS_EFFICIENCY)) * (has(p, Skill.SKY_LORD) ? 0.7 : 1.0);
        m = com.takem.titans.item.RegimentArmorItem.recon(p) ? m * 0.75 : m;
        return VanguardArmorItem.fullSet(p) ? m * 0.8 : m;
    }

    public static double reelMul(Player p) {
        return 1.0 + 0.15 * (double)rank(p, Skill.REINFORCED_WINCH);
    }

    public static double dashMul(Player p) {
        return 1.0 + 0.15 * (double)rank(p, Skill.AFTERBURN_DASH);
    }

    public static int dashCooldown(Player p, int base) {
        return Math.max(3, (int)Math.round((double)base * (1.0 - 0.2 * (double)rank(p, Skill.AFTERBURN_DASH)) * (has(p, Skill.SKY_LORD) ? 0.7 : 1.0)));
    }

    public static int liftCapacity(Player p) {
        if (has(p, Skill.TITAN_WINCH)) {
            return 100000;
        } else {
            double c = 40.0 * (1.0 + 0.5 * (double)rank(p, Skill.REINFORCED_WINCH));
            if (has(p, Skill.WRECKING_BALL)) {
                c *= 3.0;
            }

            return (int)c;
        }
    }

    public static double demolition(@Nullable Player p) {
        return p == null ? 1.0 : 1.0 + 0.2 * (double)rank(p, Skill.DEMOLISHER);
    }

    public static int squadSize(Player p) {
        int r = rank(p, Skill.SQUAD_LEADER);
        return 2 + (r <= 0 ? 0 : (r == 1 ? 2 : 4)) + (has(p, Skill.LEGION) ? 2 : 0);
    }

    public static int comboSteps(Player p) {
        return 10 + 2 * rank(p, Skill.FLOWING_COMBO);
    }

    public static int comboWindow(Player p) {
        return 70 + 30 * rank(p, Skill.FLOWING_COMBO);
    }

    public static int overdriveCombo(Player p) {
        return has(p, Skill.ACKERMAN_AWAKENING) ? 6 : 10;
    }
}
