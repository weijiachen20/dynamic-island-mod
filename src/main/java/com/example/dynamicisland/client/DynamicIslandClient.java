package com.example.dynamicisland.client;

import com.example.dynamicisland.DynamicIslandMod;
import com.example.dynamicisland.client.config.IslandConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * Client entry point. Wires the detector (20 Hz tick), the renderer (per-frame
 * HUD callback) and a keybind to toggle the overlay on/off.
 */
@Environment(EnvType.CLIENT)
public final class DynamicIslandClient implements ClientModInitializer {

    private static final IslandState STATE = new IslandState();
    private static final EventDetector DETECTOR = new EventDetector(STATE);
    private static final DynamicIslandHud HUD = new DynamicIslandHud(STATE);

    private static KeyMapping toggleKey;
    private static KeyMapping spinKey;

    @Override
    public void onInitializeClient() {
        IslandConfig.load();

        var category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(DynamicIslandMod.MOD_ID, "category"));

        // Event detection runs once per client tick (20 Hz).
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DETECTOR.tick(client);
            while (toggleKey != null && toggleKey.consumeClick()) {
                IslandConfig cfg = IslandConfig.get();
                cfg.enabled = !cfg.enabled;
                IslandConfig.save();
                if (client.player != null) {
                    client.player.sendOverlayMessage(Component.translatable("dynamicisland.title")
                            .append(": " + (cfg.enabled ? "ON" : "OFF")));
                }
            }
            // 手持物品旋转开关（默认 R 键，每按一下切换 ON/OFF）
            while (spinKey != null && spinKey.consumeClick()) {
                SpinToggler.toggle();
                if (client.player != null) {
                    client.player.sendOverlayMessage(
                            Component.translatable("key.dynamicisland.spin")
                                    .append(": " + (SpinToggler.isEnabled() ? "ON" : "OFF")));
                }
            }
        });

        // Rendering runs every frame via the new HudElementRegistry (26.1+).
        // We attach before the CHAT layer so the island renders near the top
        // of the HUD stack.
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(DynamicIslandMod.MOD_ID, "island"),
                (graphics, deltaTracker) -> HUD.render(graphics, Minecraft.getInstance()));

        // Toggle keybind (default: K). Rebindable under Controls -> Dynamic Island.
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.dynamicisland.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                category));

        // 手持物品旋转开关（默认：R）
        spinKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.dynamicisland.spin",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                category));

        DynamicIslandMod.LOGGER.info("Dynamic Island client overlay ready.");

        // 客户端关闭时清理后台进程（网易云音乐轮询等）。
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> DETECTOR.shutdown());
    }

    /** Called by {@code AdvancementToastMixin} when a toast is queued. */
    public static void onAdvancementToast(Component title) {
        DETECTOR.onAdvancementToast(title);
    }

    public static IslandState state() {
        return STATE;
    }
}
