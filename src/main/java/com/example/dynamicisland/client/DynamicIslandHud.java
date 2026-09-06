package com.example.dynamicisland.client;

import com.example.dynamicisland.client.config.IslandConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the Dynamic Island overlay each frame and advances the island's
 * eased geometry.
 *
 * <p>Visual design (post-makeover):
 * <ul>
 *   <li>Pill background: dark top + lighter bottom vertical gradient (glass)</li>
 *   <li>Soft outer glow ring (subtle) for a premium feel</li>
 *   <li>Status line uses Unicode icons (⚡ 🌐 🎧 🎵) + tiered colours</li>
 *   <li>Pill auto-resizes width based on content; height expands to two rows
 *       when a lyric line is shown</li>
 * </ul>
 */
public final class DynamicIslandHud {

    private final IslandState state;
    private long lastNanos;
    private float timeSeconds;
    /** 波形平滑：上一帧显示的采样（与目标采样做lerp逼近） */
    private float[] smoothedWaveform = new float[StatusInfo.WAVEFORM_BARS];
    /** 方块消耗进度条平滑值（0..1 或 -1=无进度） */
    private float smoothedProgress = -1f;
    /** 模式切换弹入计时器（>0 表示弹入动画进行中，单位秒）。 */
    private float popTimer = 0f;
    /** 上一帧的模式，用于检测模式切换并触发弹入。 */
    private IslandState.Mode prevMode = IslandState.Mode.COLLAPSED;
    /** 图标入场弹跳计时器（>0 表示弹跳进行中，单位秒）。 */
    private float iconEnterTimer = 0f;
    /** 上一帧的 contentAlpha，用于检测上升沿触发图标弹跳。 */
    private float prevContentAlpha = 0f;

    public DynamicIslandHud(IslandState state) {
        this.state = state;
    }

    public void render(GuiGraphicsExtractor context, Minecraft client) {
        IslandConfig cfg = IslandConfig.get();
        if (!cfg.enabled || client.player == null || client.level == null) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : (float) Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        timeSeconds += dt;

        // --- 1. Compute adaptive status geometry before easing ---
        // 音乐播放时也进入状态栏显示（歌曲名并入待机信息）。
        state.statusActive = StatusInfo.hasStatus(client) || state.hasMusicEvent();
        if (state.statusActive) {
            updateStatusTarget(client);
        }

        state.tick(dt);

        IslandState.Mode mode = state.computeMode();

        // --- 动画 #5：模式切换弹入（药丸中心做衰减余弦弹跳，~0.35s） ---
        if (mode != prevMode) {
            prevMode = mode;
            popTimer = 0.35f;
        }
        if (popTimer > 0f) popTimer = Math.max(0f, popTimer - dt);
        float popScale = 1f;
        if (popTimer > 0f) {
            float pt = 1f - (popTimer / 0.35f); // 0..1 进度
            popScale = 1f + 0.08f * (float) Math.exp(-pt * 5.5) * (float) Math.cos(pt * 9.0);
        }

        // --- 动画 #6：图标入场弹跳计时（contentAlpha 上升沿触发） ---
        float contentAlphaNow = state.contentAlpha.get();
        if (contentAlphaNow > 0.3f && prevContentAlpha <= 0.3f) iconEnterTimer = 0.4f;
        prevContentAlpha = contentAlphaNow;
        if (iconEnterTimer > 0f) iconEnterTimer = Math.max(0f, iconEnterTimer - dt);

        float scale = Math.max(0.5f, cfg.scale);
        float logicalW = state.width.get();
        float logicalH = state.height.get();
        int screenW = context.guiWidth();

        float scaledW = logicalW * scale;
        float topMargin = 6f;
        float screenX;
        switch (cfg.position) {
            case 1 -> screenX = 8f;
            case 2 -> screenX = screenW - scaledW - 8f;
            default -> screenX = (screenW - scaledW) / 2f;
        }
        float screenY = topMargin;

        // --- 动画：URGENT 横向晃动 shake (±1.5px @ 11Hz) ---
        boolean urgent = isUrgent();
        float shakeX = urgent ? (float) Math.sin(timeSeconds * 22.0) * 1.5f : 0f;
        // 叠加微小的整体呼吸位移（避免呼吸只改透明度）
        float breathY = (float) Math.sin(timeSeconds * 1.7) * 0.35f;
        // --- 动画 #7：折叠态常驻信息时的极轻横向摇摆（让药丸"活着"） ---
        float swayX = (!urgent && mode == IslandState.Mode.COLLAPSED && state.statusActive)
                ? (float) Math.sin(timeSeconds * 0.8) * 0.3f : 0f;

        var matrices = context.pose();
        matrices.pushMatrix();
        // 以药丸几何中心做缩放（cfg.scale + popScale），保证弹入时左右上下对称生长
        float cx = screenX + shakeX + swayX + scaledW / 2f;
        float cy = screenY + breathY + logicalH * scale / 2f;
        matrices.translate(cx, cy);
        matrices.scale(scale * popScale, scale * popScale);
        matrices.translate(-logicalW / 2f, -logicalH / 2f);

        float baseOpacity = cfg.bgOpacity / 100f * state.alpha.get();

        // --- 动画：URGENT 快速闪烁 + 折叠态呼吸脉动 ---
        if (urgent) {
            baseOpacity *= 0.90f + 0.10f * Mth.sin(timeSeconds * 6.3f);
        } else if (mode == IslandState.Mode.COLLAPSED && state.statusActive) {
            // 折叠态常驻时做轻柔呼吸：透明度 ±2.5% @ 1.2Hz
            baseOpacity *= 0.975f + 0.025f * (0.5f + 0.5f * Mth.sin(timeSeconds * 1.2f * 3.14159f * 2f - 1.57f));
        }

        if (mode == IslandState.Mode.SPLIT) {
            float side = IslandState.MERGED_SIDE_W;
            float gap = state.splitGap.get();
            float r = clampRadius(state.radius.get(), side, logicalH);
            drawFancyPill(context, 0, 0, side, logicalH, r, baseOpacity);
            drawFancyPill(context, side + gap, 0, side, logicalH, r, baseOpacity);
            drawContent(context, client, state.getPrimary(), 0, side, logicalH, true);
            drawContent(context, client, state.getSecondary(), side + gap, side, logicalH, true);
        } else {
            float r = clampRadius(state.radius.get(), logicalW, logicalH);
            drawFancyPill(context, 0, 0, logicalW, logicalH, r, baseOpacity);
            if (mode == IslandState.Mode.SINGLE) {
                drawContent(context, client, state.getPrimary(), 0, logicalW, logicalH, false);
            } else if (mode == IslandState.Mode.COLLAPSED && state.statusActive) {
                drawStatus(context, client, logicalW, logicalH, dt);
            }
        }

        matrices.popMatrix();
    }

