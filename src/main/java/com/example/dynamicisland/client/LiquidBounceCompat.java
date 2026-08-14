package com.example.dynamicisland.client;

import com.example.dynamicisland.DynamicIslandMod;
import com.example.dynamicisland.client.config.IslandConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Loose-coupling compatibility layer for <b>LiquidBounce Nextgen</b> (commit
 * {@code 6c1bbea}).
 *
 * <p>提供以下能力：
 * <ul>
 *   <li>模块开启/关闭事件推送（模块切换反馈通知）</li>
 *   <li>按名查询模块启用状态 {@link #isModuleEnabled(String)}</li>
 *   <li>模块激活总数 {@link #getActiveModuleCount()} + 核心模块状态快照 {@link #getCoreModuleStatus()}</li>
 *   <li>KillAura：目标、APS（每秒攻击数） {@link #getKaTarget()}, {@link #getKaAps()}</li>
 * </ul>
 *
 * <p>全部基于反射：没有 LB 安装时所有方法静默返回 null / 0 / false，绝不影响游戏。
 */
public final class LiquidBounceCompat {

    public static final String MOD_ID = "liquidbounce";

    // ---- 核心模块（用于折叠态指示灯缩写）----
    public static final List<String> CORE_MODULES = List.of(
            "KillAura", "Speed", "Fly", "NoFall", "AutoArmor");
    public static final Map<String, String> CORE_ABBREV = new LinkedHashMap<>();
    static {
        CORE_ABBREV.put("KillAura", "K");
        CORE_ABBREV.put("Speed",    "S");
        CORE_ABBREV.put("Fly",      "F");
        CORE_ABBREV.put("NoFall",   "N");
        CORE_ABBREV.put("AutoArmor","A");
    }

    private static boolean checked;
    private static boolean available;
    private static Object moduleManagerInstance;
    private static Field modulesField;
    private static Method modulesGetter;
    private static Method moduleNameGetter;
    private static Method moduleEnabledGetter;

    /** 缓存的 module-name → enabled（来自上一次 tick）*/
    private final Map<String, Boolean> prevStates = new HashMap<>();
    /** 活跃的最新快照（每 tick 更新，给其他功能查询）*/
    private static final Map<String, Boolean> currentStates = new HashMap<>();
    private static int activeCount;
    private boolean initialised;

    // ---- KillAura 专用反射 ----
    private static Object kaModuleCached;
    private static Field kaTargetField;
    private static Field kaApsField;
    private static Class<?> kombatUnitClass;
    private static Method kombatTargetGetter;
    private static Method kombatHealthGetter;
    private static Method kombatNameGetter;
    private static boolean kaReflectionTried;
    // APS 滚动窗口（KA模块没有直接APS时，靠检测目标切换+攻击估算，每50ms一次）
    private static final Deque<Integer> kaAttackWindow = new ArrayDeque<>(20);
    private static Entity lastTargetEntity;
    private static int ticksPerKaPollCounter;

    /**
     * 通用单模块启用状态查询（非LB或未找到模块→false）。
     * 大小写不敏感。
     */
    public static boolean isModuleEnabled(String name) {
        if (!isPresent()) return false;
        for (var e : currentStates.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) return Boolean.TRUE.equals(e.getValue());
        }
        return false;
    }

    /** 本 tick 激活的模块总数。 */
    public static int getActiveModuleCount() {
        return activeCount;
    }

    /**
     * 返回核心模块快照：Map 名字 → 是否开启（顺序按 CORE_MODULES）。
     * 没有 LB 时返回空 map。
     */
    public static Map<String, Boolean> getCoreModuleStatus() {
        LinkedHashMap<String, Boolean> r = new LinkedHashMap<>();
        for (String m : CORE_MODULES) r.put(m, isModuleEnabled(m));
        return r;
    }

    // =========================================================
    // KillAura 数据（目标名、目标血量、APS）
    // =========================================================

    /** KA 目标的名字；无目标/KA没开返回 null。 */
    public static String getKaTarget() {
        if (!isPresent() || !isModuleEnabled("KillAura")) return null;
        resolveKa();
        if (kaModuleCached == null) return null;
        try {
            Entity ent = getKaTargetEntity();
            if (ent instanceof LivingEntity le) return le.getName().getString();
            if (ent != null) return ent.getName().getString();
        } catch (Throwable ignored) {}
        return null;
    }

    /** KA 目标的剩余血量（0..36左右），没目标返回 -1。 */
    public static float getKaTargetHealth() {
        if (!isPresent() || !isModuleEnabled("KillAura")) return -1f;
        resolveKa();
        try {
            Entity ent = getKaTargetEntity();
            if (ent instanceof LivingEntity le) return le.getHealth();
        } catch (Throwable ignored) {}
        return -1f;
    }

    /** KA 每秒攻击数（滚动窗口），KA没开返回 0。 */
    public static int getKaAps() {
        if (!isModuleEnabled("KillAura")) {
            kaAttackWindow.clear();
            return 0;
        }
        // 优先直接从模块字段拿 aps
        resolveKa();
        if (kaModuleCached != null && kaApsField != null) {
            try {
                Object v = kaApsField.get(kaModuleCached);
                if (v instanceof Number n) return n.intValue();
            } catch (Throwable ignored) {}
        }
        // 回退：我们自己估算的滚动窗口
        int sum = 0;
        for (int n : kaAttackWindow) sum += n;
        return sum;
    }

    private static Entity getKaTargetEntity() {
        if (kaTargetField != null && kaModuleCached != null) {
            try {
                Object v = kaTargetField.get(kaModuleCached);
                if (v instanceof Entity e) return e;
                if (v instanceof LivingEntity le) return le;
                // KombatUnit / Target 包装类
                if (kombatUnitClass != null && kombatUnitClass.isInstance(v)) {
                    if (kombatTargetGetter != null) {
                        Object t = kombatTargetGetter.invoke(v);
                        if (t instanceof Entity e) return e;
                    }
                }
            } catch (Throwable ignored) {}
        }
        // 回退：mc.crosshairPickEntity 当 KA 使用
        var mc = Minecraft.getInstance();
        Entity hit = mc.crosshairPickEntity;
        if (hit != null) return hit;
        return null;
    }

    private static void resolveKa() {
        if (kaReflectionTried) return;
        kaReflectionTried = true;
        try {
            // 找模块
            Object km = findModuleByName("KillAura");
            if (km == null) return;
            kaModuleCached = km;
            Class<?> cls = km.getClass();

            // target / targetEntity / currentTarget 字段
            for (Field f : cls.getFields()) {
                String n = f.getName().toLowerCase();
                if (n.contains("target") && (Entity.class.isAssignableFrom(f.getType())
                        || LivingEntity.class.isAssignableFrom(f.getType())
                        || f.getType().getSimpleName().toLowerCase().contains("kombat")
                        || f.getType().getSimpleName().toLowerCase().contains("target"))) {
                    kaTargetField = f;
                    kaTargetField.setAccessible(true);
                    // 如果是 KombatUnit 类，顺手探测其下的 getter
                    if (!Entity.class.isAssignableFrom(f.getType())
                            && !LivingEntity.class.isAssignableFrom(f.getType())) {
                        kombatUnitClass = f.getType();
                        for (Method m : kombatUnitClass.getMethods()) {
                            String mn = m.getName().toLowerCase();
                            if (kombatTargetGetter == null
                                    && (mn.equals("getentity") || mn.equals("gettarget") || mn.equals("entity") || mn.equals("target"))
                                    && m.getParameterCount() == 0
                                    && (Entity.class.isAssignableFrom(m.getReturnType())
                                        || LivingEntity.class.isAssignableFrom(m.getReturnType()))) {
                                m.setAccessible(true);
                                kombatTargetGetter = m;
                            }
                            if (kombatHealthGetter == null
                                    && (mn.equals("gethealth") || mn.equals("health"))
                                    && m.getParameterCount() == 0
                                    && (m.getReturnType() == float.class || m.getReturnType() == Float.class
                                        || m.getReturnType() == double.class || m.getReturnType() == Double.class)) {
                                m.setAccessible(true);
                                kombatHealthGetter = m;
                            }
                            if (kombatNameGetter == null
                                    && (mn.equals("getname") || mn.equals("name"))
                                    && m.getParameterCount() == 0
                                    && m.getReturnType() == String.class) {
                                m.setAccessible(true);
                                kombatNameGetter = m;
                            }
                        }
                    }
                    break;
                }
            }
            // 找 APS 字段（int / Integer / double）
            for (Field f : cls.getFields()) {
                String n = f.getName().toLowerCase();
                if (n.equals("aps") || n.equals("attackpersecond") || n.contains("aps") && n.contains("attack")) {
                    f.setAccessible(true);
                    kaApsField = f;
                    break;
                }
            }
        } catch (Throwable t) {
            DynamicIslandMod.LOGGER.debug("KA resolve failed", t);
        }
    }

    /** 按名在模块集合里找到 module 对象（首字母大写其余忽略大小写匹配）。 */
    private static Object findModuleByName(String name) {
        if (!isPresent()) return null;
        try {
            Object raw = modulesField != null
                    ? modulesField.get(moduleManagerInstance)
                    : modulesGetter.invoke(moduleManagerInstance);
            if (!(raw instanceof Iterable<?> it)) return null;
            for (Object m : it) {
                String nm = readName(m);
                if (nm != null && nm.equalsIgnoreCase(name)) return m;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // =========================================================
    // 基础：LB 可用性 + tick 轮询（含模块切换事件推送）
    // =========================================================

    private static synchronized void resolve() {
        if (checked) return;
        checked = true;

        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return;

        try {
            Class<?> mmClass = Class.forName(
                    "net.ccbluex.liquidbounce.features.module.ModuleManager");
            try {
                Field f = mmClass.getDeclaredField("INSTANCE");
                f.setAccessible(true);
                moduleManagerInstance = f.get(null);
            } catch (NoSuchFieldException e) {
                moduleManagerInstance = mmClass;
            }

            try {
                modulesField = mmClass.getDeclaredField("modules");
                modulesField.setAccessible(true);
            } catch (NoSuchFieldException ignored) {
                for (Method m : mmClass.getMethods()) {
                    if (m.getName().equals("getModules") && m.getParameterCount() == 0) {
                        modulesGetter = m;
                        break;
                    }
                }
            }

            available = modulesField != null || modulesGetter != null;
            if (available) {
                DynamicIslandMod.LOGGER.info(
                        "Dynamic Island: LiquidBounce Nextgen detected, module integration active.");
            }
        } catch (Throwable t) {
            DynamicIslandMod.LOGGER.debug("Dynamic Island: LB ModuleManager not resolvable", t);
        }
    }

    public static boolean isPresent() {
        resolve();
        return available;
    }

    /** 每客户端 tick 轮询一次：更新缓存 + 发模块切换事件。 */
    public void tick(IslandState state) {
        if (!IslandConfig.get().liquidbounce || !isPresent()) {
            currentStates.clear();
            activeCount = 0;
            return;
        }

        Iterable<?> modules;
        try {
            Object raw = modulesField != null
                    ? modulesField.get(moduleManagerInstance)
                    : modulesGetter.invoke(moduleManagerInstance);
            if (!(raw instanceof Iterable<?> iter)) return;
            modules = iter;
        } catch (Throwable t) {
            return;
        }

        Map<String, Boolean> current = new HashMap<>();
        int count = 0;
        for (Object module : modules) {
            try {
                String name = readName(module);
                boolean enabled = readEnabled(module);
                if (name == null) continue;
                current.put(name, enabled);
                if (enabled) count++;

                if (initialised) {
                    Boolean prev = prevStates.get(name);
                    if (prev == null || prev != enabled) {
                        emitToggle(state, name, enabled);
                    }
                }
            } catch (Throwable ignored) {}
        }

        prevStates.clear();
        prevStates.putAll(current);
        currentStates.clear();
        currentStates.putAll(current);
        activeCount = count;
        initialised = true;

        // KA APS 回退估算（每 1 tick 推进一次窗口）
        ticksPerKaPollCounter++;
        int attacksThisTick = 0;
        Entity tEnt = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().crosshairPickEntity : null;
        if (isModuleEnabled("KillAura")) {
            Entity kaEnt = null;
            try { kaEnt = getKaTargetEntity(); } catch (Throwable ignored) {}
            if (kaEnt != null) {
                if (lastTargetEntity == null || lastTargetEntity != kaEnt) {
                    // 新目标出现，先不计数；但如果同一目标连续存在且被攻击过
                    lastTargetEntity = kaEnt;
                } else {
                    // 目标相同，如果这 tick 做了攻击动作，通常 mc 会有玩家手部摆动
                    var p = Minecraft.getInstance().player;
                    if (p != null && p.attackAnim > 0.95f) attacksThisTick = 1;
                }
            } else {
                lastTargetEntity = null;
            }
        } else {
            lastTargetEntity = null;
        }
        kaAttackWindow.addLast(attacksThisTick);
        while (kaAttackWindow.size() > 20) kaAttackWindow.removeFirst();
    }

    /** 模块切换弹出药丸事件 — 让用户按快捷键时能看到反馈。 */
    private void emitToggle(IslandState state, String moduleName, boolean enabled) {
        ItemStack icon = enabled ? new ItemStack(Items.LIME_DYE) : new ItemStack(Items.GRAY_DYE);
        Component title = Component.literal(moduleName)
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        Component sub = Component.translatable(
                enabled ? "dynamicisland.event.liquidbounce.on"
                        : "dynamicisland.event.liquidbounce.off");
        state.offer(new IslandEvent(
                IslandEvent.Type.LIQUIDBOUNCE,
                enabled ? IslandEvent.Priority.INFO : IslandEvent.Priority.LOW,
                icon, title, sub,
                (int) (Math.max(1.2f, IslandState.displaySeconds() * 0.6f) * 20), // ~1.2s 显示
                "liquidbounce:" + moduleName, false));
    }

    private static String readName(Object module) throws Exception {
        if (moduleNameGetter == null) moduleNameGetter = resolveGetter(module.getClass(), "getName");
        return (String) moduleNameGetter.invoke(module);
    }

    private static boolean readEnabled(Object module) throws Exception {
        if (moduleEnabledGetter == null) moduleEnabledGetter = resolveGetter(module.getClass(), "isEnabled", "getEnabled");
        Object result = moduleEnabledGetter.invoke(module);
        return result instanceof Boolean b ? b : false;
    }

    private static Method resolveGetter(Class<?> clazz, String... names) {
        for (String n : names) {
            try {
                Method m = clazz.getMethod(n);
                if (m.getParameterCount() == 0) return m;
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }
}
