package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.GlobalWindState;
import com.jvn.wherewindsblow.client.wind.GustFrontState;
import com.jvn.wherewindsblow.config.ClientConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL20C;

public final class SodiumFoliageUniforms {
    private static final Map<Integer, ProgramUniforms> PROGRAM_UNIFORMS = new HashMap<>();

    private SodiumFoliageUniforms() {
    }

    public static void reset() {
        PROGRAM_UNIFORMS.clear();
    }

    public static void uploadActiveProgramUniforms() {
        uploadActiveProgramUniforms(false);
    }

    public static void uploadActiveAcediumProgramUniforms() {
        uploadActiveProgramUniforms(true);
    }

    private static void uploadActiveProgramUniforms(boolean acedium) {
        boolean shouldUpload = acedium
                ? ResponsiveFoliageShaders.shouldPatchAcediumShaders()
                : ResponsiveFoliageShaders.shouldPatchSodiumShaders();
        if (!shouldUpload) {
            return;
        }

        try {
            int program = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            if (program == 0) {
                return;
            }

            ProgramUniforms uniforms = PROGRAM_UNIFORMS.computeIfAbsent(program, ProgramUniforms::new);
            if (uniforms.windTimeUniform >= 0) {
                uniforms.upload();
            }
        } catch (RuntimeException exception) {
            if (acedium) {
                ResponsiveFoliageShaders.disableAcediumShaderPatch(
                        "Failed to upload Acedium foliage shader uniforms.",
                        exception
                );
            } else {
                ResponsiveFoliageShaders.disableSodiumShaderPatch(
                        "Failed to upload Sodium foliage shader uniforms.",
                        exception
                );
            }
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
            GL20C.glUniform3f(
                    location,
                    ResponsiveFoliageShaders.wrapShaderCoordinate(cameraPosition.x),
                    (float) cameraPosition.y,
                    ResponsiveFoliageShaders.wrapShaderCoordinate(cameraPosition.z)
            );
        }
    }

    private static void uploadVector2Uniform(int location, float x, float y) {
        if (location >= 0) {
            GL20C.glUniform2f(location, x, y);
        }
    }

    private static void uploadVector3Uniform(int location, float x, float y, float z) {
        if (location >= 0) {
            GL20C.glUniform3f(location, x, y, z);
        }
    }

    private static void uploadVector4Uniform(int location, float x, float y, float z, float w) {
        if (location >= 0) {
            GL20C.glUniform4f(location, x, y, z, w);
        }
    }

    private static final class ProgramUniforms {
        private final int windTimeUniform;
        private final int ambientWindStrengthUniform;
        private final int windTurbulenceUniform;
        private final int weatherStateUniform;
        private final int activeGustCountUniform;
        private final int plantSwayStrengthUniform;
        private final int leafSwayStrengthUniform;
        private final int lanternSwayStrengthUniform;
        private final int plantSheenStrengthUniform;
        private final int leafSheenStrengthUniform;
        private final int foliageColorVariationStrengthUniform;
        private final int foliageAnimationStepRateUniform;
        private final int pixelLockedWavingUniform;
        private final int windDirectionUniform;
        private final int cameraPositionUniform;
        private final int interactorCountUniform;
        private final int[] gustOriginTimeUniforms = new int[DynamicWindManager.MAX_ACTIVE_GUSTS];
        private final int[] gustDirectionSpeedUniforms = new int[DynamicWindManager.MAX_ACTIVE_GUSTS];
        private final int[] gustStrengthUniforms = new int[DynamicWindManager.MAX_ACTIVE_GUSTS];
        private final int[] gustEnvelopeUniforms = new int[DynamicWindManager.MAX_ACTIVE_GUSTS];
        private final int[] interactorUniforms = new int[ResponsiveFoliageShaders.MAX_FOLIAGE_INTERACTORS];
        private final int[] interactorMotionUniforms = new int[ResponsiveFoliageShaders.MAX_FOLIAGE_INTERACTORS];