    // ---- Status geometry estimation (called before tick each frame) ----

    private void updateStatusTarget(Minecraft client) {
        IslandConfig cfg = IslandConfig.get();
        boolean hasLyric   = StatusInfo.hasLyricLine(client);
        boolean hasScaffold = StatusInfo.hasScaffoldLine(client);
        boolean showProgress = hasScaffold && StatusInfo.getScaffoldProgress(client) >= 0f;
        boolean hasModules = StatusInfo.hasModulesLine(client);
        boolean hasKa      = StatusInfo.hasKaLine(client);
        boolean hasSpeed   = StatusInfo.hasSpeedLine(client);
        boolean hasArmor   = StatusInfo.hasArmorLine(client);
        boolean hasMusic   = state.hasMusicEvent();

        Component info    = StatusInfo.getInfoLine(client);
        Component lyric   = hasLyric   ? StatusInfo.getLyricLine(client)   : null;
        Component scaf    = hasScaffold? StatusInfo.getScaffoldLine(client): null;
        Component modules = hasModules ? StatusInfo.getModulesLine(client) : null;
        Component ka      = hasKa      ? StatusInfo.getKaLine(client)      : null;
        Component speed   = hasSpeed   ? StatusInfo.getSpeedLine(client)   : null;
        Component armor   = hasArmor   ? StatusInfo.getArmorLine(client)   : null;
        Component music   = hasMusic   ? buildMusicLine()                  : null;

        int wInfo    = info    == null ? 0 : client.font.width(info);
        int wLyric   = lyric   == null ? 0 : client.font.width(lyric);
        int wScaf    = scaf    == null ? 0 : client.font.width(scaf);
        int wModules = modules == null ? 0 : client.font.width(modules);
        int wKa      = ka      == null ? 0 : client.font.width(ka);
        int wSpeed   = speed   == null ? 0 : client.font.width(speed);
        int wArmor   = armor   == null ? 0 : client.font.width(armor);
        int wMusic   = music   == null ? 0 : client.font.width(music);
        float maxTextW = Math.max(Math.max(Math.max(Math.max(Math.max(
                                Math.max(Math.max(wInfo, wLyric), wScaf), wModules), wKa), wSpeed), wArmor), wMusic);

        float pad = 18f;
        state.statusTargetW = Mth.clamp(maxTextW + pad,
                IslandState.STATUS_MIN_W, IslandState.STATUS_MAX_W);

        // 高度：按文本行数 + 波形 + 进度条 累加
        float textRowH = 8f, lineGap = 2f;
        int rows = 0;
        if (info != null) rows++;
        if (music != null) rows++;   // 歌曲名行（与待机信息共存）
        if (hasModules) rows++;
        if (hasSpeed) rows++;
        if (hasLyric) rows++;
        if (hasKa) rows++;
        if (hasArmor) rows++;
        if (hasScaffold && scaf != null) rows++;
        float textTotal = rows * textRowH + Math.max(0, rows - 1) * lineGap;
        boolean hasWave = hasScaffold;
        boolean hasBar = showProgress;
        float extra = 0f;
        if (hasWave) extra += 8f; // waveform (gap 2 + waveH 6)
        if (hasBar)  extra += 6f; // progress bar (bar4 + gap2)
        float totalH = 8f /* top/bottom margin 4 each */
                + textTotal + extra;
        state.statusTargetH = Mth.clamp(totalH,
                IslandState.STATUS_SINGLE_H, IslandState.STATUS_MAX_H);
    }

    // ---- Drawing helpers ----

