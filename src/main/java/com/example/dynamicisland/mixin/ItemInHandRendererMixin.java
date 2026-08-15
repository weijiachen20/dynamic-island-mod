package com.example.dynamicisland.mixin;

import com.example.dynamicisland.client.SpinToggler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 按下 R 键（可在控制中重绑定）开启时，手持物品在第一人称视角中
 * 绕 Y 轴水平旋转（每秒 1.5 圈）。
 * 注入点：renderArmWithItem 内部调用 renderItem 的前后，确保旋转发生在
 * 手部变换之后、实际模型渲染之前，使物品原地旋转而非整体平移。
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    /** renderArmWithItem 中 renderItem 被调用前 push、调用后 pop 的配对标记 */
    @Unique
    private boolean dynamicIsland$spinPushed = false;

    /**
     * 在 renderItem 调用前注入：若用户通过 R 键开启旋转，则 push + 绕 Y 轴旋转。
     */
    @Inject(
            method = "submitArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
            ),
            require = 0
    )
    private void dynamicIsland$spinBeforeRenderItem(
            AbstractClientPlayer player, float partialTicks, float interpolatedPitch,
            InteractionHand hand, float swingProgress, ItemStack stack,
            float equipProgress, PoseStack poseStack, SubmitNodeCollector buffer,
            int combinedLight, CallbackInfo ci) {
        dynamicIsland$spinPushed = false;
        if (SpinToggler.isEnabled() && stack != null && !stack.isEmpty()) {
            // 每秒 1.5 圈的水平旋转（Y 轴 → 物品左右翻转）
            long periodMs = (long) (1000.0 / 1.5);
            float angle = ((float) (System.currentTimeMillis() % periodMs) / (float) periodMs) * (float) Math.PI * 2f;
            poseStack.pushPose();
            poseStack.mulPose(new Quaternionf().rotationY(angle));
            dynamicIsland$spinPushed = true;
        }
    }

    /**
     * 在 renderItem 调用后注入：若之前 push 过则 pop 恢复矩阵。
     */
    @Inject(
            method = "submitArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void dynamicIsland$spinAfterRenderItem(
            AbstractClientPlayer player, float partialTicks, float interpolatedPitch,
            InteractionHand hand, float swingProgress, ItemStack stack,
            float equipProgress, PoseStack poseStack, SubmitNodeCollector buffer,
            int combinedLight, CallbackInfo ci) {
        if (dynamicIsland$spinPushed) {
            poseStack.popPose();
            dynamicIsland$spinPushed = false;
        }
    }
}
