package com.example.dynamicisland.client;

import com.example.dynamicisland.client.config.IslandConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 收集并在灵动岛折叠态显示的常驻状态信息。
 *
 * <p>与 LiquidBounce 相关的 5 项功能在此产出折叠态行：
 * <ol>
 *   <li>🎚️ 模块激活仪表盘：激活数 + K/S/F/N/A 核心模块缩写指示灯</li>
 *   <li>⚔️ KillAura 面板：APS + 目标名 + 目标血量</li>
 *   <li>🏃 实时速度计：m/s + 速度倍率 + Speed/Fly 标签</li>
 *   <li>🛡️ 盔甲完整度：平均剩余耐久% + AutoArmor 标签（AA）</li>
 *   <li>Scaffold（已有）：BPS / 波形 / 方块进度</li>
 *   <li>歌词 / FPS / IP / LB 版本（已有）</li>
 * </ol>
 *
 * <p>布局最多可排 6 条：info → modules → speed → lyric → ka → armor → scaffold → waveform → progress-bar
 */
public final class StatusInfo {

    private static String cachedLbVersion;
    private static boolean lbVersionChecked;
    private static Component cachedInfoLine;
    private static Component cachedLyricLine;
    private static Component cachedScaffoldLine;
    private static Component cachedModulesLine;   // new: 模块仪表盘
    private static Component cachedKaLine;        // new: KA 面板
    private static Component cachedSpeedLine;     // new: 速度计
    private static Component cachedArmorLine;     // new: 盔甲完整度

    private static boolean cachedHasLyric;
    private static boolean cachedHasScaffold;
    private static boolean cachedHasModules;
    private static boolean cachedHasKa;
    private static boolean cachedHasSpeed;
    private static boolean cachedHasArmor;

    // --- 动画5：速度计数值帧间平滑（避免 5.1→8.3 瞬时跳变） ---
    private static double smoothedMps;   // 当前显示的平滑速度
    private static long speedLastNanos;  // 上次平滑更新的时间戳

    private static float cachedScaffoldProgress;
    private static float[] cachedWaveform; // 10 bars normalised 0..1
    private static int tickCounter;

    // --- Ping (延迟) 缓存：每秒刷新一次，避免每 tick 查 PlayerInfo ---
    private static int cachedPing = -1;
    private static long lastPingNs;

    public static final int WAVEFORM_BARS = 10;

    private static NetEaseMusicCompat netEaseMusic;
    private static ScaffoldTracker scaffoldTracker;

    public static void setNetEaseMusic(NetEaseMusicCompat compat) {
        netEaseMusic = compat;
    }

    public static void setScaffoldTracker(ScaffoldTracker tracker) {
        scaffoldTracker = tracker;
    }

    public static Component getInfoLine(Minecraft client)      { refresh(client); return cachedInfoLine; }
    public static Component getLyricLine(Minecraft client)     { refresh(client); return cachedLyricLine; }
    public static Component getScaffoldLine(Minecraft client)  { refresh(client); return cachedScaffoldLine; }
    public static Component getModulesLine(Minecraft client)   { refresh(client); return cachedModulesLine; }
    public static Component getKaLine(Minecraft client)        { refresh(client); return cachedKaLine; }
    public static Component getSpeedLine(Minecraft client)     { refresh(client); return cachedSpeedLine; }
    public static Component getArmorLine(Minecraft client)     { refresh(client); return cachedArmorLine; }

    public static boolean hasLyricLine(Minecraft client)    { refresh(client); return cachedHasLyric; }
    public static boolean hasScaffoldLine(Minecraft client) { refresh(client); return cachedHasScaffold; }
    public static boolean hasModulesLine(Minecraft client)  { refresh(client); return cachedHasModules; }
    public static boolean hasKaLine(Minecraft client)       { refresh(client); return cachedHasKa; }
    public static boolean hasSpeedLine(Minecraft client)    { refresh(client); return cachedHasSpeed; }
    public static boolean hasArmorLine(Minecraft client)    { refresh(client); return cachedHasArmor; }

    public static float getScaffoldProgress(Minecraft client) {
        refresh(client);
        return cachedScaffoldProgress;
    }

    public static float[] getScaffoldWaveform(Minecraft client) {
        refresh(client);
        float[] wf = cachedWaveform;
        if (wf == null) wf = new float[WAVEFORM_BARS];
        return wf;
    }

