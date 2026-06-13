package com.jvn.wherewindsblow.mixin.client.compat.sodium;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import org.lwjgl.opengl.GL20C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public abstract class SodiumShaderChunkRendererMixin {
    @Unique
    private static final int WHEREWINDSBLOW$UNRESOLVED_UNIFORM = Integer.MIN_VALUE;

    @Unique
    private static int wherewindsblow$lastProgram;

    @Unique
    private static int wherewindsblow$windTimeUniform = WHEREWINDSBLOW$UNRESOLVED_UNIFORM;

    @Unique
    private static int wherewindsblow$weatherWindPowerUniform = WHEREWINDSBLOW$UNRESOLVED_UNIFORM;

    @Unique
    private static int wherewindsblow$swayStrengthUniform = WHEREWINDSBLOW$UNRESOLVED_UNIFORM;

    @Unique
    private static int wherewindsblow$sheenStrengthUniform = WHEREWINDSBLOW$UNRESOLVED_UNIFORM;

    @Inject(method = "begin", at = @At("TAIL"), require = 0)
    private void wherewindsblow$uploadResponsiveFoliageWindTime(CallbackInfo ci) {
        if (!ResponsiveFoliageShaders.shouldPatchSodiumShaders()) {
            return;
        }

        try {
            int program = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            if (program == 0) {
                return;
            }

            wherewindsblow$refreshUniformLocations(program);
            wherewindsblow$uploadUniform(wherewindsblow$windTimeUniform, ResponsiveFoliageShaders.windTime());
            wherewindsblow$uploadUniform(wherewindsblow$weatherWindPowerUniform, ResponsiveFoliageShaders.weatherWindPower());
            wherewindsblow$uploadUniform(wherewindsblow$swayStrengthUniform, ResponsiveFoliageShaders.windSwayStrength());
            wherewindsblow$uploadUniform(wherewindsblow$sheenStrengthUniform, ResponsiveFoliageShaders.windSheenStrength());
        } catch (RuntimeException exception) {
            ResponsiveFoliageShaders.disableSodiumShaderPatch("Failed to upload Sodium foliage shader uniforms.", exception);
        }
    }

    @Unique
    private static void wherewindsblow$refreshUniformLocations(int program) {
        if (wherewindsblow$lastProgram == program) {
            return;
        }

        wherewindsblow$lastProgram = program;
        wherewindsblow$windTimeUniform = GL20C.glGetUniformLocation(program, "u_WwbTime");
        wherewindsblow$weatherWindPowerUniform = GL20C.glGetUniformLocation(program, "u_WwbWeatherWindPower");
        wherewindsblow$swayStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbSwayStrength");
        wherewindsblow$sheenStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbSheenStrength");
    }

    @Unique
    private static void wherewindsblow$uploadUniform(int location, float value) {
        if (location >= 0) {
            GL20C.glUniform1f(location, value);
        }
    }
}
