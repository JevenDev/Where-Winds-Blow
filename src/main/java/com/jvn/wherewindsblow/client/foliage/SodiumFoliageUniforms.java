package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.client.wind.WindDirection;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL20C;

public final class SodiumFoliageUniforms {
    private static final int UNRESOLVED_UNIFORM = Integer.MIN_VALUE;
    private static int lastProgram;
    private static int windTimeUniform = UNRESOLVED_UNIFORM;
    private static int weatherWindPowerUniform = UNRESOLVED_UNIFORM;
    private static int plantSwayStrengthUniform = UNRESOLVED_UNIFORM;
    private static int leafSwayStrengthUniform = UNRESOLVED_UNIFORM;
    private static int lanternSwayStrengthUniform = UNRESOLVED_UNIFORM;
    private static int plantSheenStrengthUniform = UNRESOLVED_UNIFORM;
    private static int leafSheenStrengthUniform = UNRESOLVED_UNIFORM;
    private static int windDirectionUniform = UNRESOLVED_UNIFORM;
    private static int cameraPositionUniform = UNRESOLVED_UNIFORM;
    private static int interactorCountUniform = UNRESOLVED_UNIFORM;
    private static final int[] interactorUniforms = new int[ResponsiveFoliageShaders.MAX_FOLIAGE_INTERACTORS];
    private static final int[] interactorStrengthUniforms = new int[(ResponsiveFoliageShaders.MAX_FOLIAGE_INTERACTORS + 3) / 4];

    private SodiumFoliageUniforms() {
    }

    public static void uploadActiveProgramUniforms() {
        if (!ResponsiveFoliageShaders.shouldPatchSodiumShaders()) {
            return;
        }

        try {
            int program = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            if (program == 0) {
                return;
            }

            ResponsiveFoliagePhysics.updateShaderInteractors();
            refreshUniformLocations(program);
            uploadUniform(windTimeUniform, ResponsiveFoliageShaders.windTime());
            uploadUniform(weatherWindPowerUniform, ResponsiveFoliageShaders.weatherWindPower());
            uploadUniform(plantSwayStrengthUniform, ResponsiveFoliageShaders.plantWindSwayStrength());
            uploadUniform(leafSwayStrengthUniform, ResponsiveFoliageShaders.leafWindSwayStrength());
            uploadUniform(lanternSwayStrengthUniform, ResponsiveFoliageShaders.lanternWindSwayStrength());
            uploadUniform(plantSheenStrengthUniform, ResponsiveFoliageShaders.plantWindSheenStrength());
            uploadUniform(leafSheenStrengthUniform, ResponsiveFoliageShaders.leafWindSheenStrength());
            uploadWindDirectionUniform(windDirectionUniform);
            Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            uploadCameraPositionUniform(cameraPositionUniform, cameraPosition);
            int interactorCount = ResponsiveFoliageShaders.foliageInteractorCount();
            uploadIntUniform(interactorCountUniform, interactorCount);
            uploadInteractorUniforms(ResponsiveFoliageShaders.foliageInteractors(), ResponsiveFoliageShaders.foliageInteractorStrengths(), interactorCount, cameraPosition);
        } catch (RuntimeException exception) {
            ResponsiveFoliageShaders.disableSodiumShaderPatch("Failed to upload Sodium foliage shader uniforms.", exception);
        }
    }

    private static void refreshUniformLocations(int program) {
        if (lastProgram == program) {
            return;
        }

        lastProgram = program;
        windTimeUniform = GL20C.glGetUniformLocation(program, "u_WwbTime");
        weatherWindPowerUniform = GL20C.glGetUniformLocation(program, "u_WwbWeatherWindPower");
        plantSwayStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbPlantSwayStrength");
        leafSwayStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbLeafSwayStrength");
        lanternSwayStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbLanternSwayStrength");
        plantSheenStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbPlantSheenStrength");
        leafSheenStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbLeafSheenStrength");
        windDirectionUniform = GL20C.glGetUniformLocation(program, "u_WwbWindDirection");
        cameraPositionUniform = GL20C.glGetUniformLocation(program, "u_WwbCameraPosition");
        interactorCountUniform = GL20C.glGetUniformLocation(program, "u_WwbInteractorCount");
        Arrays.fill(interactorUniforms, UNRESOLVED_UNIFORM);
        for (int index = 0; index < interactorUniforms.length; index++) {
            interactorUniforms[index] = GL20C.glGetUniformLocation(program, "u_WwbInteractor" + index);
        }
        for (int group = 0; group < interactorStrengthUniforms.length; group++) {
            interactorStrengthUniforms[group] = GL20C.glGetUniformLocation(program, "u_WwbInteractorStrengths" + group);
        }
    }

    private static void uploadUniform(int location, float value) {
        if (location >= 0) {
            GL20C.glUniform1f(location, value);
        }
    }

    private static void uploadIntUniform(int location, int value) {
        if (location >= 0) {
            GL20C.glUniform1i(location, value);
        }
    }

    private static void uploadCameraPositionUniform(int location, Vec3 cameraPosition) {
        if (location >= 0) {
            GL20C.glUniform3f(location, (float) cameraPosition.x, (float) cameraPosition.y, (float) cameraPosition.z);
        }
    }

    private static void uploadWindDirectionUniform(int location) {
        if (location >= 0) {
            GL20C.glUniform2f(location, WindDirection.x(), WindDirection.z());
        }
    }

    private static void uploadInteractorUniforms(float[] interactors, float[] strengths, int interactorCount, Vec3 cameraPosition) {
        for (int index = 0; index < interactorCount; index++) {
            int location = interactorUniforms[index];
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

        for (int group = 0; group < ResponsiveFoliageShaders.interactorStrengthGroupCount(interactorCount); group++) {
            int location = interactorStrengthUniforms[group];
            if (location >= 0) {
                int offset = group * 4;
                GL20C.glUniform4f(location, strengths[offset], strengths[offset + 1], strengths[offset + 2], strengths[offset + 3]);
            }
        }
    }
}
