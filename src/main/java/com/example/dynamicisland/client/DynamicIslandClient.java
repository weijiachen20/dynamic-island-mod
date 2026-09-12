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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Client entry point. Wires the detector (20 Hz tick), the renderer (per-frame
 * HUD callback) and a keybind to toggle the overlay on/off.
 */
@Environment(EnvType.CLIENT)
public final class DynamicIslandClient implements ClientModInitializer {

    private static final IslandState STATE = new IslandState();
    private static final EventDetector DETECTOR = new EventDetector(STATE);
    private static final DynamicIslandHud HUD = new DynamicIslandHud(STATE);
    private static final JumpResetModule JUMP_RESET = new JumpResetModule();

    private static KeyMapping toggleKey;
    private static KeyMapping spinKey;
    private static KeyMapping jumpResetKey;
    private static KeyMapping settingsKey;

    @Override
    public void onInitializeClient() {
        IslandConfig.load();

        var category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(DynamicIslandMod.MOD_ID, "category"));

        // Event detection runs once per client tick (20 Hz).
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DETECTOR.tick(client);
            JUMP_RESET.tick(client);
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
            // 跳跃重置模块开关（默认 J 键，每按一下切换 ON/OFF）
            while (jumpResetKey != null && jumpResetKey.consumeClick()) {
                IslandConfig cfg = IslandConfig.get();
                cfg.jumpReset = !cfg.jumpReset;
                IslandConfig.save();
                if (client.player != null) {
                    client.player.sendOverlayMessage(
                            Component.translatable("key.dynamicisland.jumpreset")
                                    .append(": " + (cfg.jumpReset ? "ON" : "OFF")));
                }
            }
            // 打开外置 .NET 设置器（默认 O 键）：把打包的 exe 解压到 config 目录并启动
            while (settingsKey != null && settingsKey.consumeClick()) {
                openExternalSettings(client);
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

        // 跳跃重置模块开关（默认：J）
        jumpResetKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.dynamicisland.jumpreset",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                category));

        // 打开设置界面（默认：O）
        settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.dynamicisland.settings",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                category));

        DynamicIslandMod.LOGGER.info("Dynamic Island client overlay ready.");

        // 客户端关闭时清理后台进程（网易云音乐轮询等）。
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> DETECTOR.shutdown());
    }

    /** Called by {@code AdvancementToastMixin} when a toast is queued. */
    public static void onAdvancementToast(Component title) {
        DETECTOR.onAdvancementToast(title);
    }

    /**
     * 启动外置 WinUI 3 设置器：把打包在 jar 里的 DynamicIslandSettings.zip 解压到
     * {@code config/dynamicisland-settings/} 目录（zip 校验一致则跳过），随后启动 exe。
     */
    private static void openExternalSettings(Minecraft client) {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("dynamicisland-settings");
            Files.createDirectories(dir);
            Path exe = dir.resolve("DynamicIslandSettings.exe");

            byte[] data;
            try (InputStream in = DynamicIslandClient.class.getResourceAsStream(
                    "/assets/dynamicisland/settings/DynamicIslandSettings.zip")) {
                if (in == null) {
                    throw new IOException("DynamicIslandSettings.zip not bundled in the mod jar");
                }
                data = in.readAllBytes();
            }

            // 已解压且 zip 内容一致则跳过解压，避免每次按键都重写 40MB+
            Path marker = dir.resolve(".zip.sha256");
            String hash = sha256(data);
            if (!Files.exists(exe) || !hash.equals(readMarker(marker))) {
                extractZip(data, dir);
                Files.write(marker, hash.getBytes(StandardCharsets.UTF_8));
            }

            new ProcessBuilder(exe.toString()).start();
            if (client.player != null) {
                client.player.sendOverlayMessage(Component.translatable("dynamicisland.settings.launched"));
            }
        } catch (Exception e) {
            DynamicIslandMod.LOGGER.warn("Failed to launch external .NET settings app", e);
            if (client.player != null) {
                client.player.sendOverlayMessage(Component.translatable("dynamicisland.settings.manual"));
            }
        }
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String readMarker(Path marker) {
        try {
            return Files.exists(marker)
                    ? new String(Files.readAllBytes(marker), StandardCharsets.UTF_8)
                    : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static void extractZip(byte[] data, Path dir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            byte[] buf = new byte[8192];
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = dir.resolve(entry.getName()).normalize();
                if (!out.startsWith(dir)) {
                    continue; // 防止 zip 路径穿越
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buf)) > 0) {
                            os.write(buf, 0, n);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    public static IslandState state() {
        return STATE;
    }
}
