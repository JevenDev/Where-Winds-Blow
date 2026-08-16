package com.jvn.wherewindsblow.mixin.client.compat.sodium;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import com.jvn.wherewindsblow.client.foliage.SodiumFoliageShaderSource;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class SodiumShaderLoaderMixin {
    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, require = 0)
    private static void wherewindsblow$patchResponsiveFoliageWindShader(ResourceLocation name, CallbackInfoReturnable<String> cir) {
        String source = cir.getReturnValue();
        if (source == null
                || !SodiumFoliageShaderSource.isSodiumTerrainVertexShader(name, source)
                || !ResponsiveFoliageShaders.shouldPrepareSodiumShaders()) {
            return;
        }

        cir.setReturnValue(SodiumFoliageShaderSource.patch(name, source));
    }
}
