package com.jvn.wherewindsblow.mixin.client.compat.sodium;

import com.jvn.wherewindsblow.client.foliage.SodiumFoliageUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public abstract class SodiumShaderChunkRendererMixin {
    @Inject(
            method = "begin(Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;)V",
            at = @At("TAIL"),
            require = 0
    )
    private void wherewindsblow$uploadResponsiveFoliageWindUniforms(Object terrainRenderPass, CallbackInfo ci) {
        SodiumFoliageUniforms.uploadActiveProgramUniforms();
    }
}
