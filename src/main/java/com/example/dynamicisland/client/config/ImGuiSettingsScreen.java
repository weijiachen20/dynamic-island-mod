package com.example.dynamicisland.client.config;

import com.mojang.blaze3d.platform.NativeImage;
import imgui.ImDrawData;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec4;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiConfigFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 基于 Dear ImGui（imgui-java）的独立设置界面。
 *
 * <p>Minecraft 26.2 的 GUI 是完全延迟渲染的：{@link Screen} 只有
 * {@code extractRenderState(GuiGraphicsExtractor, ...)}，只负责<em>记录</em>绘制命令，
 * 实际绘制由 MC 渲染器稍后统一执行，期间不允许直接调用 OpenGL。
 *
 * <p>因此本实现<strong>不使用</strong> ImGui 的 OpenGL3 后端（ImGuiImplGl3 在 MC 26.2
 * 上会因延迟渲染架构冲突而崩溃），而是把 ImGui 生成的 draw data 桥接成
 * {@link GuiGraphicsExtractor} 的绘制命令：
 * <ul>
 *   <li>纯色三角形（textureId == 0）→ 扫描线填充（逐行 ctx.fill）</li>
 *   <li>字体字形四边形（textureId != 0）→ ImGui 字体图集上传为 MC 纹理后按 quad 做 blit</li>
 * </ul>
 *
 * <p>界面通过「打开设置」键位（默认 O）从游戏内呼出，仅由客户端渲染。
 */
public class ImGuiSettingsScreen extends Screen {

    /** ImGui 字体图集上传为 MC 纹理后的 Identifier。 */
    private static Identifier fontTexId;
    private static int fontTexW, fontTexH;
    private static volatile boolean initialized;

    private final Screen parent;
    private final IslandConfig cfg = IslandConfig.get();

