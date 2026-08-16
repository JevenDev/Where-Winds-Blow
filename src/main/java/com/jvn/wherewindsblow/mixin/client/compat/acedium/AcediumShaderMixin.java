package com.jvn.wherewindsblow.mixin.client.compat.acedium;

import com.jvn.wherewindsblow.client.foliage.SodiumFoliageUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "me.cortex.nvidium.gl.shader.Shader", remap = false)
public abstract class AcediumShaderMixin {
    @Inject(method = "bind", at = @At("TAIL"), require = 0)
    private void wherewindsblow$uploadWindUniforms(CallbackInfo ci) {
        SodiumFoliageUniforms.uploadActiveAcediumProgramUniforms();
    }
}
