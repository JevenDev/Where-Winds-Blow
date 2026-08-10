package com.jvn.wherewindsblow.client.foliage;

import com.jvn.toucanlib.neoforge.client.ToucanShaders;
import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.GlobalWindState;
import com.jvn.wherewindsblow.client.wind.GustFrontState;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.lang.reflect.Method;
import java.util.Arrays;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

public final class ResponsiveFoliageShaders {
    private static final String IRIS_API_CLASS_NAME = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String SODIUM_MOD_ID = "sodium";
    private static final String[] SHADER_RENDERER_MOD_IDS = {"iris", "oculus"};
    private static final String[] INCOMPATIBLE_RENDERER_MOD_IDS = {"chunksfadein"};
    private static final long SHADER_PACK_STATE_CACHE_MILLIS = 250L;
    private static final float LEAF_WIND_SWAY_SCALE = 0.35F;
    public static final int MAX_FOLIAGE_INTERACTORS = 16;
    private static final ResourceLocation WIND_SHADER = WhereWindsBlow.IDS.id("rendertype_responsive_foliage_cutout");
    private static boolean irisApiLookupAttempted;
    @Nullable
    private static Method irisGetInstanceMethod;
    @Nullable
    private static Method irisShaderPackInUseMethod;
    @Nullable
    private static ShaderInstance shader;
    private static long lastShaderPackStateCheckMillis = Long.MIN_VALUE;
    private static boolean externalShaderPackActive;
    private static volatile boolean customShaderDisabled;
    private static volatile boolean sodiumShaderPatchDisabled;
    private static boolean incompatibleRendererWarningLogged;
    private static int foliageInteractorCount;
    // Interactor: center x, min y, center z, radius. Motion: max y, trailing x/z, strength.
    private static final float[] foliageInteractors = new float[MAX_FOLIAGE_INTERACTORS * 4];
    private static final float[] foliageInteractorMotions = new float[MAX_FOLIAGE_INTERACTORS * 4];

    private ResponsiveFoliageShaders() {
    }

    public static void register(RegisterShadersEvent event) {
        ToucanShaders.registerOptional(
                event,
                WIND_SHADER,
                DefaultVertexFormat.BLOCK,
                loadedShader -> shader = loadedShader,
                exception -> disableCustomFoliageShader("Failed to load responsive foliage wind shader.", exception)
        );
    }

    @Nullable
    public static ShaderInstance getShader() {
        return shouldUseCustomFoliageShaders() ? shader : null;
    }

    public static boolean isIrisLoaded() {
        return isModLoaded("iris");
    }

    public static boolean shouldUseCustomFoliageShaders() {
        if (!ClientConfig.ENABLE_CUSTOM_FOLIAGE_SHADER.getAsBoolean()
                || customShaderDisabled
                || isIncompatibleRendererLoaded()) {
            return false;
        }

        return !isExternalShaderPackActive();
    }

    public static boolean shouldPatchSodiumShaders() {
        return shouldUseCustomFoliageShaders()
                && ClientConfig.ENABLE_SODIUM_SHADER_PATCH.getAsBoolean()
                && !sodiumShaderPatchDisabled;
    }

    public static boolean shouldEncodeFoliageVertexMarkers() {
        if (!shouldUseCustomFoliageShaders()) {
            return false;
        }

        return !isModLoaded(SODIUM_MOD_ID) || shouldPatchSodiumShaders();
    }

    public static void setFoliageInteractors(int interactorCount, FoliageInteractorWriter writer) {
        int count = Mth.clamp(interactorCount, 0, MAX_FOLIAGE_INTERACTORS);
        Arrays.fill(foliageInteractors, 0.0F);
        Arrays.fill(foliageInteractorMotions, 0.0F);
        for (int index = 0; index < count; index++) {
            writer.write(foliageInteractors, foliageInteractorMotions, index);
        }

        foliageInteractorCount = count;
    }

