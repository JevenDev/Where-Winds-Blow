package com.jvn.wherewindsblow.mixin.client;

import com.jvn.wherewindsblow.client.wind.PixelLockedWaving;
import com.jvn.wherewindsblow.client.sign.HangingSignSway;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.HangingSignRenderer;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HangingSignRenderer.class)
public abstract class HangingSignRendererMixin {
    @Unique
    private static final double wherewindsblow$CHAIN_PIVOT_OFFSET = 0.3125D;

    @Unique
    private float wherewindsblow$swayDegrees;
    @Unique
    private Matrix4f wherewindsblow$restingPose;
    @Unique
    private Matrix4f wherewindsblow$animatedPose;

    @ModifyVariable(
            method = "render(Lnet/minecraft/world/level/block/entity/SignBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"),
            argsOnly = true,
            require = 1
    )
    private MultiBufferSource wherewindsblow$lockSignPixels(MultiBufferSource source) {
        if (!PixelLockedWaving.enabled()) {
            return source;
        }
        return type -> this.wherewindsblow$restingPose == null ? source.getBuffer(type)
                : PixelLockedWaving.buffers(source, this.wherewindsblow$restingPose, this.wherewindsblow$animatedPose).getBuffer(type);
    }

    @Inject(
            method = "render(Lnet/minecraft/world/level/block/entity/SignBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"),
            require = 1
    )
    private void wherewindsblow$captureHangingSignSway(
            SignBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            CallbackInfo ci
    ) {
        this.wherewindsblow$swayDegrees = HangingSignSway.angleDegrees(blockEntity);
        this.wherewindsblow$restingPose = null;
        this.wherewindsblow$animatedPose = null;
    }

    @Inject(method = "translateSign", at = @At("TAIL"), require = 1)
    private void wherewindsblow$applyHangingSignSway(PoseStack poseStack, float yRot, BlockState state, CallbackInfo ci) {
        if (Math.abs(this.wherewindsblow$swayDegrees) <= 0.001F) {
            return;
        }

        if (PixelLockedWaving.enabled()) {
            this.wherewindsblow$restingPose = new Matrix4f(poseStack.last().pose());
        }
        poseStack.translate(0.0D, wherewindsblow$CHAIN_PIVOT_OFFSET, 0.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(this.wherewindsblow$swayDegrees));
        poseStack.translate(0.0D, -wherewindsblow$CHAIN_PIVOT_OFFSET, 0.0D);
        if (this.wherewindsblow$restingPose != null) {
            this.wherewindsblow$animatedPose = new Matrix4f(poseStack.last().pose());
        }
    }
}