    /** 在折叠态药丸内绘制常驻状态信息（支持最多 7 行 + 波形/进度条）。dt 用于平滑动画 */
    private void drawStatus(GuiGraphicsExtractor ctx, Minecraft client, float w, float h, float dt) {
        int textAlpha = Math.round(Mth.clamp(state.contentAlpha.get(), 0f, 1f) * 255);
        if (textAlpha <= 2) return;

        boolean hasLyric   = StatusInfo.hasLyricLine(client);
        boolean hasScaffold = StatusInfo.hasScaffoldLine(client);
        boolean hasModules = StatusInfo.hasModulesLine(client);
        boolean hasKa      = StatusInfo.hasKaLine(client);
        boolean hasSpeed   = StatusInfo.hasSpeedLine(client);
        boolean hasArmor   = StatusInfo.hasArmorLine(client);
        float scProgress   = hasScaffold ? StatusInfo.getScaffoldProgress(client) : -1f;
        boolean showProgress = scProgress >= 0f;
        float[] waveform   = hasScaffold ? StatusInfo.getScaffoldWaveform(client) : null;

        // --- 动画：波形/进度条平滑逼近（lerp），避免柱子瞬时跳变 ---
        float lerpK = Math.min(1.0f, dt * 14.0f); // 每帧 ~14Hz 逼近，约 2.5 帧完成过渡
        if (hasScaffold && waveform != null) {
            int n = Math.min(waveform.length, smoothedWaveform.length);
            for (int i = 0; i < n; i++) {
                smoothedWaveform[i] += (waveform[i] - smoothedWaveform[i]) * lerpK;
            }
            // 填充超出部分为 0（若目标更短），或复制剩余（若目标更长）
            for (int i = n; i < smoothedWaveform.length; i++) smoothedWaveform[i] = 0f;
        } else {
            // 无波形时快速归零（保持动画的"收回去"效果）
            float k = Math.min(1.0f, dt * 30.0f);
            for (int i = 0; i < smoothedWaveform.length; i++) smoothedWaveform[i] *= (1f - k);
        }
        // 进度条平滑（无进度时拉回 -1）
        float targetProg = showProgress ? scProgress : -1f;
        if (smoothedProgress < 0f && targetProg >= 0f) {
            smoothedProgress = targetProg; // 出现时直接对齐，避免从 -1 爬上来
        } else {
            smoothedProgress += (targetProg - smoothedProgress) * lerpK;
        }
        boolean showSmoothedProgress = smoothedProgress >= 0f;
        float smoothedProgressDisplay = Math.max(0f, Math.min(1f, smoothedProgress));

        Component info    = StatusInfo.getInfoLine(client);
        Component lyric   = hasLyric   ? StatusInfo.getLyricLine(client)   : null;
        Component scaf    = hasScaffold? StatusInfo.getScaffoldLine(client): null;
        Component modules = hasModules ? StatusInfo.getModulesLine(client) : null;
        Component ka      = hasKa      ? StatusInfo.getKaLine(client)      : null;
        Component speed   = hasSpeed   ? StatusInfo.getSpeedLine(client)   : null;
        Component armor   = hasArmor   ? StatusInfo.getArmorLine(client)   : null;
        Component music   = state.hasMusicEvent() ? buildMusicLine() : null;

        int pad = 8;
        int maxW = Math.round(w - pad * 2f);

        // 构建行序列：保持与 updateStatusTarget 中顺序一致
        // 歌曲名紧跟在 info 之后，与 FPS/IP/歌词等待机信息同屏显示。
        java.util.List<Component> rows = new java.util.ArrayList<>();
        if (info != null)    rows.add(info);
        if (music != null)   rows.add(music);
        if (modules != null) rows.add(modules);
        if (speed != null)   rows.add(speed);
        if (lyric != null)   rows.add(lyric);
        if (ka != null)      rows.add(ka);
        if (armor != null)   rows.add(armor);
        if (scaf != null)    rows.add(scaf);

        float textRowH = 8f, lineGap = 2f;
        float waveH = 6f, waveGap = 2f;
        float barH = 4f, barGap = 2f;

        float textTotal = rows.size() * textRowH + Math.max(0, rows.size() - 1) * lineGap;
        boolean hasWave = (hasScaffold); // 平滑后可能仍有残余，所以总是在有 scaffold 时保留位
        boolean hasBar = showSmoothedProgress;

        float extra = 0f;
        if (hasWave) extra += waveGap + waveH;
        if (hasBar)  extra += barGap + barH;
        float totalContent = textTotal + extra;
        float cursorY = (h - totalContent) / 2f;

        for (Component c : rows) {
            Component disp = truncateToWidth(client, c, maxW);
            if (disp != null) {
                int dw = client.font.width(disp);
                int x = Math.round((w - dw) / 2f);
                text(ctx, client, disp, x, Math.round(cursorY), textAlpha);
            }
            cursorY += textRowH + lineGap;
        }
        if (hasWave) {
            cursorY -= lineGap; // 去掉最后一个多余lineGap
            cursorY += waveGap;
            float wfX = pad;
            float wfY = cursorY;
            float wfW = w - pad * 2f;
            drawBpsWaveform(ctx, wfX, wfY, wfW, waveH, smoothedWaveform, textAlpha);
            cursorY += waveH;
        }
        if (hasBar) {
            if (!hasWave) cursorY -= lineGap; // 没波形就同样回退多余lineGap
            cursorY += barGap;
            float barX = pad;
            float barY = cursorY;
            float barW = w - pad * 2f;
            drawProgressBar(ctx, barX, barY, barW, barH, smoothedProgressDisplay, textAlpha);
        }
    }

