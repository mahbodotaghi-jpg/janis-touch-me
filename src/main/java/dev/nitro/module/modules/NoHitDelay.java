package dev.nitro.module.modules;

import dev.nitro.mixin.MinecraftClientAccessor;
import dev.nitro.module.Category;
import dev.nitro.module.Module;
import dev.nitro.setting.BooleanSetting;
import dev.nitro.util.Safe;

/** Clears the client's click cooldowns, so you can attack (and optionally place) with no built-in pause. Throttled while SafeMode is on. */
public class NoHitDelay extends Module {
    private final BooleanSetting useDelay = add(new BooleanSetting("Also use delay", true));

    public NoHitDelay() { super("NoHitDelay", "Removes the client-side attack/use click delay.", Category.COMBAT); }

    @Override
    public void onPreTick() {
        if (mc.player == null) return;
        MinecraftClientAccessor acc = (MinecraftClientAccessor) mc;
        if (Safe.on()) {
            // SafeMode: only every third tick, so the click rate stays human-looking
            if (mc.player.age % 3 == 0) acc.nitro$setAttackCooldown(0);
            return;
        }
        acc.nitro$setAttackCooldown(0);
        if (useDelay.get()) acc.nitro$setItemUseCooldown(0);
    }
}
