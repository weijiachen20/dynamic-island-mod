package com.example.dynamicisland.client;

/**
 * A single scalar value that smoothly chases a target using frame-rate
 * independent exponential easing.
 *
 * <p>The classic Dynamic Island "morph" feel comes from every visible
 * dimension (width, height, x, y, radius, alpha) independently easing toward
 * its target with slightly different rates, so the pill appears to breathe
 * rather than snap.
 */
public final class EasedValue {
    private float current;
    private float target;
    /** Higher = snappier. ~12 is smooth-but-responsive, ~30 is nearly instant. */
    private final float rate;

    public EasedValue(float initial, float rate) {
        this.current = initial;
        this.target = initial;
        this.rate = rate;
    }

    public void setTarget(float target) {
        this.target = target;
    }

    public void snap(float value) {
        this.current = value;
        this.target = value;
    }

    /** Advance one frame. {@code dt} is seconds since last frame. */
    public void update(float dt) {
        // Exponential approach: current += (target - current) * (1 - e^(-rate*dt))
        float factor = 1f - (float) Math.exp(-rate * dt);
        current += (target - current) * factor;
    }

    public float get() {
        return current;
    }

    public float getTarget() {
        return target;
    }

    /** True once the value is within {@code epsilon} of its target. */
    public boolean settled(float epsilon) {
        return Math.abs(target - current) < epsilon;
    }
}
