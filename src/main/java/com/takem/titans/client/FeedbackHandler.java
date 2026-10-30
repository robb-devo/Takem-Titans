package com.takem.titans.client;

import com.takem.titans.client.fx.ImpactFrames;
import com.takem.titans.client.gui.ScreenFx;
import com.takem.titans.client.hud.OdmHud;
import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.odm.HookSide;
import com.takem.titans.odm.HookState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class FeedbackHandler {
    private FeedbackHandler() {
    }

    public static void handle(FeedbackPayload msg, IPayloadContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        switch (msg.kind()) {
            case 0:
                OdmHud.addHit(msg.a(), msg.b(), msg.c());
                // the shake, red flash and slash across the view are a blade's; a shot or a titan's fist has its own
                if (mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof com.takem.titans.item.OdmPistolsItem) && !ShiftClient.local()) {
                    ClientEffects.onHit(msg.a(), msg.b() == 1);
                }
                MusicDirector.combat();
                break;
            case 1:
                OdmHud.addKill(msg.a(), msg.b(), msg.c());
                ClientEffects.onKill();
                MusicDirector.combat();
                break;
            case 2:
                ClientEffects.shake((float)msg.a() / 100.0F, msg.b());
                break;
            case 3:
                ClientTitanState.setGrabbed(msg.a());
                MusicDirector.combat();
                break;
            case 4:
                Waypoints.add(new BlockPos(msg.a(), msg.b(), msg.c()));
                break;
            case 5:
                mc.gui.setOverlayMessage(Component.translatable(msg.text()), false);
                break;
            case 6:
                OdmHud.bladesBroke();
                break;
            case 7:
                RemoteHooks.setBoosting(msg.a(), msg.b() != 0);
                break;
            case 8:
                OdmHud.setCombo(msg.a(), msg.b());
                break;
            case 9:
                boolean starting = msg.a() > 0 && ClientOdm.INSTANCE.overdrive <= 0;
                ClientOdm.INSTANCE.overdrive = msg.a();
                if (starting) {
                    OdmHud.overdriveStarted();
                    ClientEffects.shake(0.7F, 14);
                    ClientEffects.flash(16720440, 0.9F);
                }
                break;
            case 10:
                ImpactFrames.trigger((float)msg.a() / 100.0F, msg.b(), (float)msg.c() / 100.0F);
                break;
            case 11:
                if (mc.player != null) {
                    double up = (double)msg.a() / 100.0;
                    mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, up, mc.player.getDeltaMovement().z);
                    ClientOdm.INSTANCE.odmAirborne = true;
                    ClientEffects.kick(0.8F);
                }
                break;
            case 12:
                mc.gui.setOverlayMessage(Component.translatable("message.takemtitans.carry_hint"), false);
                break;
            case 13:
                if (mc.level != null && mc.player != null) {
                    HookState h = ClientOdm.INSTANCE.hook(HookSide.byIndex(msg.a()));
                    Entity e = mc.level.getEntity(msg.b());
                    if (e != null) {
                        h.phase = HookState.Phase.ATTACHED;
                        h.entityId = e.getId();
                        h.partIndex = -1;
                        h.tether = true;
                        h.localOffset = e instanceof FallingSectionEntity fs ? fs.worldToLocal(h.pos) : HookState.toLocal(e, h.pos);
                        h.ropeLength = Math.max(1.6, h.anchor(mc.level, 1.0F).distanceTo(mc.player.position().add(0.0, 0.95, 0.0)));
                    }
                }
                break;
            case 14:
                ClientOdm.INSTANCE.dashCooldown = 0;
                ClientOdm.INSTANCE.spinCooldown = 0;
                ClientOdm.INSTANCE.rushCooldown = 0;
                ClientEffects.flash(13625599, 0.5F);
                break;
            case 15:
                if (mc.player != null) {
                    double up = (double)msg.a() / 100.0;
                    Vec3 v = mc.player.getDeltaMovement();
                    mc.player.setDeltaMovement(v.x, Math.max(v.y, up), v.z);
                    ClientOdm.INSTANCE.odmAirborne = true;
                    ClientEffects.shake(0.5F, 10);
                }
                break;
            case 16:
                ScreenFx.blood((float)msg.a() / 100.0F, msg.b());
                break;
            case 30:
                ShiftClient.status(msg.a(), msg.b(), msg.c());
                break;
            case 31:
                if (mc.player != null) {
                    mc.player.setDeltaMovement((double)msg.a() / 100.0, (double)msg.b() / 100.0, (double)msg.c() / 100.0);
                }
                break;
            case 17:
                if (msg.b() >= 0 && msg.b() < ClientOdm.INSTANCE.cd.length) {
                    ClientOdm.INSTANCE.cd[msg.b()] = msg.a();
                    ClientOdm.INSTANCE.cdMax[msg.b()] = Math.max(1, msg.a());
                }
        }
    }
}
