package com.jvn.wherewindsblow.mixin.client.compat.sodium;

import com.jvn.wherewindsblow.client.foliage.SodiumFoliageUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.GlProgram", remap = false)
public abstract class SodiumGlProgramMixin {
    @Inject(method = "delete", at = @At("HEAD"), require = 0)
    private void wherewindsblow$discardWindUniforms(CallbackInfo ci) {
        SodiumFoliageUniforms.reset();
    }
}
