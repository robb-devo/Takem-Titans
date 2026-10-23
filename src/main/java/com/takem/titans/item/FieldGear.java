package com.takem.titans.item;

import com.takem.titans.entity.npc.JaegerEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModEntities;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.story.Squad;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class FieldGear {
    private FieldGear() {
    }

    static final class Items {
        static final Item COBWEB_ITEM = net.minecraft.world.item.Items.COBWEB;
    }

    public static final class MedKit extends FieldGear.Tool {
        public MedKit(Properties p) {
            super(p, "med_kit", 300);
        }

        @Override
        boolean apply(ServerLevel level, ServerPlayer player, ItemStack stack) {
            player.heal(12.0F);
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 1));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
            level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.6, player.getZ(), 8, 0.4, 0.3, 0.4, 0.0);
            return true;
        }
    }

    public static class Net extends ThrowableItemProjectile {
        private static final BlockParticleOption WEB = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState());

        public Net(EntityType<? extends FieldGear.Net> type, Level level) {
            super(type, level);
        }

        Net(Level level, LivingEntity owner) {
            super(ModEntities.NET.get(), owner, level);
        }

        @Override
        protected Item getDefaultItem() {
            return FieldGear.Items.COBWEB_ITEM;
        }

        @Override
        protected double getDefaultGravity() {
            return 0.02;
        }

        @Override
        protected void onHit(HitResult result) {
            super.onHit(result);
            if (this.level() instanceof ServerLevel sl && !this.isRemoved()) {
                Vec3 c = result.getLocation();
                sl.sendParticles(WEB, c.x, c.y, c.z, 60, 1.5, 1.5, 1.5, 0.2);
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 2.0F, 0.6F);
                if (result instanceof EntityHitResult ehr) {
                    Entity e = ehr.getEntity();
                    AbstractTitan t = e instanceof TitanPart part ? part.getParent() : (e instanceof AbstractTitan at ? at : null);
                    if (t != null) {
                        t.pin(sl, 220);
                        sl.sendParticles(WEB, t.getX(), t.getY() + 1.0, t.getZ(), 120, (double)t.getBbWidth(), 1.0, (double)t.getBbWidth(), 0.1);
                    } else if (e instanceof LivingEntity l) {
                        l.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 4));
                    }
                }

                this.discard();
            }
        }
    }

    public static final class NetLauncher extends FieldGear.Thrown {
        public NetLauncher(Properties p) {
            super(p, "net_launcher", 80);
        }

        @Override
        boolean consumes() {
            return false;
        }

        @Override
        float speed() {
            return 2.8F;
        }

        @Override
        ThrowableItemProjectile make(ServerLevel level, ServerPlayer player) {
            return new FieldGear.Net(level, player);
        }
    }

    public static class SmokeBomb extends ThrowableItemProjectile {
        public SmokeBomb(EntityType<? extends FieldGear.SmokeBomb> type, Level level) {
            super(type, level);
        }

        SmokeBomb(Level level, LivingEntity owner) {
            super(ModEntities.SMOKE_BOMB.get(), owner, level);
        }

        @Override
        protected Item getDefaultItem() {
            return ModItems.SMOKE_BOMB.get();
        }

        @Override
        protected void onHit(HitResult result) {
            super.onHit(result);
            if (this.level() instanceof ServerLevel sl && !this.isRemoved()) {
                Vec3 c = result.getLocation();
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 3.0F, 0.5F);
                sl.playSound(null, c.x, c.y, c.z, ModSounds.BOMBLET_POP.get(), SoundSource.PLAYERS, 2.0F, 1.3F);
                sl.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.x, c.y + 1.0, c.z, 90, 3.5, 2.0, 3.5, 0.02);
                sl.sendParticles(ModParticles.BIG_STEAM.get(), c.x, c.y + 1.0, c.z, 60, 4.0, 2.5, 4.0, 0.03);

                for (AbstractTitan t : sl.getEntitiesOfClass(AbstractTitan.class, new AABB(c, c).inflate(14.0, 20.0, 14.0))) {
                    t.blind(160);
                }

                this.discard();
            }
        }
    }

    public static final class SmokeBombItem extends FieldGear.Thrown {
        public SmokeBombItem(Properties p) {
            super(p, "smoke_bomb", 60);
        }

        @Override
        ThrowableItemProjectile make(ServerLevel level, ServerPlayer player) {
            return new FieldGear.SmokeBomb(level, player);
        }
    }

    public static final class SquadHorn extends FieldGear.Tool {
        public SquadHorn(Properties p) {
            super(p, "squad_horn", 200);
        }

        @Override
        boolean consumes() {
            return false;
        }

        @Override
        boolean apply(ServerLevel level, ServerPlayer player, ItemStack stack) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 4.0F, 1.2F);
            int n = 0;

            for (JaegerEntity j : level.getEntitiesOfClass(
                JaegerEntity.class, player.getBoundingBox().inflate(300.0, 120.0, 300.0), jx -> player.getUUID().equals(jx.owner())
            )) {
                Squad.arrive(level, j, player);
                j.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
                j.heal(8.0F);
                j.say("follow");
                n++;
            }

            player.displayClientMessage(Component.translatable("message.takemtitans.horn", n), true);
            return true;
        }
    }

    public static final class Stim extends FieldGear.Tool {
        public Stim(Properties p) {
            super(p, "adrenaline", 1200);
        }

        @Override
        boolean apply(ServerLevel level, ServerPlayer player, ItemStack stack) {
            OdmServer.startOverdrive(player, OdmServer.data(player));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 300, 0));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 1.0F, 0.6F);
            return true;
        }
    }

    public abstract static class Thrown extends FieldGear.Tool {
        Thrown(Properties p, String tip, int cooldown) {
            super(p, tip, cooldown);
        }

        abstract ThrowableItemProjectile make(ServerLevel var1, ServerPlayer var2);

        float speed() {
            return 1.6F;
        }

        @Override
        boolean apply(ServerLevel level, ServerPlayer player, ItemStack stack) {
            ThrowableItemProjectile p = this.make(level, player);
            p.setItem(stack.copyWithCount(1));
            p.shootFromRotation(player, player.getXRot(), player.getYRot(), -4.0F, this.speed(), 0.6F);
            level.addFreshEntity(p);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.EXPLOSIVE_THROW.get(), SoundSource.PLAYERS, 1.0F, 1.1F);
            return true;
        }
    }

    abstract static class Tool extends Item {
        private final String tip;
        private final int cooldown;

        Tool(Properties p, String tip, int cooldown) {
            super(p);
            this.tip = tip;
            this.cooldown = cooldown;
        }

        abstract boolean apply(ServerLevel var1, ServerPlayer var2, ItemStack var3);

        boolean consumes() {
            return true;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                if (this.apply((ServerLevel)level, sp, stack)) {
                    if (this.consumes()) {
                        stack.consume(1, player);
                    } else if (stack.isDamageableItem()) {
                        stack.hurtAndBreak(1, sp, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                    }

                    player.getCooldowns().addCooldown(this, this.cooldown);
                    return InteractionResultHolder.consume(stack);
                } else {
                    return InteractionResultHolder.fail(stack);
                }
            } else {
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.takemtitans." + this.tip).withStyle(ChatFormatting.GRAY));
        }
    }

    public static final class Whetstone extends FieldGear.Tool {
        public Whetstone(Properties p) {
            super(p, "whetstone", 40);
        }

        @Override
        boolean consumes() {
            return false;
        }

        @Override
        boolean apply(ServerLevel level, ServerPlayer player, ItemStack stack) {
            for (ItemStack st : player.getInventory().items) {
                if (st.getItem() instanceof BladeItem && st.getDamageValue() > 0) {
                    st.setDamageValue(0);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.2, 0.3, 0.2);
                    player.displayClientMessage(Component.translatable("message.takemtitans.sharpened", st.getHoverName()), true);
                    return true;
                }
            }

            player.displayClientMessage(Component.translatable("message.takemtitans.nothing_to_sharpen"), true);
            return false;
        }
    }
}
