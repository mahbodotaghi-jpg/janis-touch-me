package dev.nitro.module.modules;

import dev.nitro.module.Category;
import dev.nitro.module.Module;
import dev.nitro.setting.BooleanSetting;
import dev.nitro.setting.NumberSetting;
import dev.nitro.util.RenderUtil;
import dev.nitro.util.Safe;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

/**
 * Makes other players' hitboxes bigger for your crosshair (see EntityMixin), so you can hit them from slightly
 * off-target. With SafeMode on the expansion is capped (default 0.1) so it stays close to what servers allow.
 */
public class Hitbox extends Module {
    private final NumberSetting expand = add(new NumberSetting("Expand", 0.3, 0.0, 1.0, 0.05));
    private final BooleanSetting show = add(new BooleanSetting("Show boxes", true));

    public Hitbox() { super("Hitbox", "Expands player hitboxes for easier targeting.", Category.COMBAT); }

    public float expansion() { return Safe.hitboxCap(expand.get().floatValue()); }

    @Override
    public void onRender(WorldRenderContext ctx) {
        if (!show.get() || mc.world == null || mc.player == null) return;
        float td = mc.getRenderTickCounter().getTickDelta(true);
        RenderUtil.Batch b = new RenderUtil.Batch(ctx);
        boolean any = false;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator() || mc.player.distanceTo(p) > 24) continue;
            double x = MathHelper.lerp(td, p.prevX, p.getX());
            double y = MathHelper.lerp(td, p.prevY, p.getY());
            double z = MathHelper.lerp(td, p.prevZ, p.getZ());
            Box box = p.getBoundingBox().offset(x - p.getX(), y - p.getY(), z - p.getZ()).expand(expansion());
            b.outline(box, 0xFFFF5555);
            any = true;
        }
        if (any) b.draw();
    }
}
