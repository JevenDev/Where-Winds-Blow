package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

public final class ResponsiveFoliageShaders {
    private static final ResourceLocation WIND_SHADER = ResourceLocation.fromNamespaceAndPath(
            WhereWindsBlow.MOD_ID,
            "rendertype_responsive_foliage_cutout"
    );
    private static final float WEATHER_SPEED_SCALE = 0.34F;
    @Nullable
    private static ShaderInstance shader;
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
        return shader;
    }

    public static void uploadWeatherUniforms(ShaderInstance shader) {
        updateWeatherWindState();
        shader.safeGetUniform("WindTime").set(windTimeSeconds);
        shader.safeGetUniform("WeatherWindPower").set(smoothedWeatherWindPower);
    }

    public static float windTime() {
        updateWeatherWindState();
        return windTimeSeconds;
    }

    public static float weatherWindPower() {
        updateWeatherWindState();
        return smoothedWeatherWindPower;
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
}
