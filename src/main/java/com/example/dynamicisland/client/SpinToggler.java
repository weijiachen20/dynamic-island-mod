package com.example.dynamicisland.client;

/**
 * 手持物品旋转的全局开关状态。
 * 按下 R 键（可在控制中重绑定）切换 ON/OFF。
 */
public final class SpinToggler {
    private static volatile boolean enabled;

    private SpinToggler() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean v) {
        enabled = v;
    }

    public static void toggle() {
        enabled = !enabled;
    }
}
