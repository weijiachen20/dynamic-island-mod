package com.example.dynamicisland.client.config;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 基于 Dear ImGui（imgui-java）的独立设置界面。
 *
 * <p>区别于 {@link IslandConfigScreen}（vanilla 循环按钮式），本界面使用 ImGui
 * 的悬浮窗口，提供复选框 / 滑块 / 下拉框，改动立即写回 {@link IslandConfig} 并落盘。
 *
 * <p>此界面通过「打开设置」键位（默认 O）从游戏内呼出，仅由客户端渲染。
 * 为不破坏 Minecraft 自身的输入管线，这里<b>不</b>安装 ImGui 的 GLFW 回调，
 * 而是每帧直接从 GLFW 轮询鼠标状态写入 ImGuiIO（无文本输入框，因此键盘可省略）。
 */
public class ImGuiSettingsScreen extends Screen {

    /** ImGui 的 OpenGL3 后端（负责绘制 draw data）。全进程共享一份。 */
    private static ImGuiImplGl3 gl3;
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

    /** 全局初始化一次 ImGui 上下文与 OpenGL3 后端。 */
    private void initImGui() {
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        // 不落盘窗口布局（每次从固定尺寸/位置开始）。
        io.setIniFilename(null);
        // 不强制改变系统鼠标指针样式，避免与 Minecraft 的光标管理冲突。
        io.addConfigFlags(ImGuiConfigFlags.NoMouseCursorChange);
        ImGui.styleColorsDark();

        gl3 = new ImGuiImplGl3();
        gl3.init("#version 150");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // 先让 Minecraft 绘制背景，再把 ImGui 叠在上层。
        super.extractRenderState(context, mouseX, mouseY, delta);
        renderImGui();
    }

    /** 每帧驱动 ImGui：更新显示度量 + 轮询鼠标输入 → newFrame → 面板 → render。 */
    private void renderImGui() {
        if (!initialized) return;

        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        ImGuiIO io = ImGui.getIO();

        // --- 显示度量（framebuffer 像素坐标，缩放比按 GUI scale 处理） ---
        int[] fbW = new int[]{1}, fbH = new int[]{1};
        GLFW.glfwGetFramebufferSize(window, fbW, fbH);
        float guiScale = Math.max(1f, (float) mc.getWindow().getGuiScale());
        io.setDisplaySize(fbW[0], fbH[0]);
        io.setDisplayFramebufferScale(1f, 1f);
        io.setFontGlobalScale(guiScale);
        io.setDeltaTime(1f / 60f);

        // --- 鼠标输入（直接轮询 GLFW，不覆盖 MC 回调链） ---
        double[] mx = new double[]{0.0}, my = new double[]{0.0};
        GLFW.glfwGetCursorPos(window, mx, my);
        io.setMousePos((float) mx[0], (float) my[0]);
        io.setMouseDown(0, GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS);
        io.setMouseDown(1, GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS);
        io.setMouseDown(2, GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS);

        // --- 面板布局 ---
        ImGui.newFrame();

        float winW = fbW[0] * 0.72f;
        float winH = fbH[0] * 0.9f;
        ImGui.setNextWindowPos((fbW[0] - winW) / 2f, (fbH[0] - winH) / 2f, ImGuiCond.Once);
        ImGui.setNextWindowSize(winW, winH, ImGuiCond.Once);

        if (ImGui.begin("Dynamic Island Settings")) {
            drawGeneral();
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
        gl3.renderDrawData(ImGui.getDrawData());
    }

    // ---------------------------------------------------------------
    // 面板分组
    // ---------------------------------------------------------------

    private void drawGeneral() {
        if (ImGui.collapsingHeader("General")) {
            checkbox("Enabled (overlay master switch)", () -> cfg.enabled, v -> cfg.enabled = v);

            int[] pos = {cfg.position};
            if (ImGui.combo("Position", pos, new String[]{"Top", "Top Left", "Top Right"})) {
                cfg.position = pos[0];
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
        boolean[] v = {getter.getAsBoolean()};
        if (ImGui.checkbox(label, v)) {
            setter.accept(v[0]);
            IslandConfig.save();
        }
    }

    @Override
    public void onClose() {
        // Minecraft 26.2: setScreen moved from Minecraft to Gui
        this.minecraft.gui.setScreen(parent);
    }
}