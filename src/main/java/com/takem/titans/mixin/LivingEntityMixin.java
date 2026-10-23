package com.takem.titans.mixin;

import com.takem.titans.odm.OdmMovementHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LivingEntity.class})
public abstract class LivingEntityMixin {
    @Inject(
        method = {"travel"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void takemtitans$odmTravel(Vec3 input, CallbackInfo ci) {
        if (OdmMovementHooks.tryTravel((LivingEntity)(Object)this, input)) {
            ci.cancel();
        }
    }
}
