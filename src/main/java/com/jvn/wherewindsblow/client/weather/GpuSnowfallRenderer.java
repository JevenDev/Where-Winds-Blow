package com.jvn.wherewindsblow.client.weather;

import com.jvn.toucanlib.client.ToucanEasing;
import com.jvn.toucanlib.client.ToucanScaledAnimationClock;
import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.WindSample;
import com.jvn.wherewindsblow.config.ClientConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3f;

/**
 * Draws a persistent grid of snowfall seeds. The vertex shader performs all per-frame flake
 * simulation and billboard expansion; the CPU only refreshes a small roof/biome texture when the
 * camera changes block or the nearby terrain cache ages out.
 */
public final class GpuSnowfallRenderer {
    private static final ResourceLocation SNOW_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/environment/snow.png");
    private static final ResourceLocation HEIGHT_TEXTURE = WhereWindsBlow.IDS.id("dynamic/gpu_snowfall_height");
    private static final int HEIGHT_MAP_RADIUS = 20;
    private static final int HEIGHT_MAP_SIZE = HEIGHT_MAP_RADIUS * 2 + 1;
    private static final int MAX_COLUMN_CACHE_SIZE = 8192;
    private static final int MAX_BIOME_CACHE_SIZE = 4096;
    private static final int SNOW_BAND_COUNT = 8;
    private static final int SNOW_BAND_HEIGHT = 3;
    private static final int SNOW_BAND_BELOW_CAMERA = 12;
    private static final int WORLD_HASH_PERIOD = 8192;
    private static final SnowMesh FANCY_MESH = new SnowMesh(12, 12, 22.0F);
    private static final SnowMesh FAST_MESH = new SnowMesh(7, 6, 14.0F);

    private static final Long2ObjectLinkedOpenHashMap<ColumnSample> COLUMN_CACHE = new Long2ObjectLinkedOpenHashMap<>();
    private static final Long2ObjectLinkedOpenHashMap<Biome> BIOME_CACHE = new Long2ObjectLinkedOpenHashMap<>();
    private static DynamicTexture heightTexture;
    private static ClientLevel cachedLevel;
    private static int cachedCenterX = Integer.MIN_VALUE;
    private static int cachedCenterY = Integer.MIN_VALUE;
    private static int cachedCenterZ = Integer.MIN_VALUE;
    private static boolean heightTextureDirty = true;
    private static final ToucanScaledAnimationClock SNOWFALL_ANIMATION_CLOCK =
            new ToucanScaledAnimationClock(5.0D);
    private static boolean gpuDisabled;

    private GpuSnowfallRenderer() {
    }

