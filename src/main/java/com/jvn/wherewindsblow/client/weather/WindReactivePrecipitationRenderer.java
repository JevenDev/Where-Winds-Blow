package com.jvn.wherewindsblow.client.weather;

import com.jvn.toucanlib.client.ToucanEasing;
import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.WindSample;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;

public final class WindReactivePrecipitationRenderer {
    private static final ResourceLocation RAIN_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/rain.png");
    private static final float NORMAL_RAIN_DENSITY = 0.82F;
    private static final float THUNDER_EXTRA_RAIN_DENSITY = 0.42F;
    private static final float RAIN_DENSITY_FADE_WIDTH = 0.10F;
    private static final Biome.Precipitation[] PRECIPITATION_RENDER_ORDER = {
            Biome.Precipitation.RAIN
    };
    private static double rainScrollTime;
    private static float lastRainAnimationTime = Float.NaN;

    private WindReactivePrecipitationRenderer() {
    }

    public static void render(
            ClientLevel level,
            int ticks,
            float[] rainSizeX,
            float[] rainSizeZ,
            LightTexture lightTexture,
            float partialTick,
            double camX,
            double camY,
            double camZ
    ) {
        float animationTime = ticks + partialTick;
        WeatherLevels weather = smoothWeatherLevels(
                level,
                animationTime,
                level.getRainLevel(partialTick),
                level.getThunderLevel(partialTick)
        );
        float rainLevel = weather.rain();
        if (rainLevel <= 0.0F) {
            return;
        }

        float thunder = weather.thunder();
        BlockPos cameraPos = BlockPos.containing(camX, camY, camZ);
        WindSample cameraWind = DynamicWindManager.sampleWind(level, cameraPos);
        float rainAngleVariation = Mth.lerp(
                thunder,
                (float) ClientConfig.RAIN_ANGLE_VARIATION.getAsDouble(),
                (float) ClientConfig.THUNDER_RAIN_ANGLE_VARIATION.getAsDouble()
        );
        boolean dynamicRainSqualls = ClientConfig.ENABLE_DYNAMIC_RAIN_SQUALLS.getAsBoolean();
        float squallStrength = (float) ClientConfig.RAIN_SQUALL_STRENGTH.getAsDouble();
        float lullAmount = dynamicRainSqualls ? DynamicWindManager.currentState().lullAmount() : 0.0F;
        int centerX = Mth.floor(camX);
        int centerY = Mth.floor(camY);
        int centerZ = Mth.floor(camZ);
        int radius = Minecraft.useFancyGraphics() ? 10 : 5;

        Tesselator tesselator = Tesselator.getInstance();
        PrecipitationResponse cameraWeatherResponse = precipitationResponse(
                cameraWind, lullAmount, dynamicRainSqualls, squallStrength
        );
        double precipitationAnimationTime = advanceRainAnimationTime(
                animationTime,
                cameraWeatherResponse.speedMultiplier() * (1.0F + thunder * 0.8F)
        );
        BlizzardRenderer.render(
                level, rainSizeX, rainSizeZ, lightTexture,
                rainLevel, thunder, precipitationAnimationTime, camX, camY, camZ
        );
        DesertStormRenderer.render(
                level, rainLevel, thunder, animationTime, camX, camY, camZ
        );
        SnowfallRenderer.render(
                level, lightTexture, rainLevel, thunder,
                animationTime, camX, camY, camZ
        );

        // The dedicated weather passes restore render state, so configure the rain stream state
        // afterward rather than relying on state set before them.
        lightTexture.turnOnLightLayer();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getParticleShader);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (Biome.Precipitation renderPass : PRECIPITATION_RENDER_ORDER) {
            BufferBuilder buffer = null;
            int activeType = -1;
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    int sizeIndex = (z - centerZ + 16) * 32 + x - centerX + 16;
                    double widthX = rainSizeX[sizeIndex] * 0.5D;
                    double widthZ = rainSizeZ[sizeIndex] * 0.5D;
                    pos.set(x, camY, z);
                    Holder<Biome> biomeHolder = level.getBiome(pos);
                    if (ClientConfig.ENABLE_DESERT_STORM_EFFECTS.getAsBoolean()
                            && (biomeHolder.is(Tags.Biomes.IS_DESERT)
                                    || biomeHolder.is(Tags.Biomes.IS_BADLANDS))) {
                        continue;
                    }
                    Biome biome = biomeHolder.value();
                    if (!biome.hasPrecipitation()) {
                        continue;
                    }

                    int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    int bottomY = Math.max(centerY - radius, surfaceY);
                    int topY = Math.max(centerY + radius, surfaceY);
                    if (bottomY == topY) {
                        continue;
                    }

                    pos.set(x, bottomY, z);
                    Biome.Precipitation precipitation = biome.getPrecipitationAt(pos);
                    if (precipitation != renderPass) {
                        continue;
                    }
                    long hash = precipitationHash(x, z);
                    if (precipitation == Biome.Precipitation.RAIN) {
                        if (!ClientConfig.ENABLE_RAIN_EFFECTS.getAsBoolean()) {
                            continue;
                        }
                        WindSample wind = DynamicWindManager.sampleWind(level, pos);
                        float windStrength = Mth.clamp(wind.strength(), 0.0F, 3.0F);
                        PrecipitationResponse weatherResponse = precipitationResponse(
                                wind, lullAmount, dynamicRainSqualls, squallStrength
                        );
                        float primaryDensity = Mth.clamp(
                                Mth.lerp(thunder, NORMAL_RAIN_DENSITY, 1.0F) + weatherResponse.densityDelta(),
                                0.45F,
                                1.0F
                        );
                        float primaryVisibility = rainStreamVisibility(hash, primaryDensity);
                        float extraDensity = Mth.clamp(
                                thunder * THUNDER_EXTRA_RAIN_DENSITY + weatherResponse.extraDensity(),
                                0.0F,
                                0.85F
                        );
                        float secondaryVisibility = rainStreamVisibility(hash ^ 0x6A09E667F3BCC909L, extraDensity);
                        if (primaryVisibility <= 0.01F && secondaryVisibility <= 0.01F) {
                            continue;
                        }
                        if (activeType != 0) {
                            if (activeType >= 0) {
                                BufferUploader.drawWithShader(buffer.buildOrThrow());
                            }
                            activeType = 0;
                            RenderSystem.setShaderTexture(0, RAIN_LOCATION);
                            buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                        }

                        float scrollSpeed = 2.8F
                                + unitFloat(hash ^ 0x243F6A8885A308D3L) * 1.8F;
                        float scroll = (float) (-((precipitationAnimationTime + (hash & 255L)) / 32.0D * scrollSpeed) % 32.0D);
                        float distance = horizontalDistance(x, z, camX, camZ) / radius;
                        float alpha = ((1.0F - distance * distance) * 0.5F + 0.5F) * rainLevel;
                        alpha *= Mth.lerp(thunder, 0.68F, 0.96F);
                        alpha *= Mth.lerp(unitFloat(hash ^ 0xA4093822299F31D0L), 0.86F, 1.08F);
                        alpha *= weatherResponse.opacityMultiplier();
                        alpha = Mth.clamp(alpha, 0.0F, 1.0F);
                        pos.set(x, Math.max(surfaceY, centerY), z);
                        int light = LevelRenderer.getLightColor(level, pos);

                        if (primaryVisibility > 0.01F) {
                            float widthScale = Mth.lerp(unitFloat(hash ^ 0x082EFA98EC4E6C89L), 0.78F, 1.22F);
                            RainDrift primaryDrift = ClientConfig.ENABLE_SLANTED_RAIN.getAsBoolean()
                                    ? rainDrift(
                                            wind, windStrength, thunder,
                                            rainAngleVariation * weatherResponse.angleVariationMultiplier(),
                                            topY - bottomY, hash, weatherResponse.tiltMultiplier()
                                    )
                                    : RainDrift.NONE;
                            addRainQuad(buffer, x, z, bottomY, topY, camX, camY, camZ,
                                    widthX * widthScale, widthZ * widthScale,
                                    primaryDrift.x(), primaryDrift.z(), scroll, alpha * primaryVisibility, light, 0.0F);
                        }

                        if (secondaryVisibility > 0.01F) {
                            RainDrift secondaryDrift = ClientConfig.ENABLE_SLANTED_RAIN.getAsBoolean()
                                    ? rainDrift(
                                            wind, windStrength, thunder,
                                            rainAngleVariation * weatherResponse.angleVariationMultiplier(),
                                            topY - bottomY, hash ^ 0x452821E638D01377L,
                                            weatherResponse.tiltMultiplier() * 1.18F
                                    )
                                    : RainDrift.NONE;
                            float secondaryWidth = Mth.lerp(unitFloat(hash ^ 0xBE5466CF34E90C6CL), 0.72F, 1.08F);
                            addRainQuad(buffer, x, z, bottomY, topY, camX, camY, camZ,
                                    widthX * secondaryWidth, widthZ * secondaryWidth,
                                    secondaryDrift.x(), secondaryDrift.z(), (scroll + 11.0F) % 32.0F,
                                    alpha * 0.78F * secondaryVisibility, light, 0.28F);
                        }
                    }
                }
            }

            if (activeType >= 0) {
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            }
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        lightTexture.turnOffLightLayer();
    }

    private static void addRainQuad(BufferBuilder buffer, int x, int z, int bottomY, int topY,
                                    double camX, double camY, double camZ, double widthX, double widthZ,
                                    float driftX, float driftZ, float scroll, float alpha, int light, float offset) {
        float leftX = (float) (x - camX - widthX + 0.5D + offset);
        float rightX = (float) (x - camX + widthX + 0.5D + offset);
        float leftZ = (float) (z - camZ - widthZ + 0.5D + offset);
        float rightZ = (float) (z - camZ + widthZ + 0.5D + offset);
        float top = (float) (topY - camY);
        float bottom = (float) (bottomY - camY);
        buffer.addVertex(leftX + driftX, top, leftZ + driftZ).setUv(0.0F, bottomY * 0.25F + scroll).setColor(0.82F, 0.86F, 0.72F, alpha).setLight(light);
        buffer.addVertex(rightX + driftX, top, rightZ + driftZ).setUv(1.0F, bottomY * 0.25F + scroll).setColor(0.82F, 0.86F, 0.72F, alpha).setLight(light);
        buffer.addVertex(rightX, bottom, rightZ).setUv(1.0F, topY * 0.25F + scroll).setColor(0.82F, 0.86F, 0.72F, alpha).setLight(light);
        buffer.addVertex(leftX, bottom, leftZ).setUv(0.0F, topY * 0.25F + scroll).setColor(0.82F, 0.86F, 0.72F, alpha).setLight(light);
    }

    private static float horizontalDistance(int x, int z, double camX, double camZ) {
        double dx = x + 0.5D - camX;
        double dz = z + 0.5D - camZ;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static long precipitationHash(int x, int z) {
        return x * (long) x * 3121L + x * 45238971L ^ z * (long) z * 418711L + z * 13761L;
    }

    private static RainDrift rainDrift(
            WindSample wind,
            float windStrength,
            float thunder,
            float angleVariationDegrees,
            int fallDistance,
            long hash,
            float slopeMultiplier
    ) {
        float angleJitter = signedUnitFloat(hash ^ 0xC0AC29B7C97C50DDL)
                * angleVariationDegrees * Mth.DEG_TO_RAD;
        float directionCos = Mth.cos(angleJitter);
        float directionSin = Mth.sin(angleJitter);
        float directionX = wind.directionX() * directionCos - wind.directionZ() * directionSin;
        float directionZ = wind.directionX() * directionSin + wind.directionZ() * directionCos;
        float slopeVariation = Mth.lerp(unitFloat(hash ^ 0x3F84D5B5B5470917L), 0.82F, 1.20F);
        float slope = (0.045F + windStrength * 0.075F + thunder * 0.18F)
                * slopeVariation * slopeMultiplier;
        float maxDrift = Mth.lerp(thunder, 3.8F, 6.6F) * Mth.sqrt(Math.max(slopeMultiplier, 0.0F));
        float drift = Math.min(fallDistance * slope, maxDrift);
        return new RainDrift(-directionX * drift, -directionZ * drift);
    }

    private static double advanceRainAnimationTime(float animationTime, float speedMultiplier) {
        if (!Float.isFinite(lastRainAnimationTime)) {
            rainScrollTime = animationTime;
        } else {
            float delta = animationTime - lastRainAnimationTime;
            if (delta < 0.0F || delta > 5.0F) {
                rainScrollTime = animationTime;
            } else if (delta > 0.0F) {
                rainScrollTime += delta * Math.max(speedMultiplier, 0.1F);
            }
        }
        lastRainAnimationTime = animationTime;
        return rainScrollTime;
    }

    private static WeatherLevels smoothWeatherLevels(
            ClientLevel level,
            float animationTime,
            float targetRain,
            float targetThunder
    ) {
        return WeatherTransitionState.sample(level, animationTime, targetRain, targetThunder);
    }

    private static PrecipitationResponse precipitationResponse(
            WindSample wind,
            float lullAmount,
            boolean enabled,
            float configuredStrength
    ) {
        if (!enabled || configuredStrength <= 0.0F) {
            return PrecipitationResponse.NONE;
        }

        float squall = ToucanEasing.smoothstep(Mth.clamp(wind.gustStrength() * 2.4F, 0.0F, 1.0F)) * configuredStrength;
        float lull = ToucanEasing.smoothstep(lullAmount) * configuredStrength;
        return new PrecipitationResponse(
                squall * 0.14F - lull * 0.20F,
                squall * 0.22F - lull * 0.16F,
                Math.max(0.55F, 1.0F + squall * 0.55F - lull * 0.28F),
                Math.max(0.55F, 1.0F + squall * 0.18F - lull * 0.30F),
                Math.max(0.55F, 1.0F + squall * 0.65F - lull * 0.22F),
                Math.max(0.65F, 1.0F + squall * 0.30F - lull * 0.15F)
        );
    }

    private static float rainStreamVisibility(long hash, float density) {
        if (density <= 0.0F) {
            return 0.0F;
        }
        if (density >= 1.0F) {
            return 1.0F;
        }
        float transition = (density - unitFloat(hash)) / RAIN_DENSITY_FADE_WIDTH + 0.5F;
        return ToucanEasing.smoothstep(transition);
    }

    private static float unitFloat(long hash) {
        long mixed = hash ^ hash >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        return (mixed >>> 40) / (float) (1 << 24);
    }

    private static float signedUnitFloat(long hash) {
        return unitFloat(hash) * 2.0F - 1.0F;
    }

    private record RainDrift(float x, float z) {
        private static final RainDrift NONE = new RainDrift(0.0F, 0.0F);
    }

    private record WeatherLevels(float rain, float thunder) {
    }

    private static final class WeatherTransitionState {
        private static ClientLevel activeLevel;
        private static float rain;
        private static float thunder;
        private static float lastAnimationTime = Float.NaN;

        private WeatherTransitionState() {
        }

        private static WeatherLevels sample(
                ClientLevel level,
                float animationTime,
                float targetRain,
                float targetThunder
        ) {
            if (activeLevel != level || !Float.isFinite(lastAnimationTime)) {
                activeLevel = level;
                rain = targetRain;
                thunder = targetThunder;
                lastAnimationTime = animationTime;
                return new WeatherLevels(rain, thunder);
            }

            float delta = animationTime - lastAnimationTime;
            lastAnimationTime = animationTime;
            if (delta < 0.0F || delta > 5.0F) {
                rain = targetRain;
                thunder = targetThunder;
                return new WeatherLevels(rain, thunder);
            }

            // Vanilla weather levels usually move gradually, but servers and commands can replace
            // them in a single update. A short render-side ease keeps all precipitation variants on
            // the same continuous clear/rain/thunder curve without making weather feel delayed.
            float blend = 1.0F - (float) Math.exp(-delta * 0.12F);
            rain = Mth.lerp(blend, rain, targetRain);
            thunder = Mth.lerp(blend, thunder, targetThunder);
            if (Math.abs(rain - targetRain) < 0.0001F) {
                rain = targetRain;
            }
            if (Math.abs(thunder - targetThunder) < 0.0001F) {
                thunder = targetThunder;
            }
            return new WeatherLevels(rain, thunder);
        }
    }

    private record PrecipitationResponse(
            float densityDelta,
            float extraDensity,
            float speedMultiplier,
            float opacityMultiplier,
            float tiltMultiplier,
            float angleVariationMultiplier
    ) {
        private static final PrecipitationResponse NONE = new PrecipitationResponse(
                0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F
        );
    }
}
