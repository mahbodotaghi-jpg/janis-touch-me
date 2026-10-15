package dev.nitro.util;

/**
 * Talks to Baritone through reflection, so NITRO compiles and runs without it.
 * Baritone (baritone-api-fabric for 1.21.4) only has to be in the mods folder when you use it.
 */
public final class BaritoneBridge {
    private BaritoneBridge() {}

    public static boolean available() {
        try {
            Class.forName("baritone.api.BaritoneAPI");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Object primary() throws Exception {
        Object provider = Class.forName("baritone.api.BaritoneAPI").getMethod("getProvider").invoke(null);
        return Class.forName("baritone.api.IBaritoneProvider").getMethod("getPrimaryBaritone").invoke(provider);
    }

    /** Runs a Baritone command without its chat prefix, e.g. "build house.litematic 10 64 10". */
    public static boolean execute(String command) {
        try {
            Object baritone = primary();
            Object manager = Class.forName("baritone.api.IBaritone").getMethod("getCommandManager").invoke(baritone);
            Object result = Class.forName("baritone.api.command.manager.ICommandManager")
                    .getMethod("execute", String.class).invoke(manager, command);
            return !(result instanceof Boolean b) || b;
        } catch (Throwable t) {
            return false;
        }
    }

    /** true while Baritone's builder process still has a schematic it is working on */
    public static boolean builderActive() {
        try {
            Object baritone = primary();
            Object builder = Class.forName("baritone.api.IBaritone").getMethod("getBuilderProcess").invoke(baritone);
            Object active = Class.forName("baritone.api.process.IBaritoneProcess").getMethod("isActive").invoke(builder);
            return active instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }
}
