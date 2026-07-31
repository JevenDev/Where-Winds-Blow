package com.jvn.wherewindsblow.client.weather;

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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Adds low spindrift to exposed snow-covered surfaces during wind-driven snowfall. */
public final class BlizzardRenderer {
    private static final ResourceLocation BLOWING_SNOW_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "where_winds_blow", "textures/environment/desert_dust.png"
    );
    private static final float DENSITY_FADE_WIDTH = 0.10F;

    private BlizzardRenderer() {
    }

    public static void render(
            ClientLevel level,
            float[] rainSizeX,
            float[] rainSizeZ,
            LightTexture lightTexture,
            float rainLevel,
            float thunder,
            double animationTime,
            double camX,
            double camY,
            double camZ
    ) {
        float configuredIntensity = (float) ClientConfig.BLIZZARD_INTENSITY.getAsDouble();
        if (!ClientConfig.ENABLE_SNOW_EFFECTS.getAsBoolean()
                || !ClientConfig.ENABLE_WIND_DRIVEN_SNOW.getAsBoolean()
                || !ClientConfig.ENABLE_BLIZZARD_EFFECTS.getAsBoolean()
                || configuredIntensity <= 0.0F
                || rainLevel <= 0.0F) {
            return;
        }

        int centerX = Mth.floor(camX);
        int centerY = Mth.floor(camY);
        int centerZ = Mth.floor(camZ);
        int radius = Minecraft.useFancyGraphics() ? 14 : 8;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BufferBuilder buffer = null;

        lightTexture.turnOnLightLayer();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderTexture(0, BLOWING_SNOW_TEXTURE);

        Tesselator tesselator = Tesselator.getInstance();
        for (int z = centerZ - radius; z <= centerZ + radius; z++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                pos.set(x, Math.max(surfaceY, centerY), z);
                Holder<Biome> biome = level.getBiome(pos);
                if (!biome.value().hasPrecipitation()
                        || biome.value().getPrecipitationAt(pos) != Biome.Precipitation.SNOW) {
                    continue;
                }

                int bottomY = Math.max(centerY - radius, surfaceY);
                int topY = Math.max(centerY + radius, surfaceY);
                if (bottomY == topY) {
                    continue;
                }

                pos.set(x, surfaceY, z);
                WindSample wind = DynamicWindManager.sampleWind(level, pos);
                float windStrength = Mth.clamp(wind.strength(), 0.0F, 3.0F);
                float gust = smoothFade(Mth.clamp(wind.gustStrength() * 2.2F, 0.0F, 1.0F));
                float stormEnergy = BlizzardWeatherEffects.blizzardEnergy(
                        wind, rainLevel, thunder, configuredIntensity
                );
                long hash = precipitationHash(x, z);

                boolean snowCovered = isSnowCovered(level, x, surfaceY, z, pos);
                float driftVisibility = snowCovered
                        ? streamVisibility(
                                hash ^ 0x9FB21C651E98DF25L,
                                Mth.clamp(0.10F + stormEnergy * 0.62F, 0.0F, 0.92F)
                        )
                        : 0.0F;
                boolean snowyLedge = snowCovered && isDownwindLedge(level, x, surfaceY, z, wind);
                if (driftVisibility <= 0.01F && !snowyLedge) {
                    continue;
                }

                if (buffer == null) {
                    buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                }

                int sizeIndex = (z - centerZ + 16) * 32 + x - centerX + 16;
                double widthX = rainSizeX[sizeIndex] * 0.82D;
                double widthZ = rainSizeZ[sizeIndex] * 0.82D;
                float travelPhase = fractionalPart(
                        unitFloat(hash ^ 0xD6E8FEB86659FD93L) + (float) animationTime * (0.020F + windStrength * 0.003F)
                );
                float travelRange = 7.0F + windStrength * 2.5F + gust * 5.0F + thunder * 3.0F;
                float travel = (travelPhase - 0.5F) * travelRange;
                float flutterPhase = (float) animationTime * 0.071F + (hash & 255L);
                float crosswind = Mth.sin(flutterPhase)
                        * (0.12F + wind.turbulence() * 0.55F + thunder * 0.16F);
                float motionX = wind.directionX() * travel - wind.directionZ() * crosswind;
                float motionZ = wind.directionZ() * travel + wind.directionX() * crosswind;
                float horizontalScroll = -(float) animationTime * (0.022F + windStrength * 0.004F);
                float offsetU = unitFloat(hash ^ 0x94D049BB133111EBL);
                float offsetV = unitFloat(hash ^ 0xBF58476D1CE4E5B9L);

                pos.set(x, Math.max(surfaceY, centerY), z);
                int light = LevelRenderer.getLightColor(level, pos);
                int blockLight = light >> 16 & 65535;
                int skyLight = light & 65535;
                int snowSkyLight = (skyLight * 2 + 240) / 3;
                int snowBlockLight = (blockLight * 2 + 240) / 3;

                if (driftVisibility > 0.01F) {
                    int driftTopY = Math.min(
                            topY,
                            surfaceY + 1 + Mth.floor(stormEnergy * 1.8F + gust * 1.4F)
                    );
                    if (driftTopY > surfaceY) {
                        float driftAlpha = Mth.clamp(
                                rainLevel * (0.15F + stormEnergy * 0.34F) * driftVisibility,
                                0.0F,
                                0.62F
                        );
                        float lift = 0.14F + wind.turbulence() * 0.32F + gust * 0.34F;
                        addSnowSheet(buffer, x, z, surfaceY, driftTopY, camX, camY, camZ,
                                widthX * 1.28D, widthZ * 1.28D,
                                motionX, motionZ,
                                -wind.directionX() * lift, -wind.directionZ() * lift,
                                horizontalScroll * 1.8F, offsetU + 0.37F, offsetV + 0.51F,
                                driftAlpha, snowSkyLight, snowBlockLight, -0.34F);
                    }
                }

                if (snowyLedge) {
                    int plumeTopY = Math.min(topY, surfaceY + 2 + Mth.floor(stormEnergy * 2.2F));
                    if (plumeTopY > surfaceY) {
                        float plumeAlpha = Mth.clamp(
                                rainLevel * (0.20F + stormEnergy * 0.28F),
                                0.0F,
                                0.58F
                        );
                        addSnowSheet(buffer, x, z, surfaceY, plumeTopY, camX, camY, camZ,
                                widthX * 1.42D, widthZ * 1.42D,
                                motionX + wind.directionX() * 1.4F,
                                motionZ + wind.directionZ() * 1.4F,
                                -wind.directionX() * 0.9F, -wind.directionZ() * 0.9F,
                                horizontalScroll * 2.1F, offsetU + 0.71F, offsetV + 0.18F,
                                plumeAlpha, snowSkyLight, snowBlockLight, 0.38F);
                    }
                }
            }
        }

        if (buffer != null) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        lightTexture.turnOffLightLayer();
    }

    private static boolean isSnowCovered(
            ClientLevel level,
            int x,
            int surfaceY,
            int z,
            BlockPos.MutableBlockPos pos
    ) {
        pos.set(x, surfaceY - 1, z);
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW);
    }

    private static boolean isDownwindLedge(
            ClientLevel level,
            int x,
            int surfaceY,
            int z,
            WindSample wind
    ) {
        int stepX = Math.abs(wind.directionX()) >= 0.35F ? (wind.directionX() > 0.0F ? 1 : -1) : 0;
        int stepZ = Math.abs(wind.directionZ()) >= 0.35F ? (wind.directionZ() > 0.0F ? 1 : -1) : 0;
        if (stepX == 0 && stepZ == 0) {
            return false;
        }
        int downwindSurface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + stepX, z + stepZ);
        return downwindSurface <= surfaceY - 2;
    }

    private static void addSnowSheet(
            BufferBuilder buffer,
            int x,
            int z,
            int bottomY,
            int topY,
            double camX,
            double camY,
            double camZ,
            double widthX,
            double widthZ,
            float motionX,
            float motionZ,
            float driftX,
            float driftZ,
            float horizontalScroll,
            float offsetU,
            float offsetV,
            float alpha,
            int skyLight,
            int blockLight,
            float lateralOffset
    ) {
        float offsetX = (float) widthX * lateralOffset;
        float offsetZ = (float) widthZ * lateralOffset;
        float leftX = (float) (x - camX - widthX + 0.5D) + offsetX + motionX;
        float rightX = (float) (x - camX + widthX + 0.5D) + offsetX + motionX;
        float leftZ = (float) (z - camZ - widthZ + 0.5D) + offsetZ + motionZ;
        float rightZ = (float) (z - camZ + widthZ + 0.5D) + offsetZ + motionZ;
        float top = (float) (topY - camY);
        float bottom = (float) (bottomY - camY);
        float flowingU = offsetU + horizontalScroll;
        buffer.addVertex(leftX + driftX, top, leftZ + driftZ).setUv(flowingU, bottomY * 0.25F + offsetV).setColor(0.94F, 0.97F, 1.0F, alpha).setUv2(skyLight, blockLight);
        buffer.addVertex(rightX + driftX, top, rightZ + driftZ).setUv(1.0F + flowingU, bottomY * 0.25F + offsetV).setColor(0.94F, 0.97F, 1.0F, alpha).setUv2(skyLight, blockLight);
        buffer.addVertex(rightX, bottom, rightZ).setUv(1.0F + flowingU, topY * 0.25F + offsetV).setColor(0.94F, 0.97F, 1.0F, alpha).setUv2(skyLight, blockLight);
        buffer.addVertex(leftX, bottom, leftZ).setUv(flowingU, topY * 0.25F + offsetV).setColor(0.94F, 0.97F, 1.0F, alpha).setUv2(skyLight, blockLight);
    }

    private static long precipitationHash(int x, int z) {
        return x * (long) x * 3121L + x * 45238971L ^ z * (long) z * 418711L + z * 13761L;
    }

    private static float streamVisibility(long hash, float density) {
        if (density <= 0.0F) {
            return 0.0F;
        }
        float transition = (density - unitFloat(hash)) / DENSITY_FADE_WIDTH + 0.5F;
        return smoothFade(transition);
    }

    private static float smoothFade(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);
        return value * value * (3.0F - 2.0F * value);
    }

    private static float fractionalPart(float value) {
        return value - Mth.floor(value);
    }

    private static float unitFloat(long hash) {
        long mixed = hash ^ hash >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        return (mixed >>> 40) / (float) (1 << 24);
    }
}
