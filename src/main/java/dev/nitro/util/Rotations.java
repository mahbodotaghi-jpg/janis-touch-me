package dev.nitro.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Turns the player toward a point in limited steps that match mouse-sensitivity increments. */
public class Rotations {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** @return true when the player is already looking at the point (within ~1.5 degrees); otherwise turns one step and returns false */
    public static boolean aim(Vec3d point) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return false;

        Vec3d d = point.subtract(p.getEyePos());
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(d.y, flat));

        float dy = MathHelper.wrapDegrees(targetYaw - p.getYaw());
        float dp = targetPitch - p.getPitch();
        if (Math.abs(dy) < 1.5f && Math.abs(dp) < 1.5f) return true;

        float max = 40f;
        dy = MathHelper.clamp(dy, -max, max);
        dp = MathHelper.clamp(dp, -max, max);

        // snap the step to what the mouse can actually produce at the current sensitivity
        double f = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        double gcd = f * f * f * 8.0 * 0.15;
        dy = (float) (Math.round(dy / gcd) * gcd);
        dp = (float) (Math.round(dp / gcd) * gcd);

        p.setYaw(p.getYaw() + dy);
        p.setPitch(MathHelper.clamp(p.getPitch() + dp, -90f, 90f));
        return false;
    }
}
