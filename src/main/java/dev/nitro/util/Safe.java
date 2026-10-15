package dev.nitro.util;

import dev.nitro.NitroClient;
import dev.nitro.module.modules.SafeMode;

import java.util.concurrent.ThreadLocalRandom;

/** Helpers the modules call so SafeMode can tone them down. All of them are no-ops while SafeMode is off. */
public class Safe {
    private static SafeMode mod() {
        if (NitroClient.modules == null) return null;
        SafeMode m = NitroClient.modules.get(SafeMode.class);
        return m != null && m.isEnabled() ? m : null;
    }

    public static boolean on() { return mod() != null; }

    public static boolean rotate() {
        SafeMode m = mod();
        return m != null && m.rotate();
    }

    /** base delay plus a random 0..jitter ticks */
    public static int delay(int base) {
        SafeMode m = mod();
        return m == null ? base : base + ThreadLocalRandom.current().nextInt(m.jitter() + 1);
    }

    public static int minDelay(int base, int min) {
        return on() ? Math.max(base, min) : base;
    }

    /** vanilla entity reach is 3.0 */
    public static double attackReach(double r) {
        SafeMode m = mod();
        return m != null && m.capReach() ? Math.min(r, 3.0) : r;
    }

    /** vanilla block reach is 4.5 */
    public static double blockReach(double r) {
        SafeMode m = mod();
        return m != null && m.capReach() ? Math.min(r, 4.5) : r;
    }

    public static float hitboxCap(float v) {
        SafeMode m = mod();
        return m == null ? v : Math.min(v, m.hitboxCap());
    }
}
