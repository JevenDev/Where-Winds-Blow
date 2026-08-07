package com.jvn.wherewindsblow.client.weather;

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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
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
    private static final ResourceLocation HEIGHT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            WhereWindsBlow.MOD_ID,
            "dynamic/gpu_snowfall_height"
    );
    private static final int HEIGHT_MAP_RADIUS = 20;
    private static final int HEIGHT_MAP_SIZE = HEIGHT_MAP_RADIUS * 2 + 1;
    private static final int HEIGHT_REFRESH_TICKS = 5;
    private static final int SNOW_BAND_COUNT = 8;
    private static final int SNOW_BAND_HEIGHT = 3;
    private static final int SNOW_BAND_BELOW_CAMERA = 12;
    private static final int WORLD_HASH_PERIOD = 8192;
    private static final SnowMesh FANCY_MESH = new SnowMesh(12, 12, 22.0F);
    private static final SnowMesh FAST_MESH = new SnowMesh(7, 6, 14.0F);

    private static DynamicTexture heightTexture;
    private static ClientLevel cachedLevel;
    private static int cachedCenterX = Integer.MIN_VALUE;
    private static int cachedCenterY = Integer.MIN_VALUE;
    private static int cachedCenterZ = Integer.MIN_VALUE;
    private static long lastHeightRefreshTick = Long.MIN_VALUE;
    private static double snowfallAnimationTime;
    private static double lastSnowfallAnimationTime = Double.NaN;
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
                    ? smoothFade(Mth.clamp(wind.gustStrength() * 2.4F, 0.0F, 1.0F))
                            * squallStrength * stormActivity
                    : 0.0F;
            float lull = dynamicSqualls
                    ? smoothFade(DynamicWindManager.currentState().lullAmount())
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
            double snowTime = advanceSnowfallAnimationTime(
                    animationTime,
                    (1.0F + thunder * 0.16F) * profile.speedScale()
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

        long gameTime = level.getGameTime();
        boolean cacheFresh = cachedLevel == level
                && cachedCenterX == centerX
                && cachedCenterY == centerY
                && cachedCenterZ == centerZ
                && gameTime >= lastHeightRefreshTick
                && gameTime - lastHeightRefreshTick < HEIGHT_REFRESH_TICKS;
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
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int snowMask = 0;
                int snowBandBase = centerY - SNOW_BAND_BELOW_CAMERA;
                for (int band = 0; band < SNOW_BAND_COUNT; band++) {
                    int sampleY = snowBandBase + band * SNOW_BAND_HEIGHT + SNOW_BAND_HEIGHT / 2;
                    biomePos.set(x, sampleY, z);
                    Biome biome = level.getBiome(biomePos).value();
                    if (biome.hasPrecipitation()
                            && biome.getPrecipitationAt(biomePos) == Biome.Precipitation.SNOW) {
                        snowMask |= 1 << band;
                    }
                }
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
        lastHeightRefreshTick = gameTime;
        return true;
    }

    private static double advanceSnowfallAnimationTime(
            double animationTime,
            float speedMultiplier
    ) {
        if (!Double.isFinite(lastSnowfallAnimationTime)) {
            snowfallAnimationTime = animationTime;
            lastSnowfallAnimationTime = animationTime;
            return snowfallAnimationTime;
        }

        double delta = animationTime - lastSnowfallAnimationTime;
        lastSnowfallAnimationTime = animationTime;
        if (delta < 0.0D || delta > 5.0D) {
            snowfallAnimationTime = animationTime;
        } else {
            snowfallAnimationTime += delta * Math.max(speedMultiplier, 0.2F);
        }
        return snowfallAnimationTime;
    }

    private static float smoothFade(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);
        return value * value * (3.0F - 2.0F * value);
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
