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
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;

/** Renders dry, wind-driven precipitation in biomes that cannot display vanilla rain. */
public final class DesertStormRenderer {
    private static final ResourceLocation SAND_GRAIN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "where_winds_blow", "textures/environment/desert_dust.png"
    );
    private static final float DENSITY_FADE_WIDTH = 0.12F;

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
        int radius = Minecraft.useFancyGraphics() ? 10 : 5;
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
                if (!biome.is(Tags.Biomes.IS_DESERT)) {
                    continue;
                }

                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int bottomY = Math.max(centerY - radius, surfaceY);
                int topY = Math.max(centerY + radius, surfaceY);
                if (bottomY == topY) {
                    continue;
                }

                long hash = precipitationHash(x, z);
                WindSample wind = DynamicWindManager.sampleWind(level, pos);
                float windStrength = Mth.clamp(wind.strength(), 0.0F, 3.0F);
                float density = Mth.clamp(
                        0.58F + thunder * 0.34F + wind.gustStrength() * 0.18F,
                        0.48F,
                        1.0F
                );
                float primaryVisibility = streamVisibility(hash, density);
                float secondaryVisibility = streamVisibility(
                        hash ^ 0x9E3779B97F4A7C15L,
                        Mth.clamp(thunder * 0.52F + wind.gustStrength() * 0.16F, 0.0F, 0.72F)
                );
                if (primaryVisibility <= 0.01F && secondaryVisibility <= 0.01F) {
                    continue;
                }

                if (buffer == null) {
                    buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                }

                int sizeIndex = (z - centerZ + 16) * 32 + x - centerX + 16;
                double widthX = rainSizeX[sizeIndex] * 0.62D;
                double widthZ = rainSizeZ[sizeIndex] * 0.62D;
                float distance = horizontalDistance(x, z, camX, camZ) / radius;
                float distanceFade = (1.0F - distance * distance) * 0.34F + 0.52F;
                float alpha = distanceFade * rainLevel * Mth.lerp(thunder, 0.46F, 0.76F);
                alpha *= Mth.lerp(unitFloat(hash ^ 0xD1B54A32D192ED03L), 0.82F, 1.08F);
                alpha = Mth.clamp(alpha, 0.0F, 0.88F);

                float fallDistance = topY - bottomY;
                float slope = (0.10F + windStrength * 0.18F + thunder * 0.16F)
                        * Mth.lerp(unitFloat(hash ^ 0x94D049BB133111EBL), 0.82F, 1.24F);
                float drift = Math.min(fallDistance * slope, Mth.lerp(thunder, 6.5F, 9.5F));
                float flutterPhase = animationTime * (0.045F + windStrength * 0.012F) + (hash & 255L);
                float flutter = Mth.sin(flutterPhase)
                        * (0.12F + wind.turbulence() * 0.24F + thunder * 0.16F);
                float driftX = -wind.directionX() * drift - wind.directionZ() * flutter;
                float driftZ = -wind.directionZ() * drift + wind.directionX() * flutter;

                float speed = 0.007F + windStrength * 0.004F + thunder * 0.005F;
                float scroll = -animationTime * speed;
                float offsetU = unitFloat(hash ^ 0xDB4F0B9175AE2165L)
                        + animationTime * wind.directionX() * speed * 0.35F;
                float offsetV = unitFloat(hash ^ 0xBBE0563303A4615FL)
                        + animationTime * wind.directionZ() * speed * 0.12F;

                pos.set(x, Math.max(surfaceY, centerY), z);
                int light = LevelRenderer.getLightColor(level, pos);
                int blockLight = light >> 16 & 65535;
                int skyLight = light & 65535;
                int softenedSkyLight = (skyLight * 3 + 208) / 4;
                int softenedBlockLight = (blockLight * 3 + 208) / 4;

                if (primaryVisibility > 0.01F) {
                    addSandQuad(buffer, x, z, bottomY, topY, camX, camY, camZ,
                            widthX, widthZ, driftX, driftZ, scroll, offsetU, offsetV,
                            alpha * primaryVisibility, softenedSkyLight, softenedBlockLight,
                            0.86F, 0.66F, 0.34F, 0.0F);
                }
                if (secondaryVisibility > 0.01F) {
                    addSandQuad(buffer, x, z, bottomY, topY, camX, camY, camZ,
                            widthX * 0.88D, widthZ * 0.88D,
                            driftX * 1.16F, driftZ * 1.16F,
                            scroll * 1.35F, offsetU + 0.41F, offsetV + 0.57F,
                            alpha * 0.76F * secondaryVisibility, softenedSkyLight, softenedBlockLight,
                            0.73F, 0.50F, 0.22F, 0.42F);
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
            float driftX, float driftZ, float scroll, float offsetU, float offsetV,
            float alpha, int skyLight, int blockLight,
            float red, float green, float blue, float lateralOffset
    ) {
        float offsetX = (float) widthX * lateralOffset;
        float offsetZ = (float) widthZ * lateralOffset;
        float leftX = (float) (x - camX - widthX + 0.5D) + offsetX;
        float rightX = (float) (x - camX + widthX + 0.5D) + offsetX;
        float leftZ = (float) (z - camZ - widthZ + 0.5D) + offsetZ;
        float rightZ = (float) (z - camZ + widthZ + 0.5D) + offsetZ;
        float top = (float) (topY - camY);
        float bottom = (float) (bottomY - camY);
        buffer.addVertex(leftX + driftX, top, leftZ + driftZ).setUv(offsetU, bottomY * 0.25F + scroll + offsetV).setColor(red, green, blue, alpha).setUv2(skyLight, blockLight);
        buffer.addVertex(rightX + driftX, top, rightZ + driftZ).setUv(1.0F + offsetU, bottomY * 0.25F + scroll + offsetV).setColor(red, green, blue, alpha).setUv2(skyLight, blockLight);
        buffer.addVertex(rightX, bottom, rightZ).setUv(1.0F + offsetU, topY * 0.25F + scroll + offsetV).setColor(red, green, blue, alpha).setUv2(skyLight, blockLight);
        buffer.addVertex(leftX, bottom, leftZ).setUv(offsetU, topY * 0.25F + scroll + offsetV).setColor(red, green, blue, alpha).setUv2(skyLight, blockLight);
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
