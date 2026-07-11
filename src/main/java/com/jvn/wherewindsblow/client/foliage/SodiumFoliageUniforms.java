package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.GlobalWindState;
import com.jvn.wherewindsblow.client.wind.GustFrontState;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL20C;

public final class SodiumFoliageUniforms {
    private static final int UNRESOLVED_UNIFORM = Integer.MIN_VALUE;
    private static int lastProgram;
    private static int windTimeUniform = UNRESOLVED_UNIFORM;
    private static int ambientWindStrengthUniform = UNRESOLVED_UNIFORM;
    private static int windTurbulenceUniform = UNRESOLVED_UNIFORM;
    private static int activeGustCountUniform = UNRESOLVED_UNIFORM;
    private static int plantSwayStrengthUniform = UNRESOLVED_UNIFORM;
    private static int leafSwayStrengthUniform = UNRESOLVED_UNIFORM;
    private static int lanternSwayStrengthUniform = UNRESOLVED_UNIFORM;
    private static int plantSheenStrengthUniform = UNRESOLVED_UNIFORM;
    private static int leafSheenStrengthUniform = UNRESOLVED_UNIFORM;
    private static int windDirectionUniform = UNRESOLVED_UNIFORM;
    private static int cameraPositionUniform = UNRESOLVED_UNIFORM;
    private static int interactorCountUniform = UNRESOLVED_UNIFORM;
    private static final int[] gustOriginTimeUniforms = new int[DynamicWindManager.MAX_ACTIVE_GUSTS];
    private static final int[] gustDirectionSpeedUniforms = new int[DynamicWindManager.MAX_ACTIVE_GUSTS];
    private static final int[] gustStrengthUniforms = new int[DynamicWindManager.MAX_ACTIVE_GUSTS];
    private static final int[] gustEnvelopeUniforms = new int[DynamicWindManager.MAX_ACTIVE_GUSTS];
    private static final int[] interactorUniforms = new int[ResponsiveFoliageShaders.MAX_FOLIAGE_INTERACTORS];
    private static final int[] interactorStrengthUniforms = new int[(ResponsiveFoliageShaders.MAX_FOLIAGE_INTERACTORS + 3) / 4];

    private SodiumFoliageUniforms() {
    }