    /**
     * 绘制 BPS 柱状波形条：10 个柱，底部对齐，高度按采样强度。
     *  - 每个柱 1px 圆角
     *  - 颜色从左（旧）青绿 渐变到 右（新）橙
     *  - 基底深色凹槽
     */
    private void drawBpsWaveform(GuiGraphicsExtractor ctx, float x, float y, float w, float h,
                                 float[] samples, int textAlpha) {
        int n = samples.length;
        if (n == 0 || h <= 0f || w <= 0f) return;

        int grooveAlpha = Math.max(30, textAlpha / 3);
        int groove = applyAlpha(0x000000, grooveAlpha);
        // 基底凹槽（整条）
        fillRounded(ctx, x, y, w, h, Math.min(h / 2f, 2f), groove);

        float gap = 1f;
        float usableW = w - gap * (n - 1);
        float barW = Math.max(1f, usableW / n);
        // 柱水平 inset（小于凹槽，所以周围留一圈深色边）
        float insetX = 1f;
        float clipY = y;
        for (int i = 0; i < n; i++) {
            float s = samples[i] < 0f ? 0f : (samples[i] > 1f ? 1f : samples[i]);
            // 最小高 1px，避免完全看不见（但 s=0 时不画）
            if (s <= 0.01f) continue;
            float bh = Math.max(1f, h * s);
            float bx = x + i * (barW + gap) + insetX * 0.5f;
            // 柱底对齐凹槽底部
            float by = clipY + (h - bh);

            // 颜色：左→右 从 青绿 (50,220,220) 渐变到 橙黄 (255,160,50)
            float t = (float) i / (float) Math.max(1, n - 1);
            int r = (int) (50 + t * 205);
            int g = (int) (220 - t * 60);
            int b = (int) (220 - t * 170);
            int barColor = applyAlpha((r << 16) | (g << 8) | b, textAlpha);

            float bw = Math.max(1f, barW - insetX);
            fillRounded(ctx, bx, by, bw, bh, Math.min(bh / 2f, 1f), barColor);

            // 顶部 1px 高光（只在高柱上明显显示）
            if (bh >= 3f) {
                int glossAlpha = Math.max(10, textAlpha / 4);
                int gl = applyAlpha(0xFFFFFF, glossAlpha);
                int gx1 = Math.round(bx) + 1;
                int gx2 = Math.round(bx + bw) - 1;
                if (gx2 > gx1) ctx.fill(gx1, Math.round(by), gx2, Math.round(by) + 1, gl);
            }
        }
    }

    /**
     * 绘制带毛玻璃感的进度条：
     *  - 底色：深色半透明凹槽
     *  - 前景：按进度的渐变色填充（绿→黄→红，随剩余量变化）
     *  - 1px 顶部高光
     */
    private void drawProgressBar(GuiGraphicsExtractor ctx, float x, float y, float w, float h,
                                 float progress, int textAlpha) {
        float p = Mth.clamp(progress, 0f, 1f);
        int grooveAlpha = Math.max(40, textAlpha / 2);
        int groove = applyAlpha(0x000000, grooveAlpha);

        // 底色凹槽（带圆角感的 4px 条）
        fillRounded(ctx, x, y, w, h, Math.min(h / 2f, 2f), groove);

        // 前景填充按进度宽度
        float fillW = Math.max(0f, w * p);
        if (fillW <= 0.5f) return;

        // 按剩余量颜色分级：
        //   - progress > 0.5 : 青绿色（消耗较少）
        //   - progress 0.2~0.5 : 黄色（消耗过半）
        //   - progress < 0.2 : 红色（即将耗尽）
        int rgb;
        if (p > 0.5f) {
            float t = (p - 0.5f) / 0.5f; // 0..1 where 1 = full stack
            int r = (int) (30 - t * 20);
            int g = (int) (200 + t * 40);
            int b = (int) (220 + t * 20);
            rgb = (r << 16) | (g << 8) | b;
        } else if (p > 0.2f) {
            float t = (p - 0.2f) / 0.3f; // 0..1 approaching 0.5
            int r = (int) (220 - t * 30);
            int g = (int) (180 + t * 20);
            int b = (int) (60 - t * 40);
            rgb = (r << 16) | (g << 8) | b;
        } else {
            float t = p / 0.2f;
            int r = 230;
            int g = (int) (50 + t * 30);
            int b = (int) (40 + t * 10);
            rgb = (r << 16) | (g << 8) | b;
        }
        int fillColor = applyAlpha(rgb, textAlpha);
        fillRounded(ctx, x, y, fillW, h, Math.min(h / 2f, 2f), fillColor);

        // 1px 高光在填充条顶部（比填充色稍亮）
        int glossAlpha = Math.max(20, textAlpha / 3);
        int gloss = applyAlpha(0xFFFFFF, glossAlpha);
        int gx1 = Math.round(x) + 1;
        int gx2 = Math.round(x + fillW) - 1;
        int gy = Math.round(y);
        if (gx2 > gx1) ctx.fill(gx1, gy, gx2, gy + 1, gloss);

        // --- 动画 #11：扫光高亮段（每 2.5s 沿 gloss 线横扫一次，体现进度"在动"） ---
        if (fillW > 8f && gx2 > gx1) {
            float period = 2.5f;
            float u = (timeSeconds % period) / period; // 0..1 周期
            int spotCenter = Math.round(x + u * fillW);
            int spotHalf = Math.max(2, (int) (fillW * 0.12f));
            int sx1 = Math.max(gx1, spotCenter - spotHalf);
            int sx2 = Math.min(gx2, spotCenter + spotHalf);
            if (sx2 > sx1) {
                int spotAlpha = Math.max(40, textAlpha / 2);
                ctx.fill(sx1, gy, sx2, gy + 1, applyAlpha(0xFFFFFF, spotAlpha));
            }
        }
    }

