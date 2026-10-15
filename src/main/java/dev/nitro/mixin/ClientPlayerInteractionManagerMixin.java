package dev.nitro.mixin;

import dev.nitro.module.modules.AutoJumpReset;
import dev.nitro.module.modules.CrystalOptimizer;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackEntity", at = @At("RETURN"), require = 0)
    private void nitro$onAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        CrystalOptimizer.onAttack(target);
        AutoJumpReset.onAttack(target);
    }
}
