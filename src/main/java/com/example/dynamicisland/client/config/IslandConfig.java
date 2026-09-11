package com.example.dynamicisland.client.config;

import com.example.dynamicisland.DynamicIslandMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent client configuration for the Dynamic Island overlay.
 *
 * <p>Stored as {@code config/dynamicisland.json}. Fields are intentionally public
 * so GSON (de)serialises them directly and the config GUI can read/write without
 * boilerplate accessors.
 */
public class IslandConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("dynamicisland.json");

    private static IslandConfig instance;

    public static IslandConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                IslandConfig loaded = GSON.fromJson(json, IslandConfig.class);
                instance = loaded != null ? loaded : new IslandConfig();
            } catch (Exception e) {
                DynamicIslandMod.LOGGER.warn("Failed to read Dynamic Island config, using defaults", e);
                instance = new IslandConfig();
            }
        } else {
            instance = new IslandConfig();
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(get()));
        } catch (IOException e) {
            DynamicIslandMod.LOGGER.warn("Failed to save Dynamic Island config", e);
        }
    }

    // ---- Settings ----

    /** Master kill-switch for the overlay. */
    public boolean enabled = true;

    /** Screen anchor: 0 = top centre, 1 = top left, 2 = top right. */
    public int position = 0;

    /** 0..4, higher = more event types trigger (e.g. damage at lower thresholds). */
    public int sensitivity = 2;

    /** UI scale multiplier on top of Minecraft's GUI scale. */
    public float scale = 1.0f;

    public boolean potions = true;
    public boolean music = true;
    public boolean weather = true;
    public boolean advancement = true;
    public boolean health = true;
    public boolean hunger = true;
    public boolean daynight = true;
    public boolean damage = true;
    public boolean levelUp = true;
    /** LiquidBounce Nextgen module toggle notifications. */
    public boolean liquidbounce = true;
    /** 网易云音乐客户端播放通知. */
    public boolean netease = true;
    /** 网易云音乐歌词显示（需要开启桌面歌词）. */
    public boolean neteaseLyric = true;
    /** 折叠态常驻显示帧率. */
    public boolean statusFps = true;
    /** 折叠态常驻显示服务器 IP. */
    public boolean statusIp = true;
    /** 折叠态常驻显示 LiquidBounce 版本. */
    public boolean statusLbVersion = true;
    /** 折叠态常驻显示网易云音乐歌词. */
    public boolean statusLyric = true;
    /** 折叠态常驻显示 LiquidBounce Scaffold 的 BPS 和方块消耗进度. */
    public boolean statusScaffold = true;
    /** 折叠态常驻显示 LB 模块激活仪表盘（激活数 + 核心模块指示灯）. */
    public boolean statusModules = true;
    /** 折叠态 KillAura 开启时显示 APS + 目标. */
    public boolean statusKa = true;
    /** 折叠态常驻显示实时速度计. */
    public boolean statusSpeed = true;
    /** 折叠态常驻显示盔甲完整度 + 低耐久告警. */
    public boolean statusArmor = true;
    /** 折叠态常驻显示延迟（ping ms）. */
    public boolean statusPing = true;

    /** 跳跃重置（JumpReset）功能模块总开关：受击落地时自动跳跃重置动量。 */
    public boolean jumpReset = false;

    /** 跳跃重置的击退判定水平速度阈值（方块/tick，越大越不敏感）。 */
    public float jumpResetThreshold = 0.16f;

    /** 使用 ImGui 独立设置界面（默认 true，通过「打开设置」按键呼出）。 */
    public boolean imguiSettings = true;

    /** Below this many hearts the low-health event fires. */
    public int healthThreshold = 5;

    /** Below this many drumsticks the low-hunger event fires. */
    public int hungerThreshold = 3;

    /** How many seconds a transient event stays expanded. */
    public float displayTime = 3.5f;

    /** Background opacity 0..100. */
    public int bgOpacity = 88;

    /** Corner radius in scaled pixels. */
    public int cornerRadius = 12;

    public boolean categoryEnabled(String key) {
        return switch (key) {
            case "potions" -> potions;
            case "music" -> music;
            case "weather" -> weather;
            case "advancement" -> advancement;
            case "health" -> health;
            case "hunger" -> hunger;
            case "daynight" -> daynight;
            case "damage" -> damage;
            case "levelUp" -> levelUp;
            case "liquidbounce" -> liquidbounce;
            case "netease" -> netease;
            case "neteaseLyric" -> neteaseLyric;
            case "statusFps" -> statusFps;
            case "statusIp" -> statusIp;
            case "statusLbVersion" -> statusLbVersion;
            case "statusLyric" -> statusLyric;
            case "statusScaffold" -> statusScaffold;
            case "statusModules" -> statusModules;
            case "statusKa" -> statusKa;
            case "statusSpeed" -> statusSpeed;
            case "statusArmor" -> statusArmor;
            case "statusPing" -> statusPing;
            case "jumpReset" -> jumpReset;
            case "imguiSettings" -> imguiSettings;
            default -> true;
        };
    }
}