    /**
     * 构建音乐行（歌曲名），用于与待机信息同屏显示。
     * <ul>
     *   <li>网易云音乐：title 即歌曲名</li>
     *   <li>唱片机：subtitle 是曲目名（title 是通用的"Now Playing"）</li>
     * </ul>
     */
    private Component buildMusicLine() {
        IslandEvent ev = state.getMusicEvent();
        if (ev == null) return null;
        MutableComponent line = Component.empty();
        line.append(Component.literal("\uD83C\uDFB5 ").withStyle(ChatFormatting.LIGHT_PURPLE));
        Component name = (ev.type == IslandEvent.Type.NETEASE_MUSIC) ? ev.title : ev.subtitle;
        if (name == null) name = ev.title;
        if (name != null) line.append(name.copy().withStyle(ChatFormatting.AQUA));
        return line;
    }

    private static Component truncateToWidth(Minecraft client, Component c, int maxW) {
        if (c == null) return null;
        if (maxW <= 4) return null;
        int w = client.font.width(c);
        if (w <= maxW) return c;
        String plain = c.getString();
        // Binary-ish shrink
        int keep = Math.max(1, plain.length() * maxW / Math.max(1, w));
        // Guard against Unicode codepoint boundary / length mis-match; loop down.
        while (keep > 0) {
            String s = plain.substring(0, keep) + "\u2026";
            if (client.font.width(s) <= maxW) return Component.literal(s);
            keep--;
        }
        return Component.literal("\u2026");
    }

    private static void text(GuiGraphicsExtractor ctx, Minecraft client, Component c,
                             int x, int y, int alpha) {
        // 0 表示白色; applyAlpha 会把 alpha 通道放在高 8 位
        int color = applyAlpha(0xFFFFFF, alpha);
        ctx.text(client.font, c, x, y, color, true);
    }

    private static int applyAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * HSV → RGB（0xRRGGBB）。
     *
     * @param h 色相 0..360（自动取模）
     * @param s 饱和度 0..1
     * @param v 明度 0..1
     */
    private static int hsvToRGB(float h, float s, float v) {
        h = ((h % 360f) + 360f) % 360f;
        float c = v * s;
        float hp = h / 60f;
        float x = c * (1f - Math.abs(hp % 2f - 1f));
        float r, g, b;
        if (hp < 1)      { r = c; g = x; b = 0; }
        else if (hp < 2) { r = x; g = c; b = 0; }
        else if (hp < 3) { r = 0; g = c; b = x; }
        else if (hp < 4) { r = 0; g = x; b = c; }
        else if (hp < 5) { r = x; g = 0; b = c; }
        else             { r = c; g = 0; b = x; }
        float m = v - c;
        return (Math.round((r + m) * 255) << 16)
             | (Math.round((g + m) * 255) << 8)
             | Math.round((b + m) * 255);
    }

    /**
     * 流动彩虹色：phase（0..1，常为空间位置）映射到色相环，
     * time 驱动整体色相偏移（每 5s 走完一圈），形成横向流动的彩虹。
     */
    private static int rainbowRGB(float phase, float time) {
        float hue = (phase * 360f + time * 72f) % 360f;
        return hsvToRGB(hue, 0.85f, 1f);
    }

    // ---- Urgent detection ----

    private boolean isUrgent() {
        IslandEvent p = state.getPrimary();
        IslandEvent s = state.getSecondary();
        return (p != null && p.priority == IslandEvent.Priority.URGENT)
                || (s != null && s.priority == IslandEvent.Priority.URGENT);
    }

    // ---- Expanded content (same as before; kept visually consistent) ----

