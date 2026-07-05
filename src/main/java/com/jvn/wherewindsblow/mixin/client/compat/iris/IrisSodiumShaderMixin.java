package com.jvn.wherewindsblow.mixin.client.compat.iris;

import com.jvn.wherewindsblow.client.foliage.SodiumFoliageUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.irisshaders.iris.pipeline.programs.SodiumShader", remap = false)
public abstract class IrisSodiumShaderMixin {
    @Inject(method = "setupState", at = @At("TAIL"), require = 0)
    private void wherewindsblow$uploadResponsiveFoliageWindUniforms(CallbackInfo ci) {
        SodiumFoliageUniforms.uploadActiveProgramUniforms();
    }
}