    private static void refresh(Minecraft client) {
        if (tickCounter++ % 2 == 0 || cachedInfoLine == null) {
            IslandConfig cfg = IslandConfig.get();
            cachedInfoLine = buildInfoLine(client);
            LyricResult lr = buildLyricLine(client);
            cachedLyricLine = lr.line; cachedHasLyric = lr.has;
            ScaffoldResult sr = buildScaffoldLine();
            cachedScaffoldLine = sr.line; cachedHasScaffold = sr.has;
            cachedScaffoldProgress = sr.progress; cachedWaveform = sr.waveform;
            // ---- 新增 5 功能中的 4 条行（按配置开关控制）----
            LineResult mr = cfg.statusModules ? buildModulesLine() : LineResult.of(null, false);
            cachedModulesLine = mr.line; cachedHasModules = mr.has;
            LineResult kr = cfg.statusKa ? buildKaLine() : LineResult.of(null, false);
            cachedKaLine = kr.line; cachedHasKa = kr.has;
            LineResult spr = cfg.statusSpeed ? buildSpeedLine(client) : LineResult.of(null, false);
            cachedSpeedLine = spr.line; cachedHasSpeed = spr.has;
            LineResult ar = cfg.statusArmor ? buildArmorLine(client) : LineResult.of(null, false);
            cachedArmorLine = ar.line; cachedHasArmor = ar.has;
        }
    }

    public static boolean hasStatus(Minecraft client) {
        IslandConfig cfg = IslandConfig.get();
        if (cfg.statusFps) return true;
        if (cfg.statusIp && (client.getCurrentServer() != null || client.hasSingleplayerServer())) return true;
        if (cfg.statusLbVersion && getLiquidBounceVersion() != null) return true;
        if (cfg.statusLyric && hasLyricLine(client)) return true;
        if (cfg.statusScaffold && hasScaffoldLine(client)) return true;
        // 新增功能的触发条件：受细分配置控制
        if (LiquidBounceCompat.isPresent()) {
            if (cfg.statusModules && hasModulesLine(client)) return true;
            if (cfg.statusSpeed   && hasSpeedLine(client))   return true;
            if (cfg.statusArmor   && hasArmorLine(client))   return true;
            if (cfg.statusKa      && hasKaLine(client))      return true;
        }
        return false;
    }

    // ==========================================================
    // Line builders
    // ==========================================================

    private static Component buildInfoLine(Minecraft client) {
        IslandConfig cfg = IslandConfig.get();
        MutableComponent line = Component.empty();
        boolean has = false;

        if (cfg.statusFps) {
            int fps = client.getFps();
            ChatFormatting color = fps >= 60 ? ChatFormatting.GREEN
                              : fps >= 30 ? ChatFormatting.YELLOW
                              : ChatFormatting.RED;
            line.append(Component.literal("\u26A1 ").withStyle(ChatFormatting.GOLD));
            line.append(Component.literal(fps + "").withStyle(color));
            line.append(Component.literal("fps").withStyle(ChatFormatting.DARK_GRAY));
            has = true;
        }

        if (cfg.statusIp) {
            var server = client.getCurrentServer();
            String ip = null;
            if (server != null && server.ip != null && !server.ip.isEmpty()) {
                ip = server.ip;
                if (ip.length() > 18) ip = ip.substring(0, 16) + "..";
            } else if (client.hasSingleplayerServer()) {
                if (has) line.append(sep());
                line.append(Component.literal("\uD83C\uDFAE ").withStyle(ChatFormatting.DARK_GREEN));
                line.append(Component.translatable("dynamicisland.status.singleplayer")
                        .withStyle(ChatFormatting.GRAY));
                has = true;
            }
            if (ip != null) {
                if (has) line.append(sep());
                line.append(Component.literal("\uD83C\uDF10 ").withStyle(ChatFormatting.BLUE));
                line.append(Component.literal(ip).withStyle(ChatFormatting.WHITE));
                has = true;
            }
        }

        if (cfg.statusLbVersion) {
            String lbVer = getLiquidBounceVersion();
            if (lbVer != null) {
                if (has) line.append(sep());
                line.append(Component.literal("\uD83C\uDFA7 ").withStyle(ChatFormatting.LIGHT_PURPLE));
                line.append(Component.literal("LB ").withStyle(ChatFormatting.DARK_AQUA));
                line.append(Component.literal(lbVer).withStyle(ChatFormatting.AQUA));
                has = true;
            }
        }

        if (cfg.statusPing) {
            int ping = getPing(client);
            if (ping >= 0) {
                ChatFormatting c = ping < 50 ? ChatFormatting.GREEN
                                 : ping < 150 ? ChatFormatting.YELLOW
                                 : ping < 300 ? ChatFormatting.GOLD
                                 : ChatFormatting.RED;
                if (has) line.append(sep());
                line.append(Component.literal("\uD83D\uDCF6 ").withStyle(ChatFormatting.DARK_GREEN));
                line.append(Component.literal(ping + "").withStyle(c));
                line.append(Component.literal("ms").withStyle(ChatFormatting.DARK_GRAY));
                has = true;
            } else if (client.hasSingleplayerServer()) {
                if (has) line.append(sep());
                line.append(Component.literal("\uD83D\uDCF6 ").withStyle(ChatFormatting.DARK_GREEN));
                line.append(Component.literal("0").withStyle(ChatFormatting.GREEN));
                line.append(Component.literal("ms").withStyle(ChatFormatting.DARK_GRAY));
                has = true;
            }
        }

        return line;
    }

