package com.example.dynamicisland.client;

import com.example.dynamicisland.client.config.IslandConfig;

/**
 * Owns the live state of the island: the active event slots, their timers,
 * and the continuously-eased geometry that the renderer reads each frame.
 *
 * <h2>Shape vocabulary</h2>
 * <ul>
 *   <li><b>Collapsed</b> &mdash; a thin idle pill, nothing happening.</li>
 *   <li><b>Single</b> &mdash; one event expanded, icon + two text lines.</li>
 *   <li><b>Split</b> &mdash; two concurrent events shown as two pills that
 *       ease apart with a gap (the signature Dynamic Island "merge" motion).</li>
 * </ul>
 *
 * <p>Geometry is expressed in <i>logical</i> pixels at 1&times; GUI scale; the
 * renderer multiplies by the configured scale before drawing.
 */
public final class IslandState {

    // ---- Logical geometry (pre user-scale) ----
    public static final float COLLAPSED_W = 132f;
    public static final float COLLAPSED_H = 12f;
    public static final float COLLAPSED_R = 6f;

    /** 折叠态显示状态信息时的默认基础尺寸；实际宽度由 HUD 按内容自适应。 */
    public static final float STATUS_MIN_W = 200f;
    public static final float STATUS_MAX_W = 420f; // 增加上限容纳更多文字
    public static final float STATUS_SINGLE_H = 16f;   // 单行：FPS/IP/LB 信息
    public static final float STATUS_DUAL_H   = 28f;   // 双行
    // 加模块/速度/盔甲/KA的 4 条新行：每条 8+2px
    public static final float STATUS_BASE_NO_LYRIC = 28f;   // info + modules + speed ...起步
    public static final float STATUS_BASE_WITH_LYRIC = 38f; // 含歌词时打底
    // Scaffold 全量时（含波形+进度条）需加额外14px
    public static final float STATUS_SCAFFOLD_EXTRA = 14f;  // scaffold 行 + waveform + progress
    // 上限：info(10) + modules/speed/lyric/ka/armor(5*10) + scaffold(8) + waveform(8) + progress(6) ≈ 88
    public static final float STATUS_MAX_H = 92f;
    public static final float STATUS_SCAFFOLD_H = 50f; // 向后兼容（旧常量保留）
    public static final float STATUS_TRIPLE_H = 62f;   // 向后兼容

    public static final float EXPANDED_W = 264f;
    public static final float EXPANDED_H = 46f;
    public static final float EXPANDED_R = 18f;

    // Equals EXPANDED_W / 2 so a closed split (gap = 0) is pixel-identical to
    // the single-event pill — the morph from one pill to two is seamless.
    public static final float MERGED_SIDE_W = EXPANDED_W / 2f;
    public static final float MERGED_GAP = 16f;

    public enum Mode { COLLAPSED, SINGLE, SPLIT }

    // ---- Eased geometry ----
    public final EasedValue width   = new EasedValue(COLLAPSED_W, 14f);
    public final EasedValue height  = new EasedValue(COLLAPSED_H, 14f);
    public final EasedValue radius  = new EasedValue(COLLAPSED_R, 16f);
    /** 0 = single pill spans full width, >0 = split gap opens. */
    public final EasedValue splitGap = new EasedValue(0f, 10f);
    /** 0 = content invisible, 1 = fully shown. Drives icon + text fade. */
    public final EasedValue contentAlpha = new EasedValue(0f, 9f);
    /** 0 = invisible, 1 = fully opaque. */
    public final EasedValue alpha = new EasedValue(1f, 12f);

    /** 折叠态是否显示常驻状态信息（FPS/IP/版本）。由 HUD 在渲染前设置。 */
    public boolean statusActive;
    /** HUD 每帧根据实际内容宽度设置目标宽度（自适应）。 */
    public float statusTargetW = STATUS_MIN_W;
    /** HUD 每帧根据是否有歌词行设置目标高度（单行/双行）。 */
    public float statusTargetH = STATUS_SINGLE_H;

    // ---- Active events ----
    private IslandEvent primary;
    private IslandEvent secondary;

    public IslandEvent getPrimary() { return primary; }
    public IslandEvent getSecondary() { return secondary; }

    public Mode computeMode() {
        if (primary != null && secondary != null) return Mode.SPLIT;
        // 音乐事件被合并进折叠态状态栏（与 FPS/IP/歌词等共存），因此
        // 当唯一的活跃事件是音乐时，仍按 COLLAPSED 处理，由 HUD 在状态栏
        // 中追加一行歌曲名。若同时有其它瞬时事件，则走 SPLIT 正常展开。
        if (primary != null && !isMusic(primary)) return Mode.SINGLE;
        return Mode.COLLAPSED;
    }

