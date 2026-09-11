package com.example.dynamicisland.client;

import com.example.dynamicisland.client.config.IslandConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * 跳跃重置（JumpReset）功能模块。
 *
 * <p>核心机制：当玩家被击退、落入空中后，在落地的一瞬间自动起跳，
 * 借此重置水平动量、抵消击退位移（PvP 常见的「跳跃重置」技巧）。
 *
 * <p>检测策略（纯客户端、无混入）：
 * <ol>
 *   <li>上一帧在地面、这一帧离地，且并非玩家主动按下跳跃键，且水平速度
 *       超过 {@link IslandConfig#jumpResetThreshold} → 判定为「被击退抛入空中」；</li>
 *   <li>标记 {@code pendingReset} 后，一旦检测到落地（离地 → 地面），立即
 *       模拟按下跳跃键，并在一帧后松开。</li>
 * </ol>
 *
 * <p>为规避「疾跑冲下悬崖」等误触发，可通过 {@code jumpResetThreshold} 调高阈值；
 * 主动跳跃（自起跳）时不会标记，因而不影响普通移动。
 */
public final class JumpResetModule {

    /** 上一帧是否在地面。 */
    private boolean wasOnGround = true;
    /** 是否处于「落地即重置」的待命状态。 */
    private boolean pendingReset;
    /** 上一帧发出的自动跳跃是否等待释放。 */
    private boolean jumpQueued;

    /**
     * 每个客户端 tick 调用一次（在 END_CLIENT_TICK 阶段）。
     */
    public void tick(Minecraft client) {
        Player p = client.player;
        if (p == null) {
            wasOnGround = true;
            pendingReset = false;
            jumpQueued = false;
            return;
        }

        // 释放上一帧发出的跳跃输入（让 keyJump 在下一帧输入采样后自然松开）。
        if (jumpQueued) {
            client.options.keyJump.setDown(false);
            jumpQueued = false;
        }

        if (!IslandConfig.get().jumpReset) {
            wasOnGround = p.onGround();
            pendingReset = false;
            return;
        }

        boolean onGround = p.onGround();
        double hSpeed = Math.hypot(p.getDeltaMovement().x, p.getDeltaMovement().z);
        boolean selfJumping = client.options.keyJump.isDown();
        float threshold = IslandConfig.get().jumpResetThreshold;

        // 击退判定：上一帧在地面、这一帧离地、非自己起跳、水平速度超阈值。
        if (wasOnGround && !onGround && !selfJumping && hSpeed > threshold) {
            pendingReset = true;
        }

        // 落地瞬间自动起跳，重置水平动量。
        if (pendingReset && onGround && !wasOnGround && p.isAlive()) {
            client.options.keyJump.setDown(true);
            jumpQueued = true;
            pendingReset = false;
        }

        wasOnGround = onGround;
    }
}