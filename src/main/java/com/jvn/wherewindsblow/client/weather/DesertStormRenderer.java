package com.jvn.wherewindsblow.client.weather;

import com.jvn.toucanlib.client.ToucanEasing;
import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.WindSample;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;

/**
 * Draws a persistent grid of GPU-simulated dust particles in deserts and badlands. A small terrain
 * texture tells the shader where the ground is and whether its palette should come from sand or
 * red sand; the CPU does not rebuild storm geometry each frame.
 */
public final class DesertStormRenderer {
    private static final ResourceLocation SAND_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/sand.png");
    private static final ResourceLocation RED_SAND_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/red_sand.png");
    private static final ResourceLocation TERRAIN_TEXTURE = WhereWindsBlow.IDS.id("dynamic/gpu_desert_storm_terrain");
    private static final int TERRAIN_RADIUS = 25;
    private static final int TERRAIN_SIZE = TERRAIN_RADIUS * 2 + 1;
    private static final int TERRAIN_FULL_REFRESH_TICKS = 100;
    private static final int WORLD_HASH_PERIOD = 8192;
    private static final StormMesh FANCY_MESH = new StormMesh(12, 12, 22.0F);
    private static final StormMesh FAST_MESH = new StormMesh(7, 6, 14.0F);

    private static DynamicTexture terrainTexture;
    private static ClientLevel cachedLevel;
    private static int cachedCenterX = Integer.MIN_VALUE;
    private static int cachedCenterY = Integer.MIN_VALUE;
    private static int cachedCenterZ = Integer.MIN_VALUE;
    private static long lastFullTerrainRefreshTick = Long.MIN_VALUE;
    private static final int[] TERRAIN_SHIFT_BUFFER = new int[TERRAIN_SIZE * TERRAIN_SIZE];
    private static double dustAnimationTime;
    private static double lastDustAnimationTime = Double.NaN;
    private static boolean gpuDisabled;

    private DesertStormRenderer() {
    }