    private void drawContent(GuiGraphicsExtractor ctx, Minecraft client, IslandEvent ev,
                             float ox, float w, float h, boolean split) {
        if (ev == null) return;
        float a = Mth.clamp(state.contentAlpha.get(), 0f, 1f);
        int textAlpha = Math.round(a * 255);
        if (textAlpha <= 2 && (ev.icon == null || ev.icon.isEmpty())) return;

        ItemStack icon = ev.icon;
        // 动画 #9：图标入场弹跳（iconEnterTimer 由 render() 在 contentAlpha 上升沿重置）
        float iconBounceY = 0f;
        if (iconEnterTimer > 0f) {
            float t = 1f - (iconEnterTimer / 0.4f); // 0..1 进度
            iconBounceY = (float) (Math.sin(t * Math.PI * 2.2) * 3.0 * (1f - t));
        }
        if (icon != null && !icon.isEmpty() && a > 0.25f) {
            ctx.item(icon, Math.round(ox + 9), Math.round((h - 16) / 2f + iconBounceY));
        }

        if (textAlpha <= 2) return;
        // 动画 #10：文字入场从右侧轻微滑入（仅 contentAlpha 目标为显示时）
        float slideIn = 0f;
        if (state.contentAlpha.getTarget() > 0.5f) {
            slideIn = (1f - a) * 4f;
        }
        int textX = Math.round(ox + 30 + slideIn);

        if (split) {
            if (ev.title != null) {
                drawText(ctx, client, ev.title, textX, Math.round(h / 2f - 4), textAlpha, 0xFFFFFF);
            }
        } else {
            if (ev.title != null) {
                drawText(ctx, client, ev.title, textX, Math.round(h / 2f - 10), textAlpha, 0xFFFFFF);
            }
            if (ev.subtitle != null) {
                drawText(ctx, client, ev.subtitle, textX, Math.round(h / 2f + 1), textAlpha, 0xB8D4FF);
            }
        }

        // --- 动画 #4：LiquidBounce 切换通知药丸做扫光高光（每 1.3s 一次） ---
        if (ev.type == IslandEvent.Type.LIQUIDBOUNCE) {
            float period = 1.3f;
            float u = ((timeSeconds % period) / period); // 0..1 周期
            // 高光条中心位置（从 -0.15 走到 1.15 条药丸整个宽度）
            float sweepCenter = -0.15f + u * 1.30f;
            float sweepW = w * 0.22f;          // 高光条宽度
            float sweepH = h * 0.85f;
            float sweepY = (h - sweepH) / 2f;
            float sweepX = ox + sweepCenter * w - sweepW / 2f;

            // 用 5 条窄条叠加模拟渐变高光条（中心最亮，两侧渐暗）
            float subW = sweepW / 5f;
            for (int i = 0; i < 5; i++) {
                float dist = Math.abs((i + 0.5f) / 5f - 0.5f) * 2f; // 0..1，中心=0
                float bright = 1f - dist; // 1 中心 -> 0 两侧
                int stripeAlpha = (int) (textAlpha * 0.22f * bright);
                if (stripeAlpha < 4) continue;
                int color = applyAlpha(0xFFFFFF, stripeAlpha);
                float sx = sweepX + i * subW;
                // 高光略微倾斜（用 +i*0.3px 的 y 偏移营造斜扫感）
                float sy = sweepY + i * 0.30f;
                int ix1 = Math.round(sx);
                int iy1 = Math.round(sy);
                int ix2 = Math.round(sx + subW + 0.5f);
                int iy2 = Math.round(sy + sweepH - i * 0.60f);
                if (ix2 > ix1 && iy2 > iy1) ctx.fill(ix1, iy1, ix2, iy2, color);
            }
        }
    }

    private void drawText(GuiGraphicsExtractor ctx, Minecraft client, Component text,
                          int x, int y, int alpha, int rgb) {
        int color = applyAlpha(rgb, alpha);
        ctx.text(client.font, text, x, y, color, true);
    }

    // ---- Fancy pill background (gradient + outer glow) ----

