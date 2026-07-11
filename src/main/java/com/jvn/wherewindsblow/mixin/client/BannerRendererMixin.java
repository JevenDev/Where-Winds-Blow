package com.jvn.wherewindsblow.mixin.client;

import com.jvn.wherewindsblow.client.banner.WindReactiveBannerRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BannerRenderer.class)
abstract class BannerRendererMixin {
    @Shadow
    @Final
    private ModelPart pole;

    @Shadow
    @Final
    private ModelPart bar;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void wherewindsblow$renderWindReactiveCloth(
            BannerBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            CallbackInfo ci
    ) {
        if (WindReactiveBannerRenderer.tryRender(
                blockEntity,
                partialTick,
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay,
                this.pole,
                this.bar
        )) {
            ci.cancel();
        }
    }
}