    public static boolean render(
            ClientLevel level,
            float rainLevel,
            float thunder,
            double animationTime,
            double camX,
            double camY,
            double camZ
    ) {
        ShaderInstance shader = SnowfallShaders.shader();
        if (gpuDisabled || shader == null) {
            return false;
        }

        try {
            boolean fancy = Minecraft.useFancyGraphics();
            SnowMesh mesh = fancy ? FANCY_MESH : FAST_MESH;
            mesh.ensureUploaded();

            int centerX = Mth.floor(camX);
            int centerY = Mth.floor(camY);
            int centerZ = Mth.floor(camZ);
            if (!updateHeightTexture(level, centerX, centerY, centerZ)) {
                return false;
            }

            boolean windDriven = ClientConfig.ENABLE_WIND_DRIVEN_SNOW.getAsBoolean();
            WindSample wind = DynamicWindManager.sampleWind(
                    level,
                    new BlockPos(centerX, centerY, centerZ)
            );
            float windStrength = windDriven ? Mth.clamp(wind.strength(), 0.0F, 3.0F) : 0.0F;
            float stormActivity = BlizzardWeatherEffects.blizzardActivity(thunder);
            SnowfallRenderer.SnowfallProfile profile = SnowfallRenderer.configuredProfile(stormActivity);
            boolean dynamicSqualls = ClientConfig.ENABLE_DYNAMIC_RAIN_SQUALLS.getAsBoolean();
            float squallStrength = (float) ClientConfig.RAIN_SQUALL_STRENGTH.getAsDouble();
            float squall = dynamicSqualls
                    ? ToucanEasing.smoothstep(Mth.clamp(wind.gustStrength() * 2.4F, 0.0F, 1.0F))
                            * squallStrength * stormActivity
                    : 0.0F;
            float lull = dynamicSqualls
                    ? ToucanEasing.smoothstep(DynamicWindManager.currentState().lullAmount())
                            * squallStrength * stormActivity
                    : 0.0F;
            float baseDensity = Mth.clamp(
                    0.68F + rainLevel * 0.20F + thunder * 0.08F
                            + squall * 0.12F - lull * 0.18F,
                    0.38F,
                    1.0F
            );
            float density = Mth.clamp(baseDensity * profile.densityScale(), 0.0F, 1.0F);
            float opacity = rainLevel * Math.max(0.52F, 1.0F + squall * 0.16F - lull * 0.28F);
            double snowTime = SNOWFALL_ANIMATION_CLOCK.advance(
                    animationTime,
                    (1.0F + thunder * 0.16F) * profile.speedScale(),
                    0.2F
            );

            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vector3f left = camera.getLeftVector();
            Vector3f up = camera.getUpVector();
            float partialTick = (float) (animationTime - Math.floor(animationTime));
            float snowLight = Mth.lerp(
                    Mth.clamp(level.getSkyDarken(partialTick), 0.0F, 1.0F),
                    0.72F,
                    1.0F
            );

            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(Minecraft.useShaderTransparency());
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, SNOW_TEXTURE);
            RenderSystem.setShaderTexture(1, HEIGHT_TEXTURE);

            shader.safeGetUniform("CameraState").set(
                    (float) (camX - centerX),
                    (float) camY,
                    (float) (camZ - centerZ)
            );
            shader.safeGetUniform("WorldCell").set(
                    (float) Math.floorMod(centerX, WORLD_HASH_PERIOD),
                    (float) Math.floorMod(centerZ, WORLD_HASH_PERIOD)
            );
            shader.safeGetUniform("CameraLeft").set(left.x(), left.y(), left.z());
            shader.safeGetUniform("CameraUp").set(up.x(), up.y(), up.z());
            shader.safeGetUniform("Wind").set(
                    windDriven ? wind.directionX() : 0.0F,
                    windDriven ? wind.directionZ() : 0.0F,
                    windStrength,
                    windDriven ? wind.turbulence() : 0.0F
            );
            shader.safeGetUniform("Weather").set(thunder, squall, density, opacity);
            shader.safeGetUniform("SnowTime").set((float) snowTime);
            shader.safeGetUniform("FlutterTime").set((float) animationTime);
            shader.safeGetUniform("SnowflakeSize").set(profile.sizeScale());
            shader.safeGetUniform("Radius").set((float) mesh.radius);
            shader.safeGetUniform("VerticalSpan").set(mesh.verticalSpan);
            shader.safeGetUniform("HeightBase").set((float) level.getMinBuildHeight());
            shader.safeGetUniform("HeightRadius").set(HEIGHT_MAP_RADIUS);
            shader.safeGetUniform("SnowBandBase").set((float) (centerY - SNOW_BAND_BELOW_CAMERA));
            shader.safeGetUniform("SnowBandHeight").set((float) SNOW_BAND_HEIGHT);
            shader.safeGetUniform("SnowLight").set(snowLight);

            mesh.buffer.bind();
            mesh.buffer.drawWithShader(
                    RenderSystem.getModelViewMatrix(),
                    RenderSystem.getProjectionMatrix(),
                    shader
            );
            VertexBuffer.unbind();

            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            return true;
        } catch (RuntimeException exception) {
            gpuDisabled = true;
            WhereWindsBlow.LOGGER.warn(
                    "GPU snowfall failed; snowfall will use the CPU fallback until restart.",
                    exception
            );
            VertexBuffer.unbind();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            return false;
        }
    }

    private static boolean updateHeightTexture(
            ClientLevel level,
            int centerX,
            int centerY,
            int centerZ
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (heightTexture == null) {
            heightTexture = new DynamicTexture(HEIGHT_MAP_SIZE, HEIGHT_MAP_SIZE, false);
            heightTexture.setFilter(false, false);
            minecraft.getTextureManager().register(HEIGHT_TEXTURE, heightTexture);
        }

        if (cachedLevel != level) {
            COLUMN_CACHE.clear();
            BIOME_CACHE.clear();
            heightTextureDirty = true;
        }
        boolean cacheFresh = cachedLevel == level
                && cachedCenterX == centerX
                && cachedCenterY == centerY
                && cachedCenterZ == centerZ
                && !heightTextureDirty;
        if (cacheFresh) {
            return true;
        }

        NativeImage pixels = heightTexture.getPixels();
        if (pixels == null) {
            return false;
        }

        BlockPos.MutableBlockPos biomePos = new BlockPos.MutableBlockPos();
        int minimumHeight = level.getMinBuildHeight();
        for (int textureZ = 0; textureZ < HEIGHT_MAP_SIZE; textureZ++) {
            int z = centerZ + textureZ - HEIGHT_MAP_RADIUS;
            for (int textureX = 0; textureX < HEIGHT_MAP_SIZE; textureX++) {
                int x = centerX + textureX - HEIGHT_MAP_RADIUS;
                int snowBandBase = centerY - SNOW_BAND_BELOW_CAMERA;
                ColumnSample sample = columnSample(level, x, z, snowBandBase, biomePos);
                int surfaceY = sample.surfaceY();
                int snowMask = sample.snowMask();
                int encodedHeight = Mth.clamp(surfaceY - minimumHeight, 0, 65535);
                int low = encodedHeight & 255;
                int high = encodedHeight >>> 8 & 255;
                pixels.setPixelRGBA(
                        textureX,
                        textureZ,
                        FastColor.ABGR32.color(255, snowMask, high, low)
                );
            }
        }
        heightTexture.upload();
        cachedLevel = level;
        cachedCenterX = centerX;
        cachedCenterY = centerY;
        cachedCenterZ = centerZ;
        heightTextureDirty = false;
        return true;
    }

    public static void invalidateColumn(BlockPos pos) {
        COLUMN_CACHE.remove(columnKey(pos.getX(), pos.getZ()));
        markDirtyIfVisible(pos.getX(), pos.getZ());
    }

    public static void invalidateChunk(LevelChunk chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int z = minZ; z < minZ + 16; z++) {
            for (int x = minX; x < minX + 16; x++) {
                COLUMN_CACHE.remove(columnKey(x, z));
            }
        }
        if (cachedCenterX >= minX - HEIGHT_MAP_RADIUS
                && cachedCenterX <= minX + 15 + HEIGHT_MAP_RADIUS
                && cachedCenterZ >= minZ - HEIGHT_MAP_RADIUS
                && cachedCenterZ <= minZ + 15 + HEIGHT_MAP_RADIUS) {
            heightTextureDirty = true;
        }
    }

    private static ColumnSample columnSample(
            ClientLevel level,
            int x,
            int z,
            int snowBandBase,
            BlockPos.MutableBlockPos biomePos
    ) {
        long key = columnKey(x, z);
        ColumnSample cached = COLUMN_CACHE.get(key);
        if (cached != null && cached.snowBandBase() == snowBandBase) {
            return cached;
        }

        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int snowMask = 0;
        for (int band = 0; band < SNOW_BAND_COUNT; band++) {
            int sampleY = snowBandBase + band * SNOW_BAND_HEIGHT + SNOW_BAND_HEIGHT / 2;
            biomePos.set(x, sampleY, z);
            Biome biome = biomeAt(level, biomePos);
            if (biome.hasPrecipitation()
                    && biome.getPrecipitationAt(biomePos) == Biome.Precipitation.SNOW) {
                snowMask |= 1 << band;
            }
        }

        ColumnSample sample = new ColumnSample(snowBandBase, surfaceY, snowMask);
        if (COLUMN_CACHE.size() >= MAX_COLUMN_CACHE_SIZE) {
            COLUMN_CACHE.removeFirst();
        }
        COLUMN_CACHE.put(key, sample);
        return sample;
    }

    private static Biome biomeAt(ClientLevel level, BlockPos pos) {
        int quartX = QuartPos.fromBlock(pos.getX());
        int quartY = QuartPos.fromBlock(pos.getY());
        int quartZ = QuartPos.fromBlock(pos.getZ());
        long key = BlockPos.asLong(quartX, quartY, quartZ);
        Biome cached = BIOME_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        Biome biome = level.getBiome(pos).value();
        if (BIOME_CACHE.size() >= MAX_BIOME_CACHE_SIZE) {
            BIOME_CACHE.removeFirst();
        }
        BIOME_CACHE.put(key, biome);
        return biome;
    }

    private static void markDirtyIfVisible(int x, int z) {
        if (Math.abs(x - cachedCenterX) <= HEIGHT_MAP_RADIUS
                && Math.abs(z - cachedCenterZ) <= HEIGHT_MAP_RADIUS) {
            heightTextureDirty = true;
        }
    }

    private static long columnKey(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    private record ColumnSample(int snowBandBase, int surfaceY, int snowMask) {
    }

    private static final class SnowMesh {
        private final int radius;
        private final int flakesPerColumn;
        private final float verticalSpan;
        private VertexBuffer buffer;

        private SnowMesh(int radius, int flakesPerColumn, float verticalSpan) {
            this.radius = radius;
            this.flakesPerColumn = flakesPerColumn;
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
                    for (int lane = 0; lane < flakesPerColumn; lane++) {
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