    public ImGuiSettingsScreen(Screen parent) {
        super(Component.literal("Dynamic Island — Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (!initialized) {
            initImGui();
            initialized = true;
        }
    }

    /** 全局初始化一次 ImGui 上下文，并把字体图集上传为 MC 纹理（不初始化任何 GL 后端）。 */
    private void initImGui() {
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        // 不落盘窗口布局（每次从固定尺寸/位置开始）。
        io.setIniFilename(null);
        // 不强制改变系统鼠标指针样式，避免与 Minecraft 的光标管理冲突。
        io.addConfigFlags(ImGuiConfigFlags.NoMouseCursorChange);
        ImGui.styleColorsDark();

        // 把 ImGui 默认字体图集（RGBA32）上传为一张 MC 纹理，供字形 quad 的 blit 使用。
        ImInt atlasW = new ImInt(0), atlasH = new ImInt(0);
        ByteBuffer px = io.getFonts().getTexDataAsRGBA32(atlasW, atlasH);
        fontTexW = atlasW.get();
        fontTexH = atlasH.get();
        if (px != null && fontTexW > 0 && fontTexH > 0) {
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, fontTexW, fontTexH, false);
            for (int y = 0; y < fontTexH; y++) {
                for (int x = 0; x < fontTexW; x++) {
                    int o = (y * fontTexW + x) * 4;
                    int r = px.get(o) & 0xFF;
                    int g = px.get(o + 1) & 0xFF;
                    int b = px.get(o + 2) & 0xFF;
                    int a = px.get(o + 3) & 0xFF;
                    // NativeImage 的 ABGR 打包：(A<<24)|(B<<16)|(G<<8)|R
                    img.setPixelABGR(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }
            fontTexId = Identifier.fromNamespaceAndPath("dynamicisland", "imgui_font");
            DynamicTexture tex = new DynamicTexture("dynamicisland:imgui_font", fontTexW, fontTexH, false);
            tex.setPixels(img);
            Minecraft.getInstance().getTextureManager().register(fontTexId, tex);
            tex.upload();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // 先让 Minecraft 绘制背景，再把 ImGui 叠在上层。
        super.extractRenderState(context, mouseX, mouseY, delta);
        renderImGui(context, delta);
    }

    /**
     * 每帧驱动 ImGui：更新显示度量 + 鼠标输入 → newFrame → 面板 → render，
     * 最后把 draw data 桥接为 {@link GuiGraphicsExtractor} 命令（不触碰 GL）。
     */
    private void renderImGui(GuiGraphicsExtractor ctx, float delta) {
        if (!initialized) return;

        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().handle();
        ImGuiIO io = ImGui.getIO();

        // --- 显示度量：ImGui 使用"逻辑屏幕像素"（framebuffer / guiScale），与 GuiGraphics 一致 ---
        int[] fbW = new int[]{1}, fbH = new int[]{1};
        GLFW.glfwGetFramebufferSize(window, fbW, fbH);
        float guiScale = Math.max(1f, (float) mc.getWindow().getGuiScale());
        float scrW = fbW[0] / guiScale;
        float scrH = fbH[0] / guiScale;
        io.setDisplaySize(scrW, scrH);
        io.setDisplayFramebufferScale(1f, 1f);
        io.setFontGlobalScale(1f);
        io.setDeltaTime(Math.max(delta, 0.0001f));

        // --- 鼠标输入（物理像素 → 逻辑像素；不覆盖 MC 回调链） ---
        double[] mx = new double[]{0.0}, my = new double[]{0.0};
        GLFW.glfwGetCursorPos(window, mx, my);
        io.setMousePos((float) (mx[0] / guiScale), (float) (my[0] / guiScale));
        io.setMouseDown(0, GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS);
        io.setMouseDown(1, GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS);
        io.setMouseDown(2, GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS);

        // --- 面板布局 ---
        ImGui.newFrame();

        float winW = scrW * 0.72f;
        float winH = scrH * 0.9f;
        ImGui.setNextWindowPos((scrW - winW) / 2f, (scrH - winH) / 2f, ImGuiCond.Once);
        ImGui.setNextWindowSize(winW, winH, ImGuiCond.Once);

        if (ImGui.begin("Dynamic Island Settings")) {
            drawGeneral();
            drawAppearance();
            drawModule();
            drawCategories();
            drawStatusBar();
            ImGui.separator();
            if (ImGui.button("Save & Close")) {
                IslandConfig.save();
                onClose();
            }
            ImGui.sameLine();
            ImGui.text("Escape to close, changes auto-save");
        }
        ImGui.end();

        ImGui.render();
        drawImGuiToExtractor(ctx);
    }

    // ---------------------------------------------------------------
    // ImGui draw data → GuiGraphicsExtractor 桥接（替代 OpenGL3 后端）
    // ---------------------------------------------------------------

    private void drawImGuiToExtractor(GuiGraphicsExtractor ctx) {
        ImDrawData dd = ImGui.getDrawData();
        if (dd == null || !dd.getValid()) return;

        int lists = dd.getCmdListsCount();
        for (int li = 0; li < lists; li++) {
            // 注意：imgui-java 的 getCmdListVtxBufferData/getCmdListIdxBufferData 共用同一个
            // 静态 dataBuffer，后一次调用会覆盖前一次的内容（vtx/idx 会指向同一块内存）。
            // 因此必须在两次调用之间把数据深拷贝到独立缓冲，否则 readVtx 会读到索引数据而越界。
            ByteBuffer vtx = deepCopyBuffer(dd.getCmdListVtxBufferData(li));
            ByteBuffer idx = deepCopyBuffer(dd.getCmdListIdxBufferData(li));
            if (vtx == null || idx == null) continue;

            int cmds = dd.getCmdListCmdBufferSize(li);
            for (int ci = 0; ci < cmds; ci++) {
                ImVec4 clip = dd.getCmdListCmdBufferClipRect(li, ci);
                int elemCount = dd.getCmdListCmdBufferElemCount(li, ci);
                int textureId = dd.getCmdListCmdBufferTextureId(li, ci);
                int vtxOffset = dd.getCmdListCmdBufferVtxOffset(li, ci);
                int idxOffset = dd.getCmdListCmdBufferIdxOffset(li, ci);

                // --- clip rect → MC scissor（已是逻辑像素坐标，钳制到屏幕内） ---
                int scX = (int) clip.x, scY = (int) clip.y;
                int scW = (int) (clip.z - clip.x), scH = (int) (clip.w - clip.y);
                if (scW <= 0 || scH <= 0) continue;
                int scrW2 = ctx.guiWidth(), scrH2 = ctx.guiHeight();
                scX = Math.max(0, scX);
                scY = Math.max(0, scY);
                scW = Math.max(0, Math.min(scW, scrW2 - scX));
                scH = Math.max(0, Math.min(scH, scrH2 - scY));
                if (scW <= 0 || scH <= 0) continue;
                ctx.enableScissor(scX, scY, scW, scH);

                boolean textured = textureId != 0;
                if (textured) {
                    // 字体字形：每 6 个索引 = 1 个轴对齐 quad（2 个三角形），整块 blit
                    for (int t = 0; t + 5 < elemCount; t += 6) {
                        drawFontQuad(ctx, dd, idx, vtx, idxOffset, vtxOffset, t);
                    }
                } else {
                    // 纯色：逐三角形扫描线填充
                    for (int t = 0; t + 2 < elemCount; t += 3) {
                        drawSolidTri(ctx, dd, idx, vtx, idxOffset, vtxOffset, t);
                    }
                }
                ctx.disableScissor();
            }
        }
    }

    private void drawSolidTri(GuiGraphicsExtractor ctx, ImDrawData dd, ByteBuffer idx,
                              ByteBuffer vtx, int idxOffset, int vtxOffset, int t) {
        int i0 = readIdx(idx, idxOffset + t);
        int i1 = readIdx(idx, idxOffset + t + 1);
        int i2 = readIdx(idx, idxOffset + t + 2);
        float[] a = readVtx(vtx, i0 + vtxOffset);
        float[] b = readVtx(vtx, i1 + vtxOffset);
        float[] c = readVtx(vtx, i2 + vtxOffset);
        if (a == null || b == null || c == null) return;
        fillTri(ctx, a, b, c, argbFromImGuiCol((int) a[4]));
    }

    private void drawFontQuad(GuiGraphicsExtractor ctx, ImDrawData dd, ByteBuffer idx,
                              ByteBuffer vtx, int idxOffset, int vtxOffset, int t) {
        // quad 索引：[0,1,2, 0,2,3]，6 个索引 → 4 个唯一顶点
        int[] is = {
                readIdx(idx, idxOffset + t), readIdx(idx, idxOffset + t + 1), readIdx(idx, idxOffset + t + 2),
                readIdx(idx, idxOffset + t + 3), readIdx(idx, idxOffset + t + 4), readIdx(idx, idxOffset + t + 5)
        };
        float[] va = readVtx(vtx, is[0] + vtxOffset);
        float[] vb = readVtx(vtx, is[1] + vtxOffset);
        float[] vc = readVtx(vtx, is[2] + vtxOffset);
        float[] vd = readVtx(vtx, is[5] + vtxOffset);
        if (va == null || vb == null || vc == null || vd == null) return;
        float minX = Math.min(Math.min(va[0], vb[0]), Math.min(vc[0], vd[0]));
        float minY = Math.min(Math.min(va[1], vb[1]), Math.min(vc[1], vd[1]));
        float maxX = Math.max(Math.max(va[0], vb[0]), Math.max(vc[0], vd[0]));
        float maxY = Math.max(Math.max(va[1], vb[1]), Math.max(vc[1], vd[1]));
        float minU = Math.min(Math.min(va[2], vb[2]), Math.min(vc[2], vd[2]));
        float minV = Math.min(Math.min(va[3], vb[3]), Math.min(vc[3], vd[3]));
        float maxU = Math.max(Math.max(va[2], vb[2]), Math.max(vc[2], vd[2]));
        float maxV = Math.max(Math.max(va[3], vb[3]), Math.max(vc[3], vd[3]));
        if (fontTexW <= 0 || fontTexH <= 0 || fontTexId == null) return;
        int x0 = Math.round(minX), y0 = Math.round(minY);
        int x1 = Math.round(maxX), y1 = Math.round(maxY);
        if (x1 <= x0 || y1 <= y0) return;
        ctx.blit(fontTexId, x0, y0, x1 - x0 + 1, y1 - y0 + 1,
                minU / fontTexW, minV / fontTexH, maxU / fontTexW, maxV / fontTexH);
    }

    /** 深拷贝一个 ByteBuffer 的全部内容到独立的 native-order 直接缓冲。 */
    private static ByteBuffer deepCopyBuffer(ByteBuffer src) {
        if (src == null || src.remaining() <= 0) return null;
        ByteBuffer dup = src.duplicate();
        dup.position(0);
        ByteBuffer copy = ByteBuffer.allocateDirect(dup.remaining()).order(ByteOrder.nativeOrder());
        copy.put(dup);
        copy.flip();
        return copy;
    }

    private static int readIdx(ByteBuffer idx, int index) {
        if (index < 0) return -1;
        int bytes = index * (ImDrawData.SIZEOF_IM_DRAW_IDX == 2 ? 2 : 4);
        if (bytes + (ImDrawData.SIZEOF_IM_DRAW_IDX == 2 ? 2 : 4) > idx.capacity()) return -1;
        return ImDrawData.SIZEOF_IM_DRAW_IDX == 2
                ? (idx.getShort(bytes) & 0xFFFF)
                : idx.getInt(bytes);
    }

    /** 读取一个顶点：[x, y, u, v, packedColor]。索引越界时返回 null。 */
    private static float[] readVtx(ByteBuffer vtx, int index) {
        if (index < 0) return null;
        int o = index * ImDrawData.SIZEOF_IM_DRAW_VERT;
        if (o < 0 || o + 20 > vtx.capacity()) return null;
        return new float[]{
                vtx.getFloat(o), vtx.getFloat(o + 4),
                vtx.getFloat(o + 8), vtx.getFloat(o + 12),
                (float) vtx.getInt(o + 16)
        };
    }

    /** ImGui 打包色 0xAABBGGRR → MC ARGB。 */
    private static int argbFromImGuiCol(int col) {
        int r = col & 0xFF;
        int g = (col >> 8) & 0xFF;
        int b = (col >> 16) & 0xFF;
        int a = (col >> 24) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 扫描线填充三角形（y 向下），与 GuiGraphicsExtractor 的屏幕坐标一致。 */
    private static void fillTri(GuiGraphicsExtractor ctx, float[] a, float[] b, float[] c, int argb) {
        float[] lo = a, mid = b, hi = c;
        if (lo[1] > mid[1]) { float[] t = lo; lo = mid; mid = t; }
        if (mid[1] > hi[1]) { float[] t = mid; mid = hi; hi = t; }
        if (lo[1] > mid[1]) { float[] t = lo; lo = mid; mid = t; }
        int y0 = (int) Math.ceil(lo[1]);
        int y1 = (int) Math.floor(hi[1]);
        if (y0 > y1) return;
        for (int y = y0; y <= y1; y++) {
            float fy = y + 0.5f;
            float xL, xR;
            if (fy < mid[1]) {
                xL = edgeX(lo, mid, fy);
                xR = edgeX(lo, hi, fy);
            } else {
                xL = edgeX(mid, hi, fy);
                xR = edgeX(lo, hi, fy);
            }
            if (xL > xR) {
                float tmp = xL;
                xL = xR;
                xR = tmp;
            }
            int xi0 = (int) Math.ceil(xL);
            int xi1 = (int) Math.floor(xR);
            if (xi1 >= xi0) {
                ctx.fill(xi0, y, xi1 + 1, y + 1, argb);
            }
        }
    }

    private static float edgeX(float[] p0, float[] p1, float y) {
        if (p1[1] == p0[1]) return p0[0];
        return p0[0] + (y - p0[1]) * (p1[0] - p0[0]) / (p1[1] - p0[1]);
    }

    // ---------------------------------------------------------------
    // 面板分组
    // ---------------------------------------------------------------

    private void drawGeneral() {
        if (ImGui.collapsingHeader("General")) {
            checkbox("Enabled (overlay master switch)", () -> cfg.enabled, v -> cfg.enabled = v);

            ImInt pos = new ImInt(cfg.position);
            if (ImGui.combo("Position", pos, new String[]{"Top", "Top Left", "Top Right"})) {
                cfg.position = pos.get();
                IslandConfig.save();
            }

            float[] scale = {cfg.scale};
            if (ImGui.sliderFloat("Scale", scale, 0.5f, 2.0f)) {
                cfg.scale = Math.round(scale[0] * 100f) / 100f;
                IslandConfig.save();
            }

            int[] bg = {cfg.bgOpacity};
            if (ImGui.sliderInt("Background opacity", bg, 0, 100)) {
                cfg.bgOpacity = bg[0];
                IslandConfig.save();
            }

            int[] radius = {cfg.cornerRadius};
            if (ImGui.sliderInt("Corner radius", radius, 0, 40)) {
                cfg.cornerRadius = radius[0];
                IslandConfig.save();
            }

            float[] display = {cfg.displayTime};
            if (ImGui.sliderFloat("Event display time (s)", display, 1f, 8f)) {
                cfg.displayTime = Math.round(display[0] * 10f) / 10f;
                IslandConfig.save();
            }

            int[] sensitivity = {cfg.sensitivity};
            if (ImGui.sliderInt("Sensitivity", sensitivity, 1, 4)) {
                cfg.sensitivity = sensitivity[0];
                IslandConfig.save();
            }

            int[] health = {cfg.healthThreshold};
            if (ImGui.sliderInt("Low-health threshold (hearts)", health, 1, 20)) {
                cfg.healthThreshold = health[0];
                IslandConfig.save();
            }

            int[] hunger = {cfg.hungerThreshold};
            if (ImGui.sliderInt("Low-hunger threshold (drumsticks)", hunger, 1, 20)) {
                cfg.hungerThreshold = hunger[0];
                IslandConfig.save();
            }
        }
    }

    private void drawAppearance() {
        if (ImGui.collapsingHeader("Appearance")) {
            checkbox("Rainbow colors", () -> cfg.rainbowEnabled, v -> cfg.rainbowEnabled = v);

            float[] speed = {cfg.rainbowSpeed};
            if (ImGui.sliderFloat("Rainbow flow speed (deg/s)", speed, 0f, 180f)) {
                cfg.rainbowSpeed = Math.round(speed[0] * 10f) / 10f;
                IslandConfig.save();
            }

            if (!cfg.rainbowEnabled) ImGui.text("Rainbow disabled — neutral tint.");
        }
    }

    private void drawModule() {
        if (ImGui.collapsingHeader("Jump Reset")) {
            checkbox("Jump Reset (auto jump on landing after knockback)",
                    () -> cfg.jumpReset, v -> cfg.jumpReset = v);

            float[] th = {cfg.jumpResetThreshold};
            if (ImGui.sliderFloat("Knockback threshold (blocks/tick)", th, 0.05f, 0.5f)) {
                cfg.jumpResetThreshold = Math.round(th[0] * 100f) / 100f;
                IslandConfig.save();
            }

            ImGui.text("Auto-jumps the instant you land when knocked airborne.");
        }
    }

    private void drawCategories() {
        if (ImGui.collapsingHeader("Event Categories")) {
            checkbox("Potion effects", () -> cfg.potions, v -> cfg.potions = v);
            checkbox("Jukebox music", () -> cfg.music, v -> cfg.music = v);
            checkbox("NetEase Cloud Music", () -> cfg.netease, v -> cfg.netease = v);
            checkbox("Weather changes", () -> cfg.weather, v -> cfg.weather = v);
            checkbox("Advancements", () -> cfg.advancement, v -> cfg.advancement = v);
            checkbox("Low-health alerts", () -> cfg.health, v -> cfg.health = v);
            checkbox("Low-hunger alerts", () -> cfg.hunger, v -> cfg.hunger = v);
            checkbox("Day/night transitions", () -> cfg.daynight, v -> cfg.daynight = v);
            checkbox("Damage alerts", () -> cfg.damage, v -> cfg.damage = v);
            checkbox("Level-up alerts", () -> cfg.levelUp, v -> cfg.levelUp = v);
            checkbox("LiquidBounce module toggles", () -> cfg.liquidbounce, v -> cfg.liquidbounce = v);
            checkbox("NetEase lyrics", () -> cfg.neteaseLyric, v -> cfg.neteaseLyric = v);
        }
    }

    private void drawStatusBar() {
        if (ImGui.collapsingHeader("Status Bar (collapsed idle info)")) {
            checkbox("FPS", () -> cfg.statusFps, v -> cfg.statusFps = v);
            checkbox("Server IP", () -> cfg.statusIp, v -> cfg.statusIp = v);
            checkbox("LiquidBounce version", () -> cfg.statusLbVersion, v -> cfg.statusLbVersion = v);
            checkbox("Lyrics", () -> cfg.statusLyric, v -> cfg.statusLyric = v);
            checkbox("Scaffold BPS + progress", () -> cfg.statusScaffold, v -> cfg.statusScaffold = v);
            checkbox("Modules dashboard", () -> cfg.statusModules, v -> cfg.statusModules = v);
            checkbox("KillAura APS + target", () -> cfg.statusKa, v -> cfg.statusKa = v);
            checkbox("Speedometer", () -> cfg.statusSpeed, v -> cfg.statusSpeed = v);
            checkbox("Armor integrity", () -> cfg.statusArmor, v -> cfg.statusArmor = v);
            checkbox("Ping", () -> cfg.statusPing, v -> cfg.statusPing = v);
        }
    }

    // ---------------------------------------------------------------
    // 复选框 helper：把 boolean 字段丢给 ImGui.checkbox，变更即保存
    // ---------------------------------------------------------------

    private void checkbox(String label, java.util.function.BooleanSupplier getter,
                          java.util.function.Consumer<Boolean> setter) {
        ImBoolean v = new ImBoolean(getter.getAsBoolean());
        if (ImGui.checkbox(label, v)) {
            setter.accept(v.get());
            IslandConfig.save();
        }
    }

    @Override
    public void onClose() {
        // Minecraft 26.2: setScreen moved from Minecraft to Gui
        this.minecraft.gui.setScreen(parent);
    }
}