package dev.nitro.module.modules;

import dev.nitro.NitroClient;
import dev.nitro.module.Category;
import dev.nitro.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;

/** Removes a crystal on your side the moment you hit it, instead of waiting for the server. */
public class CrystalOptimizer extends Module {
    public CrystalOptimizer() {
        super("CrystalOptimizer", "Removes crystals instantly client-side when you hit them.", Category.COMBAT);
    }

    /** Called from ClientPlayerInteractionManagerMixin after an attack packet was sent. */
    public static void onAttack(Entity target) {
        if (!(target instanceof EndCrystalEntity) || NitroClient.modules == null) return;
        CrystalOptimizer m = NitroClient.modules.get(CrystalOptimizer.class);
        if (m == null || !m.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        mc.world.removeEntity(target.getId(), Entity.RemovalReason.KILLED);
    }
}
