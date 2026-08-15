package com.example.dynamicisland.client;

import com.example.dynamicisland.client.config.IslandConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * Ticks every client tick, inspects the player / world, and pushes
 * {@link IslandEvent}s into the {@link IslandState}.
 *
 * <p>All detection is client-side and built on Mojang official mappings
 * (Minecraft 26.1 ships unobfuscated). Anything version-fragile (e.g. reading
 * the exact jukebox song title) is wrapped so a mismatch degrades gracefully
 * instead of crashing the game.
 */
public final class EventDetector {

    // Minecraft 26.2: 部分 Items 字段被重命名 / 重组为集合类型（如 LIGHTNING_ROD 是 WeatheringCopperCollection），
    // 改为通过 BuiltInRegistries 用资源 ID 直接查找，规避字段漂移。
    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("minecraft", id))
                .flatMap(ref -> ref != null ? java.util.Optional.ofNullable(ref.value()) : java.util.Optional.empty())
                .orElse(Items.AIR);
    }
    private static final Item LIGHTNING_ROD_ITEM = item("lightning_rod");
    private static final Item RED_DYE_ITEM       = item("red_dye");

    private final IslandState state;

    // Previous-state mirrors, for edge detection.
    private boolean prevRaining;
    private boolean prevThundering;
    private boolean wasDay;
    private float prevHealth = 20f;
    private int prevLevel = 0;
    private final Set<String> activePotionKeys = new HashSet<>();
    private final Set<String> activeMusicKeys = new HashSet<>();

    private final LiquidBounceCompat liquidBounce = new LiquidBounceCompat();
    private final NetEaseMusicCompat netEaseMusic = new NetEaseMusicCompat();
    private final ScaffoldTracker scaffoldTracker = new ScaffoldTracker();

    private int jukeboxScanCooldown = 0;

    public EventDetector(IslandState state) {
        this.state = state;
        // 注入实例到 StatusInfo，供折叠态显示
        StatusInfo.setNetEaseMusic(netEaseMusic);
        StatusInfo.setScaffoldTracker(scaffoldTracker);
    }

    public void tick(Minecraft client) {
        if (client.player == null || client.level == null) return;
        IslandConfig cfg = IslandConfig.get();
        if (!cfg.enabled) return;

        // 先行：Scaffold 跟踪（BPS / 方块消耗），每 tick 更新
        scaffoldTracker.tick(client);
        liquidBounce.tick(state);

        ClientLevel level = client.level;
        float health = client.player.getHealth();
        int hunger = client.player.getFoodData().getFoodLevel();
        int lvl = client.player.experienceLevel;
        long timeOfDay = level.getOverworldClockTime() % 24000L;
        boolean raining = level.isRaining();
        boolean thundering = level.isThundering();
        boolean isDay = timeOfDay < 13000L;

        // --- Persistent: potions ---
        if (cfg.potions) {
            detectPotions(client);
        } else {
            for (String k : new HashSet<>(activePotionKeys)) state.retract("potion:" + k);
            activePotionKeys.clear();
        }

        // --- Persistent: jukebox music (throttled radius scan) ---
        if (cfg.music) {
            if (--jukeboxScanCooldown <= 0) {
                jukeboxScanCooldown = 30; // 1.5s
                detectMusic(client, level);
            }
        } else {
            for (String k : new HashSet<>(activeMusicKeys)) state.retract("music:" + k);
            activeMusicKeys.clear();
        }

        // --- Transient: weather ---
        if (cfg.weather) {
            if (thundering && !prevThundering) {
                emit(IslandEvent.Type.WEATHER, IslandEvent.Priority.ALERT,
                        new ItemStack(LIGHTNING_ROD_ITEM),
                        Component.translatable("dynamicisland.event.weather.thunder"), null, "weather");
            } else if (raining && !prevRaining && !thundering) {
                emit(IslandEvent.Type.WEATHER, IslandEvent.Priority.INFO,
                        new ItemStack(Items.WATER_BUCKET),
                        Component.translatable("dynamicisland.event.weather.rain"), null, "weather");
            } else if (!raining && prevRaining) {
                emit(IslandEvent.Type.WEATHER, IslandEvent.Priority.INFO,
                        new ItemStack(Items.SUNFLOWER),
                        Component.translatable("dynamicisland.event.weather.clear"), null, "weather");
            }
        }
        prevRaining = raining;
        prevThundering = thundering;

        // --- Transient: day / night ---
        if (cfg.daynight) {
            if (isDay && !wasDay) {
                emit(IslandEvent.Type.DAY_NIGHT, IslandEvent.Priority.INFO,
                        new ItemStack(Items.CLOCK),
                        Component.translatable("dynamicisland.event.daynight.dawn"), null, "daynight");
            } else if (!isDay && wasDay) {
                emit(IslandEvent.Type.DAY_NIGHT, IslandEvent.Priority.INFO,
                        new ItemStack(Items.CLOCK),
                        Component.translatable("dynamicisland.event.daynight.dusk"), null, "daynight");
            }
        }
        wasDay = isDay;

        // --- Transient: damage taken ---
        if (cfg.damage && health < prevHealth - 0.5f) {
            int lost = Mth.ceil(prevHealth - health);
            emit(IslandEvent.Type.DAMAGE, IslandEvent.Priority.URGENT,
                    new ItemStack(Items.REDSTONE),
                    Component.translatable("dynamicisland.event.damage"),
                    Component.translatable("dynamicisland.event.damage.amount", lost).withStyle(ChatFormatting.RED),
                    "damage");
        }
        prevHealth = health;

        // --- Repeating: low health ---
        // getHealth() is in half-hearts (20 = 10 hearts); threshold config is in hearts.
        if (cfg.health && health > 0 && health <= cfg.healthThreshold * 2f) {
            state.offer(new IslandEvent(
                    IslandEvent.Type.HEALTH, IslandEvent.Priority.URGENT,
                    new ItemStack(RED_DYE_ITEM),
                    Component.translatable("dynamicisland.event.health").withStyle(ChatFormatting.RED),
                    Component.literal((int) health + " / " + (int) client.player.getMaxHealth()),
                    (int) (IslandState.displaySeconds() * 20), "health:low", true));
        } else {
            state.retract("health:low");
        }

        // --- Repeating: low hunger ---
        if (cfg.hunger && hunger > 0 && hunger <= cfg.hungerThreshold * 2) {
            state.offer(new IslandEvent(
                    IslandEvent.Type.HUNGER, IslandEvent.Priority.ALERT,
                    new ItemStack(Items.COOKED_BEEF),
                    Component.translatable("dynamicisland.event.hunger").withStyle(ChatFormatting.GOLD),
                    Component.literal(hunger + " / 20"),
                    (int) (IslandState.displaySeconds() * 20), "hunger:low", true));
        } else {
            state.retract("hunger:low");
        }

        // --- LB feature #4: 盔甲低耐久紧急闪烁（URGENT） ---
        StatusInfo.ArmorStatus as = StatusInfo.getArmorStatus(client);
        if (cfg.enabled && cfg.statusArmor && LiquidBounceCompat.isPresent() && as.lowDurability) {
            state.offer(new IslandEvent(
                    IslandEvent.Type.DAMAGE, IslandEvent.Priority.URGENT,
                    new ItemStack(Items.IRON_CHESTPLATE),
                    Component.translatable("dynamicisland.event.armor.lowdur").withStyle(ChatFormatting.RED),
                    Component.translatable("dynamicisland.event.armor.lowdur.val", as.avgPercent)
                            .withStyle(ChatFormatting.GOLD),
                    (int) (IslandState.displaySeconds() * 20), "armor:lowdur", true));
        } else {
            state.retract("armor:lowdur");
        }

        // --- Transient: level up ---
        if (cfg.levelUp && lvl > prevLevel) {
            emit(IslandEvent.Type.LEVEL_UP, IslandEvent.Priority.INFO,
                    new ItemStack(Items.EXPERIENCE_BOTTLE),
                    Component.translatable("dynamicisland.event.levelup"),
                    Component.translatable("dynamicisland.event.levelup.value", lvl).withStyle(ChatFormatting.GREEN),
                    "levelup");
        }
        prevLevel = lvl;

        // （LiquidBounce 模块切换通知已在 tick 开头统一调用，cfg.liquidbounce 检查在内部实现）

        // --- 网易云音乐客户端: 当前播放歌曲 ---
        if (cfg.netease) {
            netEaseMusic.tick(state);
        } else {
            netEaseMusic.stop();
            state.retract("netease:music");
        }
    }

    /** 在客户端关闭时调用，清理后台进程。 */
    public void shutdown() {
        netEaseMusic.stop();
    }

    /** Called by {@code AdvancementToastMixin} when a toast is queued. */
    public void onAdvancementToast(Component title) {
        IslandConfig cfg = IslandConfig.get();
        if (!cfg.enabled || !cfg.advancement) return;
        emit(IslandEvent.Type.ADVANCEMENT, IslandEvent.Priority.INFO,
                new ItemStack(Items.KNOWLEDGE_BOOK),
                Component.translatable("dynamicisland.event.advancement"),
                title, "advancement");
    }

    private void emit(IslandEvent.Type type, IslandEvent.Priority prio, ItemStack icon,
                      Component title, Component subtitle, String key) {
        state.offer(new IslandEvent(type, prio, icon, title, subtitle,
                (int) (IslandState.displaySeconds() * 20), key, false));
    }

    private void detectPotions(Minecraft client) {
        Set<String> seen = new HashSet<>();
        for (MobEffectInstance inst : client.player.getActiveEffects()) {
            Holder<MobEffect> effect = inst.getEffect();
            String id;
            try {
                id = effect.value().getDescriptionId();
            } catch (Throwable t) {
                id = "effect:" + System.identityHashCode(effect);
            }
            seen.add(id);
            activePotionKeys.add(id);
            String name;
            try {
                name = Component.translatable(effect.value().getDescriptionId()).getString();
            } catch (Throwable t) {
                name = id;
            }
            int secs = inst.getDuration() / 20;
            Component sub = Component.literal(String.format("%dm %ds", secs / 60, secs % 60))
                    .withStyle(ChatFormatting.AQUA);
            state.offer(new IslandEvent(
                    IslandEvent.Type.POTION, IslandEvent.Priority.INFO,
                    potionIcon(id),
                    Component.translatable("dynamicisland.event.potion", name),
                    sub,
                    (int) (IslandState.displaySeconds() * 20),
                    "potion:" + id, true));
        }
        // Retract expired potions.
        for (String id : new HashSet<>(activePotionKeys)) {
            if (!seen.contains(id)) {
                state.retract("potion:" + id);
                activePotionKeys.remove(id);
            }
        }
    }

    private void detectMusic(Minecraft client, ClientLevel level) {
        Set<String> seen = new HashSet<>();
        BlockPos origin = client.player.blockPosition();
        int r = 32;
        for (int x = -r; x <= r; x += 2) {
            for (int z = -r; z <= r; z += 2) {
                for (int y = -5; y <= 5; y += 2) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState blockState;
                    try {
                        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                        blockState = level.getBlockState(pos);
                    } catch (Exception e) {
                        continue;
                    }
                    if (!blockState.is(Blocks.JUKEBOX)) continue;
                    boolean hasRecord;
                    try {
                        hasRecord = blockState.getValue(JukeboxBlock.HAS_RECORD);
                    } catch (Exception e) {
                        hasRecord = true;
                    }
                    if (!hasRecord) continue;

                    String key = "music:" + pos.toShortString();
                    seen.add(key);
                    activeMusicKeys.add(key);
                    Component track = readTrackName(level, pos);
                    state.offer(new IslandEvent(
                            IslandEvent.Type.MUSIC, IslandEvent.Priority.INFO,
                            new ItemStack(Items.MUSIC_DISC_CAT),
                            Component.translatable("dynamicisland.event.music"),
                            track != null ? track : Component.translatable("dynamicisland.event.music.track", "?"),
                            (int) (IslandState.displaySeconds() * 20),
                            key, true));
                }
            }
        }
        for (String key : new HashSet<>(activeMusicKeys)) {
            if (!seen.contains(key)) {
                state.retract(key);
                activeMusicKeys.remove(key);
            }
        }
    }

    /**
     * Best-effort track-name read. Done reflectively over common accessor names
     * so a JukeboxBlockEntity API rename between versions cannot break the build
     * (or the runtime) &mdash; the worst case is a generic "Now Playing" line.
     */
    private Component readTrackName(ClientLevel level, BlockPos pos) {
        try {
            var be = level.getBlockEntity(pos);
            if (be instanceof JukeboxBlockEntity jb) {
                ItemStack record = null;
                for (String m : new String[]{"getRecord", "getStack", "getDisk", "getItem"}) {
                    try {
                        var method = be.getClass().getMethod(m);
                        Object result = method.invoke(be);
                        if (result instanceof ItemStack is && !is.isEmpty()) {
                            record = is;
                            break;
                        }
                    } catch (NoSuchMethodException ignored) {
                        // try next name
                    }
                }
                if (record == null) return null;
                return Component.translatable("dynamicisland.event.music.track", record.getHoverName().getString());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Pick a vaguely thematic item icon per effect description id. Falls back to potion. */
    private ItemStack potionIcon(String id) {
        if (id == null) return new ItemStack(Items.POTION);
        if (id.contains("speed"))           return new ItemStack(Items.SUGAR);
        if (id.contains("strength"))        return new ItemStack(Items.BLAZE_POWDER);
        if (id.contains("regeneration"))    return new ItemStack(Items.GHAST_TEAR);
        if (id.contains("jump"))            return new ItemStack(Items.RABBIT_FOOT);
        if (id.contains("fire_resistance")) return new ItemStack(Items.MAGMA_CREAM);
        if (id.contains("water_breathing")) return new ItemStack(Items.PUFFERFISH);
        if (id.contains("invisibility"))    return new ItemStack(Items.GLASS);
        if (id.contains("night_vision"))    return new ItemStack(Items.GOLDEN_CARROT);
        if (id.contains("poison"))          return new ItemStack(Items.SPIDER_EYE);
        if (id.contains("weakness"))        return new ItemStack(Items.FERMENTED_SPIDER_EYE);
        if (id.contains("slowness"))        return new ItemStack(Items.SOUL_SAND);
        if (id.contains("haste") || id.contains("dig_speed")) return new ItemStack(Items.IRON_PICKAXE);
        return new ItemStack(Items.POTION);
    }
}