    public static void reset() {
        lastProgram = 0;
        windTimeUniform = UNRESOLVED_UNIFORM;
        ambientWindStrengthUniform = UNRESOLVED_UNIFORM;
        windTurbulenceUniform = UNRESOLVED_UNIFORM;
        activeGustCountUniform = UNRESOLVED_UNIFORM;
        plantSwayStrengthUniform = UNRESOLVED_UNIFORM;
        leafSwayStrengthUniform = UNRESOLVED_UNIFORM;
        lanternSwayStrengthUniform = UNRESOLVED_UNIFORM;
        plantSheenStrengthUniform = UNRESOLVED_UNIFORM;
        leafSheenStrengthUniform = UNRESOLVED_UNIFORM;
        windDirectionUniform = UNRESOLVED_UNIFORM;
        cameraPositionUniform = UNRESOLVED_UNIFORM;
        interactorCountUniform = UNRESOLVED_UNIFORM;
        Arrays.fill(gustOriginTimeUniforms, UNRESOLVED_UNIFORM);
        Arrays.fill(gustDirectionSpeedUniforms, UNRESOLVED_UNIFORM);
        Arrays.fill(gustStrengthUniforms, UNRESOLVED_UNIFORM);
        Arrays.fill(gustEnvelopeUniforms, UNRESOLVED_UNIFORM);
        Arrays.fill(interactorUniforms, UNRESOLVED_UNIFORM);
        Arrays.fill(interactorStrengthUniforms, UNRESOLVED_UNIFORM);
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
            GlobalWindState windState = DynamicWindManager.currentState();
            uploadUniform(windTimeUniform, DynamicWindManager.simulationTime());
            uploadUniform(ambientWindStrengthUniform, DynamicWindManager.visualStrength(windState.ambientStrength()));
            uploadUniform(windTurbulenceUniform, windState.ambientTurbulence());
            uploadUniform(plantSwayStrengthUniform, ResponsiveFoliageShaders.plantWindSwayStrength());
            uploadUniform(leafSwayStrengthUniform, ResponsiveFoliageShaders.leafWindSwayStrength());
            uploadUniform(lanternSwayStrengthUniform, ResponsiveFoliageShaders.lanternWindSwayStrength());
            uploadUniform(plantSheenStrengthUniform, ResponsiveFoliageShaders.plantWindSheenStrength());
            uploadUniform(leafSheenStrengthUniform, ResponsiveFoliageShaders.leafWindSheenStrength());
            uploadVector2Uniform(windDirectionUniform, windState.directionX(), windState.directionZ());
            uploadGustUniforms();
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
        ambientWindStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbAmbientWindStrength");
        windTurbulenceUniform = GL20C.glGetUniformLocation(program, "u_WwbWindTurbulence");
        activeGustCountUniform = GL20C.glGetUniformLocation(program, "u_WwbActiveGustCount");
        plantSwayStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbPlantSwayStrength");
        leafSwayStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbLeafSwayStrength");
        lanternSwayStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbLanternSwayStrength");
        plantSheenStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbPlantSheenStrength");
        leafSheenStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbLeafSheenStrength");
        windDirectionUniform = GL20C.glGetUniformLocation(program, "u_WwbWindDirection");
        cameraPositionUniform = GL20C.glGetUniformLocation(program, "u_WwbCameraPosition");
        interactorCountUniform = GL20C.glGetUniformLocation(program, "u_WwbInteractorCount");
        for (int index = 0; index < DynamicWindManager.MAX_ACTIVE_GUSTS; index++) {
            gustOriginTimeUniforms[index] = GL20C.glGetUniformLocation(program, "u_WwbGustOriginTime" + index);
            gustDirectionSpeedUniforms[index] = GL20C.glGetUniformLocation(program, "u_WwbGustDirectionSpeed" + index);
            gustStrengthUniforms[index] = GL20C.glGetUniformLocation(program, "u_WwbGustStrength" + index);
            gustEnvelopeUniforms[index] = GL20C.glGetUniformLocation(program, "u_WwbGustEnvelope" + index);
        }
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

    private static void uploadVector2Uniform(int location, float x, float y) {
        if (location >= 0) {
            GL20C.glUniform2f(location, x, y);
        }
    }

    private static void uploadGustUniforms() {
        int gustCount = DynamicWindManager.activeGustCount();
        uploadIntUniform(activeGustCountUniform, gustCount);
        for (int index = 0; index < DynamicWindManager.MAX_ACTIVE_GUSTS; index++) {
            GustFrontState gust = DynamicWindManager.gustFront(index);
            if (gust == null) {
                uploadVector4Uniform(gustOriginTimeUniforms[index], 0.0F, 0.0F, 0.0F, 0.0F);
                uploadVector4Uniform(gustDirectionSpeedUniforms[index], 0.0F, 0.0F, 0.0F, 0.0F);
                uploadVector4Uniform(gustStrengthUniforms[index], 0.0F, 0.0F, 0.0F, 0.0F);
                uploadVector4Uniform(gustEnvelopeUniforms[index], 0.0F, 0.0F, 0.0F, 0.0F);
                continue;
            }
            uploadVector4Uniform(
                    gustOriginTimeUniforms[index],
                    gust.originX(),
                    gust.originZ(),
                    gust.startTime(),
                    gust.duration()
            );
            uploadVector4Uniform(
                    gustDirectionSpeedUniforms[index],
                    gust.directionX(),
                    gust.directionZ(),
                    gust.speed(),
                    gust.width()
            );
            uploadVector4Uniform(
                    gustStrengthUniforms[index],
                    DynamicWindManager.visualStrength(
                            gust.peakStrength() * DynamicWindManager.effectiveGustStrengthMultiplier()
                    ),
                    gust.turbulence() * DynamicWindManager.effectiveTurbulenceMultiplier(),
                    gust.noisePhase(),
                    gust.crossDrift()
            );
            uploadVector4Uniform(
                    gustEnvelopeUniforms[index],
                    gust.attackFraction(),
                    gust.releaseStartFraction(),
                    0.0F,
                    0.0F
            );
        }
    }

    private static void uploadVector4Uniform(int location, float x, float y, float z, float w) {
        if (location >= 0) {
            GL20C.glUniform4f(location, x, y, z, w);
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
