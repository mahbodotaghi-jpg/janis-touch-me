package dev.nitro.module.modules;

import dev.nitro.module.Category;
import dev.nitro.module.Module;
import dev.nitro.util.Rotations;
import dev.nitro.util.Safe;
import dev.nitro.setting.NumberSetting;
import dev.nitro.util.InvUtil;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Hold right click while aiming at a block: places two respawn anchors next to each other, charges both
 * with glowstone, then detonates them one after the other. Needs anchors, glowstone and another item in the hotbar.
 */
public class DoubleAnchor extends Module {
    private enum Step { IDLE, PLACE_A, PLACE_B, CHARGE_A, CHARGE_B, BOOM_A, BOOM_B }

    private final NumberSetting delay = add(new NumberSetting("Step delay (ticks)", 1, 0, 5, 1));
    private final NumberSetting explodeSlot = add(new NumberSetting("Explode slot", 1, 1, 9, 1));

    private Step step = Step.IDLE;
    private BlockPos a, b;
    private BlockHitResult placeHit;
    private int timer, cooldown;

    public DoubleAnchor() { super("DoubleAnchor", "Places, charges and detonates two respawn anchors in a row.", Category.COMBAT); }

    @Override
    public void onDisable() { reset(); }

    private void reset() { step = Step.IDLE; a = b = null; placeHit = null; timer = 0; }

    private boolean freeSpot(BlockPos p) {
        var st = mc.world.getBlockState(p);
        return st.isAir() || st.isReplaceable();
    }

    private boolean inReach(BlockPos p) {
        return mc.player.getEyePos().distanceTo(Vec3d.ofCenter(p)) <= Safe.blockReach(4.6);
    }

    private BlockPos findSecond(BlockPos first) {
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos p = first.offset(d);
            if (!freeSpot(p) || !inReach(p)) continue;
            var below = mc.world.getBlockState(p.down());
            if (below.isAir() || below.isReplaceable()) continue;
            return p;
        }
        return null;
    }

    private BlockHitResult top(BlockPos p) {
        return new BlockHitResult(Vec3d.ofCenter(p).add(0, 0.5, 0), Direction.UP, p, false);
    }

    /** looks at the target point first when SafeMode asks for it; returns false while still turning */
    private boolean use(BlockHitResult hit) {
        if (Safe.rotate() && !Rotations.aim(hit.getPos())) return false;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private boolean isAnchor(BlockPos p) { return mc.world.getBlockState(p).isOf(Blocks.RESPAWN_ANCHOR); }

    private boolean selectNonGlow() {
        int slot = (int) Math.round(explodeSlot.get()) - 1;
        if (!mc.player.getInventory().getStack(slot).isOf(Items.GLOWSTONE)) { InvUtil.selectSlot(slot); return true; }
        for (int i = 0; i < 9; i++) {
            if (!mc.player.getInventory().getStack(i).isOf(Items.GLOWSTONE)) { InvUtil.selectSlot(i); return true; }
        }
        return false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (cooldown > 0) { cooldown--; return; }
        if (timer > 0) { timer--; return; }

        if (step == Step.IDLE) {
            if (!mc.options.useKey.isPressed()) return;
            if (!(mc.crosshairTarget instanceof BlockHitResult h) || h.getType() != HitResult.Type.BLOCK) return;
            if (mc.world.getBlockState(h.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR)) return;
            if (InvUtil.findHotbar(Items.RESPAWN_ANCHOR) < 0 || InvUtil.findHotbar(Items.GLOWSTONE) < 0) return;

            BlockPos first = h.getBlockPos().offset(h.getSide());
            if (!freeSpot(first)) return;
            BlockPos second = findSecond(first);
            if (second == null) return;
            a = first; b = second; placeHit = h;
            step = Step.PLACE_A;
        }

        if (!inReach(a) || !inReach(b)) { reset(); return; }

        switch (step) {
            case PLACE_A -> {
                InvUtil.selectSlot(InvUtil.findHotbar(Items.RESPAWN_ANCHOR));
                if (!use(placeHit)) return;
                step = Step.PLACE_B;
            }
            case PLACE_B -> {
                BlockPos support = b.down();
                InvUtil.selectSlot(InvUtil.findHotbar(Items.RESPAWN_ANCHOR));
                if (!use(new BlockHitResult(Vec3d.ofCenter(support).add(0, 0.5, 0), Direction.UP, support, false))) return;
                step = Step.CHARGE_A;
            }
            case CHARGE_A -> {
                if (!isAnchor(a)) { reset(); return; }
                InvUtil.selectSlot(InvUtil.findHotbar(Items.GLOWSTONE));
                if (!use(top(a))) return;
                step = Step.CHARGE_B;
            }
            case CHARGE_B -> {
                if (!isAnchor(b)) { reset(); return; }
                InvUtil.selectSlot(InvUtil.findHotbar(Items.GLOWSTONE));
                if (!use(top(b))) return;
                step = Step.BOOM_A;
            }
            case BOOM_A -> {
                if (!selectNonGlow()) { reset(); return; }
                if (!use(top(a))) return;
                step = Step.BOOM_B;
            }
            case BOOM_B -> {
                selectNonGlow();
                if (!use(top(b))) return;
                reset();
                cooldown = 10;
                return;
            }
            default -> { return; }
        }
        timer = Safe.delay((int) Math.round(delay.get()));
    }
}
