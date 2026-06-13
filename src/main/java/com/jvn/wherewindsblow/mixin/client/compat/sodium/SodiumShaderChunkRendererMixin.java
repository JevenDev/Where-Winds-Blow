package com.jvn.wherewindsblow.mixin.client.compat.sodium;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliagePhysics;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
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
    private static int wherewindsblow$plantSwayStrengthUniform = WHEREWINDSBLOW$UNRESOLVED_UNIFORM;

    @Unique
    private static int wherewindsblow$leafSwayStrengthUniform = WHEREWINDSBLOW$UNRESOLVED_UNIFORM;

    @Unique
    private static int wherewindsblow$plantSheenStrengthUniform = WHEREWINDSBLOW$UNRESOLVED_UNIFORM;

    @Unique
    private static int wherewindsblow$leafSheenStrengthUniform = WHEREWINDSBLOW$UNRESOLVED_UNIFORM;

    @Unique
    private static int wherewindsblow$interactorCountUniform = WHEREWINDSBLOW$UNRESOLVED_UNIFORM;

    @Unique
    private static final int[] wherewindsblow$interactorUniforms = new int[ResponsiveFoliageShaders.MAX_FOLIAGE_INTERACTORS];

    @Unique
    private static final int[] wherewindsblow$interactorStrengthUniforms = new int[4];

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

            ResponsiveFoliagePhysics.updateShaderInteractors();
            wherewindsblow$refreshUniformLocations(program);
            wherewindsblow$uploadUniform(wherewindsblow$windTimeUniform, ResponsiveFoliageShaders.windTime());
            wherewindsblow$uploadUniform(wherewindsblow$weatherWindPowerUniform, ResponsiveFoliageShaders.weatherWindPower());
            wherewindsblow$uploadUniform(wherewindsblow$plantSwayStrengthUniform, ResponsiveFoliageShaders.plantWindSwayStrength());
            wherewindsblow$uploadUniform(wherewindsblow$leafSwayStrengthUniform, ResponsiveFoliageShaders.leafWindSwayStrength());
            wherewindsblow$uploadUniform(wherewindsblow$plantSheenStrengthUniform, ResponsiveFoliageShaders.plantWindSheenStrength());
            wherewindsblow$uploadUniform(wherewindsblow$leafSheenStrengthUniform, ResponsiveFoliageShaders.leafWindSheenStrength());
            wherewindsblow$uploadIntUniform(wherewindsblow$interactorCountUniform, ResponsiveFoliageShaders.foliageInteractorCount());
            wherewindsblow$uploadInteractorUniforms(ResponsiveFoliageShaders.foliageInteractors(), ResponsiveFoliageShaders.foliageInteractorStrengths());
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
        wherewindsblow$plantSwayStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbPlantSwayStrength");
        wherewindsblow$leafSwayStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbLeafSwayStrength");
        wherewindsblow$plantSheenStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbPlantSheenStrength");
        wherewindsblow$leafSheenStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbLeafSheenStrength");
        wherewindsblow$interactorCountUniform = GL20C.glGetUniformLocation(program, "u_WwbInteractorCount");
        Arrays.fill(wherewindsblow$interactorUniforms, WHEREWINDSBLOW$UNRESOLVED_UNIFORM);
        for (int index = 0; index < wherewindsblow$interactorUniforms.length; index++) {
            wherewindsblow$interactorUniforms[index] = GL20C.glGetUniformLocation(program, "u_WwbInteractor" + index);
        }
        for (int group = 0; group < wherewindsblow$interactorStrengthUniforms.length; group++) {
            wherewindsblow$interactorStrengthUniforms[group] = GL20C.glGetUniformLocation(program, "u_WwbInteractorStrengths" + group);
        }
    }

    @Unique
    private static void wherewindsblow$uploadUniform(int location, float value) {
        if (location >= 0) {
            GL20C.glUniform1f(location, value);
        }
    }

    @Unique
    private static void wherewindsblow$uploadIntUniform(int location, int value) {
        if (location >= 0) {
            GL20C.glUniform1i(location, value);
        }
    }

    @Unique
    private static void wherewindsblow$uploadInteractorUniforms(float[] interactors, float[] strengths) {
        Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        for (int index = 0; index < wherewindsblow$interactorUniforms.length; index++) {
            int location = wherewindsblow$interactorUniforms[index];
            if (location >= 0) {
                int offset = index * 4;
                GL20C.glUniform4f(
                        location,
                        (float) (interactors[offset] - cameraPosition.x),
                        (float) (interactors[offset + 1] - cameraPosition.y),
                        (float) (interactors[offset + 2] - cameraPosition.z),
                        interactors[offset + 3]
                );
            }
        }

        for (int group = 0; group < wherewindsblow$interactorStrengthUniforms.length; group++) {
            int location = wherewindsblow$interactorStrengthUniforms[group];
            if (location >= 0) {
                int offset = group * 4;
                GL20C.glUniform4f(location, strengths[offset], strengths[offset + 1], strengths[offset + 2], strengths[offset + 3]);
            }
        }
    }
}