    /** 判断事件是否为音乐类（唱片机 MUSIC 或网易云 NETEASE_MUSIC）。 */
    private static boolean isMusic(IslandEvent ev) {
        return ev != null && (ev.type == IslandEvent.Type.MUSIC
                || ev.type == IslandEvent.Type.NETEASE_MUSIC);
    }

    /** 当前是否有音乐事件在播放（用于触发状态栏显示）。 */
    public boolean hasMusicEvent() {
        return isMusic(primary) || isMusic(secondary);
    }

    /** 取当前的音乐事件（优先 primary，其次 secondary），无则 null。 */
    public IslandEvent getMusicEvent() {
        if (isMusic(primary)) return primary;
        if (isMusic(secondary)) return secondary;
        return null;
    }

    /**
     * Offer a new event. Rules:
     * <ul>
     *   <li>If it matches an existing slot's key, refresh that slot (timer reset).</li>
     *   <li>Else if there's room, take the empty slot.</li>
     *   <li>Else evict the lowest-priority slot, preferring to keep the new event
     *       when its priority is &gt;= the victim's.</li>
     * </ul>
     */
    public void offer(IslandEvent event) {
        if (event == null) return;

        // Refresh existing slot.
        if (primary != null && primary.sameSlot(event)) { primary = event; return; }
        if (secondary != null && secondary.sameSlot(event)) { secondary = event; return; }

        // Fill an empty slot.
        if (primary == null) { primary = event; return; }
        if (secondary == null) { secondary = event; return; }

        // Both full: evict the weaker slot.
        IslandEvent victim = primary.priority.weight <= secondary.priority.weight ? primary : secondary;
        if (event.priority.weight >= victim.priority.weight) {
            if (victim == primary) primary = event;
            else secondary = event;
        }
    }

    /** Drop a persistent event whose source condition is no longer met. */
    public void retract(String key) {
        if (primary != null && primary.key.equals(key)) primary = null;
        if (secondary != null && secondary.key.equals(key)) secondary = null;
    }

    /** Advance timers and ease geometry toward the target shape. {@code dt} in seconds. */
    public void tick(float dt) {
        // Expire transient events.
        primary = age(primary, dt);
        secondary = age(secondary, dt);

        Mode mode = computeMode();
        switch (mode) {
            case COLLAPSED -> setShape(
                    statusActive ? statusTargetW : COLLAPSED_W,
                    statusActive ? statusTargetH : COLLAPSED_H,
                    COLLAPSED_R, 0f,
                    statusActive ? 1f : 0f);
            case SINGLE   -> setShape(EXPANDED_W, EXPANDED_H, EXPANDED_R, 0f, 1f);
            case SPLIT    -> setShape(
                    MERGED_SIDE_W * 2f + MERGED_GAP,
                    EXPANDED_H,
                    EXPANDED_R,
                    MERGED_GAP,
                    1f);
            // When the gap eases from 0 -> MERGED_GAP the two halves drift apart;
            // since each half is EXPANDED_W/2, at gap = 0 the silhouette matches
            // the single pill exactly, so no pop occurs.
        }

        // When collapsing, fade content slightly ahead of the shape so text
        // vanishes before the pill finishes shrinking (avoids text clipping).
        float contentTarget = (mode == Mode.COLLAPSED && !statusActive) ? 0f : 1f;

        width.update(dt);
        height.update(dt);
        radius.update(dt);
        splitGap.update(dt);
        contentAlpha.setTarget(contentTarget);
        contentAlpha.update(dt);
        alpha.update(dt);
    }

    private void setShape(float w, float h, float r, float gap, float content) {
        width.setTarget(w);
        height.setTarget(h);
        radius.setTarget(r);
        splitGap.setTarget(gap);
        // content target is managed in tick() for the fade-ahead behaviour.
    }

    private IslandEvent age(IslandEvent event, float dt) {
        if (event == null) return null;
        if (event.persistent) return event; // timer refreshed by detector each tick
        event.durationTicks -= dt * 20f; // convert seconds back to ticks
        return event.durationTicks <= 0 ? null : event;
    }

    /** Convenience: how many seconds the overlay should keep an event open. */
    public static float displaySeconds() {
        return IslandConfig.get().displayTime;
    }
}
