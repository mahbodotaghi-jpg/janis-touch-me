package dev.nitro.module.modules;

import dev.nitro.module.Category;
import dev.nitro.module.Module;
import dev.nitro.setting.BooleanSetting;
import dev.nitro.setting.NumberSetting;

/**
 * Makes the other modules behave more like a normal player (see util/Safe): randomised delays, reach caps,
 * looking at the thing before acting on it, vanilla-style totem swaps, small hitbox expansion.
 * This lowers the chance of being flagged, it does not make you undetectable.
 */
public class SafeMode extends Module {
    private final NumberSetting jitter = add(new NumberSetting("Delay jitter (ticks)", 1, 0, 3, 1));
    private final BooleanSetting rotate = add(new BooleanSetting("Look before acting", true));
    private final BooleanSetting capReach = add(new BooleanSetting("Cap reach (3.0 / 4.5)", true));
    private final NumberSetting hitboxCap = add(new NumberSetting("Hitbox cap", 0.1, 0.0, 0.5, 0.05));

    public SafeMode() {
        super("SafeMode", "Legit-style behaviour: random delays, reach caps, looks at targets first, vanilla totem swaps.", Category.MISC);
    }

    public int jitter() { return (int) Math.round(jitter.get()); }
    public boolean rotate() { return rotate.get(); }
    public boolean capReach() { return capReach.get(); }
    public float hitboxCap() { return hitboxCap.get().floatValue(); }
}
