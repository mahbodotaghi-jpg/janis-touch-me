package dev.nitro.util;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeyUtil {
    /** Whether the physical key/button bound to this KeyBinding is held down right now. */
    public static boolean physical(KeyBinding k) {
        MinecraftClient mc = MinecraftClient.getInstance();
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(k);
        long h = mc.getWindow().getHandle();
        return switch (key.getCategory()) {
            case KEYSYM -> InputUtil.isKeyPressed(h, key.getCode());
            case MOUSE -> GLFW.glfwGetMouseButton(h, key.getCode()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }
}
