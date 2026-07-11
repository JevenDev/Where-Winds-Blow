package com.jvn.wherewindsblow.client.weather;

import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.WindSample;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

public final class WindReactivePrecipitationRenderer {
    private static final ResourceLocation RAIN_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/rain.png");
    private static final ResourceLocation SNOW_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/snow.png");
    private static final float NORMAL_RAIN_DENSITY = 0.82F;
    private static final float THUNDER_EXTRA_RAIN_DENSITY = 0.28F;

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
        float rainLevel = level.getRainLevel(partialTick);
        if (rainLevel <= 0.0F) {
            return;
        }

        float thunder = level.getThunderLevel(partialTick);
        WindSample wind = DynamicWindManager.sampleWind(camX, camY, camZ);
        float windStrength = Mth.clamp(wind.strength(), 0.0F, 3.0F);
        int centerX = Mth.floor(camX);
        int centerY = Mth.floor(camY);
        int centerZ = Mth.floor(camZ);
        int radius = Minecraft.useFancyGraphics() ? 10 : 5;

        lightTexture.turnOnLightLayer();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getParticleShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = null;
        int activeType = -1;
        float animationTime = ticks + partialTick;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int z = centerZ - radius; z <= centerZ + radius; z++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                int sizeIndex = (z - centerZ + 16) * 32 + x - centerX + 16;
                double widthX = rainSizeX[sizeIndex] * 0.5D;
                double widthZ = rainSizeZ[sizeIndex] * 0.5D;
                pos.set(x, camY, z);
                Biome biome = level.getBiome(pos).value();
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
                long hash = precipitationHash(x, z);
                RandomSource random = RandomSource.create(hash);
                if (precipitation == Biome.Precipitation.RAIN) {
                    float primaryDensity = Mth.lerp(thunder, NORMAL_RAIN_DENSITY, 1.0F);
                    if (unitFloat(hash) > primaryDensity) {
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

                    float scroll = -((ticks & 131071) + (hash & 255L) + partialTick) / 32.0F * (3.0F + random.nextFloat());
                    float distance = horizontalDistance(x, z, camX, camZ) / radius;
                    float alpha = ((1.0F - distance * distance) * 0.5F + 0.5F) * rainLevel;
                    alpha *= Mth.lerp(thunder, 0.68F, 0.88F);
                    float slope = 0.045F + windStrength * 0.075F + thunder * 0.085F;
                    float driftX = -wind.directionX() * Math.min((topY - bottomY) * slope, 3.8F);
                    float driftZ = -wind.directionZ() * Math.min((topY - bottomY) * slope, 3.8F);
                    pos.set(x, Math.max(surfaceY, centerY), z);
                    int light = LevelRenderer.getLightColor(level, pos);

                    addRainQuad(buffer, x, z, bottomY, topY, camX, camY, camZ, widthX, widthZ,
                            driftX, driftZ, scroll % 32.0F, alpha, light, 0.0F);

                    if (thunder > 0.0F && unitFloat(hash ^ 0x6A09E667F3BCC909L) < thunder * THUNDER_EXTRA_RAIN_DENSITY) {
                        addRainQuad(buffer, x, z, bottomY, topY, camX, camY, camZ, widthX, widthZ,
                                driftX, driftZ, (scroll + 11.0F) % 32.0F, alpha * 0.72F, light, 0.28F);
                    }
                } else if (precipitation == Biome.Precipitation.SNOW) {
                    if (activeType != 1) {
                        if (activeType >= 0) {
                            BufferUploader.drawWithShader(buffer.buildOrThrow());
                        }
                        activeType = 1;
                        RenderSystem.setShaderTexture(0, SNOW_LOCATION);
                        buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                    }

                    float verticalScroll = -((ticks & 511) + partialTick) / 512.0F;
                    float offsetU = (float) (random.nextDouble() + animationTime * 0.01D * random.nextGaussian());
                    float offsetV = (float) (random.nextDouble() + animationTime * random.nextGaussian() * 0.001D);
                    float distance = horizontalDistance(x, z, camX, camZ) / radius;
                    float alpha = ((1.0F - distance * distance) * 0.3F + 0.5F) * rainLevel;
                    float slope = 0.025F + windStrength * 0.095F + thunder * 0.055F;
                    float flutter = Mth.sin(animationTime * 0.035F + (hash & 255L)) * (0.12F + windStrength * 0.08F);
                    float driftX = -wind.directionX() * Math.min((topY - bottomY) * slope, 4.5F) - wind.directionZ() * flutter;
                    float driftZ = -wind.directionZ() * Math.min((topY - bottomY) * slope, 4.5F) + wind.directionX() * flutter;
                    pos.set(x, Math.max(surfaceY, centerY), z);
                    int light = LevelRenderer.getLightColor(level, pos);
                    int blockLight = light >> 16 & 65535;
                    int skyLight = light & 65535;
                    addSnowQuad(buffer, x, z, bottomY, topY, camX, camY, camZ, widthX, widthZ,
                            driftX, driftZ, verticalScroll, offsetU, offsetV, alpha,
                            (skyLight * 3 + 240) / 4, (blockLight * 3 + 240) / 4);
                }
            }
        }

        if (activeType >= 0) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
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

    private static void addSnowQuad(BufferBuilder buffer, int x, int z, int bottomY, int topY,
                                    double camX, double camY, double camZ, double widthX, double widthZ,
                                    float driftX, float driftZ, float scroll, float offsetU, float offsetV,
                                    float alpha, int skyLight, int blockLight) {
        float leftX = (float) (x - camX - widthX + 0.5D);
        float rightX = (float) (x - camX + widthX + 0.5D);
        float leftZ = (float) (z - camZ - widthZ + 0.5D);
        float rightZ = (float) (z - camZ + widthZ + 0.5D);
        float top = (float) (topY - camY);
        float bottom = (float) (bottomY - camY);
        buffer.addVertex(leftX + driftX, top, leftZ + driftZ).setUv(offsetU, bottomY * 0.25F + scroll + offsetV).setColor(1.0F, 1.0F, 1.0F, alpha).setUv2(skyLight, blockLight);
        buffer.addVertex(rightX + driftX, top, rightZ + driftZ).setUv(1.0F + offsetU, bottomY * 0.25F + scroll + offsetV).setColor(1.0F, 1.0F, 1.0F, alpha).setUv2(skyLight, blockLight);
        buffer.addVertex(rightX, bottom, rightZ).setUv(1.0F + offsetU, topY * 0.25F + scroll + offsetV).setColor(1.0F, 1.0F, 1.0F, alpha).setUv2(skyLight, blockLight);
        buffer.addVertex(leftX, bottom, leftZ).setUv(offsetU, topY * 0.25F + scroll + offsetV).setColor(1.0F, 1.0F, 1.0F, alpha).setUv2(skyLight, blockLight);
    }

    private static float horizontalDistance(int x, int z, double camX, double camZ) {
        double dx = x + 0.5D - camX;
        double dz = z + 0.5D - camZ;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static long precipitationHash(int x, int z) {
        return x * (long) x * 3121L + x * 45238971L ^ z * (long) z * 418711L + z * 13761L;
    }

    private static float unitFloat(long hash) {
        long mixed = hash ^ hash >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        return (mixed >>> 40) / (float) (1 << 24);
    }
}
