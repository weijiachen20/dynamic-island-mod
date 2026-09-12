package com.example.dynamicisland.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Vanilla-widget settings screen — a scrollable, grouped option list.
 *
 * <p>No third-party GUI library: every option is a cycling button (click to
 * advance to the next preset), grouped under coloured headers, persisted
 * immediately on change.
 */
public class IslandConfigScreen extends Screen {

    private final Screen parent;
    private final IslandConfig cfg = IslandConfig.get();
    private SettingsList list;

    public IslandConfigScreen(Screen parent) {
        super(Component.translatable("dynamicisland.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.list = new SettingsList(this.minecraft, this, this.width, this.height, 32, 24);

        // --- 主开关 ---
        this.list.addHeader("dynamicisland.config.group.general");
        this.list.addToggle("dynamicisland.config.enabled", () -> cfg.enabled, v -> cfg.enabled = v);

        // --- 外观 ---
        this.list.addHeader("dynamicisland.config.group.appearance");
        this.list.addCycle("dynamicisland.config.position",
                () -> Component.translatable(switch (cfg.position) {
                    case 1 -> "dynamicisland.config.position.top_left";
                    case 2 -> "dynamicisland.config.position.top_right";
                    default -> "dynamicisland.config.position.top";
                }),
                () -> cfg.position = (cfg.position + 1) % 3);
        this.list.addCycle("dynamicisland.config.scale",
                () -> Component.literal(String.format("%.2fx", cfg.scale)),
                () -> cfg.scale = nextF(new float[]{0.75f, 1f, 1.25f, 1.5f, 1.75f}, cfg.scale));
        this.list.addCycle("dynamicisland.config.bg_opacity",
                () -> Component.literal(cfg.bgOpacity + "%"),
                () -> cfg.bgOpacity = nextI(new int[]{60, 75, 88, 95, 100}, cfg.bgOpacity));
        this.list.addCycle("dynamicisland.config.corner_radius",
                () -> Component.literal(String.valueOf(cfg.cornerRadius)),
                () -> cfg.cornerRadius = nextI(new int[]{6, 12, 18, 24, 30}, cfg.cornerRadius));
        this.list.addToggle("dynamicisland.config.rainbow", () -> cfg.rainbowEnabled, v -> cfg.rainbowEnabled = v);
        this.list.addCycle("dynamicisland.config.rainbow_speed",
                () -> Component.literal(cfg.rainbowSpeed + "\u00b0/s"),
                () -> cfg.rainbowSpeed = nextF(new float[]{0f, 36f, 72f, 108f, 144f, 180f}, cfg.rainbowSpeed));

        // --- 事件行为 ---
        this.list.addHeader("dynamicisland.config.group.events");
        this.list.addCycle("dynamicisland.config.display_time",
                () -> Component.literal(String.format("%.1fs", cfg.displayTime)),
                () -> cfg.displayTime = nextF(new float[]{2f, 3f, 3.5f, 4.5f, 6f}, cfg.displayTime));
        this.list.addCycle("dynamicisland.config.sensitivity",
                () -> Component.literal(String.valueOf(cfg.sensitivity)),
                () -> cfg.sensitivity = nextI(new int[]{1, 2, 3, 4}, cfg.sensitivity));
        this.list.addCycle("dynamicisland.config.health_threshold",
                () -> Component.literal(cfg.healthThreshold + " \u2764"),
                () -> cfg.healthThreshold = nextI(new int[]{3, 5, 7, 10}, cfg.healthThreshold));
        this.list.addCycle("dynamicisland.config.hunger_threshold",
                () -> Component.literal(cfg.hungerThreshold + " \uD83C\uDF57"),
                () -> cfg.hungerThreshold = nextI(new int[]{2, 3, 5, 7}, cfg.hungerThreshold));

        // --- 事件类别 ---
        this.list.addHeader("dynamicisland.config.group.categories");
        this.list.addToggle("dynamicisland.config.potions", () -> cfg.potions, v -> cfg.potions = v);
        this.list.addToggle("dynamicisland.config.music", () -> cfg.music, v -> cfg.music = v);
        this.list.addToggle("dynamicisland.config.weather", () -> cfg.weather, v -> cfg.weather = v);
        this.list.addToggle("dynamicisland.config.advancement", () -> cfg.advancement, v -> cfg.advancement = v);
        this.list.addToggle("dynamicisland.config.health", () -> cfg.health, v -> cfg.health = v);
        this.list.addToggle("dynamicisland.config.hunger", () -> cfg.hunger, v -> cfg.hunger = v);
        this.list.addToggle("dynamicisland.config.daynight", () -> cfg.daynight, v -> cfg.daynight = v);
        this.list.addToggle("dynamicisland.config.damage", () -> cfg.damage, v -> cfg.damage = v);
        this.list.addToggle("dynamicisland.config.levelup", () -> cfg.levelUp, v -> cfg.levelUp = v);
        this.list.addToggle("dynamicisland.config.liquidbounce", () -> cfg.liquidbounce, v -> cfg.liquidbounce = v);
        this.list.addToggle("dynamicisland.config.netease", () -> cfg.netease, v -> cfg.netease = v);
        this.list.addToggle("dynamicisland.config.netease_lyric", () -> cfg.neteaseLyric, v -> cfg.neteaseLyric = v);

        // --- 状态栏（折叠态常驻信息） ---
        this.list.addHeader("dynamicisland.config.group.status");
        this.list.addToggle("dynamicisland.config.status_fps", () -> cfg.statusFps, v -> cfg.statusFps = v);
        this.list.addToggle("dynamicisland.config.status_ip", () -> cfg.statusIp, v -> cfg.statusIp = v);
        this.list.addToggle("dynamicisland.config.status_lb_version", () -> cfg.statusLbVersion, v -> cfg.statusLbVersion = v);
        this.list.addToggle("dynamicisland.config.status_lyric", () -> cfg.statusLyric, v -> cfg.statusLyric = v);
        this.list.addToggle("dynamicisland.config.status_scaffold", () -> cfg.statusScaffold, v -> cfg.statusScaffold = v);
        this.list.addToggle("dynamicisland.config.status_modules", () -> cfg.statusModules, v -> cfg.statusModules = v);
        this.list.addToggle("dynamicisland.config.status_ka", () -> cfg.statusKa, v -> cfg.statusKa = v);
        this.list.addToggle("dynamicisland.config.status_speed", () -> cfg.statusSpeed, v -> cfg.statusSpeed = v);
        this.list.addToggle("dynamicisland.config.status_armor", () -> cfg.statusArmor, v -> cfg.statusArmor = v);
        this.list.addToggle("dynamicisland.config.status_ping", () -> cfg.statusPing, v -> cfg.statusPing = v);

        // --- 跳跃重置 ---
        this.list.addHeader("dynamicisland.config.group.jumpreset");
        this.list.addToggle("dynamicisland.config.jumpreset", () -> cfg.jumpReset, v -> cfg.jumpReset = v);
        this.list.addCycle("dynamicisland.config.jumpreset_threshold",
                () -> Component.literal(String.format("%.2f", cfg.jumpResetThreshold)),
                () -> cfg.jumpResetThreshold = nextF(new float[]{0.05f, 0.1f, 0.16f, 0.25f, 0.4f}, cfg.jumpResetThreshold));

        this.addRenderableWidget(this.list);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                        btn -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        // Minecraft 26.2: setScreen moved from Minecraft to Gui
        this.minecraft.gui.setScreen(parent);
    }

    // ---------------------------------------------------------------
    // 滚动分组列表
    // ---------------------------------------------------------------

    /** Scrollable settings list. Each row is one entry (header or option row). */
    private static final class SettingsList extends ContainerObjectSelectionList<SettingsList.Entry> {

        private static final int ROW_WIDTH = 320;
        private final IslandConfigScreen screen;

        SettingsList(Minecraft mc, IslandConfigScreen screen, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
            this.screen = screen;
        }

        @Override
        public int getRowWidth() {
            return ROW_WIDTH;
        }

        void addHeader(String key) {
            this.addEntry(new HeaderEntry(Component.translatable(key)));
        }

        void addToggle(String key, BooleanSupplier getter, Consumer<Boolean> setter) {
            this.addEntry(new ButtonRowEntry(Component.translatable(key),
                    () -> onOff(getter.getAsBoolean()),
                    () -> setter.accept(!getter.getAsBoolean())));
        }

        void addCycle(String key, Supplier<Component> value, Runnable next) {
            this.addEntry(new ButtonRowEntry(Component.translatable(key), value, next));
        }

        private static Component onOff(boolean v) {
            return Component.translatable(v ? "options.on" : "options.off");
        }

        abstract class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        }

        final class HeaderEntry extends Entry {
            private final Component label;

            HeaderEntry(Component label) {
                this.label = label;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                       boolean hovered, float delta) {
                int textY = this.getY() + (this.getHeight() - 9) / 2;
                graphics.text(screen.font, label, this.getX() + 4, textY, 0xFF55AAFF);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of();
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of();
            }
        }

        final class ButtonRowEntry extends Entry {
            private final Component label;
            private final Button button;
            private final Supplier<Component> value;

            ButtonRowEntry(Component label, Supplier<Component> value, Runnable onChange) {
                this.label = label;
                this.value = value;
                this.button = Button.builder(Component.empty(), btn -> {
                            onChange.run();
                            IslandConfig.save();
                            refreshMessage();
                        })
                        .bounds(0, 0, 170, 20)
                        .build();
                refreshMessage();
            }

            private void refreshMessage() {
                this.button.setMessage(this.value.get());
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                       boolean hovered, float delta) {
                int x = this.getX() + ROW_WIDTH - 170;
                int y = this.getY() + (this.getHeight() - 20) / 2;
                this.button.setX(x);
                this.button.setY(y);
                int textY = this.getY() + (this.getHeight() - 9) / 2;
                graphics.text(screen.font, label, this.getX() + 4, textY, 0xE0E0E0);
                this.button.extractRenderState(graphics, mouseX, mouseY, delta);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(this.button);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(this.button);
            }
        }
    }

    private static int nextI(int[] opts, int current) {
        for (int i = 0; i < opts.length; i++) {
            if (opts[i] == current) return opts[(i + 1) % opts.length];
        }
        return opts[0];
    }

    private static float nextF(float[] opts, float current) {
        for (int i = 0; i < opts.length; i++) {
            if (Math.abs(opts[i] - current) < 0.001f) return opts[(i + 1) % opts.length];
        }
        return opts[0];
    }
}
