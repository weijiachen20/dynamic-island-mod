package com.example.dynamicisland.client;

import com.example.dynamicisland.client.config.IslandConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 当 LiquidBounce Scaffold 模块开启时，跟踪：
 * <ul>
 *   <li>BPS (Blocks Per Second) — 过去 1 秒内放置的方块数</li>
 *   <li>方块剩余数 / 初始数 — 用于绘制消耗进度条</li>
 * </ul>
 *
 * <p>Scaffold 启用状态通过反射访问 LiquidBounce 的 ModuleManager 获得，
 * 若反射失败（LB 未加载 / API 变更）则静默返回 false，不会破坏其他功能。
 *
 * <p>BPS 通过对比玩家主手方块数的逐 tick 减少量累计（滚动窗口 20 tick = 1 秒）。
 * 方块消耗进度以进入 Scaffold 状态时主手方块堆叠数为 100%。
 */
public final class ScaffoldTracker {

    // ---- BPS 滚动窗口：最近 20 tick（1 秒）内每 tick 消耗的方块数 ----
    private final Deque<Integer> recentPlacements = new ArrayDeque<>(20);
    private int placementsLastTick;
    private Item lastHandItem;   // 用于代替 slot 索引来判断切换了热栏槽
    private int lastCount = -1;

    // ---- 反射缓存 ----
    private static Method moduleManagerGetMethod;
    private static Method isEnabledMethod;
    private static Object moduleManagerInstance;
    private static Object scaffoldModule;
    private static boolean reflectionTried;
    private static final int LB_REFLECT_RETRY_TICKS = 200; // 每 10 秒重试一次（LB 可能后加载）
    private static int reflectRetryCounter;

    // Inventory.selected 字段的反射访问（Minecraft 26.1 中该字段为 private）
    private static Field inventorySelectedField;
    private static boolean inventorySelectedTried;

    // ---- 进入 Scaffold 开启状态时的快照 ----
    private boolean wasScaffolding;
    private int startCount;      // 起始堆叠数（进度条 100%）
    private int currentCount;    // 当前堆叠数
    private boolean trackedStackConsumed; // 起始堆叠耗尽，进度条固定为 0

    /** 每客户端 tick 调用一次（由 EventDetector 驱动）。 */
    public void tick(Minecraft client) {
        IslandConfig cfg = IslandConfig.get();
        if (!cfg.statusScaffold || client.player == null) {
            recentPlacements.clear();
            placementsLastTick = 0;
            lastHandItem = null;
            lastCount = -1;
            wasScaffolding = false;
            return;
        }

        boolean scaffolding = isScaffoldEnabled();

        // 主手方块检测
        ItemStack hand = client.player.getMainHandItem();
        Item handItem = hand.getItem();
        int count = (handItem instanceof BlockItem && !hand.isEmpty()) ? hand.getCount() : 0;

        // --- BPS 计算：与上 tick 比堆叠数是否减少（仍持同一种方块，未切换热栏） ---
        int placedThisTick = 0;
        boolean sameSlot = (lastHandItem == handItem && handItem != null)
                        || (lastHandItem == null && handItem == null && count == 0);
        // 更可靠：用反射获取 selected slot 来做交叉验证（若可用）
        Integer sel = getSelectedSlotReflective(client);
        if (sel != null && lastSelectedSlotCached != -1) {
            sameSlot = (sel.intValue() == lastSelectedSlotCached);
        }
        lastSelectedSlotCached = sel == null ? -1 : sel.intValue();

        if (sameSlot && lastCount >= 0 && count < lastCount) {
            placedThisTick = lastCount - count;
        }
        lastHandItem = handItem;
        lastCount = count;

        recentPlacements.addLast(placedThisTick);
        while (recentPlacements.size() > 20) recentPlacements.removeFirst();

        placementsLastTick = placedThisTick;

        // --- 方块消耗进度：当 Scaffold 激活时跟踪起始堆叠 ---
        if (scaffolding && !wasScaffolding) {
            // 进入 Scaffold 状态，记录起始堆叠
            startCount = Math.max(count, 1);
            currentCount = count;
            trackedStackConsumed = false;
        } else if (scaffolding) {
            // 如果切换了槽位 / 换了新的一堆同方块，延续当前计数（不重置 startCount）
            currentCount = count;
            if (currentCount <= 0 && !trackedStackConsumed) trackedStackConsumed = true;
        } else {
            startCount = 0;
            currentCount = 0;
            trackedStackConsumed = false;
        }
        wasScaffolding = scaffolding;

        // --- LB 反射重试 ---
        if (!reflectionTried && scaffoldModule == null) {
            reflectRetryCounter++;
            if (reflectRetryCounter >= LB_REFLECT_RETRY_TICKS) {
                reflectRetryCounter = 0;
                reflectionTried = false; // 允许下次重新尝试
            }
        }
    }

    private int lastSelectedSlotCached = -1;

