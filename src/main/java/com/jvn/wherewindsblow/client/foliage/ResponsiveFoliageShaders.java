package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import java.lang.reflect.Method;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

public final class ResponsiveFoliageShaders {
    private static final String IRIS_API_CLASS_NAME = "net.irisshaders.iris.api.v0.IrisApi";
    private static final long SHADER_PACK_STATE_CACHE_MILLIS = 250L;
    private static final float RAIN_WIND_SHEEN_MIN = 1.5F;
    private static final float THUNDER_WIND_SHEEN_MIN = 2.0F;
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
    private static long lastWeatherUpdateMillis;
    private static float smoothedWeatherWindPower;
    private static float windTimeSeconds;

    private ResponsiveFoliageShaders() {
    }

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), WIND_SHADER, DefaultVertexFormat.BLOCK), loadedShader -> shader = loadedShader);
        } catch (IOException exception) {
            WhereWindsBlow.LOGGER.error("Failed to load responsive foliage wind shader.", exception);
        }
    }

    @Nullable
    public static ShaderInstance getShader() {
        return shouldUseCustomFoliageShaders() ? shader : null;
    }

    public static boolean shouldUseCustomFoliageShaders() {
        return ClientConfig.FORCE_WWB_WIND_WITH_SHADER_PACKS.getAsBoolean() || !isExternalShaderPackActive();
    }

    public static void uploadWeatherUniforms(ShaderInstance shader) {
        if (!shouldUseCustomFoliageShaders()) {
            return;
        }

        updateWeatherWindState();
        shader.safeGetUniform("WindTime").set(windTimeSeconds);
        shader.safeGetUniform("WeatherWindPower").set(smoothedWeatherWindPower);
        shader.safeGetUniform("WindSwayStrength").set(windSwayStrength());
        shader.safeGetUniform("WindSheenStrength").set(windSheenStrength());
    }

    public static float windTime() {
        updateWeatherWindState();
        return windTimeSeconds;
    }

    public static float weatherWindPower() {
        updateWeatherWindState();
        return smoothedWeatherWindPower;
    }

    public static float windSwayStrength() {
        return shouldUseCustomFoliageShaders() && ClientConfig.ENABLE_WIND_FOLIAGE_SWAY.getAsBoolean()
                ? Math.max((float) ClientConfig.WIND_FOLIAGE_SWAY_STRENGTH.getAsDouble(), weatherDrivenSwayStrength())
                : 0.0F;
    }

    public static float windSheenStrength() {
        return shouldUseCustomFoliageShaders() && ClientConfig.ENABLE_WIND_SHEEN.getAsBoolean()
                ? Math.max((float) ClientConfig.WIND_SHEEN_STRENGTH.getAsDouble(), weatherDrivenSheenStrength())
                : 0.0F;
    }

    private static void updateWeatherWindState() {
        long now = Util.getMillis();
        if (lastWeatherUpdateMillis == 0L) {
            lastWeatherUpdateMillis = now;
            windTimeSeconds = now * 0.001F;
            smoothedWeatherWindPower = targetWeatherWindPower();
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

        float rain = minecraft.level.getRainLevel(1.0F);
        float thunder = minecraft.level.getThunderLevel(1.0F);
        return Math.min(rain * 0.75F + thunder * 1.25F, 2.0F);
    }

    private static float weatherDrivenSheenStrength() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0.0F;
        }

        float thunder = minecraft.level.getThunderLevel(1.0F);
        if (thunder > 0.01F) {
            return THUNDER_WIND_SHEEN_MIN;
        }

        float rain = minecraft.level.getRainLevel(1.0F);
        return rain > 0.01F ? RAIN_WIND_SHEEN_MIN : 0.0F;
    }

    private static float weatherDrivenSwayStrength() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0.0F;
        }

        float thunder = minecraft.level.getThunderLevel(1.0F);
        if (thunder > 0.01F) {
            return THUNDER_WIND_SHEEN_MIN;
        }

        float rain = minecraft.level.getRainLevel(1.0F);
        return rain > 0.01F ? RAIN_WIND_SHEEN_MIN : 0.0F;
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
            return false;
        }

        try {
            Object irisApi = irisGetInstanceMethod.invoke(null);
            return Boolean.TRUE.equals(irisShaderPackInUseMethod.invoke(irisApi));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
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
}
