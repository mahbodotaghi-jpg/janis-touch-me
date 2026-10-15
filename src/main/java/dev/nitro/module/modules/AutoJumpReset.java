package dev.nitro.module.modules;

import dev.nitro.NitroClient;
import dev.nitro.module.Category;
import dev.nitro.module.Module;
import dev.nitro.setting.ModeSetting;
import dev.nitro.setting.NumberSetting;
import dev.nitro.util.KeyUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.concurrent.ThreadLocalRandom;

/**
 * "Jump reset": jump the moment you take a hit (less knockback while sprinting).
 * "W-tap": release forward/sprint for a tick right after you hit someone (extra knockback on them).
 */
public class AutoJumpReset extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Jump reset", "W-tap"));
    private final NumberSetting chance = add(new NumberSetting("Chance %", 100, 0, 100, 5));
    private int lastHurt, tapTicks;
    private boolean pendingJump, jumpHeld, tapping;

    public AutoJumpReset() { super("AutoJumpReset", "Jump reset when hit, or W-tap when you hit.", Category.COMBAT); }

    private boolean roll() { return ThreadLocalRandom.current().nextInt(100) < chance.get(); }

    /** called from ClientPlayerInteractionManagerMixin after you attack something */
    public static void onAttack(Entity target) {
        if (NitroClient.modules == null || !(target instanceof LivingEntity)) return;
        AutoJumpReset m = NitroClient.modules.get(AutoJumpReset.class);
        if (m != null && m.isEnabled() && m.mode.get().equals("W-tap") && m.roll()) m.tapTicks = 1;
    }

    @Override
    public void onDisable() {
        pendingJump = false;
        tapTicks = 0;
        if (mc.options != null) {
            if (jumpHeld) mc.options.jumpKey.setPressed(KeyUtil.physical(mc.options.jumpKey));
            if (tapping) {
                mc.options.forwardKey.setPressed(KeyUtil.physical(mc.options.forwardKey));
                mc.options.sprintKey.setPressed(KeyUtil.physical(mc.options.sprintKey));
            }
        }
        jumpHeld = tapping = false;
    }

    @Override
    public void onPreTick() {
        if (mc.player == null) return;

        if (jumpHeld) {
            mc.options.jumpKey.setPressed(KeyUtil.physical(mc.options.jumpKey));
            jumpHeld = false;
        }
        if (pendingJump && mc.player.isOnGround()) {
            mc.options.jumpKey.setPressed(true);
            jumpHeld = true;
        }
        pendingJump = false;

        if (tapTicks > 0) {
            mc.options.forwardKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);
            mc.player.setSprinting(false);
            tapTicks--;
            tapping = true;
        } else if (tapping) {
            mc.options.forwardKey.setPressed(KeyUtil.physical(mc.options.forwardKey));
            mc.options.sprintKey.setPressed(KeyUtil.physical(mc.options.sprintKey));
            tapping = false;
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        int h = mc.player.hurtTime;
        if (mode.get().equals("Jump reset") && h > lastHurt && mc.player.isOnGround() && roll()) pendingJump = true;
        lastHurt = h;
    }
}