    public static void clearFoliageInteractors() {
        if (foliageInteractorCount == 0) {
            return;
        }

        foliageInteractorCount = 0;
        Arrays.fill(foliageInteractors, 0.0F);
        Arrays.fill(foliageInteractorMotions, 0.0F);
    }

    public static int foliageInteractorCount() {
        return foliageInteractorCount;
    }

    public static float[] foliageInteractors() {
        return foliageInteractors;
    }

    public static float[] foliageInteractorMotions() {
        return foliageInteractorMotions;
    }

    public static void disableCustomFoliageShader(String reason, @Nullable Throwable throwable) {
        if (customShaderDisabled) {
            return;
        }

        customShaderDisabled = true;
        if (throwable == null) {
            WhereWindsBlow.LOGGER.warn("{} Custom foliage rendering has been disabled until restart.", reason);
        } else {
            WhereWindsBlow.LOGGER.warn(reason + " Custom foliage rendering has been disabled until restart.", throwable);
        }
    }

    public static void disableSodiumShaderPatch(String reason, @Nullable Throwable throwable) {
        if (sodiumShaderPatchDisabled) {
            return;
        }

        sodiumShaderPatchDisabled = true;
        if (throwable == null) {
            WhereWindsBlow.LOGGER.warn("{} Sodium terrain shader patch has been disabled until restart.", reason);
        } else {
            WhereWindsBlow.LOGGER.warn(reason + " Sodium terrain shader patch has been disabled until restart.", throwable);
        }
    }

    public static void uploadWeatherUniforms(ShaderInstance shader) {
        if (!shouldUseCustomFoliageShaders()) {
            return;
        }

        ResponsiveFoliagePhysics.updateShaderInteractors();
        GlobalWindState windState = DynamicWindManager.currentState();
        shader.safeGetUniform("WindTime").set(DynamicWindManager.simulationTime());
        shader.safeGetUniform("AmbientWindStrength").set(DynamicWindManager.visualStrength(windState.ambientStrength()));
        shader.safeGetUniform("WindTurbulence").set(windState.ambientTurbulence());
        shader.safeGetUniform("WeatherState").set(windState.rainLevel(), windState.thunderLevel(), windState.lullAmount());
        shader.safeGetUniform("PlantWindSwayStrength").set(plantWindSwayStrength());
        shader.safeGetUniform("LeafWindSwayStrength").set(leafWindSwayStrength());
        shader.safeGetUniform("LanternWindSwayStrength").set(lanternWindSwayStrength());
        shader.safeGetUniform("PlantWindSheenStrength").set(plantWindSheenStrength());
        shader.safeGetUniform("LeafWindSheenStrength").set(leafWindSheenStrength());
        shader.safeGetUniform("FoliageColorVariationStrength").set(foliageColorVariationStrength());
        shader.safeGetUniform("FoliageAnimationStepRate").set(foliageAnimationStepRate());
        shader.safeGetUniform("WindDirection").set(windState.directionX(), windState.directionZ());
        uploadGustUniforms(shader);
        Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        shader.safeGetUniform("CameraPosition").set((float) cameraPosition.x, (float) cameraPosition.y, (float) cameraPosition.z);
        shader.safeGetUniform("FoliageInteractorCount").set(foliageInteractorCount);
        uploadFoliageInteractorUniforms(shader);
    }

