package com.jvn.wherewindsblow.mixin.client.compat.sodium;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import com.jvn.wherewindsblow.client.foliage.SodiumFoliageShaderSource;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class SodiumShaderLoaderMixin {
    @ModifyExpressionValue(
            method = "loadShader",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderLoader;getShaderSource(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;"
            ),
            require = 0
    )
    private static String wherewindsblow$patchResponsiveFoliageWindShader(
            String source,
            @Local(argsOnly = true) ResourceLocation name
    ) {
        if (source == null
                || !SodiumFoliageShaderSource.isSodiumTerrainVertexShader(name, source)
                || !ResponsiveFoliageShaders.shouldPrepareSodiumShaders()) {
            return source;
        }

        // compose wind after other mods finish replacing or tinting the shader source
        return SodiumFoliageShaderSource.patch(name, source);
    }
}
