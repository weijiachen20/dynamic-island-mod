package com.example.dynamicisland.client.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * Vanilla-widget config screen (no external config lib required).
 *
 * <p>Every option is a cycling button: click to advance to the next preset,
 * the label refreshes live and the change is persisted immediately.
 */
public class IslandConfigScreen extends Screen {

    private final Screen parent;
    private final IslandConfig cfg;

    public IslandConfigScreen(Screen parent) {
        super(Component.translatable("dynamicisland.title"));
        this.parent = parent;
        this.cfg = IslandConfig.get();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int w = 310;
        int x = cx - w / 2;
        int y = 32;

        y = row(x, y, w, "dynamicisland.config.enabled",
                () -> onOff(cfg.enabled),
                () -> cfg.enabled = !cfg.enabled);

        y = row(x, y, w, "dynamicisland.config.position",
                () -> Component.translatable(switch (cfg.position) {
                    case 1 -> "dynamicisland.config.position.top_left";
                    case 2 -> "dynamicisland.config.position.top_right";
                    default -> "dynamicisland.config.position.top";
                }),
                () -> cfg.position = (cfg.position + 1) % 3);

        y = row(x, y, w, "dynamicisland.config.scale",
                () -> Component.literal(String.format("%.2fx", cfg.scale)),
                () -> cfg.scale = nextF(new float[]{0.75f, 1f, 1.25f, 1.5f, 1.75f}, cfg.scale));

        y = row(x, y, w, "dynamicisland.config.display_time",
                () -> Component.literal(String.format("%.1fs", cfg.displayTime)),
                () -> cfg.displayTime = nextF(new float[]{2f, 3f, 3.5f, 4.5f, 6f}, cfg.displayTime));

        y = row(x, y, w, "dynamicisland.config.bg_opacity",
                () -> Component.literal(cfg.bgOpacity + "%"),
                () -> cfg.bgOpacity = nextI(new int[]{60, 75, 88, 95, 100}, cfg.bgOpacity));

        y = row(x, y, w, "dynamicisland.config.corner_radius",
                () -> Component.literal(String.valueOf(cfg.cornerRadius)),
                () -> cfg.cornerRadius = nextI(new int[]{6, 12, 18, 24, 30}, cfg.cornerRadius));

        y = row(x, y, w, "dynamicisland.config.health_threshold",
                () -> Component.literal(cfg.healthThreshold + " \u2764"),
                () -> cfg.healthThreshold = nextI(new int[]{3, 5, 7, 10}, cfg.healthThreshold));

        y = row(x, y, w, "dynamicisland.config.hunger_threshold",
                () -> Component.literal(cfg.hungerThreshold + " \uD83C\uDF57"),
                () -> cfg.hungerThreshold = nextI(new int[]{2, 3, 5, 7}, cfg.hungerThreshold));

        y = row(x, y, w, "dynamicisland.config.sensitivity",
                () -> Component.literal(String.valueOf(cfg.sensitivity)),
                () -> cfg.sensitivity = nextI(new int[]{1, 2, 3, 4}, cfg.sensitivity));

        y = row(x, y, w, "dynamicisland.config.potions",
                () -> onOff(cfg.potions), () -> cfg.potions = !cfg.potions);
        y = row(x, y, w, "dynamicisland.config.music",
                () -> onOff(cfg.music), () -> cfg.music = !cfg.music);
        y = row(x, y, w, "dynamicisland.config.weather",
                () -> onOff(cfg.weather), () -> cfg.weather = !cfg.weather);
        y = row(x, y, w, "dynamicisland.config.advancement",
                () -> onOff(cfg.advancement), () -> cfg.advancement = !cfg.advancement);
        y = row(x, y, w, "dynamicisland.config.health",
                () -> onOff(cfg.health), () -> cfg.health = !cfg.health);
        y = row(x, y, w, "dynamicisland.config.hunger",
                () -> onOff(cfg.hunger), () -> cfg.hunger = !cfg.hunger);
        y = row(x, y, w, "dynamicisland.config.daynight",
                () -> onOff(cfg.daynight), () -> cfg.daynight = !cfg.daynight);
        y = row(x, y, w, "dynamicisland.config.damage",
                () -> onOff(cfg.damage), () -> cfg.damage = !cfg.damage);
        y = row(x, y, w, "dynamicisland.config.liquidbounce",
                () -> onOff(cfg.liquidbounce), () -> cfg.liquidbounce = !cfg.liquidbounce);
        y = row(x, y, w, "dynamicisland.config.netease",
                () -> onOff(cfg.netease), () -> cfg.netease = !cfg.netease);
        y = row(x, y, w, "dynamicisland.config.netease_lyric",
                () -> onOff(cfg.neteaseLyric), () -> cfg.neteaseLyric = !cfg.neteaseLyric);
        y = row(x, y, w, "dynamicisland.config.status_fps",
                () -> onOff(cfg.statusFps), () -> cfg.statusFps = !cfg.statusFps);
        y = row(x, y, w, "dynamicisland.config.status_ip",
                () -> onOff(cfg.statusIp), () -> cfg.statusIp = !cfg.statusIp);
        y = row(x, y, w, "dynamicisland.config.status_lb_version",
                () -> onOff(cfg.statusLbVersion), () -> cfg.statusLbVersion = !cfg.statusLbVersion);
        y = row(x, y, w, "dynamicisland.config.status_lyric",
                () -> onOff(cfg.statusLyric), () -> cfg.statusLyric = !cfg.statusLyric);
        y = row(x, y, w, "dynamicisland.config.status_scaffold",
                () -> onOff(cfg.statusScaffold), () -> cfg.statusScaffold = !cfg.statusScaffold);
        y = row(x, y, w, "dynamicisland.config.status_modules",
                () -> onOff(cfg.statusModules), () -> cfg.statusModules = !cfg.statusModules);
        y = row(x, y, w, "dynamicisland.config.status_ka",
                () -> onOff(cfg.statusKa), () -> cfg.statusKa = !cfg.statusKa);
        y = row(x, y, w, "dynamicisland.config.status_speed",
                () -> onOff(cfg.statusSpeed), () -> cfg.statusSpeed = !cfg.statusSpeed);
        y = row(x, y, w, "dynamicisland.config.status_armor",
                () -> onOff(cfg.statusArmor), () -> cfg.statusArmor = !cfg.statusArmor);
        y = row(x, y, w, "dynamicisland.config.status_ping",
                () -> onOff(cfg.statusPing), () -> cfg.statusPing = !cfg.statusPing);

        // Done button.
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                        btn -> this.onClose())
                .bounds(cx - 100, Math.min(y + 4, this.height - 28), 200, 20)
                .build());
    }

    /** Adds one cycling button row; returns the y for the next row. */
    private int row(int x, int y, int w, String labelKey, Supplier<Component> value, Runnable next) {
        Button b = Button.builder(
                        Component.translatable(labelKey).append(": ").append(value.get()),
                        btn -> {
                            next.run();
                            IslandConfig.save();
                            btn.setMessage(Component.translatable(labelKey).append(": ").append(value.get()));
                        })
                .bounds(x, y, w, 20)
                .build();
        this.addRenderableWidget(b);
        return y + 22;
    }

    private static Component onOff(boolean v) {
        return Component.translatable(v ? "options.on" : "options.off");
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
