package com.jvn.wherewindsblow.mixin.client;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getRendertypeCutoutMippedShader", at = @At("HEAD"), cancellable = true, require = 0)
    private static void wherewindsblow$useResponsiveFoliageCutoutMippedShader(CallbackInfoReturnable<ShaderInstance> cir) {
        setResponsiveFoliageShader(cir);
    }

    @Inject(method = "getRendertypeCutoutShader", at = @At("HEAD"), cancellable = true, require = 0)
    private static void wherewindsblow$useResponsiveFoliageCutoutShader(CallbackInfoReturnable<ShaderInstance> cir) {
        setResponsiveFoliageShader(cir);
    }

    private static void setResponsiveFoliageShader(CallbackInfoReturnable<ShaderInstance> cir) {
        @Nullable ShaderInstance shader = ResponsiveFoliageShaders.getShader();
        if (shader != null) {
            try {
                ResponsiveFoliageShaders.uploadWeatherUniforms(shader);
                cir.setReturnValue(shader);
            } catch (RuntimeException exception) {
                ResponsiveFoliageShaders.disableCustomFoliageShader("Failed to upload responsive foliage shader uniforms.", exception);
            }
        }
    }
}
