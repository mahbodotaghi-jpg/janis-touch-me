package dev.nitro.util;

import dev.nitro.NitroClient;
import dev.nitro.module.modules.Hitbox;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public class HitboxUtil {
    /** Extra targeting margin for an entity (used by EntityMixin when the crosshair target is computed). */
    public static float margin(Entity e) {
        if (!(e instanceof PlayerEntity p) || NitroClient.modules == null) return 0f;
        if (p == MinecraftClient.getInstance().player) return 0f;
        Hitbox h = NitroClient.modules.get(Hitbox.class);
        return h != null && h.isEnabled() ? h.expansion() : 0f;
    }
}