    /**
     * OPAI 风格药丸（流动彩虹版）：
     *  - 跑道形（两端完美半圆，radius=h/2）
     *  - 三层外圈霓虹发光环：色相环上错位 1/3 的三色彩虹光晕，随时间流动
     *  - 暗彩虹底（低明度保证文字可读）+ 横向流动彩虹条纹
     *  - 1px 彩虹内描边（色相随时间流动）
     *  - 顶部流动彩虹镜面高光条
     */
    private void drawFancyPill(GuiGraphicsExtractor ctx, float x, float y,
                               float w, float h, float r, float baseOpacity) {
        if (baseOpacity <= 0.005f) return;

        // OPAI强制：两端半圆（跑道形），半径=h/2；同时不超过w/2避免异常
        float capR = Math.min(h / 2f, w / 2f);
        float rr = Math.max(1f, capR);

        // --- 1. 三层彩虹霓虹发光环（向外扩散，色相沿环错位 + 时间流动） ---
        boolean urgentGlow = isUrgent();
        float glowSpeed = urgentGlow ? 4.5f : 1.3f;
        float glowBoost  = urgentGlow ? 1.6f : 1f;
        float pulse1 = 0.82f + 0.18f * Mth.sin(timeSeconds * glowSpeed);
        float pulse2 = 0.82f + 0.18f * Mth.sin(timeSeconds * glowSpeed + 1.7f);
        float pulse3 = 0.82f + 0.18f * Mth.sin(timeSeconds * glowSpeed + 3.4f);
        // 外圈、中圈、内圈分别取彩虹色相环上错开 1/3 的颜色，形成彩虹光环
        fillRounded(ctx, x - 3f, y - 3f, w + 6f, h + 6f, rr + 3f,
                applyAlpha(rainbowRGB(0.00f, timeSeconds),
                        Math.round(baseOpacity * 0.10f * pulse1 * glowBoost * 255)));
        fillRounded(ctx, x - 2f, y - 2f, w + 4f, h + 4f, rr + 2f,
                applyAlpha(rainbowRGB(0.33f, timeSeconds),
                        Math.round(baseOpacity * 0.16f * pulse2 * glowBoost * 255)));
        fillRounded(ctx, x - 1f, y - 1f, w + 2f, h + 2f, rr + 1f,
                applyAlpha(rainbowRGB(0.66f, timeSeconds),
                        Math.round(baseOpacity * 0.12f * pulse3 * glowBoost * 255)));

        // --- 2. 主体：暗彩虹底（低明度，保证文字可读） + 横向流动彩虹条纹 ---
        // 底色随时间缓慢循环色相，明度仅 0.08 → 近黑但带彩虹色调
        float baseHue = (timeSeconds * 72f) % 360f;
        int bodyTop = applyAlpha(hsvToRGB(baseHue, 0.6f, 0.08f),
                Math.round(baseOpacity * 255));
        int bodyBot = applyAlpha(hsvToRGB(baseHue + 40f, 0.6f, 0.13f),
                Math.round(Mth.clamp(baseOpacity + 0.05f, 0f, 1f) * 255));
        fillRounded(ctx, x, y, w, h, rr, bodyTop);
        // 底部一段覆盖提亮
        float botY = y + h * 0.65f;
        float botH = h * 0.35f;
        if (botH > 0.5f) {
            fillRounded(ctx, x, botY, w, botH, rr, bodyBot);
        }

        // --- 2b. 横向流动彩虹条纹（仅在两端圆角之间的直边区域绘制，天然被药丸形状裁切） ---
        // 每条 2px 宽，颜色 = rainbowRGB(stripX / w, time)，低 alpha 叠加在暗底之上，文字仍清晰
        if (w > 24f && h > 6f) {
            int stripY1 = Math.round(y + 1);
            int stripY2 = Math.round(y + h - 1);
            int stripAlpha = Math.round(baseOpacity * 0.22f * 255);
            int startX = Math.round(x + rr);
            int endX = Math.round(x + w - rr);
            for (int sx = startX; sx + 2 <= endX; sx += 2) {
                float phase = (sx - x) / w;
                int srgb = rainbowRGB(phase, timeSeconds);
                ctx.fill(sx, stripY1, sx + 2, stripY2, applyAlpha(srgb, stripAlpha));
            }
        }

        // --- 3. 1px 彩虹内描边（色相沿宽度流动） ---
        if (baseOpacity > 0.12f) {
            // 描边取药丸中点色相，并随时间流动 → 整圈轮廓统一为当前彩虹色
            int edgeRGB = rainbowRGB(0.5f, timeSeconds);
            drawRoundedOutline(ctx, x + 0.5f, y + 0.5f, w - 1f, h - 1f,
                    Math.max(0.5f, rr - 0.5f),
                    applyAlpha(edgeRGB, Math.round(baseOpacity * 0.55f * 255)));
        }

        // --- 4. 顶部镜面高光：改为流动彩虹细条，强化"彩虹流过顶端"观感 ---
        if (baseOpacity > 0.12f && h > 10f) {
            float insetX = Math.max(rr * 0.55f, 3f);
            int gx1 = Math.round(x + insetX);
            int gx2 = Math.round(x + w - insetX);
            int gy = Math.round(y + 2f);
            if (gx2 > gx1) {
                // 同样以 2px 步进画彩虹细条，色相随位置 + 时间流动
                for (int sx = gx1; sx + 2 <= gx2; sx += 2) {
                    float phase = (sx - x) / w;
                    int srgb = rainbowRGB(phase, timeSeconds);
                    ctx.fill(sx, gy, sx + 2, gy + 1,
                            applyAlpha(srgb, Math.round(baseOpacity * 0.45f * 255)));
                }
            }
        }
    }

    // ---- Rounded-rectangle rasterisation ----

    private static float clampRadius(float r, float w, float h) {
        return Math.max(0, Math.min(r, Math.min(w, h) / 2f));
    }

    /**
     * 抗锯齿圆角矩形填充。
     *
     * <p>中间直边区域直接整块填充；四个圆角使用 4×4 子像素采样计算覆盖率，
     * 按覆盖率调制 alpha，得到平滑的圆弧边缘，告别整数扫描线的锯齿。
     */
    private void fillRounded(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float r, int argb) {
        int ix = Math.round(x), iy = Math.round(y);
        int iw = Math.round(w), ih = Math.round(h);
        int ir = (int) clampRadius(r, w, h);
        int x2 = ix + iw, y2 = iy + ih;
        if (ir <= 0 || iw <= 0 || ih <= 0) {
            if (iw > 0 && ih > 0) ctx.fill(ix, iy, x2, y2, argb);
            return;
        }
        int alpha = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;

        // 中间整块直边带（无圆角，满覆盖）
        ctx.fill(ix, iy + ir, x2, y2 - ir, argb);
        // 顶/底直边条（两角之间的平直部分）
        if (x2 - ir > ix + ir) {
            ctx.fill(ix + ir, iy, x2 - ir, iy + ir, argb);
            ctx.fill(ix + ir, y2 - ir, x2 - ir, y2, argb);
        }

        // 四个圆角：4×4 超采样计算覆盖率
        fillCornerAA(ctx, ix, iy, ir, rgb, alpha, 1, 1);
        fillCornerAA(ctx, x2, iy, ir, rgb, alpha, -1, 1);
        fillCornerAA(ctx, ix, y2, ir, rgb, alpha, 1, -1);
        fillCornerAA(ctx, x2, y2, ir, rgb, alpha, -1, -1);
    }

