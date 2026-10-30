package com.takem.titans.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.takem.titans.TakemTitans;
import com.takem.titans.client.fx.CombatFx;
import com.takem.titans.client.fx.PostFx;
import com.takem.titans.item.OdmPistolsItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * The ODM pistols in first person: both guns drawn, one in each hand, each kicking up and back when it fires with a star
 * of muzzle fire; the barrels tipped out and reseated on a reload; a shiver and a glow in the muzzles while a volley
 * charges. Plus the rounds and the charge around the crosshair, and the recoil's shove on the shooter.
 */
@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class PistolClient {
    public static final ResourceLocation HUD = TakemTitans.id("odm_pistols");
    private static final float[] KICK = new float[2];
    private static final float[] KICK_O = new float[2];
    private static final int[] FLASH = new int[2];
    private static float spin;

    private PistolClient() {
    }

    /** The shooter's own client, the instant the gun goes off: kick, flash, camera, and the shove of the recoil. */
    public static void fired(Player player, int barrel, boolean volley) {
        Minecraft mc = Minecraft.getInstance();
        if (player != mc.player) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            if (volley || barrel % 2 == i) {
                KICK[i] = volley ? 1.7F : 1.0F;
                FLASH[i] = volley ? 3 : 2;
            }
        }
        spin = player.getRandom().nextFloat() * 6.2831855F;
        ClientEffects.kick(volley ? 10.0F : 3.0F);
        ClientEffects.shake(volley ? 1.3F : 0.3F, volley ? 12 : 4);
        PostFx.kick(volley ? 1.1F : 0.28F, volley ? 0.9F : 0.14F);
        // on the ground it rocks you; in the air it throws you back along the line of fire
        Vec3 look = player.getLookAngle();
        boolean ground = player.onGround();
        double push = ground ? (volley ? 0.75 : 0.06) : (volley ? 2.3 : 0.5);
        Vec3 v = player.getDeltaMovement().subtract(look.scale(push));
        if (volley && ground) {
            v = v.add(0.0, 0.42, 0.0);
        }
        player.setDeltaMovement(v);
        if (!ground || volley) {
            ClientOdm.INSTANCE.odmAirborne = true;
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        for (int i = 0; i < 2; i++) {
            KICK_O[i] = KICK[i];
            KICK[i] *= 0.6F;
            if (FLASH[i] > 0) {
                FLASH[i]--;
            }
        }
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && charge(p, 0.0F) >= 1.0F) {
            // fully charged: the guns too hot to look at
            PostFx.hold(0.3F, 0.08F, 0.2F, 1.0F, 0.6F, 0.3F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHand(RenderHandEvent event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        ItemStack guns = p.getMainHandItem();
        if (!(guns.getItem() instanceof OdmPistolsItem)) {
            return;
        }
        boolean main = event.getHand() == InteractionHand.MAIN_HAND;
        if (!main && !p.getOffhandItem().isEmpty()) {
            return;
        }
        event.setCanceled(true);
        HumanoidArm arm = main ? p.getMainArm() : p.getMainArm().getOpposite();
        // the game dips an item out of view each time it's used; the guns stay up and kick instead
        draw(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), p, guns, arm == HumanoidArm.RIGHT ? 1 : -1, main ? 0 : 1, 0.0F, event.getPartialTick());
    }

    private static void draw(PoseStack ps, MultiBufferSource buffers, int light, LocalPlayer p, ItemStack guns, int side, int i, float equip, float pt) {
        float k = Mth.lerp(pt, KICK_O[i], KICK[i]);
        float reload = reload(p, guns, pt);
        float charge = charge(p, pt);
        ps.pushPose();
        ps.translate((float)side * 0.32F, -0.33F - equip * 0.6F - reload * 0.3F, -0.68F);
        ps.mulPose(Axis.YP.rotationDegrees((float)side * 4.5F));
        // recoil: back and up, the muzzle climbing, a twist outward
        ps.translate(0.0F, k * 0.03F, k * 0.14F);
        ps.mulPose(Axis.XP.rotationDegrees(k * 21.0F));
        ps.mulPose(Axis.ZP.rotationDegrees((float)(-side) * k * 6.0F));
        // reload: the guns tip down and roll out as the spent barrels drop, then come back up
        ps.mulPose(Axis.XP.rotationDegrees(-reload * 64.0F));
        ps.mulPose(Axis.ZP.rotationDegrees((float)side * reload * 38.0F));
        if (charge > 0.0F) {
            float j = charge * charge * 0.008F;
            ps.translate((p.getRandom().nextFloat() - 0.5F) * j, (p.getRandom().nextFloat() - 0.5F) * j, 0.0F);
        }
        ps.scale(0.42F, 0.42F, 0.42F);
        Minecraft.getInstance()
            .getItemRenderer()
            .renderStatic(
                p, guns, side > 0 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, side < 0, ps, buffers, p.level(), light,
                OverlayTexture.NO_OVERLAY, p.getId() + i
            );
        if (FLASH[i] > 0 || charge > 0.02F) {
            // the top barrel's muzzle, in the model's own space
            ps.translate(0.0F, 0.1375F, -0.52F);
            CombatFx.handFlash(ps, buffers, FLASH[i] > 0 ? ((float)FLASH[i] - pt * 0.5F) / 2.0F : 0.0F, charge, spin + (float)i * 1.3F);
        }
        ps.popPose();
    }

    /** 0 to 1 and back over a reload (the cooldown that refills the barrels), else 0. */
    private static float reload(LocalPlayer p, ItemStack guns, float pt) {
        float pct = p.getCooldowns().getCooldownPercent(guns.getItem(), pt);
        return pct > 0.0F && OdmPistolsItem.rounds(guns) >= OdmPistolsItem.ROUNDS ? Mth.sin(pct * 3.1415927F) : 0.0F;
    }

    private static float charge(LocalPlayer p, float pt) {
        return p.isUsingItem() && p.getUseItem().getItem() instanceof OdmPistolsItem
            ? Mth.clamp(((float)p.getTicksUsingItem() + pt) / (float)OdmPistolsItem.CHARGE, 0.0F, 1.0F)
            : 0.0F;
    }

    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson() || !(p.getMainHandItem().getItem() instanceof OdmPistolsItem)) {
            return;
        }
        float pt = delta.getGameTimeDeltaPartialTick(false);
        ItemStack guns = p.getMainHandItem();
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        int rounds = OdmPistolsItem.rounds(guns);
        boolean reloading = reload(p, guns, pt) > 0.0F || p.getCooldowns().getCooldownPercent(guns.getItem(), pt) > 0.0F && rounds >= OdmPistolsItem.ROUNDS;
        // the four barrels: two pips either side of the crosshair, the spent ones dark
        for (int b = 0; b < OdmPistolsItem.ROUNDS; b++) {
            int x = cx + (b % 2 == 0 ? 1 : -1) * (10 + b / 2 * 4);
            boolean live = !reloading && b >= OdmPistolsItem.ROUNDS - rounds;
            g.fill(x - 1, cy - 3, x + 1, cy + 4, live ? 0xFFF2D8A0 : 0x50FFFFFF);
        }
        if (reloading) {
            ring(g, cx, cy, 14, 1.0F - p.getCooldowns().getCooldownPercent(guns.getItem(), pt), 0xFFE8B04A, 0x40FFFFFF);
        }
        float charge = charge(p, pt);
        if (charge > 0.0F) {
            ring(g, cx, cy, 18, charge, charge >= 1.0F ? 0xFFFF7A3A : 0xFFFFC870, 0x30FFFFFF);
            if (charge >= 1.0F) {
                Component v = Component.translatable("hud.takemtitans.pistols.volley");
                int a = (int)(160.0F + 95.0F * Mth.sin(((float)p.tickCount + pt) * 0.8F)) << 24;
                g.drawString(mc.font, v, cx - mc.font.width(v) / 2, cy + 24, a | 0xFF9A50, true);
            }
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, float fill, int on, int off) {
        int n = 48;
        for (int i = 0; i < n; i++) {
            double a = -Math.PI / 2.0 + (double)i / (double)n * Math.PI * 2.0;
            int x = cx + (int)Math.round(Math.cos(a) * (double)r);
            int y = cy + (int)Math.round(Math.sin(a) * (double)r);
            g.fill(x, y, x + 1, y + 1, (float)i / (float)n < fill ? on : off);
        }
    }
}