    private static void uploadGustUniforms(ShaderInstance shader) {
        int gustCount = DynamicWindManager.activeGustCount();
        shader.safeGetUniform("ActiveGustCount").set(gustCount);
        for (int index = 0; index < DynamicWindManager.MAX_ACTIVE_GUSTS; index++) {
            GustFrontState gust = DynamicWindManager.gustFront(index);
            if (gust == null) {
                shader.safeGetUniform("GustOriginTime" + index).set(0.0F, 0.0F, 0.0F, 0.0F);
                shader.safeGetUniform("GustDirectionSpeed" + index).set(0.0F, 0.0F, 0.0F, 0.0F);
                shader.safeGetUniform("GustStrength" + index).set(0.0F, 0.0F, 0.0F, 0.0F);
                shader.safeGetUniform("GustEnvelope" + index).set(0.0F, 0.0F, 0.0F, 0.0F);
                continue;
            }
            shader.safeGetUniform("GustOriginTime" + index).set(
                    gust.originX(),
                    gust.originZ(),
                    gust.startTime(),
                    gust.duration()
            );
            shader.safeGetUniform("GustDirectionSpeed" + index).set(
                    gust.directionX(),
                    gust.directionZ(),
                    gust.speed(),
                    gust.width()
            );
            shader.safeGetUniform("GustStrength" + index).set(
                    DynamicWindManager.visualStrength(
                            gust.peakStrength() * DynamicWindManager.effectiveGustStrengthMultiplier()
                    ),
                    gust.turbulence() * DynamicWindManager.effectiveTurbulenceMultiplier(),
                    gust.noisePhase(),
                    gust.crossDrift()
            );
            shader.safeGetUniform("GustEnvelope" + index).set(
                    gust.attackFraction(),
                    gust.releaseStartFraction(),
                    0.0F,
                    0.0F
            );
        }
    }

    private static void uploadFoliageInteractorUniforms(ShaderInstance shader) {
        for (int index = 0; index < foliageInteractorCount; index++) {
            int offset = index * 4;
            shader.safeGetUniform("FoliageInteractor" + index).set(
                    foliageInteractors[offset],
                    foliageInteractors[offset + 1],
                    foliageInteractors[offset + 2],
                    foliageInteractors[offset + 3]
            );
            shader.safeGetUniform("FoliageInteractorMotion" + index).set(
                    foliageInteractorMotions[offset],
                    foliageInteractorMotions[offset + 1],
                    foliageInteractorMotions[offset + 2],
                    foliageInteractorMotions[offset + 3]
            );
        }
    }

    public static float windSwayStrength() {
        return shouldUseCustomFoliageShaders() && ClientConfig.ENABLE_WIND_FOLIAGE_SWAY.getAsBoolean()
                ? Math.max((float) ClientConfig.WIND_FOLIAGE_SWAY_STRENGTH.getAsDouble(), weatherDrivenSwayStrength())
                : 0.0F;
    }

    public static float plantWindSwayStrength() {
        return ClientConfig.ENABLE_WIND_PLANT_SWAY.getAsBoolean() ? windSwayStrength() : 0.0F;
    }

    public static float leafWindSwayStrength() {
        return ClientConfig.ENABLE_WIND_LEAF_SWAY.getAsBoolean() ? windSwayStrength() * LEAF_WIND_SWAY_SCALE : 0.0F;
    }

    public static float lanternWindSwayStrength() {
        return shouldUseCustomFoliageShaders() && ClientConfig.ENABLE_WIND_LANTERN_SWAY.getAsBoolean()
                ? (float) ClientConfig.WIND_LANTERN_SWAY_STRENGTH.getAsDouble()
                : 0.0F;
    }

    public static float windSheenStrength() {
        return shouldUseCustomFoliageShaders() && ClientConfig.ENABLE_WIND_SHEEN.getAsBoolean()
                ? Math.max((float) ClientConfig.WIND_SHEEN_STRENGTH.getAsDouble(), weatherDrivenSheenStrength())
                : 0.0F;
    }

    public static float plantWindSheenStrength() {
        return ClientConfig.ENABLE_WIND_PLANT_SWAY.getAsBoolean() ? windSheenStrength() : 0.0F;
    }

    public static float leafWindSheenStrength() {
        return 0.0F;
    }

    public static float foliageColorVariationStrength() {
        return shouldUseCustomFoliageShaders() && ClientConfig.ENABLE_FOLIAGE_COLOR_VARIATION.getAsBoolean()
                ? (float) ClientConfig.FOLIAGE_COLOR_VARIATION_STRENGTH.getAsDouble()
                : 0.0F;
    }