    /**
     * 对单个圆角做覆盖率抗锯齿填充。
     *
     * @param cornerX, cornerY 矩形角点（如左上角 = ix, iy）
     * @param dirX, dirY       从角点指向矩形内部的方向（±1）
     */
    private void fillCornerAA(GuiGraphicsExtractor ctx, int cornerX, int cornerY, int ir,
                              int rgb, int alpha, int dirX, int dirY) {
        if (ir <= 0) return;
        int cx = cornerX + dirX * ir; // 圆心 X
        int cy = cornerY + dirY * ir; // 圆心 Y
        final int S = 4;              // 4×4 超采样
        final float inv = 1f / (S * S);
        float r2 = (float) (ir * ir);

        for (int dy = 0; dy < ir; dy++) {
            for (int dx = 0; dx < ir; dx++) {
                int px = cornerX + dirX * dx;
                int py = cornerY + dirY * dy;
                int inside = 0;
                for (int sy = 0; sy < S; sy++) {
                    float fy = py + (sy + 0.5f) / S;
                    float ddy = fy - cy;
                    for (int sx = 0; sx < S; sx++) {
                        float fx = px + (sx + 0.5f) / S;
                        float ddx = fx - cx;
                        if (ddx * ddx + ddy * ddy <= r2) inside++;
                    }
                }
                float cov = inside * inv;
                if (cov > 0.001f) {
                    int a = Math.round(alpha * cov);
                    if (a > 0) ctx.fill(px, py, px + 1, py + 1, (a << 24) | rgb);
                }
            }
        }
    }

    /**
     * 抗锯齿 1px 圆角描边。
     *
     * <p>直边段直接画 1px 线；圆角段通过 4×4 超采样计算"外环覆盖 - 内环覆盖"
     * 得到环形（stroke）覆盖率，调制 alpha，描边边缘同样平滑。
     */
    private void drawRoundedOutline(GuiGraphicsExtractor ctx, float x, float y,
                                    float w, float h, float r, int argb) {
        int ix = Math.round(x), iy = Math.round(y);
        int iw = Math.round(w), ih = Math.round(h);
        int ir = (int) clampRadius(r, w, h);
        int x2 = ix + iw - 1;
        int y2 = iy + ih - 1;
        if (iw <= 0 || ih <= 0) return;

        int alpha = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;

        if (ir <= 0 || (y2 - ir) <= (iy + ir)) {
            // 无圆角或太小：直接矩形描边
            ctx.fill(ix, iy, x2 + 1, iy + 1, argb);
            ctx.fill(ix, y2, x2 + 1, y2 + 1, argb);
            ctx.fill(ix, iy, ix + 1, y2 + 1, argb);
            ctx.fill(x2, iy, x2 + 1, y2 + 1, argb);
            return;
        }

        // 四条直边（满覆盖 1px）
        ctx.fill(ix + ir, iy, x2 - ir + 1, iy + 1, argb);
        ctx.fill(ix + ir, y2, x2 - ir + 1, y2 + 1, argb);
        ctx.fill(ix, iy + ir, ix + 1, y2 - ir + 1, argb);
        ctx.fill(x2, iy + ir, x2 + 1, y2 - ir + 1, argb);

        // 四个圆角：环形覆盖率（外径 ir，内径 ir-1）
        strokeCornerAA(ctx, ix, iy, ir, rgb, alpha, 1, 1);
        strokeCornerAA(ctx, x2, iy, ir, rgb, alpha, -1, 1);
        strokeCornerAA(ctx, ix, y2, ir, rgb, alpha, 1, -1);
        strokeCornerAA(ctx, x2, y2, ir, rgb, alpha, -1, -1);
    }

    /** 单个圆角的抗锯齿描边（环形覆盖率）。 */
    private void strokeCornerAA(GuiGraphicsExtractor ctx, int cornerX, int cornerY, int ir,
                                int rgb, int alpha, int dirX, int dirY) {
        if (ir <= 0) return;
        int cx = cornerX + dirX * ir;
        int cy = cornerY + dirY * ir;
        final int S = 4;
        final float inv = 1f / (S * S);
        float rOut2 = (float) (ir * ir);
        float rIn2 = (float) ((ir - 1) * (ir - 1));

        for (int dy = 0; dy < ir; dy++) {
            for (int dx = 0; dx < ir; dx++) {
                int px = cornerX + dirX * dx;
                int py = cornerY + dirY * dy;
                int inside = 0;
                for (int sy = 0; sy < S; sy++) {
                    float fy = py + (sy + 0.5f) / S;
                    float ddy = fy - cy;
                    for (int sx = 0; sx < S; sx++) {
                        float fx = px + (sx + 0.5f) / S;
                        float ddx = fx - cx;
                        float d2 = ddx * ddx + ddy * ddy;
                        // 环形：在内径与外径之间
                        if (d2 <= rOut2 && d2 >= rIn2) inside++;
                    }
                }
                float cov = inside * inv;
                if (cov > 0.001f) {
                    int a = Math.round(alpha * cov);
                    if (a > 0) ctx.fill(px, py, px + 1, py + 1, (a << 24) | rgb);
                }
            }
        }
    }
}