        private ProgramUniforms(int program) {
            windTimeUniform = GL20C.glGetUniformLocation(program, "u_WwbTime");
            ambientWindStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbAmbientWindStrength");
            windTurbulenceUniform = GL20C.glGetUniformLocation(program, "u_WwbWindTurbulence");
            weatherStateUniform = GL20C.glGetUniformLocation(program, "u_WwbWeatherState");
            activeGustCountUniform = GL20C.glGetUniformLocation(program, "u_WwbActiveGustCount");
            plantSwayStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbPlantSwayStrength");
            leafSwayStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbLeafSwayStrength");
            lanternSwayStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbLanternSwayStrength");
            plantSheenStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbPlantSheenStrength");
            leafSheenStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbLeafSheenStrength");
            foliageColorVariationStrengthUniform = GL20C.glGetUniformLocation(program, "u_WwbFoliageColorVariationStrength");
            foliageAnimationStepRateUniform = GL20C.glGetUniformLocation(program, "u_WwbFoliageAnimationStepRate");
            pixelLockedWavingUniform = GL20C.glGetUniformLocation(program, "u_WwbPixelLockedWaving");
            windDirectionUniform = GL20C.glGetUniformLocation(program, "u_WwbWindDirection");
            cameraPositionUniform = GL20C.glGetUniformLocation(program, "u_WwbCameraPosition");
            interactorCountUniform = GL20C.glGetUniformLocation(program, "u_WwbInteractorCount");
            for (int index = 0; index < DynamicWindManager.MAX_ACTIVE_GUSTS; index++) {
                gustOriginTimeUniforms[index] = GL20C.glGetUniformLocation(program, "u_WwbGustOriginTime" + index);
                gustDirectionSpeedUniforms[index] = GL20C.glGetUniformLocation(program, "u_WwbGustDirectionSpeed" + index);
                gustStrengthUniforms[index] = GL20C.glGetUniformLocation(program, "u_WwbGustStrength" + index);
                gustEnvelopeUniforms[index] = GL20C.glGetUniformLocation(program, "u_WwbGustEnvelope" + index);
            }
            for (int index = 0; index < interactorUniforms.length; index++) {
                interactorUniforms[index] = GL20C.glGetUniformLocation(program, "u_WwbInteractor" + index);
                interactorMotionUniforms[index] = GL20C.glGetUniformLocation(program, "u_WwbInteractorMotion" + index);
            }
        }

        private void upload() {
            ResponsiveFoliagePhysics.updateShaderInteractors();
            GlobalWindState windState = DynamicWindManager.currentState();
            uploadUniform(windTimeUniform, DynamicWindManager.shaderTime());
            uploadUniform(ambientWindStrengthUniform, DynamicWindManager.visualStrength(windState.ambientStrength()));
            uploadUniform(windTurbulenceUniform, windState.ambientTurbulence());
            uploadVector3Uniform(weatherStateUniform, windState.rainLevel(), windState.thunderLevel(), windState.lullAmount());
            uploadUniform(plantSwayStrengthUniform, ResponsiveFoliageShaders.plantWindSwayStrength());
            uploadUniform(leafSwayStrengthUniform, ResponsiveFoliageShaders.leafWindSwayStrength());
            uploadUniform(lanternSwayStrengthUniform, ResponsiveFoliageShaders.lanternWindSwayStrength());
            uploadUniform(plantSheenStrengthUniform, ResponsiveFoliageShaders.plantWindSheenStrength());
            uploadUniform(leafSheenStrengthUniform, ResponsiveFoliageShaders.leafWindSheenStrength());
            uploadUniform(foliageColorVariationStrengthUniform, ResponsiveFoliageShaders.foliageColorVariationStrength());
            uploadUniform(foliageAnimationStepRateUniform, ResponsiveFoliageShaders.foliageAnimationStepRate());
            uploadIntUniform(pixelLockedWavingUniform, ClientConfig.PIXEL_LOCKED_WAVING.getAsBoolean() ? 1 : 0);
            uploadVector2Uniform(windDirectionUniform, windState.directionX(), windState.directionZ());
            uploadGustUniforms();
            Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            uploadCameraPositionUniform(cameraPositionUniform, cameraPosition);
            int interactorCount = ResponsiveFoliageShaders.foliageInteractorCount();
            uploadIntUniform(interactorCountUniform, interactorCount);
            uploadInteractorUniforms(
                    ResponsiveFoliageShaders.foliageInteractors(),
                    ResponsiveFoliageShaders.foliageInteractorMotions(),
                    interactorCount,
                    cameraPosition
            );
        }

        private void uploadGustUniforms() {
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
                        ResponsiveFoliageShaders.wrapShaderCoordinate(gust.originX()),
                        ResponsiveFoliageShaders.wrapShaderCoordinate(gust.originZ()),
                        DynamicWindManager.shaderTimeFor(gust.startTime()),
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

        private void uploadInteractorUniforms(double[] interactors, double[] motions, int interactorCount, Vec3 cameraPosition) {
            for (int index = 0; index < interactorCount; index++) {
                int offset = index * 4;
                int location = interactorUniforms[index];
                if (location >= 0) {
                    GL20C.glUniform4f(
                            location,
                            (float) (interactors[offset] - cameraPosition.x),
                            (float) (interactors[offset + 1] - cameraPosition.y),
                            (float) (interactors[offset + 2] - cameraPosition.z),
                            (float) interactors[offset + 3]
                    );
                }

                int motionLocation = interactorMotionUniforms[index];
                if (motionLocation >= 0) {
                    GL20C.glUniform4f(
                            motionLocation,
                            (float) (motions[offset] - cameraPosition.y),
                            (float) motions[offset + 1],
                            (float) motions[offset + 2],
                            (float) motions[offset + 3]
                    );
                }
            }
        }
    }
}
