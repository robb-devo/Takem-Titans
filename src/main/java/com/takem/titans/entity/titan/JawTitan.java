package com.takem.titans.entity.titan;

import com.takem.titans.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class JawTitan extends AbnormalTitan {
    private final ServerBossEvent bossBar = new ServerBossEvent(
        Component.translatable("entity.takemtitans.jaw_titan"), BossBarColor.PURPLE, BossBarOverlay.NOTCHED_10
    );

    public JawTitan(EntityType<? extends JawTitan> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected float randomHeight(RandomSource random) {
        return 5.5F;
    }

    @Override
    protected double walkSpeed(float height) {
        return 0.46;
    }

    @Override
    public int variantCount() {
        return 1;
    }

    @Override
    public float headScale() {
        return 1.3F;
    }

    @Override
    protected float napeHealthFor(float height) {
        return 340.0F;
    }

    @Override
    protected double detectionRange() {
        return 110.0;
    }

    @Override
    protected float turnRate() {
        return 22.0F;
    }

    @Override
    protected void chooseAttack(LivingEntity target, double dist) {
        super.chooseAttack(target, dist);
        this.attackCooldown = Math.min(this.attackCooldown, 22 + this.random.nextInt(14));
    }

    @Override
    public boolean hurtPart(TitanPart part, DamageSource source, float amount) {
        if (part.kind != 1 && !this.level().isClientSide) {
            this.level().playSound(null, part.getX(), part.getY(), part.getZ(), ModSounds.ARMOR_CLANG.get(), SoundSource.HOSTILE, 1.4F, 1.3F);
            return super.hurtPart(part, source, amount * 0.4F);
        } else {
            return super.hurtPart(part, source, amount);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossBar.setProgress(Mth.clamp(this.napeHealth / this.maxNapeHealth(), 0.0F, 1.0F));
        if (this.tickCount % 70 == 0 && this.getTarget() != null) {
            this.voice(ModSounds.ABNORMAL_SCREECH.get(), 4.0F, 1.5F, 30, 1);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossBar.removePlayer(player);
    }
}
