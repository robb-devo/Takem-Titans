package com.takem.titans.mixin;

import com.takem.titans.client.fx.ColossalFx;
import com.takem.titans.client.fx.EpicFx;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ClientLevel.class})
public abstract class ClientLevelMixin {
    @Inject(
        method = {"getSkyColor"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private void takemtitans$burningSky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        float h = ColossalFx.skyHeat(partialTick);
        if (h > 0.002F) {
            cir.setReturnValue(((Vec3)cir.getReturnValue()).lerp(new Vec3(1.0, 0.4, 0.12), (double)h * 0.85));
        }

        if (EpicFx.skyTint(partialTick) > 0.002F) {
            cir.setReturnValue(EpicFx.applyTint((Vec3)cir.getReturnValue(), partialTick, 0.85F));
        }
    }

    @Inject(
        method = {"getCloudColor"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private void takemtitans$burningClouds(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        float h = ColossalFx.skyHeat(partialTick);
        if (h > 0.002F) {
            cir.setReturnValue(((Vec3)cir.getReturnValue()).lerp(new Vec3(0.55, 0.3, 0.2), (double)h * 0.8));
        }

        if (EpicFx.skyTint(partialTick) > 0.002F) {
            cir.setReturnValue(EpicFx.applyTint((Vec3)cir.getReturnValue(), partialTick, 0.7F));
        }
    }
}