    /**
     * 读取当前网络延迟 (ms)。
     * - 单玩家 → 0ms (直连)
     * - 联机 → 通过 PlayerInfo 的 latency 字段（1 秒回包缓存，避免每 tick 调用）
     * - 取不到或未连接 → -1
     */
    private static int getPing(Minecraft client) {
        long now = System.nanoTime();
        // 1 秒内直接用缓存
        if (now - lastPingNs < 1_000_000_000L && cachedPing != -2) return cachedPing;
        lastPingNs = now;

        try {
            if (client.player == null || client.player.connection == null) {
                cachedPing = -1;
                return cachedPing;
            }
            if (client.hasSingleplayerServer()) {
                cachedPing = 0;
                return cachedPing;
            }
            var uuid = client.player.getUUID();
            var info = client.player.connection.getPlayerInfo(uuid);
            if (info == null) {
                cachedPing = -1;
                return cachedPing;
            }
            int lat = info.getLatency();
            // latency 在 MC 里：<0 = 未知，>=0 = ms
            cachedPing = lat < 0 ? -1 : lat;
        } catch (Throwable t) {
            cachedPing = -1;
        }
        return cachedPing;
    }

    // --- 功能一：模块激活仪表盘 ---
    /** 🎚️ 7 active · K S F N A（亮=绿，灭=灰）*/
    private static LineResult buildModulesLine() {
        if (!LiquidBounceCompat.isPresent()) return LineResult.of(null, false);
        MutableComponent line = Component.empty();
        line.append(Component.literal("\uD83C\uDF9A\uFE0F ").withStyle(ChatFormatting.DARK_PURPLE));
        int active = LiquidBounceCompat.getActiveModuleCount();
        line.append(Component.literal(active + "").withStyle(
                active >= 8 ? ChatFormatting.RED : active >= 4 ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        line.append(Component.literal("on").withStyle(ChatFormatting.DARK_GRAY));
        line.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
        var status = LiquidBounceCompat.getCoreModuleStatus();
        boolean first = true;
        for (var e : status.entrySet()) {
            String abbr = LiquidBounceCompat.CORE_ABBREV.getOrDefault(e.getKey(), "?");
            boolean on = Boolean.TRUE.equals(e.getValue());
            if (!first) line.append(Component.literal(" "));
            line.append(Component.literal(abbr).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            first = false;
        }
        return LineResult.of(line, true);
    }

    // --- 功能二：KillAura 面板 ---
    /** ⚔️ 14aps · Steve ❤ 12.5 */
    private static LineResult buildKaLine() {
        if (!LiquidBounceCompat.isModuleEnabled("KillAura")) return LineResult.of(null, false);
        MutableComponent line = Component.empty();
        line.append(Component.literal("\u2694\uFE0F ").withStyle(ChatFormatting.RED));
        int aps = LiquidBounceCompat.getKaAps();
        ChatFormatting apsColor = aps >= 14 ? ChatFormatting.RED
                                : aps >= 10 ? ChatFormatting.GOLD
                                : aps >= 4  ? ChatFormatting.YELLOW
                                :             ChatFormatting.WHITE;
        line.append(Component.literal(aps + "").withStyle(apsColor));
        line.append(Component.literal("aps").withStyle(ChatFormatting.DARK_GRAY));
        String target = LiquidBounceCompat.getKaTarget();
        float hp = LiquidBounceCompat.getKaTargetHealth();
        if (target != null || hp >= 0) {
            line.append(sep());
            if (target != null) {
                line.append(Component.literal(target).withStyle(ChatFormatting.WHITE));
                line.append(Component.literal(" "));
            } else {
                line.append(Component.literal("? "));
            }
            line.append(Component.literal("\u2764").withStyle(ChatFormatting.DARK_RED));
            line.append(Component.literal(hp < 0 ? " --" : String.format("%.1f", hp))
                    .withStyle(hp < 0 ? ChatFormatting.DARK_GRAY
                                : hp > 15f ? ChatFormatting.GREEN
                                : hp > 7f  ? ChatFormatting.YELLOW
                                :            ChatFormatting.RED));
        }
        return LineResult.of(line, true);
    }

    // --- 功能三：速度计 ---
    /** 🏃 8.42m/s × 1.7 SPEED */
    private static LineResult buildSpeedLine(Minecraft client) {
        Player p = client.player;
        if (p == null) return LineResult.of(null, false);
        // 水平位移（tick的速率，需要把 m/tick 转换为 m/s，1 tick = 1/20 秒 → ×20）
        double dx = p.getX() - p.xOld;
        double dz = p.getZ() - p.zOld;
        double distPerTick = Math.sqrt(dx * dx + dz * dz);
        double mps = distPerTick * 20d;
        if (mps < 0.01 && !p.onGround()) {
            // 可能在空中且没移动，用运动向量估算
            dx = p.getDeltaMovement().x;
            dz = p.getDeltaMovement().z;
            mps = Math.sqrt(dx * dx + dz * dz) * 20d;
        }

        // --- 动画5：速度值帧间平滑（像汽车速度表一样渐进过渡） ---
        long nowNs = System.nanoTime();
        double dt = speedLastNanos == 0 ? 0.1 : Math.min(0.25, (nowNs - speedLastNanos) / 1e9);
        speedLastNanos = nowNs;
        double lerpK = Math.min(1.0, dt * 8.0); // 每帧 ~8Hz 响应，约 3-4 帧完成平滑
        smoothedMps += (mps - smoothedMps) * lerpK;
        double dispMps = smoothedMps;
        // 速度变化时叠加轻微"颠簸"正弦扰动：速度越快颠簸越大，频率 ~10Hz
        double jitter = 0.0;
        if (dispMps > 1.0d) {
            double tMs = (nowNs / 1_000_000d);
            jitter = Math.sin(tMs / 100.0d * Math.PI * 2.0d) * Math.min(0.35d, dispMps * 0.025d);
        }
        double finalMps = Math.max(0.0d, dispMps + jitter);

        boolean fly = LiquidBounceCompat.isModuleEnabled("Fly");
        boolean spd = LiquidBounceCompat.isModuleEnabled("Speed");

        MutableComponent line = Component.empty();
        ChatFormatting iconColor = fly && spd ? ChatFormatting.LIGHT_PURPLE
                                : fly ? ChatFormatting.GOLD
                                : spd ? ChatFormatting.BLUE
                                :       ChatFormatting.GREEN;
        line.append(Component.literal("\uD83C\uDFC3 ").withStyle(iconColor));

        String speedText = String.format(finalMps >= 10 ? "%.0f" : "%.1f", finalMps);
        line.append(Component.literal(speedText).withStyle(ChatFormatting.WHITE));
        line.append(Component.literal("m/s").withStyle(ChatFormatting.DARK_GRAY));

        // 步行参考速度 ~4.3 m/s（疾跑 ~5.6）
        double vanilla = 4.3d;
        double ratio = dispMps / vanilla; // 倍率用平滑后的真实值（不用颠簸值，避免倍率飘）
        line.append(Component.literal(String.format(" \u00D7%.1f", ratio))
                .withStyle(ratio >= 2.5d ? ChatFormatting.RED
                            : ratio >= 1.5d ? ChatFormatting.GOLD
                            : ratio >= 1.1d ? ChatFormatting.YELLOW
                            : ChatFormatting.DARK_GRAY));

        if (fly || spd) {
            line.append(Component.literal("  "));
            if (spd) line.append(Component.literal("SPEED").withStyle(ChatFormatting.BLUE));
            if (fly) {
                if (spd) line.append(Component.literal("+").withStyle(ChatFormatting.DARK_GRAY));
                line.append(Component.literal("FLY").withStyle(ChatFormatting.GOLD));
            }
        }
        return LineResult.of(line, true);
    }

    // --- 功能四：盔甲完整度 + AutoArmor 标签 ---
    /** 🛡️ 73% AA (其中 AA 表示 AutoArmor 打开) */
    public static final class ArmorStatus {
        public final int avgPercent;   // 0..100
        public final boolean lowDurability; // 任意一件 < 20%
        public ArmorStatus(int p, boolean low) { avgPercent = p; lowDurability = low; }
    }

    // Inventory.armor 字段的反射缓存（26.1 中 armor 是 private 的）
    private static Field inventoryArmorField;
    private static boolean inventoryArmorTried;

    @SuppressWarnings("unchecked")
    private static List<ItemStack> getArmorStacks(Player p) {
        if (p == null) return List.of();
        Inventory inv = p.getInventory();
        if (inv == null) return List.of();

        // 优先尝试：反射拿 armor 字段（List<ItemStack> 或 ItemStack[]）
        if (!inventoryArmorTried) {
            inventoryArmorTried = true;
            Class<?> cls = inv.getClass();
            // 遍历找名字带 armor 的字段（public / private 都试）
            for (Field f : cls.getDeclaredFields()) {
                String n = f.getName().toLowerCase();
                if (n.equals("armor") || n.contains("armor") && (List.class.isAssignableFrom(f.getType()) || f.getType().isArray())) {
                    try {
                        f.setAccessible(true);
                        inventoryArmorField = f;
                        break;
                    } catch (Throwable ignored) {}
                }
            }
            if (inventoryArmorField == null) {
                try {
                    Field f = cls.getField("armor");
                    f.setAccessible(true);
                    inventoryArmorField = f;
                } catch (Throwable ignored) {}
            }
        }
        if (inventoryArmorField != null) {
            try {
                Object v = inventoryArmorField.get(inv);
                if (v instanceof List<?> list) {
                    List<ItemStack> r = new java.util.ArrayList<>(4);
                    for (Object o : list) r.add(o instanceof ItemStack s ? s : ItemStack.EMPTY);
                    return r;
                }
                if (v instanceof ItemStack[] arr) {
                    List<ItemStack> r = new java.util.ArrayList<>(arr.length);
                    for (ItemStack s : arr) r.add(s == null ? ItemStack.EMPTY : s);
                    return r;
                }
            } catch (Throwable ignored) {}
        }

        // 回退：按索引找 4 个盔甲槽（PlayerInventory 中通常 armor 槽位于 indices 0..3 或 36..39 取决于实现）
        // 再回退：简单假设 Inventory 有 armorList 方法
        try {
            var m = inv.getClass().getMethod("getArmorContents");
            Object r = m.invoke(inv);
            if (r instanceof List<?> list) {
                List<ItemStack> out = new java.util.ArrayList<>(4);
                for (Object o : list) out.add(o instanceof ItemStack s ? s : ItemStack.EMPTY);
                return out;
            }
            if (r instanceof ItemStack[] arr) {
                List<ItemStack> out = new java.util.ArrayList<>(arr.length);
                for (ItemStack s : arr) out.add(s == null ? ItemStack.EMPTY : s);
                return out;
            }
        } catch (Throwable ignored) {}

        // 最简陋回退：对玩家对象调用 getArmorSlots 或遍历前几个可能的槽
        List<ItemStack> fallback = new java.util.ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            try {
                ItemStack s = inv.getItem(i);
                fallback.add(s == null ? ItemStack.EMPTY : s);
            } catch (Throwable ignored) {
                fallback.add(ItemStack.EMPTY);
            }
        }
        return fallback;
    }

    /** 返回盔甲完整度摘要（供 EventDetector 检查低耐久URGENT事件）。 */
    public static ArmorStatus getArmorStatus(Minecraft client) {
        Player p = client.player;
        if (p == null) return new ArmorStatus(0, false);
        int total = 0, count = 0;
        boolean low = false;
        for (ItemStack stack : getArmorStacks(p)) {
            count++;
            if (stack == null || stack.isEmpty()) continue;
            // 没有 ArmorItem 类检查：只要堆有耐久就当作盔甲（对于真正的盔甲堆都是这样）
            if (stack.getMaxDamage() <= 0) continue;
            int max = Math.max(1, stack.getMaxDamage());
            int cur = max - Math.min(max, stack.getDamageValue());
            int pct = Mth.clamp(Math.round(cur * 100f / (float) max), 0, 100);
            total += pct;
            if (pct < 20 && pct > 0) low = true;
            if (stack.getDamageValue() >= max) low = true;
        }
        if (count == 0) return new ArmorStatus(0, false);
        return new ArmorStatus(total / Math.max(1, count), low);
    }

    private static LineResult buildArmorLine(Minecraft client) {
        Player p = client.player;
        if (p == null) return LineResult.of(null, false);
        var st = getArmorStatus(client);
        // 没有任何盔甲不显示
        boolean anyArmor = false;
        for (ItemStack s : getArmorStacks(p)) {
            if (s != null && !s.isEmpty()) { anyArmor = true; break; }
        }
        if (!anyArmor) return LineResult.of(null, false);

        MutableComponent line = Component.empty();
        line.append(Component.literal("\uD83D\uDEE1\uFE0F ").withStyle(ChatFormatting.DARK_AQUA));
        ChatFormatting pctColor = st.avgPercent >= 70 ? ChatFormatting.GREEN
                                : st.avgPercent >= 35 ? ChatFormatting.YELLOW
                                : st.lowDurability   ? ChatFormatting.RED
                                :                       ChatFormatting.GOLD;
        line.append(Component.literal(st.avgPercent + "%").withStyle(pctColor));
        boolean aa = LiquidBounceCompat.isModuleEnabled("AutoArmor");
        if (aa) {
            line.append(Component.literal(" AA").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return LineResult.of(line, true);
    }

    // ---- helper result holders ----

    private static final class LineResult {
        Component line;
        boolean has;
        static LineResult of(Component c, boolean h) {
            LineResult r = new LineResult(); r.line = c; r.has = h; return r;
        }
    }

    private static final class LyricResult {
        Component line; boolean has;
        static LyricResult of(Component c, boolean h) {
            LyricResult r = new LyricResult(); r.line = c; r.has = h; return r;
        }
    }

    private static LyricResult buildLyricLine(Minecraft client) {
        IslandConfig cfg = IslandConfig.get();
        if (!cfg.statusLyric || netEaseMusic == null) return LyricResult.of(null, false);
        String lyric = netEaseMusic.getCurrentLyric();
        if (lyric.isEmpty()) return LyricResult.of(null, false);
        MutableComponent line = Component.empty();
        line.append(Component.literal("\uD83C\uDFB5 ").withStyle(ChatFormatting.LIGHT_PURPLE));
        line.append(Component.literal(lyric).withStyle(ChatFormatting.WHITE));
        return LyricResult.of(line, true);
    }

    private static final class ScaffoldResult {
        Component line;
        boolean has;
        float progress;
        float[] waveform;
        static ScaffoldResult of(Component c, boolean h, float p, float[] wf) {
            ScaffoldResult r = new ScaffoldResult();
            r.line = c; r.has = h; r.progress = p; r.waveform = wf; return r;
        }
    }

    private static ScaffoldResult buildScaffoldLine() {
        IslandConfig cfg = IslandConfig.get();
        float[] zeroWf = new float[WAVEFORM_BARS];
        if (!cfg.statusScaffold || scaffoldTracker == null) return ScaffoldResult.of(null, false, -1f, zeroWf);
        if (!scaffoldTracker.isScaffolding()) return ScaffoldResult.of(null, false, -1f, zeroWf);

        int bps = scaffoldTracker.getBps();
        int remaining = scaffoldTracker.getCurrentBlocks();
        float progress = scaffoldTracker.getProgress();
        float[] waveform = scaffoldTracker.getWaveform(WAVEFORM_BARS);

        MutableComponent line = Component.empty();
        line.append(Component.literal("\uD83E\uDDF1 ").withStyle(ChatFormatting.GOLD));
        ChatFormatting bpsColor = bps >= 12 ? ChatFormatting.GREEN
                               : bps >= 5  ? ChatFormatting.YELLOW
                               : bps >= 1  ? ChatFormatting.WHITE
                               : ChatFormatting.DARK_GRAY;
        line.append(Component.literal(bps + "").withStyle(bpsColor));
        line.append(Component.literal("bps").withStyle(ChatFormatting.DARK_GRAY));
        line.append(sep());
        line.append(Component.literal(remaining + "").withStyle(ChatFormatting.AQUA));
        line.append(Component.literal("\u25CF").withStyle(ChatFormatting.DARK_AQUA));
        return ScaffoldResult.of(line, true, progress, waveform);
    }

    private static Component sep() {
        return Component.literal("  \u00B7  ").withStyle(ChatFormatting.DARK_GRAY);
    }

    private static String getLiquidBounceVersion() {
        if (!lbVersionChecked) {
            lbVersionChecked = true;
            FabricLoader.getInstance().getModContainer("liquidbounce").ifPresent(c ->
                cachedLbVersion = c.getMetadata().getVersion().getFriendlyString());
        }
        return cachedLbVersion;
    }
}
