package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.client.wind.WindDirection;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
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
    private static final ResourceLocation WIND_SHADER = ResourceLocation.fromNamespaceAndPath(
            WhereWindsBlow.MOD_ID,
            "rendertype_responsive_foliage_cutout"
    );
    private static final float WEATHER_SPEED_SCALE = 0.34F;
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
    private static long lastWeatherUpdateMillis;
    private static float smoothedWeatherWindPower;
    private static float windTimeSeconds;
    private static int foliageInteractorCount;
    private static final float[] foliageInteractors = new float[MAX_FOLIAGE_INTERACTORS * 4];
    private static final float[] foliageInteractorStrengths = new float[MAX_FOLIAGE_INTERACTORS];

    private ResponsiveFoliageShaders() {
    }

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), WIND_SHADER, DefaultVertexFormat.BLOCK), loadedShader -> shader = loadedShader);
        } catch (IOException | RuntimeException exception) {
            disableCustomFoliageShader("Failed to load responsive foliage wind shader.", exception);
        }
    }

    @Nullable
    public static ShaderInstance getShader() {
        return shouldUseCustomFoliageShaders() ? shader : null;
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
        Arrays.fill(foliageInteractorStrengths, 0.0F);
        for (int index = 0; index < count; index++) {
            writer.write(foliageInteractors, foliageInteractorStrengths, index);
        }

        foliageInteractorCount = count;
    }

    public static void clearFoliageInteractors() {
        if (foliageInteractorCount == 0) {
            return;
        }

        foliageInteractorCount = 0;
        Arrays.fill(foliageInteractors, 0.0F);
        Arrays.fill(foliageInteractorStrengths, 0.0F);
    }

    public static int foliageInteractorCount() {
        return foliageInteractorCount;
    }

    public static float[] foliageInteractors() {
        return foliageInteractors;
    }

    public static float[] foliageInteractorStrengths() {
        return foliageInteractorStrengths;
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
        updateWeatherWindState();
        shader.safeGetUniform("WindTime").set(windTimeSeconds);
        shader.safeGetUniform("WeatherWindPower").set(smoothedWeatherWindPower);
        shader.safeGetUniform("PlantWindSwayStrength").set(plantWindSwayStrength());
        shader.safeGetUniform("LeafWindSwayStrength").set(leafWindSwayStrength());
        shader.safeGetUniform("LanternWindSwayStrength").set(lanternWindSwayStrength());
        shader.safeGetUniform("PlantWindSheenStrength").set(plantWindSheenStrength());
        shader.safeGetUniform("LeafWindSheenStrength").set(leafWindSheenStrength());
        shader.safeGetUniform("WindDirection").set(WindDirection.x(), WindDirection.z());
        Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        shader.safeGetUniform("CameraPosition").set((float) cameraPosition.x, (float) cameraPosition.y, (float) cameraPosition.z);
        shader.safeGetUniform("FoliageInteractorCount").set(foliageInteractorCount);
        uploadFoliageInteractorUniforms(shader);
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
        }

        for (int group = 0; group < interactorStrengthGroupCount(foliageInteractorCount); group++) {
            int offset = group * 4;
            shader.safeGetUniform("FoliageInteractorStrengths" + group).set(
                    foliageInteractorStrengths[offset],
                    foliageInteractorStrengths[offset + 1],
                    foliageInteractorStrengths[offset + 2],
                    foliageInteractorStrengths[offset + 3]
            );
        }
    }

    public static int interactorStrengthGroupCount(int interactorCount) {
        return (Mth.clamp(interactorCount, 0, MAX_FOLIAGE_INTERACTORS) + 3) / 4;
    }

    public static float windTime() {
        updateWeatherWindState();
        return windTimeSeconds;
    }

    public static float weatherWindPower() {
        updateWeatherWindState();
        return smoothedWeatherWindPower;
    }

    public static void onClientPauseChange(ClientPauseChangeEvent.Post event) {
        lastWeatherUpdateMillis = Util.getMillis();
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

    private static void updateWeatherWindState() {
        long now = Util.getMillis();
        if (lastWeatherUpdateMillis == 0L) {
            lastWeatherUpdateMillis = now;
            windTimeSeconds = now * 0.001F;
            smoothedWeatherWindPower = targetWeatherWindPower();
            return;
        }

        if (Minecraft.getInstance().isPaused()) {
            lastWeatherUpdateMillis = now;
            return;
        }

        float deltaSeconds = Mth.clamp((now - lastWeatherUpdateMillis) * 0.001F, 0.0F, 0.25F);
        if (deltaSeconds <= 0.0F) {
            return;
        }

        lastWeatherUpdateMillis = now;
        float target = targetWeatherWindPower();
        float response = target > smoothedWeatherWindPower ? 1.7F : 1.05F;
        float blend = 1.0F - (float) Math.exp(-deltaSeconds * response);
        smoothedWeatherWindPower += (target - smoothedWeatherWindPower) * blend;
        windTimeSeconds += deltaSeconds * (1.0F + smoothedWeatherWindPower * WEATHER_SPEED_SCALE);
    }

    private static float targetWeatherWindPower() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0.0F;
        }

        return currentWeatherValue(
                ClientConfig.CLEAR_WEATHER_WIND_POWER.getAsDouble(),
                ClientConfig.RAIN_WEATHER_WIND_POWER.getAsDouble(),
                ClientConfig.THUNDER_WEATHER_WIND_POWER.getAsDouble()
        );
    }

    private static float weatherDrivenSheenStrength() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0.0F;
        }

        return currentWeatherValue(
                ClientConfig.CLEAR_WEATHER_SHEEN_STRENGTH.getAsDouble(),
                ClientConfig.RAIN_WEATHER_SHEEN_STRENGTH.getAsDouble(),
                ClientConfig.THUNDER_WEATHER_SHEEN_STRENGTH.getAsDouble()
        );
    }

    private static float weatherDrivenSwayStrength() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0.0F;
        }

        return currentWeatherValue(
                ClientConfig.CLEAR_WEATHER_SWAY_STRENGTH.getAsDouble(),
                ClientConfig.RAIN_WEATHER_SWAY_STRENGTH.getAsDouble(),
                ClientConfig.THUNDER_WEATHER_SWAY_STRENGTH.getAsDouble()
        );
    }

    private static float currentWeatherValue(double clearValue, double rainValue, double thunderValue) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0.0F;
        }

        float rain = minecraft.level.getRainLevel(1.0F);
        float thunder = minecraft.level.getThunderLevel(1.0F);
        float rainyValue = Mth.lerp(rain, (float) clearValue, (float) rainValue);
        return Mth.lerp(thunder, rainyValue, (float) thunderValue);
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
        void write(float[] interactors, float[] strengths, int index);
    }
}
