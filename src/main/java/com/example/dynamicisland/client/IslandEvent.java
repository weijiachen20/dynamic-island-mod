package com.example.dynamicisland.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A single discrete "notification" the island can surface.
 *
 * <p>Events are short-lived: they enter the island, are shown for
 * {@link #durationTicks}, then the island collapses back to idle. Persistent
 * sources (active potion, playing music) refresh the same logical event every
 * tick so the timer effectively does not run down while the condition holds.
 */
public final class IslandEvent {

    public enum Type {
        /** A status effect is currently active on the player. Persistent. */
        POTION,
        /** A jukebox record is playing nearby. Persistent. */
        MUSIC,
        /** Weather state changed. Transient. */
        WEATHER,
        /** An advancement was earned. Transient. */
        ADVANCEMENT,
        /** Player health dropped below threshold. Transient, repeating. */
        HEALTH,
        /** Player hunger dropped below threshold. Transient, repeating. */
        HUNGER,
        /** World crossed dawn / dusk. Transient. */
        DAY_NIGHT,
        /** Player took damage. Transient. */
        DAMAGE,
        /** Player reached a new experience level milestone. Transient. */
        LEVEL_UP,
        /** A LiquidBounce Nextgen module was toggled on/off. Transient. */
        LIQUIDBOUNCE,
        /** 网易云音乐客户端正在播放歌曲. Persistent. */
        NETEASE_MUSIC
    }

    public enum Priority {
        /** Idle / collapsed. Lowest. */
        LOW(0),
        /** Informational (music, potion, day/night). */
        INFO(1),
        /** Status alerts (weather, hunger). */
        ALERT(2),
        /** Urgent (low health, damage taken). Highest. */
        URGENT(3);

        public final int weight;
        Priority(int w) { this.weight = w; }
    }

    public final Type type;
    public final Priority priority;

    /** Optional icon shown in the expanded island (may be {@code ItemStack.EMPTY}). */
    public final ItemStack icon;

    /** Primary line, e.g. "Now Playing". May be {@code null} to omit. */
    public final Component title;
    /** Secondary line, e.g. the track name. May be {@code null}. */
    public final Component subtitle;

    /** Remaining ticks the island should stay expanded for this event. */
    public int durationTicks;

    /** Logical key used to coalesce / refresh repeating events of the same kind. */
    public final String key;

    /** When true, the event keeps refreshing its timer while the source is active. */
    public final boolean persistent;

    public IslandEvent(Type type, Priority priority, ItemStack icon,
                       Component title, Component subtitle, int durationTicks,
                       String key, boolean persistent) {
        this.type = type;
        this.priority = priority;
        this.icon = icon;
        this.title = title;
        this.subtitle = subtitle;
        this.durationTicks = durationTicks;
        this.key = key;
        this.persistent = persistent;
    }

    /** Two events are "the same slot" when their key matches; the newer replaces the older. */
    public boolean sameSlot(IslandEvent other) {
        return other != null && this.key.equals(other.key);
    }
}