    public static float foliageAnimationStepRate() {
        return ClientConfig.FOLIAGE_ANIMATION_MODE.get() == ClientConfig.FoliageAnimationMode.STEPPED
                ? (float) ClientConfig.FOLIAGE_STEPPED_RATE.getAsDouble()
                : 0.0F;
    }

    private static float weatherDrivenSheenStrength() {
        return currentWeatherValue(
                ClientConfig.CLEAR_WEATHER_SHEEN_STRENGTH.getAsDouble(),
                ClientConfig.RAIN_WEATHER_SHEEN_STRENGTH.getAsDouble(),
                ClientConfig.THUNDER_WEATHER_SHEEN_STRENGTH.getAsDouble()
        );
    }

    private static float weatherDrivenSwayStrength() {
        return currentWeatherValue(
                ClientConfig.CLEAR_WEATHER_SWAY_STRENGTH.getAsDouble(),
                ClientConfig.RAIN_WEATHER_SWAY_STRENGTH.getAsDouble(),
                ClientConfig.THUNDER_WEATHER_SWAY_STRENGTH.getAsDouble()
        );
    }

    private static float currentWeatherValue(double clearValue, double rainValue, double thunderValue) {
        GlobalWindState wind = DynamicWindManager.currentState();
        float rainyValue = Mth.lerp(wind.rainLevel(), (float) clearValue, (float) rainValue);
        return Mth.lerp(wind.thunderLevel(), rainyValue, (float) thunderValue);
    }

    private static boolean isExternalShaderPackActive() {
        long now = Util.getMillis();
        if (lastShaderPackStateCheckMillis != Long.MIN_VALUE
                && now - lastShaderPackStateCheckMillis < SHADER_PACK_STATE_CACHE_MILLIS) {
            return externalShaderPackActive;
        }

        lastShaderPackStateCheckMillis = now;
        externalShaderPackActive = queryExternalShaderPackActive();
        return externalShaderPackActive;
    }

    private static boolean queryExternalShaderPackActive() {
        ensureIrisApiLookup();
        if (irisGetInstanceMethod == null || irisShaderPackInUseMethod == null) {
            return isKnownShaderRendererLoaded();
        }

        try {
            Object irisApi = irisGetInstanceMethod.invoke(null);
            return Boolean.TRUE.equals(irisShaderPackInUseMethod.invoke(irisApi));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return isKnownShaderRendererLoaded();
        }
    }

    private static synchronized void ensureIrisApiLookup() {
        if (irisApiLookupAttempted) {
            return;
        }

        irisApiLookupAttempted = true;
        try {
            Class<?> irisApiClass = Class.forName(IRIS_API_CLASS_NAME);
            irisGetInstanceMethod = irisApiClass.getMethod("getInstance");
            irisShaderPackInUseMethod = irisApiClass.getMethod("isShaderPackInUse");
        } catch (ReflectiveOperationException | LinkageError exception) {
            irisGetInstanceMethod = null;
            irisShaderPackInUseMethod = null;
        }
    }

    private static boolean isKnownShaderRendererLoaded() {
        for (String modId : SHADER_RENDERER_MOD_IDS) {
            if (isModLoaded(modId)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isIncompatibleRendererLoaded() {
        for (String modId : INCOMPATIBLE_RENDERER_MOD_IDS) {
            if (isModLoaded(modId)) {
                if (!incompatibleRendererWarningLogged) {
                    incompatibleRendererWarningLogged = true;
                    WhereWindsBlow.LOGGER.warn(
                            "Custom foliage shader rendering is disabled because incompatible renderer mod '{}' is loaded.",
                            modId
                    );
                }
                return true;
            }
        }

        return false;
    }

    private static boolean isModLoaded(String modId) {
        try {
            LoadingModList loadingModList = LoadingModList.get();
            return loadingModList != null && loadingModList.getModFileById(modId) != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @FunctionalInterface
    public interface FoliageInteractorWriter {
        void write(float[] interactors, float[] motions, int index);
    }
}
