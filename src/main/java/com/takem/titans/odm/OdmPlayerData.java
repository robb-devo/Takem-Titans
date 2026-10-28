package com.takem.titans.odm;

import com.takem.titans.skill.Skill;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class OdmPlayerData implements INBTSerializable<CompoundTag> {
    public final HookState left = new HookState(HookSide.LEFT);
    public final HookState right = new HookState(HookSide.RIGHT);
    public boolean boosting;
    public int ticksSinceOdmUse = 1000;
    public int dashCooldown;
    public int reloadCooldown;
    public double pendingGas;
    public Vec3 lastPos;
    public Vec3 velocity = Vec3.ZERO;
    public double recentDownSpeed;
    public double recentSpeed;
    public int grabbedBy = -1;
    public int struggle;
    public int combo;
    public int comboTimer;
    public int groundTicks;
    public int spinTicks;
    public int spinCooldown;
    public final Set<Long> spinHits = new HashSet<>();
    public boolean cyclone;
    public int rushTicks;
    public int rushCooldown;
    public int waveCooldown;
    public int boomCooldown;
    public int warpCooldown;
    public int severCooldown;
    /** Districts whose waystone this player has woken. */
    public final java.util.Set<String> waystones = new java.util.LinkedHashSet<>();
    /** A journey being gathered: the waystone index and the game time it leaves. Not saved. */
    public int travelTo = -1;
    public long travelAt;
    public long travelReadyAt;
    public int diveGrace;
    public double diveStartY;
    public Vec3 rushFrom;
    public final Set<Long> rushHits = new HashSet<>();
    public int overdriveTicks;
    public int overdriveCooldown;
    public long supplyDropReadyAt;
    public int titanKills;
    public int bestNapeDamage;
    public boolean starterKitGiven;
    /** Training Corps drill the recruit is on (see story.Training); 99 once they have graduated. */
    public int training;
    public int trainingCount;
    public int xp;
    public int level = 1;
    public int stage;
    public int stageCount;
    public boolean introSeen;
    public int contractKind;
    public int contractNeed;
    public int contractsDone;
    public long cutsceneUntil;
    public int bossStage = -1;
    public long bossTime;
    public int syncTimer;
    public final byte[] skills = new byte[Skill.COUNT];
    public int bonusPoints;
    public int novaCooldown;
    public int immuneTicks;
    public int meteorChain;
    public int wallet;
    public int killChain;
    public long meteorReadyAt;
    public int streak;
    public long streakUntil;
    public long killChainUntil;
    public int aftershockHits;
    public long aftershockUntil;
    public int chainTicks;
    public int rallyTicks;
    public long wingsReadyAt;
    /** The player's own Survey Corps horse, and when the stables can send a new one if it falls. */
    @javax.annotation.Nullable
    public java.util.UUID horse;
    public long horseReadyAt;
    public long instinctReadyAt;
    public int medicTimer;
    public int carryNotice;
    public final List<String> squad = new ArrayList<>();
    public final Map<String, int[]> squadStats = new HashMap<>();
    /** Squad members carried off wounded, and the game time they rejoin. Not saved: a restart patches everyone up. */
    public final Map<String, Long> squadDown = new HashMap<>();
    public boolean squadHold;
    public int townCheer;

    public boolean overdrive() {
        return this.overdriveTicks > 0;
    }

    public float comboMultiplier() {
        float m = 1.0F + 0.12F * (float)Math.min(this.combo, 10 + 2 * this.skills[Skill.FLOWING_COMBO.ordinal()]);
        return this.overdrive() ? m * 1.5F : m;
    }

    public HookState hook(HookSide side) {
        return side == HookSide.LEFT ? this.left : this.right;
    }

    public boolean anyHookAttached() {
        return this.left.isAttached() || this.right.isAttached();
    }

    public boolean anyHookActive() {
        return this.left.isActive() || this.right.isActive();
    }

    public CompoundTag serializeNBT(Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("TitanKills", this.titanKills);
        tag.putInt("BestNapeDamage", this.bestNapeDamage);
        tag.putBoolean("StarterKitGiven", this.starterKitGiven);
        tag.putInt("Training", this.training);
        tag.putInt("TrainingCount", this.trainingCount);
        tag.putInt("Xp", this.xp);
        tag.putInt("Level", this.level);
        tag.putInt("Stage", this.stage);
        tag.putInt("StageCount", this.stageCount);
        tag.putBoolean("IntroSeen", this.introSeen);
        tag.putInt("ContractKind", this.contractKind);
        tag.putInt("ContractNeed", this.contractNeed);
        tag.putInt("ContractsDone", this.contractsDone);
        tag.putInt("BossStage", this.bossStage);
        tag.putLong("BossTime", this.bossTime);
        tag.putByteArray("Skills", this.skills);
        tag.putInt("BonusPoints", this.bonusPoints);
        tag.putLong("WingsReady", this.wingsReadyAt);
        if (this.horse != null) {
            tag.putUUID("Horse", this.horse);
        }

        tag.putLong("HorseReady", this.horseReadyAt);
        ListTag sq = new ListTag();

        for (String m : this.squad) {
            sq.add(StringTag.valueOf(m));
        }

        tag.put("Squad", sq);
        tag.putBoolean("SquadHold", this.squadHold);
        tag.putInt("Wallet", this.wallet);
        CompoundTag st = new CompoundTag();

        for (Entry<String, int[]> e : this.squadStats.entrySet()) {
            st.putIntArray(e.getKey(), e.getValue());
        }

        tag.put("SquadStats", st);
        ListTag ws = new ListTag();

        for (String w : this.waystones) {
            ws.add(StringTag.valueOf(w));
        }

        tag.put("Waystones", ws);
        return tag;
    }

    public void deserializeNBT(Provider provider, CompoundTag tag) {
        this.titanKills = tag.getInt("TitanKills");
        this.bestNapeDamage = tag.getInt("BestNapeDamage");
        this.starterKitGiven = tag.getBoolean("StarterKitGiven");
        this.xp = tag.getInt("Xp");
        this.level = Math.max(1, tag.getInt("Level"));
        this.stage = tag.getInt("Stage");
        this.stageCount = tag.getInt("StageCount");
        this.introSeen = tag.getBoolean("IntroSeen");
        // players from before 2.0 already have their kit and know the gear: they never sit through the drills
        this.training = tag.contains("Training") ? tag.getInt("Training") : (this.starterKitGiven || this.stage > 0 || this.introSeen ? 99 : 0);
        this.trainingCount = tag.getInt("TrainingCount");
        this.contractKind = tag.getInt("ContractKind");
        this.contractNeed = tag.getInt("ContractNeed");
        this.contractsDone = tag.getInt("ContractsDone");
        this.bossStage = tag.contains("BossStage") ? tag.getInt("BossStage") : -1;
        this.bossTime = tag.getLong("BossTime");
        byte[] sk = tag.getByteArray("Skills");

        for (int i = 0; i < this.skills.length; i++) {
            this.skills[i] = i < sk.length ? sk[i] : 0;
        }

        this.bonusPoints = tag.getInt("BonusPoints");
        this.wallet = tag.getInt("Wallet");
        this.squadStats.clear();
        CompoundTag st = tag.getCompound("SquadStats");

        for (String k : st.getAllKeys()) {
            int[] v = st.getIntArray(k);
            if (v.length >= 2) {
                this.squadStats.put(k, new int[]{Math.max(1, v[0]), v[1]});
            }
        }

        this.wingsReadyAt = tag.getLong("WingsReady");
        this.horse = tag.hasUUID("Horse") ? tag.getUUID("Horse") : null;
        this.horseReadyAt = tag.getLong("HorseReady");
        this.squad.clear();
        ListTag sq = tag.getList("Squad", 8);

        for (int i = 0; i < sq.size() && i < 4; i++) {
            this.squad.add(sq.getString(i));
        }

        this.squadHold = tag.getBoolean("SquadHold");
        this.waystones.clear();
        ListTag ws = tag.getList("Waystones", 8);

        for (int i = 0; i < ws.size(); i++) {
            this.waystones.add(ws.getString(i));
        }
    }
}