    public static void render(
            ClientLevel level,
            float rainLevel,
            float thunder,
            double animationTime,
            double camX,
            double camY,
            double camZ
    ) {
        if (!ClientConfig.ENABLE_DESERT_STORM_EFFECTS.getAsBoolean() || rainLevel <= 0.0F) {
            return;
        }

        ShaderInstance shader = DesertStormShaders.shader();
        if (gpuDisabled || shader == null) {
            return;
        }

        try {
            StormMesh mesh = Minecraft.useFancyGraphics() ? FANCY_MESH : FAST_MESH;
            mesh.ensureUploaded();

            int centerX = Mth.floor(camX);
            int centerY = Mth.floor(camY);
            int centerZ = Mth.floor(camZ);
            if (!updateTerrainTexture(level, centerX, centerY, centerZ)) {
                return;
            }

            WindSample wind = DynamicWindManager.sampleWind(
                    level,
                    new BlockPos(centerX, centerY, centerZ)
            );
            float windStrength = Mth.clamp(wind.strength(), 0.0F, 3.0F);
            float gust = ToucanEasing.smoothstep(Mth.clamp(wind.gustStrength() * 2.3F, 0.0F, 1.0F));
            float stormActivity = BlizzardWeatherEffects.blizzardActivity(thunder);
            DesertStormProfile profile = configuredProfile(stormActivity);
            float severeIntensity = Mth.lerp(
                    stormActivity,
                    1.0F,
                    (float) ClientConfig.SANDSTORM_INTENSITY.getAsDouble()
            );
            float density = Mth.clamp(
                    (0.60F + rainLevel * 0.24F + thunder * 0.10F
                            + gust * 0.10F + windStrength * 0.025F)
                            * profile.amountScale() * severeIntensity,
                    0.0F,
                    1.0F
            );
            float opacity = Mth.clamp(
                    rainLevel * (0.96F + thunder * 0.08F + gust * 0.08F)
                            * Mth.sqrt(Math.max(severeIntensity, 0.0F)),
                    0.0F,
                    1.0F
            );
            float weatherSpeedMultiplier = 0.90F
                    + Math.min(windStrength, 2.0F) * 0.12F
                    + thunder * 0.12F
                    + gust * 0.08F;
            double animatedDustTime = advanceDustAnimationTime(
                    animationTime,
                    profile.speedScale() * weatherSpeedMultiplier
            );
            float partialTick = (float) (animationTime - Math.floor(animationTime));
            float stormLight = Mth.lerp(
                    Mth.clamp(level.getSkyDarken(partialTick), 0.0F, 1.0F),
                    0.64F,
                    1.0F
            );

            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(Minecraft.useShaderTransparency());
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, TERRAIN_TEXTURE);
            RenderSystem.setShaderTexture(1, SAND_TEXTURE);
            RenderSystem.setShaderTexture(2, RED_SAND_TEXTURE);

            shader.safeGetUniform("CameraState").set(
                    (float) (camX - centerX),
                    (float) camY,
                    (float) (camZ - centerZ)
            );
            shader.safeGetUniform("WorldCell").set(
                    (float) Math.floorMod(centerX, WORLD_HASH_PERIOD),
                    (float) Math.floorMod(centerZ, WORLD_HASH_PERIOD)
            );
            shader.safeGetUniform("ScreenSize").set(
                    (float) Minecraft.getInstance().getWindow().getWidth(),
                    (float) Minecraft.getInstance().getWindow().getHeight()
            );
            shader.safeGetUniform("Wind").set(
                    wind.directionX(),
                    wind.directionZ(),
                    windStrength,
                    wind.turbulence()
            );
            shader.safeGetUniform("Storm").set(rainLevel, thunder, gust, density);
            shader.safeGetUniform("DustTime").set((float) animatedDustTime);
            shader.safeGetUniform("FlutterTime").set((float) animationTime);
            shader.safeGetUniform("DustSize").set(profile.sizeScale());
            shader.safeGetUniform("Opacity").set(opacity);
            shader.safeGetUniform("Radius").set((float) mesh.radius);
            shader.safeGetUniform("VerticalSpan").set(mesh.verticalSpan);
            shader.safeGetUniform("HeightBase").set((float) level.getMinBuildHeight());
            shader.safeGetUniform("HeightRadius").set(TERRAIN_RADIUS);
            shader.safeGetUniform("StormLight").set(stormLight);

            mesh.buffer.bind();
            mesh.buffer.drawWithShader(
                    RenderSystem.getModelViewMatrix(),
                    RenderSystem.getProjectionMatrix(),
                    shader
            );
            VertexBuffer.unbind();

            restoreRenderState();
        } catch (RuntimeException exception) {
            gpuDisabled = true;
            WhereWindsBlow.LOGGER.warn(
                    "GPU desert storm rendering failed; desert storms are disabled until restart.",
                    exception
            );
            VertexBuffer.unbind();
            restoreRenderState();
        }
    }

    private static boolean updateTerrainTexture(
            ClientLevel level,
            int centerX,
            int centerY,
            int centerZ
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (terrainTexture == null) {
            terrainTexture = new DynamicTexture(TERRAIN_SIZE, TERRAIN_SIZE, false);
            terrainTexture.setFilter(false, false);
            minecraft.getTextureManager().register(TERRAIN_TEXTURE, terrainTexture);
        }

        long gameTime = level.getGameTime();
        boolean sameLevel = cachedLevel == level;
        boolean fullRefreshDue = !sameLevel
                || cachedCenterY != centerY
                || gameTime < lastFullTerrainRefreshTick
                || gameTime - lastFullTerrainRefreshTick >= TERRAIN_FULL_REFRESH_TICKS;
        int deltaX = sameLevel ? centerX - cachedCenterX : Integer.MAX_VALUE;
        int deltaZ = sameLevel ? centerZ - cachedCenterZ : Integer.MAX_VALUE;
        if (!fullRefreshDue && deltaX == 0 && deltaZ == 0) {
            return true;
        }

        NativeImage pixels = terrainTexture.getPixels();
        if (pixels == null) {
            return false;
        }

        boolean canShift = !fullRefreshDue
                && Math.abs(deltaX) < TERRAIN_SIZE
                && Math.abs(deltaZ) < TERRAIN_SIZE;
        if (canShift) {
            snapshotTexture(pixels, TERRAIN_SHIFT_BUFFER);
        }

        int minimumHeight = level.getMinBuildHeight();
        BlockPos.MutableBlockPos biomePos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos surfacePos = new BlockPos.MutableBlockPos();
        for (int textureZ = 0; textureZ < TERRAIN_SIZE; textureZ++) {
            int z = centerZ + textureZ - TERRAIN_RADIUS;
            for (int textureX = 0; textureX < TERRAIN_SIZE; textureX++) {
                int previousX = textureX + deltaX;
                int previousZ = textureZ + deltaZ;
                if (canShift
                        && previousX >= 0 && previousX < TERRAIN_SIZE
                        && previousZ >= 0 && previousZ < TERRAIN_SIZE) {
                    int previousIndex = previousZ * TERRAIN_SIZE + previousX;
                    pixels.setPixelRGBA(textureX, textureZ, TERRAIN_SHIFT_BUFFER[previousIndex]);
                    continue;
                }

                int x = centerX + textureX - TERRAIN_RADIUS;
                sampleTerrainCell(
                        level, pixels, textureX, textureZ, x, z,
                        centerY, minimumHeight, biomePos, surfacePos
                );
            }
        }
        terrainTexture.upload();
        cachedLevel = level;
        cachedCenterX = centerX;
        cachedCenterY = centerY;
        cachedCenterZ = centerZ;
        if (!canShift) {
            lastFullTerrainRefreshTick = gameTime;
        }
        return true;
    }

    private static void snapshotTexture(NativeImage image, int[] destination) {
        for (int z = 0; z < TERRAIN_SIZE; z++) {
            for (int x = 0; x < TERRAIN_SIZE; x++) {
                destination[z * TERRAIN_SIZE + x] = image.getPixelRGBA(x, z);
            }
        }
    }

    private static void sampleTerrainCell(
            ClientLevel level,
            NativeImage terrainPixels,
            int textureX,
            int textureZ,
            int x,
            int z,
            int centerY,
            int minimumHeight,
            BlockPos.MutableBlockPos biomePos,
            BlockPos.MutableBlockPos surfacePos
    ) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        biomePos.set(x, centerY, z);
        Holder<Biome> biome = level.getBiome(biomePos);
        boolean desert = biome.is(Tags.Biomes.IS_DESERT) || biome.is(Tags.Biomes.IS_BADLANDS);
        int material = 0;
        if (desert) {
            surfacePos.set(x, Math.max(minimumHeight, surfaceY - 1), z);
            boolean redSand = biome.is(Tags.Biomes.IS_BADLANDS)
                    || level.getBlockState(surfacePos).is(Blocks.RED_SAND);
            material = redSand ? 255 : 127;
        }

        int encodedHeight = Mth.clamp(surfaceY - minimumHeight, 0, 65535);
        terrainPixels.setPixelRGBA(
                textureX,
                textureZ,
                FastColor.ABGR32.color(255, material, encodedHeight >>> 8 & 255, encodedHeight & 255)
        );
    }

    private static void restoreRenderState() {
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static double advanceDustAnimationTime(
            double animationTime,
            float speedMultiplier
    ) {
        if (!Double.isFinite(lastDustAnimationTime)) {
            dustAnimationTime = animationTime;
            lastDustAnimationTime = animationTime;
            return dustAnimationTime;
        }

        double delta = animationTime - lastDustAnimationTime;
        lastDustAnimationTime = animationTime;
        if (delta < 0.0D || delta > 5.0D) {
            dustAnimationTime = animationTime;
        } else {
            dustAnimationTime += delta * Math.max(speedMultiplier, 0.1F);
        }
        return dustAnimationTime;
    }

    private static DesertStormProfile configuredProfile(float stormActivity) {
        float blend = Mth.clamp(stormActivity, 0.0F, 1.0F);
        return new DesertStormProfile(
                blendedConfig(
                        ClientConfig.SAND_DUST_AMOUNT,
                        ClientConfig.SANDSTORM_DUST_AMOUNT,
                        blend
                ),
                blendedConfig(
                        ClientConfig.SAND_DUST_SIZE,
                        ClientConfig.SANDSTORM_DUST_SIZE,
                        blend
                ),
                blendedConfig(
                        ClientConfig.SAND_DUST_SPEED,
                        ClientConfig.SANDSTORM_DUST_SPEED,
                        blend
                )
        );
    }

    private static float blendedConfig(
            net.neoforged.neoforge.common.ModConfigSpec.DoubleValue ordinary,
            net.neoforged.neoforge.common.ModConfigSpec.DoubleValue sandstorm,
            float blend
    ) {
        return Mth.lerp(
                blend,
                (float) ordinary.getAsDouble(),
                (float) sandstorm.getAsDouble()
        );
    }

    private record DesertStormProfile(
            float amountScale,
            float sizeScale,
            float speedScale
    ) {
    }

    private static final class StormMesh {
        private final int radius;
        private final int particlesPerColumn;
        private final float verticalSpan;
        private VertexBuffer buffer;

        private StormMesh(int radius, int particlesPerColumn, float verticalSpan) {
            this.radius = radius;
            this.particlesPerColumn = particlesPerColumn;
            this.verticalSpan = verticalSpan;
        }

        private void ensureUploaded() {
            if (buffer != null && !buffer.isInvalid()) {
                return;
            }

            BufferBuilder builder = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION
            );
            int gridRadius = radius + 2;
            for (int z = -gridRadius; z <= gridRadius; z++) {
                for (int x = -gridRadius; x <= gridRadius; x++) {
                    for (int lane = 0; lane < particlesPerColumn; lane++) {
                        for (int corner = 0; corner < 4; corner++) {
                            builder.addVertex(x, lane, z);
                        }
                    }
                }
            }

            if (buffer != null) {
                buffer.close();
            }
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            try (MeshData mesh = builder.buildOrThrow()) {
                buffer.upload(mesh);
            } finally {
                VertexBuffer.unbind();
            }
        }
    }
}
