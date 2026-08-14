package com.example.dynamicisland.mixin;

import com.example.dynamicisland.client.DynamicIslandClient;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Captures advancement-toast creation so the island can surface "Advancement
 * made!" as an event.
 *
 * <p>Because the AdvancementToast constructor and its internal advancement
 * field can change between Minecraft versions, the injection uses
 * {@code require = 0} and the title is extracted reflectively. A mismatch
 * simply disables the advancement feed rather than crashing.
 */
@Mixin(AdvancementToast.class)
public class AdvancementToastMixin {

    @Inject(method = "<init>*", at = @At("RETURN"), require = 0)
    private void dynamicisland$onAdvancementToastCreated(CallbackInfo ci) {
        try {
            Component title = extractTitle((AdvancementToast) (Object) this);
            if (title != null) {
                DynamicIslandClient.onAdvancementToast(title);
            }
        } catch (Throwable ignored) {
            // Degrade silently on any mapping drift.
        }
    }

    /**
     * Walk the toast's fields looking for an advancement holder, then dig the
     * title component out of its display. Resilient to field renames.
     */
    private static Component extractTitle(Object toast) {
        for (Field f : toast.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            Object value;
            try {
                value = f.get(toast);
            } catch (Exception e) {
                continue;
            }
            if (value == null) continue;
            // Try common advancement holder types by method name.
            Component title = tryExtractFromHolder(value);
            if (title != null) return title;
        }
        return null;
    }

    private static Component tryExtractFromHolder(Object holder) {
        // Call value() to get the Advancement definition, then display().getTitle().
        for (String methodName : new String[]{"value", "advancement", "getAdvancement"}) {
            try {
                var m = holder.getClass().getMethod(methodName);
                Object adv = m.invoke(holder);
                if (adv == null) continue;
                Component title = tryExtractDisplayTitle(adv);
                if (title != null) return title;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Component tryExtractDisplayTitle(Object advancement) {
        // Try display() -> Optional<DisplayInfo> -> getTitle()
        for (String methodName : new String[]{"display", "getDisplay"}) {
            try {
                var m = advancement.getClass().getMethod(methodName);
                Object displayOpt = m.invoke(advancement);
                if (displayOpt instanceof java.util.Optional<?> opt) {
                    Object display = opt.orElse(null);
                    if (display == null) continue;
                    return tryGetTitle(display);
                } else if (displayOpt != null) {
                    return tryGetTitle(displayOpt);
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Component tryGetTitle(Object display) {
        for (String methodName : new String[]{"getTitle", "title"}) {
            try {
                var m = display.getClass().getMethod(methodName);
                Object title = m.invoke(display);
                if (title instanceof Component c) return c;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