    private static Integer getSelectedSlotReflective(Minecraft client) {
        if (client.player == null) return null;
        var inv = client.player.getInventory();
        if (inv == null) return null;
        if (!inventorySelectedTried) {
            inventorySelectedTried = true;
            try {
                Field f = inv.getClass().getField("selected");
                f.setAccessible(true);
                inventorySelectedField = f;
            } catch (NoSuchFieldException ignored) {
                // 尝试 getDeclaredField（若是 private）
                try {
                    Field f = inv.getClass().getDeclaredField("selected");
                    f.setAccessible(true);
                    inventorySelectedField = f;
                } catch (NoSuchFieldException ignored2) {
                }
            }
        }
        if (inventorySelectedField == null) return null;
        try {
            Object v = inventorySelectedField.get(inv);
            if (v instanceof Integer i) return i;
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Scaffold 模块是否正在开启。 */
    public boolean isScaffolding() {
        return isScaffoldEnabled();
    }

    /** 过去 1 秒内放置的方块数。 */
    public int getBps() {
        int sum = 0;
        for (int n : recentPlacements) sum += n;
        return sum;
    }

    /** 当前手中方块剩余数（Scaffold 激活后起始堆叠内）。 */
    public int getCurrentBlocks() {
        return Math.max(0, currentCount);
    }

    /**
     * 方块消耗进度：1.0 = 满堆叠，0.0 = 空。
     * Scaffold 未开启时返回 -1。
     */
    public float getProgress() {
        if (!isScaffoldEnabled()) return -1f;
        if (trackedStackConsumed) return 0f;
        if (startCount <= 0) return -1f;
        return Mth2.clamp01((float) currentCount / (float) startCount);
    }

    /**
     * 波形采样：将最近 20 tick 的放置数据按 {@code barCount} 个分组聚合，
     * 返回每个分组的归一化强度 0..1。用于在 HUD 上绘制柱状波形条。
     * 没有数据时返回全零数组。
     *
     * @param barCount 需要的柱数（建议 8~16）
     * @return 长度为 barCount 的 float 数组，每项 0..1；最左为最早窗口，最右为最新
     */
    public float[] getWaveform(int barCount) {
        int n = Math.max(1, barCount);
        float[] out = new float[n];
        Integer[] arr = recentPlacements.toArray(new Integer[0]);
        if (arr.length == 0) return out;

        // 分组聚合：先从右（最新）向左分，每柱包含 ticksPerBar 个 tick，
        // 超出部分的最老历史（左侧）填 0。
        int ticksPerBar = Math.max(1, 20 / n);
        int peak = 1;
        for (int v : arr) peak = Math.max(peak, v);

        // 从右（最新）填充 out
        int arrIdx = arr.length - 1;
        for (int i = n - 1; i >= 0 && arrIdx >= 0; i--) {
            int sum = 0;
            for (int k = 0; k < ticksPerBar && arrIdx >= 0; k++, arrIdx--) {
                sum += arr[arrIdx];
            }
            out[i] = Mth2.clamp01((float) sum / (float) (peak * ticksPerBar));
        }
        return out;
    }

    // ---- LiquidBounce 反射探测 ----

    private static boolean isScaffoldEnabled() {
        ensureReflected();
        if (scaffoldModule == null || isEnabledMethod == null) return false;
        try {
            Object r = isEnabledMethod.invoke(scaffoldModule);
            return Boolean.TRUE.equals(r);
        } catch (Throwable ignored) {
            scaffoldModule = null;
            return false;
        }
    }

    private static void ensureReflected() {
        if (reflectionTried && scaffoldModule == null) return;
        if (scaffoldModule != null) return;
        reflectionTried = true;

        try {
            // LiquidBounce Nextgen:
            //   net.ccbluex.liquidbounce.LiquidBounce.INSTANCE
            //   -> .getModuleManager() 或 field moduleManager
            //   -> .getModule("Scaffold") 或 findModule(...)
            //   -> .getState() / .isEnabled()
            Class<?> lbClass = Class.forName("net.ccbluex.liquidbounce.LiquidBounce");
            Object instance = null;
            try {
                var instField = lbClass.getField("INSTANCE");
                instance = instField.get(null);
            } catch (NoSuchFieldException ignored) {
                for (var f : lbClass.getFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())
                            && f.getType().equals(lbClass)) {
                        instance = f.get(null);
                        break;
                    }
                }
            }
            if (instance == null) return;

            // 找 moduleManager
            Object mm = null;
            for (var m : lbClass.getMethods()) {
                if (m.getParameterCount() == 0 && m.getName().equals("getModuleManager")) {
                    mm = m.invoke(instance);
                    break;
                }
            }
            if (mm == null) {
                for (var f : lbClass.getFields()) {
                    if (f.getName().equals("moduleManager") && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        mm = f.get(instance);
                        break;
                    }
                }
            }
            if (mm == null) return;
            moduleManagerInstance = mm;

            // 找 getModule(String) / findModule / get
            Class<?> mmClass = mm.getClass();
            for (var m : mmClass.getMethods()) {
                String name = m.getName();
                if (m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == String.class
                        && (name.equals("getModule") || name.equals("get") || name.equals("findModule"))) {
                    moduleManagerGetMethod = m;
                    break;
                }
            }
            if (moduleManagerGetMethod == null) return;

            Object mod = moduleManagerGetMethod.invoke(mm, "Scaffold");
            if (mod == null) mod = moduleManagerGetMethod.invoke(mm, "scaffold");
            if (mod == null) return;
            scaffoldModule = mod;

            // 找 isEnabled / getState / getEnabled
            for (var m : mod.getClass().getMethods()) {
                if (m.getParameterCount() == 0
                        && (m.getName().equals("isEnabled") || m.getName().equals("getState")
                            || m.getName().equals("getEnabled") || m.getName().equals("state"))) {
                    isEnabledMethod = m;
                    break;
                }
            }
        } catch (Throwable ignored) {
            scaffoldModule = null;
        }
    }

    /** 内部小型 clamp，避免额外依赖。 */
    private static final class Mth2 {
        static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    }
}
