package com.example.dynamicisland.client;

import com.example.dynamicisland.client.config.IslandConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
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
        state.statusActive = StatusInfo.hasStatus(client);
        if (state.statusActive) {
            updateStatusTarget(client);
        }

        state.tick(dt);

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

        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(screenX + shakeX, screenY + breathY);
        matrices.scale(scale, scale);

        IslandState.Mode mode = state.computeMode();
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

        Component info    = StatusInfo.getInfoLine(client);
        Component lyric   = hasLyric   ? StatusInfo.getLyricLine(client)   : null;
        Component scaf    = hasScaffold? StatusInfo.getScaffoldLine(client): null;
        Component modules = hasModules ? StatusInfo.getModulesLine(client) : null;
        Component ka      = hasKa      ? StatusInfo.getKaLine(client)      : null;
        Component speed   = hasSpeed   ? StatusInfo.getSpeedLine(client)   : null;
        Component armor   = hasArmor   ? StatusInfo.getArmorLine(client)   : null;

        int wInfo    = info    == null ? 0 : client.font.width(info);
        int wLyric   = lyric   == null ? 0 : client.font.width(lyric);
        int wScaf    = scaf    == null ? 0 : client.font.width(scaf);
        int wModules = modules == null ? 0 : client.font.width(modules);
        int wKa      = ka      == null ? 0 : client.font.width(ka);
        int wSpeed   = speed   == null ? 0 : client.font.width(speed);
        int wArmor   = armor   == null ? 0 : client.font.width(armor);
        float maxTextW = Math.max(Math.max(Math.max(Math.max(
                                Math.max(Math.max(wInfo, wLyric), wScaf), wModules), wKa), wSpeed), wArmor);

        float pad = 18f;
        state.statusTargetW = Mth.clamp(maxTextW + pad,
                IslandState.STATUS_MIN_W, IslandState.STATUS_MAX_W);

        // 高度：按文本行数 + 波形 + 进度条 累加
        float textRowH = 8f, lineGap = 2f;
        int rows = 0;
        if (info != null) rows++;
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

        int pad = 8;
        int maxW = Math.round(w - pad * 2f);

        // 构建行序列：保持与 updateStatusTarget 中顺序一致
        java.util.List<Component> rows = new java.util.ArrayList<>();
        if (info != null)    rows.add(info);
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
        if (icon != null && !icon.isEmpty() && a > 0.25f) {
            ctx.item(icon, Math.round(ox + 9), Math.round((h - 16) / 2f));
        }

        if (textAlpha <= 2) return;
        int textX = Math.round(ox + 30);

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
     * OPAI 风格药丸：
     *  - 跑道形（两端完美半圆，radius=h/2）
     *  - 三层外圈霓虹发光环：紫 → 青 → 白
     *  - 纯深黑实底 + 底部轻微提亮
     *  - 1px 白色内描边（opai标志性）
     *  - 顶部镜面高光条
     */
    private void drawFancyPill(GuiGraphicsExtractor ctx, float x, float y,
                               float w, float h, float r, float baseOpacity) {
        if (baseOpacity <= 0.005f) return;

        // OPAI强制：两端半圆（跑道形），半径=h/2；同时不超过w/2避免异常
        float capR = Math.min(h / 2f, w / 2f);
        float rr = Math.max(1f, capR);

        // --- 1. 三层霓虹发光环（紫→青→白，向外出圈，opai特色光晕） ---
        // 外圈紫色发光
        fillRounded(ctx, x - 3f, y - 3f, w + 6f, h + 6f, rr + 3f,
                applyAlpha(0xA855FF, Math.round(baseOpacity * 0.10f * 255)));
        // 中圈青色发光
        fillRounded(ctx, x - 2f, y - 2f, w + 4f, h + 4f, rr + 2f,
                applyAlpha(0x32DCDC, Math.round(baseOpacity * 0.16f * 255)));
        // 内圈白色弱发光（紧贴药丸）
        fillRounded(ctx, x - 1f, y - 1f, w + 2f, h + 2f, rr + 1f,
                applyAlpha(0xE8F0FF, Math.round(baseOpacity * 0.12f * 255)));

        // --- 2. 主体：纯深黑（opai偏好纯黑底）+ 底部5%提亮渐变 ---
        int bodyTop = applyAlpha(0x05070B, Math.round(baseOpacity * 255));
        int bodyBot = applyAlpha(0x0B0F18,
                Math.round(Mth.clamp(baseOpacity + 0.05f, 0f, 1f) * 255));
        fillRounded(ctx, x, y, w, h, rr, bodyTop);
        // 底部一段覆盖提亮
        float botY = y + h * 0.65f;
        float botH = h * 0.35f;
        if (botH > 0.5f) {
            fillRounded(ctx, x, botY, w, botH, rr, bodyBot);
        }

        // --- 3. 1px 白色内描边（opai药丸标志性轮廓） ---
        if (baseOpacity > 0.12f) {
            drawRoundedOutline(ctx, x + 0.5f, y + 0.5f, w - 1f, h - 1f,
                    Math.max(0.5f, rr - 0.5f),
                    applyAlpha(0xFFFFFF, Math.round(baseOpacity * 0.42f * 255)));
        }

        // --- 4. 顶部镜面高光（亚克力感） ---
        if (baseOpacity > 0.12f && h > 10f) {
            int gloss = applyAlpha(0xFFFFFF, Math.round(baseOpacity * 0.28f * 255));
            float insetX = Math.max(rr * 0.55f, 3f);
            int gx1 = Math.round(x + insetX);
            int gx2 = Math.round(x + w - insetX);
            int gy = Math.round(y + 2f);
            if (gx2 > gx1) ctx.fill(gx1, gy, gx2, gy + 1, gloss);
        }
    }

    // ---- Rounded-rectangle rasterisation ----

    private static float clampRadius(float r, float w, float h) {
        return Math.max(0, Math.min(r, Math.min(w, h) / 2f));
    }

    private void fillRounded(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float r, int argb) {
        int ix = Math.round(x), iy = Math.round(y);
        int iw = Math.round(w), ih = Math.round(h);
        int ir = (int) clampRadius(r, w, h);
        int x2 = ix + iw, y2 = iy + ih;
        if (ir <= 0) {
            ctx.fill(ix, iy, x2, y2, argb);
            return;
        }
        // Full-width middle band.
        ctx.fill(ix, iy + ir, x2, y2 - ir, argb);
        for (int i = 0; i < ir; i++) {
            int dTop = ir - i;
            int dBot = ir - 1 - i;
            int insetTop = ir - (int) Math.round(Math.sqrt(Math.max(0.0,
                    (double) ir * ir - (double) dTop * dTop)));
            int insetBot = ir - (int) Math.round(Math.sqrt(Math.max(0.0,
                    (double) ir * ir - (double) dBot * dBot)));
            int lt = Math.max(0, insetTop);
            int lb = Math.max(0, insetBot);
            if (lt < iw) ctx.fill(ix + lt, iy + i, x2 - lt, iy + i + 1, argb);
            if (lb < iw) ctx.fill(ix + lb, y2 - 1 - i, x2 - lb, y2 - i, argb);
        }
    }

    /**
     * 1px 宽圆角矩形描边（内描边，按经验1225089建议向内缩halfBorder以避免边缘裁切）。
     * 画4条边：上下直线 + 左右直线 + 4个圆角（每角扫描ir行算inset）。
     */
    private void drawRoundedOutline(GuiGraphicsExtractor ctx, float x, float y,
                                    float w, float h, float r, int argb) {
        int ix = Math.round(x), iy = Math.round(y);
        int iw = Math.round(w), ih = Math.round(h);
        int ir = (int) clampRadius(r, w, h);
        int x2 = ix + iw - 1;
        int y2 = iy + ih - 1;
        if (iw <= 0 || ih <= 0) return;

        // 顶边（中间直线段）
        if (ir > 0 && (y2 - ir) > (iy + ir)) {
            int topY = iy + ir;
            int botY = y2 - ir;
            // 顶横直线
            ctx.fill(ix + ir, iy, x2 - ir + 1, iy + 1, argb);
            // 底横直线
            ctx.fill(ix + ir, y2, x2 - ir + 1, y2 + 1, argb);
            // 左竖直线
            ctx.fill(ix, iy + ir, ix + 1, botY + 1, argb);
            // 右竖直线
            ctx.fill(x2, iy + ir, x2 + 1, botY + 1, argb);
        } else {
            // 太小或无圆角：直接整体矩形描边4边
            ctx.fill(ix, iy, x2 + 1, iy + 1, argb);
            ctx.fill(ix, y2, x2 + 1, y2 + 1, argb);
            ctx.fill(ix, iy, ix + 1, y2 + 1, argb);
            ctx.fill(x2, iy, x2 + 1, y2 + 1, argb);
            return;
        }

        // 4 个圆角（按角度扫描，0..ir-1 每条半径画点）
        for (int i = 0; i < ir; i++) {
            // 距角中心的Y距离（从角的顶/底线向中心算）：i = 0 是最上/最下
            int d = ir - i;
            // 该Y行下，距角中心的水平 insets (整格数)
            int inset = ir - (int) Math.round(Math.sqrt(Math.max(0.0,
                    (double) ir * ir - (double) d * d)));
            int xi = Math.max(0, inset);
            // 左上：角中心=(ix+ir, iy+ir)；上方水平行 = iy + i
            // 该行需要填充：最左边 1 像素（即轮廓位置）
            int lx = ix + ir - d;   // 角圆弧上点的x
            int ly = iy + ir - xi;  // 角圆弧上点的y（y方向距离）
            // 简单画1px：在 x = ix + i 位置，取 y = iy + (ir - xi - 1)
            // 但为保险直接绘制：左上半径区域 顶部扫描i行 从外向内 1px
            int cxL = ix + ir;        // 左角中心x
            int cyT = iy + ir;        // 上角中心y
            int cxB = y2 - ir + iy;   // 底角中心y (等价 y2 - ir)
            int cyL = x2 - ir + ix;   // 右角中心x (等价 x2 - ir)
            // 直接按 (d,xi) 在4角位置画 1px
            // 左上 (cxL - d, cyT - xi) 和 (cxL - xi, cyT - d)
            ctx.fill(cxL - d, cyT - xi, cxL - d + 1, cyT - xi + 1, argb);
            if (d != xi) ctx.fill(cxL - xi, cyT - d, cxL - xi + 1, cyT - d + 1, argb);
            // 右上：cxR - cxL = x2 - 2*ir，中心x = x2 - ir
            int cxR = x2 - ir;
            ctx.fill(cxR + d, cyT - xi, cxR + d + 1, cyT - xi + 1, argb);
            if (d != xi) ctx.fill(cxR + xi, cyT - d, cxR + xi + 1, cyT - d + 1, argb);
            // 左下：中心y = y2 - ir
            int cyB = y2 - ir;
            ctx.fill(cxL - d, cyB + xi, cxL - d + 1, cyB + xi + 1, argb);
            if (d != xi) ctx.fill(cxL - xi, cyB + d, cxL - xi + 1, cyB + d + 1, argb);
            // 右下
            ctx.fill(cxR + d, cyB + xi, cxR + d + 1, cyB + xi + 1, argb);
            if (d != xi) ctx.fill(cxR + xi, cyB + d, cxR + xi + 1, cyB + d + 1, argb);
        }
    }
}
