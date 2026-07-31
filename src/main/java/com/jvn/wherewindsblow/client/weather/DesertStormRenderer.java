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
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;

/** Renders dry, wind-driven precipitation in biomes that cannot display vanilla rain. */
public final class DesertStormRenderer {
    private static final ResourceLocation SAND_GRAIN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "where_winds_blow", "textures/environment/desert_dust.png"
    );
    private static final float DENSITY_FADE_WIDTH = 0.08F;

    private DesertStormRenderer() {
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
        if (!ClientConfig.ENABLE_DESERT_STORM_EFFECTS.getAsBoolean()) {
            return;
        }

        float rainLevel = level.getRainLevel(partialTick);
        if (rainLevel <= 0.0F) {
            return;
        }

        float thunder = level.getThunderLevel(partialTick);
        float animationTime = ticks + partialTick;
        int centerX = Mth.floor(camX);
        int centerY = Mth.floor(camY);
        int centerZ = Mth.floor(camZ);
        int radius = Minecraft.useFancyGraphics() ? 12 : 7;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BufferBuilder buffer = null;

        lightTexture.turnOnLightLayer();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderTexture(0, SAND_GRAIN_TEXTURE);

        Tesselator tesselator = Tesselator.getInstance();
        for (int z = centerZ - radius; z <= centerZ + radius; z++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                pos.set(x, camY, z);
                Holder<Biome> biome = level.getBiome(pos);
                if (!biome.is(Tags.Biomes.IS_DESERT) && !biome.is(Tags.Biomes.IS_BADLANDS)) {
                    continue;
                }

                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                pos.set(x, Math.max(level.getMinBuildHeight(), surfaceY - 1), z);
                boolean redSand = level.getBlockState(pos).is(Blocks.RED_SAND)
                        || biome.is(Tags.Biomes.IS_BADLANDS);
                int tint = (redSand ? Blocks.RED_SAND : Blocks.SAND)
                        .defaultBlockState()
                        .getMapColor(level, pos)
                        .col;
                float tintRed = (tint >> 16 & 255) / 255.0F;
                float tintGreen = (tint >> 8 & 255) / 255.0F;
                float tintBlue = (tint & 255) / 255.0F;
                int bottomY = Math.max(centerY - radius, surfaceY);
                int topY = Math.max(centerY + radius, surfaceY);
                if (bottomY == topY) {
                    continue;
                }

                long hash = precipitationHash(x, z);
                WindSample wind = DynamicWindManager.sampleWind(level, pos);
                float windStrength = Mth.clamp(wind.strength(), 0.0F, 3.0F);
                float density = Mth.clamp(
                        0.78F + rainLevel * 0.12F + thunder * 0.14F
                                + wind.gustStrength() * 0.22F + windStrength * 0.04F,
                        0.72F,
                        1.0F
                );
                float primaryVisibility = streamVisibility(hash, density);
                float secondaryVisibility = streamVisibility(
                        hash ^ 0x9E3779B97F4A7C15L,
                        Mth.clamp(
                                0.32F + rainLevel * 0.18F + thunder * 0.36F
                                        + wind.gustStrength() * 0.25F,
                                0.18F,
                                0.96F
                        )
                );
                float groundVisibility = streamVisibility(
                        hash ^ 0xC2B2AE3D27D4EB4FL,
                        Mth.clamp(
                                0.22F + rainLevel * 0.14F + thunder * 0.30F
                                        + windStrength * 0.08F + wind.turbulence() * 0.14F,
                                0.12F,
                                0.86F
                        )
                );
                if (primaryVisibility <= 0.01F
                        && secondaryVisibility <= 0.01F
                        && groundVisibility <= 0.01F) {
                    continue;
                }

                if (buffer == null) {
                    buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                }

                int sizeIndex = (z - centerZ + 16) * 32 + x - centerX + 16;
                double widthX = rainSizeX[sizeIndex] * 0.76D;
                double widthZ = rainSizeZ[sizeIndex] * 0.76D;
                float distance = horizontalDistance(x, z, camX, camZ) / radius;
                float distanceFade = (1.0F - distance * distance) * 0.30F + 0.62F;
                float alpha = distanceFade * rainLevel * Mth.lerp(thunder, 0.62F, 0.96F);
                alpha *= Mth.lerp(unitFloat(hash ^ 0xD1B54A32D192ED03L), 0.88F, 1.14F);
                alpha = Mth.clamp(alpha, 0.0F, 0.98F);

                float fallDistance = topY - bottomY;
                float slope = (0.18F + windStrength * 0.28F + thunder * 0.24F)
                        * Mth.lerp(unitFloat(hash ^ 0x94D049BB133111EBL), 0.86F, 1.32F);
                float drift = Math.min(fallDistance * slope, Mth.lerp(thunder, 9.5F, 14.0F));
                float flutterPhase = animationTime * (0.065F + windStrength * 0.018F) + (hash & 255L);
                float flutter = Mth.sin(flutterPhase)
                        * (0.18F + wind.turbulence() * 0.34F + thunder * 0.24F);
                float driftX = -wind.directionX() * drift - wind.directionZ() * flutter;
                float driftZ = -wind.directionZ() * drift + wind.directionX() * flutter;

                float speed = 0.012F + windStrength * 0.0065F + thunder * 0.008F;
                float travelRange = 8.0F + windStrength * 2.0F + thunder * 4.0F;
                float travelSpeed = 0.055F + windStrength * 0.075F
                        + wind.gustStrength() * 0.10F + thunder * 0.06F;
                float travel = (unitFloat(hash ^ 0x165667B19E3779F9L) * travelRange
                        + animationTime * travelSpeed) % travelRange - travelRange * 0.5F;
                float crossTravel = Mth.sin(flutterPhase * 0.53F + unitFloat(hash) * Mth.TWO_PI)
                        * (0.15F + wind.turbulence() * 0.5F + thunder * 0.12F);
                float motionX = wind.directionX() * travel - wind.directionZ() * crossTravel;
                float motionZ = wind.directionZ() * travel + wind.directionX() * crossTravel;

                float projectedWind = wind.directionX() * (float) widthX
                        + wind.directionZ() * (float) widthZ;
                float screenDirection = projectedWind < 0.0F ? -1.0F : 1.0F;
                float projectionStrength = 0.45F + Math.min(Math.abs(projectedWind) * 1.4F, 0.95F);
                float horizontalScroll = -animationTime * speed
                        * (0.34F + windStrength * 0.08F + thunder * 0.10F)
                        * screenDirection * projectionStrength;
                float offsetU = unitFloat(hash ^ 0xDB4F0B9175AE2165L);
                float offsetV = unitFloat(hash ^ 0xBBE0563303A4615FL)
                        + Mth.sin(flutterPhase * 0.41F)
                                * (0.025F + wind.turbulence() * 0.045F);

                pos.set(x, Math.max(surfaceY, centerY), z);
                int light = LevelRenderer.getLightColor(level, pos);
                int blockLight = light >> 16 & 65535;
                int skyLight = light & 65535;
                int softenedSkyLight = (skyLight * 3 + 208) / 4;
                int softenedBlockLight = (blockLight * 3 + 208) / 4;

                if (primaryVisibility > 0.01F) {
                    addSandQuad(buffer, x, z, bottomY, topY, camX, camY, camZ,
                            widthX, widthZ, motionX, motionZ, driftX, driftZ,
                            horizontalScroll, offsetU, offsetV,
                            alpha * primaryVisibility, softenedSkyLight, softenedBlockLight,
                            tintRed, tintGreen, tintBlue, 0.0F);
                }
                if (secondaryVisibility > 0.01F) {
                    addSandQuad(buffer, x, z, bottomY, topY, camX, camY, camZ,
                            widthX * 0.88D, widthZ * 0.88D,
                            motionX + wind.directionX() * 1.7F,
                            motionZ + wind.directionZ() * 1.7F,
                            driftX * 1.16F, driftZ * 1.16F,
                            horizontalScroll * 1.35F, offsetU + 0.41F, offsetV + 0.57F,
                            alpha * 0.86F * secondaryVisibility, softenedSkyLight, softenedBlockLight,
                            tintRed * 0.82F, tintGreen * 0.78F, tintBlue * 0.72F, 0.42F);
                }
                int groundTopY = Math.min(
                        topY,
                        bottomY + 3 + Mth.floor(thunder * 3.0F + windStrength * 0.75F)
                );
                if (groundVisibility > 0.01F && groundTopY > bottomY) {
                    addSandQuad(buffer, x, z, bottomY, groundTopY, camX, camY, camZ,
                            widthX * 1.14D, widthZ * 1.14D,
                            motionX - wind.directionX() * 1.35F,
                            motionZ - wind.directionZ() * 1.35F,
                            driftX * 0.52F, driftZ * 0.52F,
                            horizontalScroll * 1.75F, offsetU + 0.73F, offsetV + 0.19F,
                            alpha * 0.72F * groundVisibility, softenedSkyLight, softenedBlockLight,
                            tintRed * 0.68F, tintGreen * 0.62F, tintBlue * 0.56F, -0.38F);
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

    private static void addSandQuad(
            BufferBuilder buffer, int x, int z, int bottomY, int topY,
            double camX, double camY, double camZ, double widthX, double widthZ,
            float motionX, float motionZ, float driftX, float driftZ,
            float horizontalScroll, float offsetU, float offsetV,
            float alpha, int skyLight, int blockLight,
            float red, float green, float blue, float lateralOffset
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
        buffer.addVertex(leftX + driftX, top, leftZ + driftZ).setUv(flowingU, bottomY * 0.25F + offsetV).setColor(red, green, blue, alpha).setUv2(skyLight, blockLight);
        buffer.addVertex(rightX + driftX, top, rightZ + driftZ).setUv(1.0F + flowingU, bottomY * 0.25F + offsetV).setColor(red, green, blue, alpha).setUv2(skyLight, blockLight);
        buffer.addVertex(rightX, bottom, rightZ).setUv(1.0F + flowingU, topY * 0.25F + offsetV).setColor(red, green, blue, alpha).setUv2(skyLight, blockLight);
        buffer.addVertex(leftX, bottom, leftZ).setUv(flowingU, topY * 0.25F + offsetV).setColor(red, green, blue, alpha).setUv2(skyLight, blockLight);
    }

    private static float horizontalDistance(int x, int z, double camX, double camZ) {
        double dx = x + 0.5D - camX;
        double dz = z + 0.5D - camZ;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static long precipitationHash(int x, int z) {
        return x * (long) x * 3121L + x * 45238971L ^ z * (long) z * 418711L + z * 13761L;
    }

    private static float streamVisibility(long hash, float density) {
        float transition = (density - unitFloat(hash)) / DENSITY_FADE_WIDTH + 0.5F;
        transition = Mth.clamp(transition, 0.0F, 1.0F);
        return transition * transition * (3.0F - 2.0F * transition);
    }

    private static float unitFloat(long hash) {
        long mixed = hash ^ hash >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        return (mixed >>> 40) / (float) (1 << 24);
    }
}
