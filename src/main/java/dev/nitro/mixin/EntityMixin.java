package dev.nitro.mixin;

import dev.nitro.module.modules.Freecam;
import dev.nitro.util.HitboxUtil;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    // Mouse look goes to the freecam camera instead of the real player.
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
    private void nitro$freecamLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (Freecam.redirectLook((Entity) (Object) this, cursorDeltaX, cursorDeltaY)) ci.cancel();
    }

    // Hitbox: bigger crosshair target box for other players.
    @Inject(method = "getTargetingMargin", at = @At("HEAD"), cancellable = true, require = 0)
    private void nitro$margin(CallbackInfoReturnable<Float> cir) {
        float m = HitboxUtil.margin((Entity) (Object) this);
        if (m > 0f) cir.setReturnValue(m);
    }
}
