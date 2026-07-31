package com.jvn.wherewindsblow.client.weather;

import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.WindSample;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Applies a snow-biome visibility response that remains present, but softer, inside shelter. */
public final class BlizzardWeatherEffects {
    private static final float WHITEOUT_START_ENERGY = 0.24F;
    private static final float WHITEOUT_FULL_ENERGY = 1.05F;
    private static final float FULL_WHITEOUT_FOG_END = 13.0F;
    private static final float FULL_WHITEOUT_FOG_START = 1.5F;
    private static final float ENCLOSED_WHITEOUT_SCALE = 0.88F;
    private static final float PARTIAL_SHELTER_WHITEOUT_SCALE = 0.95F;

    private BlizzardWeatherEffects() {
    }

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) {
            return;
        }

        float whiteout = whiteoutAtCamera(event.getCamera(), (float) event.getPartialTick());
        if (whiteout <= 0.001F) {
            return;
        }
        if (event.getFarPlaneDistance() <= FULL_WHITEOUT_FOG_END) {
            return;
        }

        // Geometric interpolation keeps shelter from becoming a visibility exploit. A small
        // reduction in whiteout strength moves the fog wall outward modestly instead of exposing
        // most of the configured render distance through a window.
        float originalFarDistance = event.getFarPlaneDistance();
        float farDistance = (float) (originalFarDistance * Math.pow(
                FULL_WHITEOUT_FOG_END / originalFarDistance,
                whiteout
        ));
        float nearDistance = Mth.lerp(
                whiteout,
                Math.min(event.getNearPlaneDistance(), farDistance * 0.72F),
                FULL_WHITEOUT_FOG_START
        );
        event.setNearPlaneDistance(Math.min(nearDistance, farDistance - 0.5F));
        event.setFarPlaneDistance(farDistance);
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }

        float whiteout = whiteoutAtCamera(event.getCamera(), (float) event.getPartialTick());
        if (whiteout <= 0.001F) {
            return;
        }

        float colorBlend = whiteout * 0.72F;
        event.setRed(Mth.lerp(colorBlend, event.getRed(), 0.78F));
        event.setGreen(Mth.lerp(colorBlend, event.getGreen(), 0.84F));
        event.setBlue(Mth.lerp(colorBlend, event.getBlue(), 0.90F));
    }

    static float blizzardEnergy(
            WindSample wind,
            float rainLevel,
            float thunder,
            float configuredIntensity
    ) {
        float windStrength = Mth.clamp(wind.strength(), 0.0F, 3.0F);
        float gust = smoothFade(Mth.clamp(wind.gustStrength() * 2.2F, 0.0F, 1.0F));
        return Mth.clamp(
                rainLevel * configuredIntensity * (
                        0.08F + windStrength * 0.12F + gust * 0.48F + thunder * 0.72F
                ),
                0.0F,
                1.35F
        );
    }

    private static float whiteoutAtCamera(Camera camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        float configuredIntensity = (float) ClientConfig.BLIZZARD_INTENSITY.getAsDouble();
        if (level == null
                || !ClientConfig.ENABLE_SNOW_EFFECTS.getAsBoolean()
                || !ClientConfig.ENABLE_WIND_DRIVEN_SNOW.getAsBoolean()
                || !ClientConfig.ENABLE_BLIZZARD_EFFECTS.getAsBoolean()
                || configuredIntensity <= 0.0F) {
            return 0.0F;
        }

        BlockPos cameraPos = camera.getBlockPosition();
        Biome biome = level.getBiome(cameraPos).value();
        if (!biome.hasPrecipitation()
                || biome.getPrecipitationAt(cameraPos) != Biome.Precipitation.SNOW) {
            return 0.0F;
        }

        float rainLevel = level.getRainLevel(partialTick);
        if (rainLevel <= 0.0F) {
            return 0.0F;
        }
        WindSample wind = DynamicWindManager.sampleWind(level, cameraPos);
        float energy = blizzardEnergy(
                wind, rainLevel, level.getThunderLevel(partialTick), configuredIntensity
        );
        float whiteout = smoothFade((energy - WHITEOUT_START_ENERGY)
                / (WHITEOUT_FULL_ENERGY - WHITEOUT_START_ENERGY));
        if (level.canSeeSky(cameraPos)) {
            return whiteout;
        }

        float shelterScale = Mth.lerp(
                Mth.clamp(wind.exposure(), 0.0F, 1.0F),
                ENCLOSED_WHITEOUT_SCALE,
                PARTIAL_SHELTER_WHITEOUT_SCALE
        );
        return whiteout * shelterScale;
    }

    private static float smoothFade(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);
        return value * value * (3.0F - 2.0F * value);
    }
}
