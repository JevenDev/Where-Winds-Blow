package com.jvn.wherewindsblow.mixin.client.compat.sodium;

import com.jvn.wherewindsblow.WhereWindsBlow;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.LoadingModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class SodiumShaderLoaderMixin {
    private static final String WHEREWINDSBLOW$SODIUM_WIND_SHADER_PATH = "sodium/block_layer_opaque.vsh";
    private static final String WHEREWINDSBLOW$SODIUM_WIND_SHADER_RESOURCE =
            "/assets/" + WhereWindsBlow.MOD_ID + "/shaders/" + WHEREWINDSBLOW$SODIUM_WIND_SHADER_PATH;

    @Inject(method = "getShaderSource", at = @At("HEAD"), cancellable = true, require = 0)
    private static void wherewindsblow$loadResponsiveFoliageWindShader(ResourceLocation name, CallbackInfoReturnable<String> cir) {
        if (!WhereWindsBlow.MOD_ID.equals(name.getNamespace()) || !WHEREWINDSBLOW$SODIUM_WIND_SHADER_PATH.equals(name.getPath())) {
            return;
        }

        try {
            Path shaderPath = LoadingModList.get().findResource(WHEREWINDSBLOW$SODIUM_WIND_SHADER_RESOURCE.substring(1));
            if (shaderPath == null) {
                throw new IllegalStateException("Missing shader resource " + WHEREWINDSBLOW$SODIUM_WIND_SHADER_RESOURCE);
            }

            cir.setReturnValue(Files.readString(shaderPath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read shader resource " + WHEREWINDSBLOW$SODIUM_WIND_SHADER_RESOURCE, exception);
        }
    }
}
