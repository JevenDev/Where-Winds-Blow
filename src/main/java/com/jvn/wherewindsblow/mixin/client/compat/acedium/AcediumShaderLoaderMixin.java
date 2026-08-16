package com.jvn.wherewindsblow.mixin.client.compat.acedium;

import com.jvn.wherewindsblow.client.foliage.AcediumFoliageShaderSource;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "me.cortex.nvidium.sodiumCompat.ShaderLoader", remap = false)
public abstract class AcediumShaderLoaderMixin {
    @Inject(method = "parse", at = @At("RETURN"), cancellable = true, require = 0)
    private static void wherewindsblow$patchTerrainMeshShader(
            ResourceLocation name,
            CallbackInfoReturnable<String> cir
    ) {
        if (!AcediumFoliageShaderSource.isTerrainMeshShader(name)
                || !ResponsiveFoliageShaders.shouldPrepareAcediumShaders()) {
            return;
        }

        cir.setReturnValue(AcediumFoliageShaderSource.patch(name, cir.getReturnValue()));
    }
}
