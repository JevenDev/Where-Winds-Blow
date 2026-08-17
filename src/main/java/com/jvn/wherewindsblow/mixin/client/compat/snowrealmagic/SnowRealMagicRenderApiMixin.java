package com.jvn.wherewindsblow.mixin.client.compat.snowrealmagic;

import com.jvn.wherewindsblow.client.foliage.SnowRealMagicCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "snownee.snow.client.FabricRendererRenderAPI", remap = false)
public abstract class SnowRealMagicRenderApiMixin {
    @Shadow
    @Final
    private BlockAndTintGetter level;

    @Shadow
    @Final
    private BlockPos pos;

    @Inject(method = "render", at = @At("HEAD"))
    private void wherewindsblow$beginSnowRealMagicModelRender(CallbackInfoReturnable<Boolean> cir) {
        SnowRealMagicCompat.beginRender(level, pos);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void wherewindsblow$endSnowRealMagicModelRender(CallbackInfoReturnable<Boolean> cir) {
        SnowRealMagicCompat.endRender();
    }

    @WrapOperation(
            method = "lambda$render$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/fabricmc/fabric/api/renderer/v1/mesh/MutableQuadView;"
                            + "color(II)Lnet/fabricmc/fabric/api/renderer/v1/mesh/MutableQuadView;"
            )
    )
    private MutableQuadView wherewindsblow$preserveFoliageMarkerAlpha(
            MutableQuadView quad,
            int vertexIndex,
            int biomeColor,
            Operation<MutableQuadView> original
    ) {
        int markerAlpha = quad.color(vertexIndex) & 0xFF000000;
        int colorWithMarkerAlpha = biomeColor & 0x00FFFFFF | markerAlpha;
        return original.call(quad, vertexIndex, colorWithMarkerAlpha);
    }
}
