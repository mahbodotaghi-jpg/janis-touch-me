package dev.nitro.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("attackCooldown")
    void nitro$setAttackCooldown(int value);

    @Accessor("itemUseCooldown")
    void nitro$setItemUseCooldown(int value);
}
