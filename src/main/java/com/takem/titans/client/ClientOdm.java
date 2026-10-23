package com.takem.titans.client;

import com.takem.titans.TakemConfig;
import com.takem.titans.block.GateControlBlock;
import com.takem.titans.block.SupplyStationBlock;
import com.takem.titans.client.fx.WorldFx;
import com.takem.titans.client.story.Cutscene;
import com.takem.titans.entity.DebrisEntity;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.item.BladeItem;
import com.takem.titans.item.BladeTier;
import com.takem.titans.network.HookActionPayload;
import com.takem.titans.network.OdmActionPayload;
import com.takem.titans.network.OdmInputPayload;
import com.takem.titans.odm.Grapple;
import com.takem.titans.odm.HookSide;
import com.takem.titans.odm.HookState;
import com.takem.titans.odm.OdmGear;
import com.takem.titans.odm.OdmMovementHooks;
import com.takem.titans.odm.OdmPhysics;
import com.takem.titans.odm.OdmTuning;
import com.takem.titans.registry.ModParticles;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.skill.Skills;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.Clone;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.ClientTickEvent.Pre;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class ClientOdm implements OdmMovementHooks.Controller, OdmPhysics.Flight {
    public static final ClientOdm INSTANCE = new ClientOdm();
    public final HookState left = new HookState(HookSide.LEFT);
    public final HookState right = new HookState(HookSide.RIGHT);
    private final HookState[] hooks = new HookState[]{this.left, this.right};
    private final BlockPos[] anchorBlocks = new BlockPos[2];
    private final Vec3[] lastAnchor = new Vec3[2];
    private final int[] lostTicks = new int[2];
    private final boolean[] regripOk = new boolean[2];
    private final boolean[] wasHeld = new boolean[2];
    private final boolean[] held = new boolean[2];
    private final int[] blockRipTicks = new int[2];
    public boolean odmAirborne;
    public boolean boosting;
    private boolean boostArmed;
    private boolean boostWasDown;
    private boolean dashWasDown;
    private boolean useWasDown;
    private boolean rmbHooks;
    private Vec3 impulse = Vec3.ZERO;
    public int dashCooldown;
    public int ticksSinceDash = 100;
    public int reloadTicks;
    public int hitstop;
    private Vec3 hitstopVel = Vec3.ZERO;
    public int spinTicks;
    public int spinCooldown;
    public boolean cyclone;
    public int rushCharge;
    public int rushTicks;
    public int diveTicks;
    public final int[] cd = new int[2];
    public final int[] cdMax = new int[]{1, 1};
    public double diveSpeed;
    public double diveStartY;
    private boolean diveLock;
    public int rushCooldown;
    private Vec3 rushDir = Vec3.ZERO;
    public int overdrive;
    private int lastFlags = -1;
    private int resendTimer;
    private boolean flyingAtTickStart;
    private boolean emptyClickPlayed;
    public Vec3 velocity = Vec3.ZERO;
    public Vec3 prevVelocity = Vec3.ZERO;
    public double speed;
    private Vec3 lastPos;
    public int flightTicks;
    public boolean everSwung;
    @Nullable
    public ClientOdm.Aim aim;

    private ClientOdm() {
    }

    public boolean anyAttached() {
        return this.left.isAttached() || this.right.isAttached();
    }

    public boolean anyActive() {
        return this.left.isActive() || this.right.isActive();
    }

    public boolean anyReeling() {
        return this.left.isAttached() && this.left.reeling || this.right.isAttached() && this.right.reeling;
    }

    public boolean inFlight() {
        Player p = Minecraft.getInstance().player;
        return p != null && (this.anyAttached() || this.odmAirborne) && !p.onGround();
    }

    public HookState hook(HookSide side) {
        return side == HookSide.LEFT ? this.left : this.right;
    }

    private static Vec3 ropeOrigin(Player p) {
        return p.position().add(0.0, 0.95, 0.0);
    }

    private static boolean canFly(Player p) {
        return !p.isInWater()
            && !p.isInLava()
            && !p.isPassenger()
            && !p.getAbilities().flying
            && !p.isFallFlying()
            && !p.isSleeping()
            && !ClientTitanState.isGrabbed();
    }

    @Override
    public boolean shouldControl(Player p) {
        if (!OdmGear.hasGear(p) || !canFly(p)) {
            return false;
        } else if (this.hitstop > 0 || this.rushTicks > 0 || this.diveTicks > 0) {
            return true;
        } else if (this.impulse != Vec3.ZERO) {
            return true;
        } else {
            return this.anyAttached() ? !p.onGround() || this.anyReeling() : (this.odmAirborne || this.boosting) && !p.onGround();
        }
    }

    @Override
    public void travel(Player p, Vec3 input) {
        if (this.hitstop > 0) {
            this.hitstop--;
            p.setDeltaMovement(this.hitstopVel.scale(0.04));
            p.move(MoverType.SELF, p.getDeltaMovement());
            if (this.hitstop == 0) {
                p.setDeltaMovement(this.hitstopVel);
            }
        } else if (this.rushTicks > 0) {
            this.rushMove(p);
        } else if (this.diveTicks > 0) {
            this.diveMove(p);
        } else {
            if (!p.onGround()) {
                this.odmAirborne = true;
            }

            OdmPhysics.travel(p, input, this);
        }
    }

    @Override
    public double boostScale() {
        return this.overdrive > 0 ? 1.3 : 1.0;
    }

    private void rushMove(Player p) {
        this.rushTicks--;
        Vec3 v = this.rushDir.scale(3.1 * (this.overdrive > 0 ? 1.15 : 1.0));
        p.setDeltaMovement(v);
        p.move(MoverType.SELF, v);
        this.odmAirborne = true;
        boolean blocked = p.horizontalCollision || p.verticalCollision && this.rushDir.y < -0.3;
        if (blocked || this.rushTicks == 0) {
            this.rushTicks = 0;
            p.setDeltaMovement(blocked ? Vec3.ZERO : this.rushDir.scale(1.35));
            if (blocked) {
                ClientEffects.shake(0.6F, 10);
                p.level().playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.6F, false);
            }
        }

        p.calculateEntityAnimation(false);
    }

    public void hitStop(int ticks) {
        Player p = Minecraft.getInstance().player;
        if (p != null && TakemConfig.HIT_STOP.get() && OdmGear.hasGear(p) && ticks > 0) {
            if (this.hitstop <= 0) {
                this.hitstopVel = p.getDeltaMovement();
            }

            this.hitstop = Math.max(this.hitstop, ticks);
        }
    }

    @Override
    public HookState[] hooks() {
        boolean l = this.left.isActive() && (!this.left.isAttached() || !this.left.tether);
        boolean r = this.right.isActive() && (!this.right.isAttached() || !this.right.tether);
        if (l && r) {
            return this.hooks;
        } else if (l) {
            return new HookState[]{this.left};
        } else {
            return r ? new HookState[]{this.right} : new HookState[0];
        }
    }

    public HookState[] allHooks() {
        return this.hooks;
    }

    @Override
    public Vec3 anchorOf(HookState hook) {
        Player p = Minecraft.getInstance().player;
        return p == null ? hook.pos : hook.anchor(p.level(), 1.0F);
    }

    @Override
    public boolean isReeling(HookState hook) {
        return hook.reeling;
    }

    @Override
    public boolean isBoosting() {
        return this.boosting;
    }

    @Override
    public boolean arrivalAssist() {
        return TakemConfig.ARRIVAL_ASSIST.get();
    }

    @Override
    public Vec3 consumeImpulse() {
        Vec3 i = this.impulse;
        this.impulse = Vec3.ZERO;
        return i;
    }

    @Override
    public void afterMove(Player p, Vec3 intended, Vec3 actual, boolean horizontalCollision) {
        if (horizontalCollision) {
            Vec3 lost = intended.subtract(actual);
            double lostH = Math.sqrt(lost.x * lost.x + lost.z * lost.z);
            if (lostH > 0.45) {
                ClientEffects.shake((float)Math.min(1.0, lostH * 0.35), 8);
            }

            if (lostH > 1.085) {
                p.level()
                    .playLocalSound(
                        p.getX(), p.getY() + 1.0, p.getZ(), SoundEvents.PLAYER_SMALL_FALL, SoundSource.PLAYERS, (float)Math.min(1.0, lostH * 0.5), 0.7F, false
                    );
            }

            if (lostH > 1.55) {
                PacketDistributor.sendToServer(new OdmActionPayload(2, (int)(lostH * 1000.0)));
            }
        }
    }

    @SubscribeEvent
    public static void onClientTickPre(Pre event) {
        INSTANCE.tickPre();
    }

    @SubscribeEvent
    public static void onClientTickPost(Post event) {
        INSTANCE.tickPost();
    }

    @SubscribeEvent
    public static void onLogout(LoggingOut event) {
        INSTANCE.resetAll();
    }

    @SubscribeEvent
    public static void onRespawn(Clone event) {
        INSTANCE.resetAll();
    }

    @SubscribeEvent
    public static void onInteractionKey(InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p != null && event.isAttack() && INSTANCE.rushCharge > 2 && OdmGear.hasGear(p) && OdmGear.holdingBlades(p)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        } else if (p != null && event.isUseItem() && OdmGear.hasGear(p) && OdmGear.holdingBlades(p) && (INSTANCE.rmbHooks || !isInteractionTarget(mc, p))) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private void resetAll() {
        for (HookState h : this.hooks) {
            h.reset();
        }

        this.anchorBlocks[0] = this.anchorBlocks[1] = null;
        this.wasHeld[0] = this.wasHeld[1] = false;
        this.odmAirborne = this.boosting = this.boostArmed = this.rmbHooks = false;
        this.impulse = Vec3.ZERO;
        this.dashCooldown = this.reloadTicks = 0;
        this.rushCharge = this.rushTicks = this.rushCooldown = this.overdrive = this.diveTicks = 0;
        this.lastFlags = -1;
        this.lastPos = null;
        this.velocity = this.prevVelocity = Vec3.ZERO;
        this.speed = 0.0;
        this.aim = null;
    }

    private void tickPre() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p != null && mc.level != null) {
            if (!mc.isPaused()) {
                this.flyingAtTickStart = p.getAbilities().flying;

                for (int k = 0; k < this.cd.length; k++) {
                    if (this.cd[k] > 0) {
                        this.cd[k]--;
                    }
                }

                if (OdmGear.hasGear(p) && p.isAlive()) {
                    ItemStack gear = OdmGear.getGear(p);
                    boolean creative = p.getAbilities().instabuild;
                    if (this.overdrive > 0) {
                        this.overdrive--;
                    }

                    boolean freeGas = creative || this.overdrive > 0;
                    boolean hasGas = freeGas || OdmGear.getGas(gear) > 0;
                    boolean blades = OdmGear.holdingBlades(p);
                    boolean screen = mc.screen != null || Cutscene.active();
                    Options o = mc.options;
                    boolean hijack = blades && TakemConfig.HIJACK_VANILLA_KEYS.get();
                    if (hijack && !screen) {
                        for (KeyMapping hookKey : new KeyMapping[]{OdmKeys.HOOK_LEFT, OdmKeys.HOOK_RIGHT, OdmKeys.HOOK_BOTH}) {
                            for (KeyMapping v : clickKeys(o)) {
                                if (!hookKey.isUnbound() && v.getKey().equals(hookKey.getKey())) {
                                    while (v.consumeClick()) {
                                    }
                                }
                            }
                        }
                    }

                    boolean useDown = o.keyUse.isDown() && !screen;
                    if (useDown && !this.useWasDown) {
                        this.rmbHooks = blades && !isInteractionTarget(mc, p);
                    } else if (!useDown) {
                        this.rmbHooks = false;
                    }

                    this.useWasDown = useDown;
                    boolean both = usable(OdmKeys.HOOK_BOTH, blades, hijack) && OdmKeys.HOOK_BOTH.isDown() | OdmKeys.HOOK_BOTH.consumeClick()
                        || this.rmbHooks && useDown;
                    boolean l = usable(OdmKeys.HOOK_LEFT, blades, hijack) && OdmKeys.HOOK_LEFT.isDown() | OdmKeys.HOOK_LEFT.consumeClick();
                    boolean r = usable(OdmKeys.HOOK_RIGHT, blades, hijack) && OdmKeys.HOOK_RIGHT.isDown() | OdmKeys.HOOK_RIGHT.consumeClick();
                    drainOwnClicks();
                    this.held[0] = !screen && (l || both);
                    this.held[1] = !screen && (r || both);
                    boolean bothFresh = this.held[0] && this.held[1] && !this.wasHeld[0] && !this.wasHeld[1];
                    if (!canFly(p)) {
                        this.held[0] = this.held[1] = false;
                    }

                    for (int i = 0; i < 2; i++) {
                        if (this.held[i] && !this.wasHeld[i]) {
                            this.fire(p, i, bothFresh ? this.hooks[i].side.sign : 0, hasGas);
                        } else if (!this.held[i] && this.wasHeld[i]) {
                            this.release(p, i);
                        }

                        this.wasHeld[i] = this.held[i];
                    }

                    if (!this.held[0] && !this.held[1]) {
                        this.emptyClickPlayed = false;
                    }

                    for (int i = 0; i < 2; i++) {
                        this.tickHook(p, i);
                    }

                    boolean lock = !screen && OdmKeys.winchLockDown();

                    for (int i = 0; i < 2; i++) {
                        HookState h = this.hooks[i];
                        h.reeling = h.isAttached() && this.held[i] && !lock && hasGas;
                        if (h.isAttached() && p.onGround() && !h.reeling) {
                            h.ropeLength = Math.max(h.ropeLength, this.anchorOf(h).distanceTo(ropeOrigin(p)));
                        }
                    }

                    boolean airborne = !p.onGround();
                    boolean boostDown = !screen && OdmKeys.boostDown();
                    if (boostDown && !this.boostWasDown && airborne) {
                        this.boostArmed = true;
                    }

                    if (!boostDown) {
                        this.boostArmed = false;
                    }

                    this.boostWasDown = boostDown;
                    boolean wasBoosting = this.boosting;
                    this.boosting = boostDown && airborne && hasGas && canFly(p) && (this.boostArmed || this.odmAirborne || this.anyAttached());
                    if (this.boosting && !wasBoosting) {
                        OdmSounds.startGas();
                    }

                    if (this.boosting) {
                        this.odmAirborne = true;
                    }

                    boolean dashDown = !screen && OdmKeys.dashDown();
                    boolean attackHeld = !screen && o.keyAttack.isDown();
                    if (dashDown
                        && !this.dashWasDown
                        && attackHeld
                        && airborne
                        && canFly(p)
                        && bladesReady(p)
                        && this.diveTicks <= 0
                        && this.cd[0] <= 0
                        && this.rushTicks <= 0
                        && p.getXRot() > 25.0F
                        && heightAboveGround(p) > 3.0) {
                        this.dive(p);
                    } else if (dashDown && !this.dashWasDown && hasGas && canFly(p) && (airborne || this.anyAttached())) {
                        if (this.dashCooldown <= 0) {
                            this.dash(p, freeGas || OdmGear.getGas(gear) >= 25);
                        } else if (airborne && this.spinCooldown <= 0 && this.ticksSinceDash <= 16 && bladesReady(p)) {
                            boolean drill = this.anyReeling();
                            this.spin(p, drill, freeGas || OdmGear.getGas(gear) >= (drill ? 90 : 70) / 2);
                        }
                    }

                    this.dashWasDown = dashDown;
                    if (this.dashCooldown > 0) {
                        this.dashCooldown--;
                    }

                    if (this.spinCooldown > 0) {
                        this.spinCooldown--;
                    }

                    if (this.spinTicks > 0) {
                        this.spinTicks--;
                        this.spinEffects(p);
                    }

                    this.ticksSinceDash++;
                    boolean attackDown = !screen && o.keyAttack.isDown();
                    if (this.rushCooldown > 0) {
                        this.rushCooldown--;
                    }

                    if (!attackDown) {
                        this.diveLock = false;
                    }

                    if (attackDown && bladesReady(p) && canFly(p) && this.rushTicks <= 0 && this.diveTicks <= 0 && !this.diveLock) {
                        this.rushCharge++;
                        this.chargeEffects(p);
                    } else {
                        if (!attackDown
                            && this.rushCharge >= this.rushChargeNeeded()
                            && this.rushCooldown <= 0
                            && bladesReady(p)
                            && canFly(p)
                            && (freeGas || OdmGear.getGas(gear) >= 30)) {
                            this.rush(p);
                        }

                        this.rushCharge = 0;
                    }

                    while (OdmKeys.RELOAD.consumeClick()) {
                        this.tryReload(p, gear);
                    }

                    while (!screen && OdmKeys.GAS_NOVA.consumeClick()) {
                        PacketDistributor.sendToServer(new OdmActionPayload(12, 0));
                    }

                    if (this.reloadTicks > 0) {
                        this.reloadTicks--;
                    }

                    boolean tethered = this.left.tether || this.right.tether;
                    if (!screen && tethered && o.keyAttack.consumeClick()) {
                        PacketDistributor.sendToServer(new OdmActionPayload(10, 0));
                    }

                    boolean ripShift = !screen && OdmKeys.winchLockDown();

                    for (int ri = 0; ri < 2; ri++) {
                        HookState rh = this.hooks[ri];
                        if (ripShift && this.held[ri] && rh.isAttached() && rh.entityId < 0) {
                            if (++this.blockRipTicks[ri] >= 12) {
                                this.blockRipTicks[ri] = 0;
                                PacketDistributor.sendToServer(new OdmActionPayload(11, ri));
                            }
                        } else {
                            this.blockRipTicks[ri] = 0;
                        }
                    }

                    this.sendFlags(p);
                } else {
                    if (this.anyActive()) {
                        for (int ix = 0; ix < 2; ix++) {
                            this.release(p, ix);
                        }
                    }

                    this.odmAirborne = this.boosting = false;
                    this.impulse = Vec3.ZERO;

                    for (HookState h : this.hooks) {
                        h.reeling = false;
                    }

                    this.sendFlags(p);

                    for (int ix = 0; ix < 2; ix++) {
                        this.tickHook(p, ix);
                    }

                    drainOwnClicks();
                }
            }
        } else if (this.anyActive() || this.odmAirborne) {
            this.resetAll();
        }
    }

    private void tickPost() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p != null && mc.level != null && !mc.isPaused()) {
            if (!this.flyingAtTickStart && p.getAbilities().flying && (this.odmAirborne || this.anyActive()) && OdmGear.hasGear(p)) {
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
            }

            Vec3 pos = p.position();
            this.prevVelocity = this.velocity;
            this.velocity = this.lastPos == null ? Vec3.ZERO : pos.subtract(this.lastPos);
            if (this.velocity.lengthSqr() > 100.0) {
                this.velocity = Vec3.ZERO;
            }

            this.lastPos = pos;
            this.speed = this.velocity.length();
            if ((p.onGround() || !canFly(p)) && !this.anyAttached()) {
                this.odmAirborne = false;
            }

            if (this.inFlight()) {
                this.flightTicks++;
                if (this.anyAttached() && this.flightTicks > 20) {
                    this.everSwung = true;
                }
            } else {
                this.flightTicks = 0;
            }

            if (OdmGear.hasGear(p)) {
                this.aim = this.findAim(p, 0.0);
                this.emitGas(p);
            } else {
                this.aim = null;
            }

            if (DevTune.get("telemetry", 0.0F) > 0.0F) {
                this.telemetry(p);
            }
        }
    }

    private void telemetry(LocalPlayer p) {
        try {
            Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("config/takemtitans-telemetry.csv");
            String line = String.format(
                Locale.ROOT,
                "%d,%.3f,%.3f,%.3f,%.4f,%.4f,%.4f,%.4f,%s,%.2f,%s,%.2f,%b,%b,%b%n",
                p.tickCount,
                p.getX(),
                p.getY(),
                p.getZ(),
                this.velocity.x,
                this.velocity.y,
                this.velocity.z,
                this.speed,
                this.left.phase,
                this.left.ropeLength,
                this.right.phase,
                this.right.ropeLength,
                this.boosting,
                p.onGround(),
                this.inFlight()
            );
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException var4) {
        }
    }

    private static KeyMapping[] clickKeys(Options o) {
        KeyMapping[] base = new KeyMapping[]{
            o.keyDrop,
            o.keyInventory,
            o.keySwapOffhand,
            o.keyPickItem,
            o.keyTogglePerspective,
            o.keyAdvancements,
            o.keySocialInteractions,
            o.keyPlayerList,
            o.keyChat,
            o.keyCommand
        };
        KeyMapping[] all = new KeyMapping[base.length + o.keyHotbarSlots.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(o.keyHotbarSlots, 0, all, base.length, o.keyHotbarSlots.length);
        return all;
    }

    private static boolean sharesVanillaKey(KeyMapping key) {
        Options o = Minecraft.getInstance().options;

        for (KeyMapping v : o.keyMappings) {
            if (v != key && !isOwnKey(v) && v.getKey().equals(key.getKey())) {
                return true;
            }
        }

        return false;
    }

    private static boolean isOwnKey(KeyMapping k) {
        for (KeyMapping own : OdmKeys.ALL) {
            if (own == k) {
                return true;
            }
        }

        return false;
    }

    private static boolean usable(KeyMapping key, boolean blades, boolean hijack) {
        return key.isUnbound() ? false : hijack || !sharesVanillaKey(key);
    }

    private static void drainOwnClicks() {
        for (KeyMapping k : OdmKeys.ALL) {
            if (k != OdmKeys.RELOAD && k != OdmKeys.GAS_NOVA && k != OdmKeys.SKILLS) {
                while (k.consumeClick()) {
                }
            }
        }
    }

    private static boolean isInteractionTarget(Minecraft mc, LocalPlayer p) {
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bhr && hit.getType() == Type.BLOCK) {
            BlockState state = p.level().getBlockState(bhr.getBlockPos());
            Block block = state.getBlock();
            return block instanceof SupplyStationBlock
                || block instanceof GateControlBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock
                || block instanceof ButtonBlock
                || block instanceof LeverBlock
                || block instanceof BellBlock
                || state.getMenuProvider(p.level(), bhr.getBlockPos()) != null;
        }

        if (hit instanceof EntityHitResult ehr && hit.getType() == Type.ENTITY) {
            Entity e = ehr.getEntity();
            return !(e instanceof AbstractTitan) && !(e instanceof TitanPart) && !(e instanceof Enemy);
        }

        return false;
    }

    public static boolean canHook(Entity e) {
        if (e.isSpectator() || !e.isPickable() || e instanceof ArmorStand) {
            return false;
        } else if (e == Minecraft.getInstance().player) {
            return false;
        } else {
            return Grapple.grabbable(e) ? true : e instanceof LivingEntity || e instanceof PartEntity;
        }
    }

    @Nullable
    public ClientOdm.Aim findAim(Player p, double yawOffsetDeg) {
        Vec3 eye = p.getEyePosition(1.0F);
        Vec3 look = p.getViewVector(1.0F);
        if (yawOffsetDeg != 0.0) {
            look = look.yRot((float)Math.toRadians(-yawOffsetDeg));
        }

        double range = OdmGear.hookRange();
        ClientOdm.Aim exact = trace(p, eye, look, range, false);
        if (exact != null) {
            return exact;
        } else {
            Vec3 rightV = look.cross(new Vec3(0.0, 1.0, 0.0));
            if (rightV.lengthSqr() < 1.0E-4) {
                rightV = new Vec3(1.0, 0.0, 0.0);
            }

            rightV = rightV.normalize();
            Vec3 upV = rightV.cross(look).normalize();

            for (double ang : new double[]{2.0, 4.0, 6.5}) {
                double s = Math.sin(Math.toRadians(ang));
                double c = Math.cos(Math.toRadians(ang));
                ClientOdm.Aim best = null;

                for (int k = 0; k < 8; k++) {
                    double a = (double)k * 3.141592653589793 / 4.0;
                    Vec3 off = rightV.scale(Math.cos(a)).add(upV.scale(Math.sin(a)));
                    Vec3 dir = look.scale(c).add(off.scale(s)).normalize();
                    ClientOdm.Aim hit = trace(p, eye, dir, range, true);
                    if (hit != null && (best == null || hit.distance < best.distance)) {
                        best = hit;
                    }
                }

                if (best != null) {
                    return best;
                }
            }

            return null;
        }
    }

    @Nullable
    private static ClientOdm.Aim trace(Player p, Vec3 eye, Vec3 dir, double range, boolean assisted) {
        Level level = p.level();
        Vec3 end = eye.add(dir.scale(range));
        BlockHitResult bhr = level.clip(new ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER, Fluid.NONE, p));
        Vec3 blockEnd = bhr.getType() == Type.MISS ? end : bhr.getLocation();
        Vec3 secHit = null;

        for (FallingSectionEntity fs : level.getEntitiesOfClass(FallingSectionEntity.class, new AABB(eye, blockEnd).inflate(32.0))) {
            Vec3 h = fs.clip(eye, blockEnd);
            if (h != null && (secHit == null || h.distanceToSqr(eye) < secHit.distanceToSqr(eye))) {
                secHit = h;
            }
        }

        if (secHit != null) {
            return new ClientOdm.Aim(secHit, secHit.distanceTo(eye), true, assisted);
        } else {
            EntityHitResult ehr = ProjectileUtil.getEntityHitResult(level, p, eye, blockEnd, new AABB(eye, blockEnd).inflate(1.0), ClientOdm::canHook, 0.0F);
            if (ehr != null) {
                return new ClientOdm.Aim(ehr.getLocation(), ehr.getLocation().distanceTo(eye), true, assisted);
            } else {
                return bhr.getType() != Type.MISS ? new ClientOdm.Aim(bhr.getLocation(), bhr.getLocation().distanceTo(eye), false, assisted) : null;
            }
        }
    }

    private void fire(LocalPlayer p, int i, int spreadSign, boolean hasGas) {
        HookState h = this.hooks[i];
        if (!hasGas) {
            if (!this.emptyClickPlayed) {
                p.level().playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), ModSounds.GAS_EMPTY.get(), SoundSource.PLAYERS, 0.6F, 1.5F, false);
                this.emptyClickPlayed = true;
            }
        } else {
            Vec3 origin = HookState.launcherPos(p, h.side, 1.0F);
            ClientOdm.Aim target = spreadSign != 0 ? this.findAim(p, OdmTuning.DUAL_SPREAD * (double)spreadSign) : null;
            if (target == null || target.distance < 6.0) {
                target = this.findAim(p, 0.0);
            }

            Vec3 point = target != null ? target.point : p.getEyePosition(1.0F).add(p.getViewVector(1.0F).scale(OdmGear.hookRange()));
            Vec3 dir = point.subtract(origin);
            if (dir.lengthSqr() < 1.0E-6) {
                dir = p.getViewVector(1.0F);
            }

            dir = dir.normalize();
            h.reset();
            this.anchorBlocks[i] = null;
            h.phase = HookState.Phase.FLYING;
            h.pos = origin;
            h.prevPos = origin;
            h.vel = dir.scale(8.0);
            PacketDistributor.sendToServer(new HookActionPayload(i, 0, origin, h.vel, -1, -1, Vec3.ZERO, 0.0F));
            p.level()
                .playLocalSound(
                    origin.x, origin.y, origin.z, ModSounds.HOOK_FIRE.get(), SoundSource.PLAYERS, 0.9F, 0.95F + p.getRandom().nextFloat() * 0.1F, false
                );
            ClientEffects.kick(0.6F);
            Vec3 puff = origin.add(dir.scale(0.4));

            for (int k = 0; k < 3; k++) {
                p.level()
                    .addParticle(
                        ModParticles.GAS.get(),
                        puff.x,
                        puff.y,
                        puff.z,
                        dir.x * 0.08 + (p.getRandom().nextDouble() - 0.5) * 0.04,
                        dir.y * 0.08 + 0.01,
                        dir.z * 0.08 + (p.getRandom().nextDouble() - 0.5) * 0.04
                    );
            }
        }
    }

    public void releaseAll(LocalPlayer p) {
        for (int i = 0; i < 2; i++) {
            this.release(p, i);
        }

        this.boosting = false;
        this.odmAirborne = false;
        this.impulse = Vec3.ZERO;
    }

    private void release(LocalPlayer p, int i) {
        HookState h = this.hooks[i];
        if (h.phase == HookState.Phase.FLYING || h.phase == HookState.Phase.ATTACHED) {
            Vec3 at = h.phase == HookState.Phase.ATTACHED ? h.anchor(p.level(), 1.0F) : h.pos;
            boolean wasAttached = h.isAttached();
            h.phase = HookState.Phase.RETRACTING;
            h.pos = at;
            h.prevPos = at;
            h.entityId = -1;
            h.reeling = false;
            h.tether = false;
            h.vel = Vec3.ZERO;
            this.anchorBlocks[i] = null;
            this.lastAnchor[i] = null;
            this.lostTicks[i] = 0;
            PacketDistributor.sendToServer(new HookActionPayload(i, 2, at, Vec3.ZERO, -1, -1, Vec3.ZERO, 0.0F));
            if (wasAttached) {
                p.level()
                    .playLocalSound(
                        p.getX(),
                        p.getY() + 1.0,
                        p.getZ(),
                        ModSounds.HOOK_RETRACT.get(),
                        SoundSource.PLAYERS,
                        0.5F,
                        1.0F + p.getRandom().nextFloat() * 0.15F,
                        false
                    );
            }
        }
    }

    private void tickHook(LocalPlayer p, int i) {
        HookState h = this.hooks[i];
        h.prevPos = h.pos;
        switch (h.phase) {
            case FLYING:
                h.age++;
                Vec3 from = h.pos;
                Vec3 to = from.add(h.vel);
                HitResult hit = traceHook(p, from, to);
                if (hit != null) {
                    this.attach(p, i, hit);
                } else {
                    h.pos = to;
                    h.traveled = h.traveled + h.vel.length();
                    if (h.traveled > OdmGear.hookRange() + 2.0 || h.age > 40) {
                        this.release(p, i);
                    }
                }
                break;
            case ATTACHED:
                h.age++;
                if (this.lostTicks[i] <= 0 && this.anchorValid(p, i)) {
                    this.lastAnchor[i] = h.anchor(p.level(), 1.0F);
                    double dist = h.anchor(p.level(), 1.0F).distanceTo(ropeOrigin(p));
                    if (dist > OdmGear.hookRange() + 24.0) {
                        this.release(p, i);
                    }
                } else if (!h.tether && this.regripOk[i]) {
                    if (this.lostTicks[i] == 0) {
                        Vec3 last = this.lastAnchor[i] != null ? this.lastAnchor[i] : h.pos;
                        h.entityId = -1;
                        h.partIndex = -1;
                        h.pos = last;
                        h.prevPos = last;
                        this.anchorBlocks[i] = null;
                    }

                    if (this.regrip(p, i)) {
                        this.lostTicks[i] = 0;
                    } else if (++this.lostTicks[i] > 8) {
                        this.release(p, i);
                    }
                } else {
                    this.release(p, i);
                }
                break;
            case RETRACTING:
                Vec3 target = HookState.launcherPos(p, h.side, 1.0F);
                Vec3 d = target.subtract(h.pos);
                double len = d.length();
                double step = 8.0 + this.speed;
                if (len <= step) {
                    h.reset();
                    h.pos = target;
                    h.prevPos = target;
                } else {
                    h.pos = h.pos.add(d.scale(step / len));
                }
        }
    }

    @Nullable
    private static HitResult traceHook(LocalPlayer p, Vec3 from, Vec3 to) {
        Level level = p.level();
        AABB span = new AABB(from, to).inflate(1.0);
        double best = 1.7976931348623157E308;
        Vec3 bestPt = null;
        Entity bestEnt = null;

        for (FallingSectionEntity fs : level.getEntitiesOfClass(FallingSectionEntity.class, span.inflate(32.0))) {
            Vec3 hit = fs.clip(from, to);
            if (hit != null) {
                double d = hit.distanceToSqr(from);
                if (d < best) {
                    best = d;
                    bestPt = hit;
                    bestEnt = fs;
                }
            }
        }

        BlockHitResult bhr = level.clip(new ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER, Fluid.NONE, p));
        Vec3 blockEnd = bhr.getType() == Type.MISS ? to : bhr.getLocation();
        if (bhr.getType() != Type.MISS && blockEnd.distanceToSqr(from) < best) {
            return bhr;
        } else {
            EntityHitResult ehr = ProjectileUtil.getEntityHitResult(level, p, from, blockEnd, span, ClientOdm::canHook, 0.2F);
            if (ehr != null && ehr.getLocation().distanceToSqr(from) < best) {
                return ehr;
            } else if (bestEnt != null && bestPt != null) {
                return new EntityHitResult(bestEnt, bestPt);
            } else {
                return bhr.getType() == Type.MISS ? null : bhr;
            }
        }
    }

    private void attach(LocalPlayer p, int i, HitResult hit) {
        HookState h = this.hooks[i];
        Level level = p.level();
        Vec3 point = hit.getLocation();
        Entity anchorEntity = null;
        TitanPart titanPart = null;
        BlockPos block = null;
        if (hit instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            anchorEntity = e instanceof PartEntity<?> part ? part.getParent() : e;
            if (e instanceof TitanPart tp) {
                titanPart = tp;
            }
        } else if (hit instanceof BlockHitResult bhr) {
            block = bhr.getBlockPos();
            point = point.add(Vec3.atLowerCornerOf(bhr.getDirection().getNormal()).scale(0.04));
        }

        h.phase = HookState.Phase.ATTACHED;
        h.pos = point;
        h.prevPos = point;
        h.vel = Vec3.ZERO;
        h.entityId = anchorEntity != null ? anchorEntity.getId() : -1;
        h.partIndex = titanPart != null ? titanPart.index : -1;
        if (titanPart != null && anchorEntity instanceof AbstractTitan titan) {
            h.localOffset = titan.partLocal(titanPart.index, point);
        } else if (anchorEntity instanceof FallingSectionEntity fs) {
            h.localOffset = fs.worldToLocal(point);
        } else {
            h.localOffset = anchorEntity != null ? HookState.toLocal(anchorEntity, point) : Vec3.ZERO;
        }

        h.tether = anchorEntity != null && Grapple.catches(p, anchorEntity);
        this.regripOk[i] = anchorEntity == null || anchorEntity instanceof FallingSectionEntity || anchorEntity instanceof DebrisEntity;
        h.ropeLength = Math.max(1.6, point.distanceTo(ropeOrigin(p)));
        h.age = 0;
        this.anchorBlocks[i] = block;
        PacketDistributor.sendToServer(new HookActionPayload(i, 1, point, Vec3.ZERO, h.entityId, h.partIndex, h.localOffset, (float)h.ropeLength));
        if (p.getVehicle() instanceof net.minecraft.world.entity.animal.horse.AbstractHorse) {
            // hooks bite while riding: you leave the saddle and the cable takes you
            p.stopRiding();
            this.impulse = this.impulse.add(0.0, 0.45, 0.0);
        }

        SoundEvent sound = anchorEntity != null ? ModSounds.HOOK_HIT_FLESH.get() : ModSounds.HOOK_HIT.get();
        level.playLocalSound(point.x, point.y, point.z, sound, SoundSource.PLAYERS, 1.0F, 0.95F + p.getRandom().nextFloat() * 0.1F, false);
        if (block != null) {
            BlockState state = level.getBlockState(block);
            if (!state.isAir()) {
                for (int k = 0; k < 8; k++) {
                    level.addParticle(
                        new BlockParticleOption(ParticleTypes.BLOCK, state),
                        point.x,
                        point.y,
                        point.z,
                        (p.getRandom().nextDouble() - 0.5) * 0.3,
                        p.getRandom().nextDouble() * 0.2,
                        (p.getRandom().nextDouble() - 0.5) * 0.3
                    );
                }
            }

            level.addParticle(ModParticles.SPARK.get(), point.x, point.y, point.z, 0.0, 0.05, 0.0);
        } else if (anchorEntity instanceof AbstractTitan t) {
            for (int k = 0; k < 5; k++) {
                level.addParticle(
                    t.isDummy() ? new BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS.defaultBlockState()) : ModParticles.BLOOD.get(),
                    point.x,
                    point.y,
                    point.z,
                    (p.getRandom().nextDouble() - 0.5) * 0.2,
                    p.getRandom().nextDouble() * 0.15,
                    (p.getRandom().nextDouble() - 0.5) * 0.2
                );
            }
        }
    }

    private boolean regrip(LocalPlayer p, int i) {
        HookState h = this.hooks[i];
        Vec3 last = h.pos;
        Vec3 n = last.subtract(ropeOrigin(p));
        if (n.lengthSqr() < 1.0E-4) {
            return false;
        } else {
            n = n.normalize();
            double drop = 2.0 + (double)this.lostTicks[i] * 1.5;
            Vec3[][] probes = new Vec3[][]{
                {last.subtract(n.scale(1.5)), last.add(n.scale(3.0))},
                {last.add(0.0, 2.0, 0.0), last.add(0.0, -drop - 4.0, 0.0)},
                {last.subtract(n.scale(1.0)), last.add(n.scale(2.0)).add(0.0, -drop, 0.0)}
            };

            for (Vec3[] seg : probes) {
                HitResult hit;
                boolean var10000;
                label49: {
                    hit = traceHook(p, seg[0], seg[1]);
                    label37:
                    if (!(hit instanceof BlockHitResult)) {
                        if (hit instanceof EntityHitResult ehr && (ehr.getEntity() instanceof FallingSectionEntity || ehr.getEntity() instanceof DebrisEntity)) {
                            break label37;
                        }

                        var10000 = false;
                        break label49;
                    }

                    var10000 = true;
                }

                boolean building = var10000;
                if (hit != null && hit.getType() != Type.MISS && building && hit.getLocation().distanceTo(last) < 4.0 + drop) {
                    this.attach(p, i, hit);
                    ClientEffects.shake(0.25F, 4);
                    return true;
                }
            }

            return false;
        }
    }

    private boolean anchorValid(LocalPlayer p, int i) {
        HookState h = this.hooks[i];
        Level level = p.level();
        if (h.entityId >= 0) {
            Entity e = level.getEntity(h.entityId);
            return e != null && e.isAlive() && !e.isRemoved();
        } else {
            BlockPos b = this.anchorBlocks[i];
            return b == null || !level.getBlockState(b).getCollisionShape(level, b).isEmpty();
        }
    }

    public static boolean bladesReady(Player p) {
        return p.getMainHandItem().getItem() instanceof BladeItem && !BladeItem.isBroken(p.getMainHandItem());
    }

    public boolean red(Player p) {
        if (this.overdrive > 0) {
            return true;
        } else {
            if (p.getMainHandItem().getItem() instanceof BladeItem b && b.tier == BladeTier.ACKERMAN) {
                return true;
            }

            return false;
        }
    }

    private void spin(LocalPlayer p, boolean drill, boolean enoughGas) {
        if (enoughGas) {
            this.cyclone = drill;
            this.spinTicks = drill ? 22 : 10;
            this.spinCooldown = this.overdrive > 0 ? 14 : 28;
            Vec3 push = p.getViewVector(1.0F);
            if (drill) {
                Vec3 sum = Vec3.ZERO;

                for (HookState h : this.hooks) {
                    if (h.isAttached() && h.reeling) {
                        sum = sum.add(this.anchorOf(h).subtract(ropeOrigin(p)).normalize());
                    }
                }

                if (sum.lengthSqr() > 1.0E-4) {
                    push = sum.normalize();
                }
            }

            this.impulse = this.impulse.add(push.scale(0.95 * (drill ? 0.7 : 0.45) * OdmGear.tier(p).dash));
            this.odmAirborne = true;
            PacketDistributor.sendToServer(new OdmActionPayload(4, drill ? 1 : 0));
            ClientEffects.spin(this.spinTicks, drill ? 3 : 1);
            p.swing(InteractionHand.MAIN_HAND);
            p.level().playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.7F, false);
            p.level().playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), ModSounds.GAS_BURST.get(), SoundSource.PLAYERS, 1.0F, 1.3F, false);
            if (drill) {
                p.level().playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), SoundEvents.TRIDENT_RIPTIDE_2.value(), SoundSource.PLAYERS, 1.4F, 1.2F, false);
            }
        }
    }

    private void spinEffects(LocalPlayer p) {
        int total = this.cyclone ? 22 : 10;
        int step = total - this.spinTicks;
        double turns = this.cyclone ? 3.0 : 1.0;
        double base = (double)(p.getYRot() * 0.017453292F) + (double)step * (6.2831854820251465 * turns / (double)total);
        double r = (this.cyclone ? 4.4 : 3.4) * 0.7;

        for (int k = 0; k < 3; k++) {
            double a = base + (double)((float)k * 6.2831855F) / 3.0;
            p.level()
                .addParticle(
                    ParticleTypes.SWEEP_ATTACK, p.getX() + Math.cos(a) * r, p.getY() + 1.0 + (double)(k - 1) * 0.4, p.getZ() + Math.sin(a) * r, 0.0, 0.0, 0.0
                );
        }

        if (step % 2 == 0) {
            float yaw = (float)(base * 180.0 / 3.1415927410125732);
            WorldFx.add(
                new WorldFx.SlashArc(
                    p.position().add(0.0, 1.0, 0.0),
                    yaw,
                    -90.0F + (p.getRandom().nextFloat() - 0.5F) * 30.0F,
                    0.0F,
                    (float)r + 0.6F,
                    this.red(p) ? 16720440 : 13625599,
                    this.cyclone
                )
            );
        }

        if (step % (this.cyclone ? 2 : 3) == 2) {
            p.swing(InteractionHand.MAIN_HAND);
        }
    }

    public int rushChargeNeeded() {
        return this.overdrive > 0 ? 6 : 12;
    }

    public float rushChargeFraction() {
        return this.rushCharge <= 2 ? 0.0F : Mth.clamp((float)(this.rushCharge - 2) / (float)(this.rushChargeNeeded() - 2), 0.0F, 1.0F);
    }

    private void chargeEffects(LocalPlayer p) {
        int need = this.rushChargeNeeded();
        if (this.rushCharge == need) {
            p.level().playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.2F, 1.6F, false);
            p.level().playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4F, 1.4F, false);
            ClientEffects.kick(1.2F);
        } else if (this.rushCharge > 2 && this.rushCharge < need && this.rushCharge % 3 == 0) {
            p.level()
                .playLocalSound(
                    p.getX(),
                    p.getY() + 1.0,
                    p.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS,
                    0.8F,
                    0.6F + (float)this.rushCharge / (float)need,
                    false
                );
        }

        if (this.rushCharge > 2) {
            Vec3 look = p.getViewVector(1.0F);
            float yaw = p.getYRot() * 0.017453292F;
            Vec3 side = new Vec3((double)(-Mth.cos(yaw)), 0.0, (double)(-Mth.sin(yaw)));
            int n = this.rushCharge >= need ? 3 : 1;

            for (int k = 0; k < n; k++) {
                double s = p.getRandom().nextBoolean() ? 0.45 : -0.45;
                Vec3 at = p.getEyePosition().add(look.scale(0.6)).add(side.scale(s)).add(0.0, -0.45, 0.0);
                p.level()
                    .addParticle(
                        this.rushCharge >= need && this.red(p) ? ModParticles.EMBER.get() : ParticleTypes.ELECTRIC_SPARK,
                        at.x,
                        at.y,
                        at.z,
                        (p.getRandom().nextDouble() - 0.5) * 0.1,
                        0.05,
                        (p.getRandom().nextDouble() - 0.5) * 0.1
                    );
            }
        }
    }

    private void rush(LocalPlayer p) {
        this.rushDir = p.getViewVector(1.0F);
        this.rushTicks = 7;
        this.rushCooldown = this.overdrive > 0 ? 15 : 30;
        this.hitstop = 0;

        for (int i = 0; i < 2; i++) {
            if (this.hooks[i].isActive()) {
                this.release(p, i);
            }
        }

        this.odmAirborne = true;
        PacketDistributor.sendToServer(new OdmActionPayload(5, 0));
        p.swing(InteractionHand.MAIN_HAND);
        p.level().playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 2.0F, 1.1F, false);
        p.level().playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), ModSounds.GAS_BURST.get(), SoundSource.PLAYERS, 1.4F, 0.8F, false);
        p.level().playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4F, 0.5F, false);
        ClientEffects.rush();
        Vec3 c = p.position().add(0.0, 1.0, 0.0);
        WorldFx.add(new WorldFx.SonicRing(c.add(this.rushDir.scale(1.5)), this.rushDir, 4.5F, 8, this.red(p) ? 16720440 : 16777215));
        WorldFx.add(new WorldFx.SonicRing(c, this.rushDir, 2.5F, 6, 16777215));

        for (int k = 0; k < 24; k++) {
            p.level()
                .addParticle(
                    ModParticles.GAS.get(),
                    c.x,
                    c.y,
                    c.z,
                    -this.rushDir.x * 0.4 + (p.getRandom().nextDouble() - 0.5) * 0.5,
                    -this.rushDir.y * 0.4 + (p.getRandom().nextDouble() - 0.5) * 0.5,
                    -this.rushDir.z * 0.4 + (p.getRandom().nextDouble() - 0.5) * 0.5
                );
        }
    }

    private static double heightAboveGround(Player p) {
        Vec3 from = p.position();
        BlockHitResult hit = p.level()
            .clip(new ClipContext(from, from.add(0.0, -64.0, 0.0), net.minecraft.world.level.ClipContext.Block.COLLIDER, Fluid.ANY, p));
        return hit.getType() == Type.MISS ? 64.0 : from.y - hit.getLocation().y;
    }

    private void dive(LocalPlayer p) {
        this.diveTicks = 120;
        this.diveStartY = p.getY();
        this.diveLock = true;
        this.diveSpeed = Math.max(1.2, -p.getDeltaMovement().y + 0.8);
        this.rushCharge = 0;

        for (int i = 0; i < 2; i++) {
            if (this.hooks[i].isActive()) {
                this.release(p, i);
            }
        }

        this.odmAirborne = true;
        PacketDistributor.sendToServer(new OdmActionPayload(8, 0));
        p.swing(InteractionHand.MAIN_HAND);
        p.level().playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), ModSounds.GAS_BURST.get(), SoundSource.PLAYERS, 1.6F, 0.6F, false);
        p.level().playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 2.0F, 0.7F, false);
        ClientEffects.kick(1.5F);
        Vec3 c = p.position().add(0.0, 1.0, 0.0);
        WorldFx.add(new WorldFx.SonicRing(c, new Vec3(0.0, -1.0, 0.0), 4.0F, 8, 16777215));
    }

    private void diveMove(Player p) {
        if (this.diveTicks > 2) {
            this.diveTicks--;
        }

        Vec3 look = p.getViewVector(1.0F);
        Vec3 dir = new Vec3(look.x * 0.6, Math.min(-0.75, look.y), look.z * 0.6).normalize();
        double fallen = Math.max(0.0, this.diveStartY - p.getY());
        double cap = 5.44 * OdmGear.tier(p).maxSpeed * (1.0 + fallen / 90.0);
        this.diveSpeed = Math.min(cap, this.diveSpeed + 0.1 + this.diveSpeed * 0.045);
        Vec3 v = dir.scale(this.diveSpeed);
        p.setDeltaMovement(v);
        p.fallDistance = 0.0F;
        p.move(MoverType.SELF, v);
        this.odmAirborne = true;
        this.diveEffects(p, dir);
        boolean landed = p.onGround() || p.verticalCollision || p.horizontalCollision || p.isInWater() || p.isInLava();
        if (landed || this.diveTicks == 0) {
            if (landed && !p.isInWater() && !p.isInLava()) {
                PacketDistributor.sendToServer(new OdmActionPayload(9, (int)(this.diveSpeed * 100.0)));
                ClientEffects.shake((float)Math.min(2.0, 0.4 + this.diveSpeed * 0.1 + Math.max(0.0, this.diveStartY - p.getY()) / 150.0), 24);
                p.setDeltaMovement(0.0, 0.25, 0.0);
            } else {
                p.setDeltaMovement(v.scale(0.4));
            }

            this.diveTicks = 0;
        }

        p.calculateEntityAnimation(false);
    }

    private void diveEffects(Player p, Vec3 dir) {
        Level level = p.level();
        Vec3 c = p.position().add(0.0, 0.9, 0.0);
        double s = this.diveSpeed;
        double heat = Math.max(0.0, this.diveStartY - p.getY()) / 30.0;
        if (heat > 1.0) {
            int n = (int)Math.min(40.0, heat * 4.0);
            double rad = Math.min(4.0, 0.6 + heat * 0.35);

            for (int k = 0; k < n; k++) {
                double a = p.getRandom().nextDouble() * 3.141592653589793 * 2.0;
                double r = rad * Math.sqrt(p.getRandom().nextDouble());
                level.addAlwaysVisibleParticle(
                    k % 3 == 0 ? ModParticles.EMBER.get() : ModParticles.FIREBALL.get(),
                    c.x + Math.cos(a) * r,
                    c.y + 0.4 + p.getRandom().nextDouble() * 0.6,
                    c.z + Math.sin(a) * r,
                    -dir.x * 0.6 + (p.getRandom().nextDouble() - 0.5) * 0.2,
                    -dir.y * 0.6,
                    -dir.z * 0.6 + (p.getRandom().nextDouble() - 0.5) * 0.2
                );
            }

            WorldFx.add(new WorldFx.RushTrail(c.subtract(dir.scale(Math.min(40.0, 6.0 + heat * 4.0))), c, (float)Math.min(5.0, 1.0 + heat * 0.5), 16742944));
            if (heat > 2.5) {
                ClientEffects.shake((float)Math.min(0.6, heat * 0.06), 3);
            }

            if (heat > 2.0 && this.diveTicks % 3 == 0) {
                WorldFx.add(new WorldFx.SonicRing(c, dir.scale(-1.0), (float)Math.min(14.0, 2.0 + heat * 1.2), 9, 16747050));
            }
        }

        WorldFx.add(
            new WorldFx.RushTrail(
                c.subtract(dir.scale(Math.min(8.0, s * 1.8))), c, (float)(0.6 + s * 0.15), s > 3.5 ? 16742944 : (this.red(p) ? 16720440 : 14544639)
            )
        );

        for (int k = 0; k < 2 + (int)s; k++) {
            double ox = (p.getRandom().nextDouble() - 0.5) * 1.2;
            double oz = (p.getRandom().nextDouble() - 0.5) * 1.2;
            level.addParticle(
                s > 3.5 ? ModParticles.FIREBALL.get() : ModParticles.WIND_STREAK.get(), c.x + ox, c.y + 0.5, c.z + oz, -dir.x * 0.3, -dir.y * 0.3, -dir.z * 0.3
            );
        }

        if (s > 3.0) {
            level.addParticle(ModParticles.EMBER.get(), c.x, c.y + 1.0, c.z, -dir.x * 0.5, -dir.y * 0.5, -dir.z * 0.5);
        }

        if (this.diveTicks % 5 == 0) {
            WorldFx.add(new WorldFx.SonicRing(c.add(dir.scale(1.5)), dir.scale(-1.0), (float)(1.5 + s * 0.6), 7, s > 3.5 ? 16756848 : 16777215));
            level.playLocalSound(
                c.x, c.y, c.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, (float)Math.min(1.0, 0.2 + s * 0.12), (float)(0.6 + s * 0.12), false
            );
        }
    }

    private void dash(LocalPlayer p, boolean enoughGas) {
        if (enoughGas) {
            Vec3 look = p.getViewVector(1.0F);
            float fwd = p.input.forwardImpulse;
            float strafe = p.input.leftImpulse;
            Vec3 dir;
            if (Math.abs(fwd) < 0.1F && Math.abs(strafe) < 0.1F) {
                dir = look;
            } else {
                float yaw = p.getYRot() * 0.017453292F;
                Vec3 leftV = new Vec3((double)Mth.cos(yaw), 0.0, (double)Mth.sin(yaw));
                Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
                Vec3 forwardV = fwd > 0.0F ? look : flatLook.normalize();
                dir = forwardV.scale((double)fwd).add(leftV.scale((double)strafe));
            }

            dir = dir.normalize().add(0.0, 0.12, 0.0).normalize();
            this.impulse = this.impulse.add(dir.scale(0.95 * OdmGear.tier(p).dash * Skills.dashMul(p) * (this.overdrive > 0 ? 1.2 : 1.0)));
            this.dashCooldown = Skills.dashCooldown(p, this.overdrive > 0 ? 8 : 16);
            this.ticksSinceDash = 0;
            this.odmAirborne = true;
            PacketDistributor.sendToServer(new OdmActionPayload(0, 0));
            p.level()
                .playLocalSound(
                    p.getX(), p.getY() + 1.0, p.getZ(), ModSounds.GAS_BURST.get(), SoundSource.PLAYERS, 1.0F, 0.9F + p.getRandom().nextFloat() * 0.2F, false
                );
            ClientEffects.shake(0.25F, 5);
            Vec3 back = p.position().add(0.0, 1.0, 0.0).subtract(dir.scale(0.5));

            for (int k = 0; k < 14; k++) {
                p.level()
                    .addParticle(
                        ModParticles.GAS.get(),
                        back.x,
                        back.y,
                        back.z,
                        -dir.x * 0.25 + (p.getRandom().nextDouble() - 0.5) * 0.15,
                        -dir.y * 0.25 + (p.getRandom().nextDouble() - 0.5) * 0.15,
                        -dir.z * 0.25 + (p.getRandom().nextDouble() - 0.5) * 0.15
                    );
            }
        }
    }

    private void emitGas(LocalPlayer p) {
        if (this.boosting) {
            Vec3 look = p.getViewVector(1.0F);
            Vec3 base = p.position().add(0.0, 0.95, 0.0).subtract(look.scale(0.45));
            Vec3 v = this.velocity.scale(0.6);

            for (int k = 0; k < 2; k++) {
                p.level()
                    .addParticle(
                        ModParticles.GAS.get(),
                        base.x + (p.getRandom().nextDouble() - 0.5) * 0.3,
                        base.y + (p.getRandom().nextDouble() - 0.5) * 0.3,
                        base.z + (p.getRandom().nextDouble() - 0.5) * 0.3,
                        v.x - look.x * 0.2,
                        v.y - look.y * 0.2,
                        v.z - look.z * 0.2
                    );
            }
        }
    }

    private void tryReload(LocalPlayer p, ItemStack gear) {
        ItemStack blades = p.getMainHandItem();
        if (blades.getItem() instanceof BladeItem && this.reloadTicks <= 0 && blades.getDamageValue() != 0) {
            if (OdmGear.getSpareBlades(gear) <= 0) {
                PacketDistributor.sendToServer(new OdmActionPayload(1, 0));
            } else {
                this.reloadTicks = 14;
                PacketDistributor.sendToServer(new OdmActionPayload(1, 0));
                p.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    private void sendFlags(LocalPlayer p) {
        int flags = 0;
        if (this.boosting) {
            flags |= 1;
        }

        if (this.left.isAttached() && this.left.reeling) {
            flags |= 2;
        }

        if (this.right.isAttached() && this.right.reeling) {
            flags |= 4;
        }

        if (this.odmAirborne && !p.onGround()) {
            flags |= 8;
        }

        boolean resend = flags != 0 && ++this.resendTimer >= 20;
        if (flags != this.lastFlags || resend) {
            PacketDistributor.sendToServer(new OdmInputPayload(flags));
            this.lastFlags = flags;
            this.resendTimer = 0;
        }
    }

    public static record Aim(Vec3 point, double distance, boolean entity, boolean assisted) {
    }
}
