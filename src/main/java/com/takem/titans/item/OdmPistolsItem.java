package com.takem.titans.item;

import com.takem.titans.combat.TitanShift;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.fx.Fx;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.world.Destruction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Anti-personnel ODM pistols: a pair of double-barrelled guns with four rounds of buckshot between them, fired one barrel
 * at a time and reloaded together. Each shot throws an airborne shooter back along the line of fire, so the guns are
 * movement as much as weapons; pellets in a titan's eyes blind it. Sneak and hold to charge a volley: all four barrels at
 * once, incendiary, the shooter hurled away. The pellets are rolled from a seed the server sends with the shot, so every
 * client draws the same tracers and impacts the server hit.
 */
public class OdmPistolsItem extends Item {
    public static final int ROUNDS = 4;
    public static final int RELOAD = 34;
    public static final int CHARGE = 18;
    public static final int PELLETS = 9;
    public static final int VOLLEY_PELLETS = 30;
    public static final float SPREAD = 2.6F;
    public static final float VOLLEY_SPREAD = 6.5F;
    public static final double RANGE = 56.0;

    public OdmPistolsItem(Properties properties) {
        super(properties);
    }

    public static int rounds(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains("Rounds") ? data.copyTag().getInt("Rounds") : ROUNDS;
    }

    private static void setRounds(ItemStack stack, int rounds) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("Rounds", rounds));
    }

    /** One pellet's direction: spread evenly over a disc around the aim. */
    public static Vec3 pellet(float yaw, float pitch, float spread, Random r) {
        double a = r.nextDouble() * Math.PI * 2.0;
        double s = Math.sqrt(r.nextDouble()) * (double)spread;
        return Vec3.directionFromRotation(pitch + (float)(Math.sin(a) * s), yaw + (float)(Math.cos(a) * s));
    }

    /** What a pellet can hit: anything pickable but the shooter, and a titan only through its body parts. */
    public static boolean canHit(Entity shooter, Entity e) {
        return e != shooter && !e.isSpectator() && e.isPickable() && !(e instanceof AbstractTitan);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || player.getCooldowns().isOnCooldown(this) || TitanShift.isShifted(player)) {
            return InteractionResultHolder.fail(stack);
        }
        if (player.isSecondaryUseActive()) {
            player.startUsingItem(hand);
            if (!level.isClientSide) {
                level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.PISTOL_CHARGE.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
            }
            return InteractionResultHolder.consume(stack);
        }
        int rounds = rounds(stack);
        this.fire(level, player, ROUNDS - rounds, false);
        if (rounds <= 1) {
            setRounds(stack, ROUNDS);
            player.getCooldowns().addCooldown(this, RELOAD);
            if (!level.isClientSide) {
                level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.PISTOL_RELOAD.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        } else {
            setRounds(stack, rounds - 1);
            player.getCooldowns().addCooldown(this, 5);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (entity instanceof Player player && this.getUseDuration(stack, entity) - timeLeft >= CHARGE && !player.getCooldowns().isOnCooldown(this)) {
            this.fire(level, player, 0, true);
            setRounds(stack, ROUNDS);
            player.getCooldowns().addCooldown(this, 64);
            if (!level.isClientSide) {
                level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.PISTOL_RELOAD.get(), SoundSource.PLAYERS, 1.0F, 0.9F);
            }
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    private void fire(Level level, Player player, int barrel, boolean volley) {
        if (level.isClientSide) {
            com.takem.titans.client.PistolClient.fired(player, barrel, volley);
        } else if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            shoot(sl, sp, barrel, volley);
        }
    }

    private static void shoot(ServerLevel level, ServerPlayer p, int barrel, boolean volley) {
        Vec3 eye = p.getEyePosition();
        float yaw = p.getYRot();
        float pitch = p.getXRot();
        int seed = level.random.nextInt(1 << 22);
        Random r = new Random((long)seed);
        DamageSource src = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.ARROW), null, p);
        Map<TitanPart, Float> parts = new HashMap<>();
        Map<Entity, Float> others = new HashMap<>();
        List<Vec3> blasts = new ArrayList<>();
        int n = volley ? VOLLEY_PELLETS : PELLETS;
        for (int i = 0; i < n; i++) {
            Vec3 dir = pellet(yaw, pitch, volley ? VOLLEY_SPREAD : SPREAD, r);
            Vec3 end = eye.add(dir.scale(RANGE));
            BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, p, eye, stop, new AABB(eye, stop).inflate(1.0), e -> canHit(p, e));
            if (eh != null) {
                if (eh.getEntity() instanceof TitanPart part) {
                    parts.merge(part, volley ? 16.0F : 12.0F, Float::sum);
                } else {
                    others.merge(eh.getEntity(), volley ? 5.0F : 3.5F, Float::sum);
                }
            } else if (bh.getType() == HitResult.Type.BLOCK) {
                BlockState state = level.getBlockState(bh.getBlockPos());
                if (state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES)) {
                    level.destroyBlock(bh.getBlockPos(), false);
                }
                if (volley && blasts.size() < 5 && blasts.stream().noneMatch(b -> b.distanceToSqr(stop) < 6.25)) {
                    blasts.add(stop);
                }
            }
        }
        parts.forEach((part, dmg) -> part.getParent().hurtPart(part, src, dmg));
        others.forEach((e, dmg) -> e.hurt(src, dmg));
        for (Vec3 b : blasts) {
            Destruction.blast(level, b, 1.5);
        }
        Fx.send(level, 68, eye.x, eye.y, eye.z, yaw, pitch, (float)seed, (float)((volley ? 4 : 0) + barrel), 160.0);
        level.playSound(
            null, eye.x, eye.y, eye.z, volley ? ModSounds.PISTOL_VOLLEY.get() : ModSounds.PISTOL_SHOT.get(), SoundSource.PLAYERS, volley ? 5.0F : 3.0F,
            0.94F + level.random.nextFloat() * 0.12F
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.takemtitans.odm_pistols").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.takemtitans.odm_pistols_volley").withStyle(ChatFormatting.GOLD));
    }
}
