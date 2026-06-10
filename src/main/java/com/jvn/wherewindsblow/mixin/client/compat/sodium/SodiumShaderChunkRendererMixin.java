package com.jvn.wherewindsblow.mixin.client.compat.sodium;

import com.jvn.wherewindsblow.WhereWindsBlow;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL20C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public abstract class SodiumShaderChunkRendererMixin {
    @Unique
    private static final ResourceLocation WHEREWINDSBLOW$SODIUM_WIND_SHADER = ResourceLocation.fromNamespaceAndPath(
            WhereWindsBlow.MOD_ID,
            "sodium/block_layer_opaque.vsh"
    );

    @ModifyArg(
            method = "createShader",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderLoader;loadShader(Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderType;Lnet/minecraft/resources/ResourceLocation;Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderConstants;)Lnet/caffeinemc/mods/sodium/client/gl/shader/GlShader;",
                    ordinal = 0
            ),
            index = 1,
            require = 0
    )
    private ResourceLocation wherewindsblow$useResponsiveFoliageWindShader(ResourceLocation original) {
        return WHEREWINDSBLOW$SODIUM_WIND_SHADER;
    }

    @Inject(method = "begin", at = @At("TAIL"), require = 0)
    private void wherewindsblow$uploadResponsiveFoliageWindTime(CallbackInfo ci) {
        int program = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        if (program == 0) {
            return;
        }

        int location = GL20C.glGetUniformLocation(program, "u_WwbTime");
        if (location >= 0) {
            GL20C.glUniform1f(location, Util.getMillis() * 0.001F);
        }
    }
}
