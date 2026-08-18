package com.jvn.wherewindsblow.client.weather;

import static com.jvn.toucanlib.util.ToucanRandom.unitFloat;

import com.jvn.toucanlib.client.ToucanEasing;
import com.jvn.toucanlib.client.ToucanScaledAnimationClock;
import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.WindSample;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Arrays;
import net.minecraft.client.Camera;
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
import org.joml.Vector3f;

/**
 * Renders snowfall as deterministic world-space flakes instead of camera-centered texture sheets.
 * Flakes move through the player, test their own landing column, and fade only at the volume edge.
 */
public final class SnowfallRenderer {
    private static final ResourceLocation SNOW_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/environment/snow.png");
    // Conservative fallback values used only when the persistent GPU snowfall path is unavailable.
    private static final int FANCY_RADIUS = 10;
    private static final int FAST_RADIUS = 6;
    private static final int FANCY_FLAKES_PER_COLUMN = 8;
    private static final int FAST_FLAKES_PER_COLUMN = 4;
    private static final float FANCY_VERTICAL_SPAN = 20.0F;
    private static final float FAST_VERTICAL_SPAN = 14.0F;
    private static final float EDGE_FADE_START = 0.72F;
    private static final float VERTICAL_FADE_DEPTH = 2.0F;
    private static final ToucanScaledAnimationClock SNOWFALL_ANIMATION_CLOCK =
            new ToucanScaledAnimationClock(5.0D);
    private static final FlakeUv[] FLAKE_UVS = {
            flakeUv(30, 45),
            flakeUv(25, 54),
            flakeUv(44, 66),
            flakeUv(12, 80),
            flakeUv(55, 84),
            flakeUv(13, 93),
            flakeUv(46, 105),
            flakeUv(41, 156),
            flakeUv(14, 174),
            flakeUv(42, 194),
            flakeUv(22, 209),
            flakeUv(12, 231)
    };

    private SnowfallRenderer() {
    }

    public static void render(
            ClientLevel level,
            LightTexture lightTexture,
            float rainLevel,
            float thunder,
            double animationTime,
            double camX,
            double camY,
            double camZ
    ) {
        if (!ClientConfig.ENABLE_SNOW_EFFECTS.getAsBoolean() || rainLevel <= 0.0F) {
            return;
        }
        if (GpuSnowfallRenderer.render(
                level, rainLevel, thunder, animationTime, camX, camY, camZ
        )) {
            return;
        }

        boolean fancy = Minecraft.useFancyGraphics();
        int radius = fancy ? FANCY_RADIUS : FAST_RADIUS;
        int flakesPerColumn = fancy ? FANCY_FLAKES_PER_COLUMN : FAST_FLAKES_PER_COLUMN;
        float verticalSpan = fancy ? FANCY_VERTICAL_SPAN : FAST_VERTICAL_SPAN;
        float halfVerticalSpan = verticalSpan * 0.5F;
        int centerX = Mth.floor(camX);
        int centerZ = Mth.floor(camZ);
        int surfaceCacheRadius = radius + 6;
        int surfaceCacheSize = surfaceCacheRadius * 2 + 1;
        int[] surfaceCache = new int[surfaceCacheSize * surfaceCacheSize];
        Arrays.fill(surfaceCache, Integer.MIN_VALUE);
        boolean windDriven = ClientConfig.ENABLE_WIND_DRIVEN_SNOW.getAsBoolean();
        boolean dynamicSqualls = ClientConfig.ENABLE_DYNAMIC_RAIN_SQUALLS.getAsBoolean();
        float squallStrength = (float) ClientConfig.RAIN_SQUALL_STRENGTH.getAsDouble();
        float stormActivity = BlizzardWeatherEffects.blizzardActivity(thunder);
        SnowfallProfile profile = configuredProfile(stormActivity);
        float lull = dynamicSqualls
                ? DynamicWindManager.currentState().lullAmount() * stormActivity
                : 0.0F;
        double flakeAnimationTime = SNOWFALL_ANIMATION_CLOCK.advance(
                animationTime,
                (1.0F + thunder * 0.16F) * profile.speedScale(),
                0.2F
        );

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
        BufferBuilder buffer = null;
        Tesselator tesselator = Tesselator.getInstance();

        lightTexture.turnOnLightLayer();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderTexture(0, SNOW_TEXTURE);

        for (int z = centerZ - radius - 2; z <= centerZ + radius + 2; z++) {
            for (int x = centerX - radius - 2; x <= centerX + radius + 2; x++) {
                samplePos.set(x, camY, z);
                Holder<Biome> biomeHolder = level.getBiome(samplePos);
                if (ClientConfig.ENABLE_DESERT_STORM_EFFECTS.getAsBoolean()
                        && (biomeHolder.is(Tags.Biomes.IS_DESERT)
                                || biomeHolder.is(Tags.Biomes.IS_BADLANDS))) {
                    continue;
                }
                Biome biome = biomeHolder.value();
                if (!biome.hasPrecipitation()
                        || biome.getPrecipitationAt(samplePos) != Biome.Precipitation.SNOW) {
                    continue;
                }

                WindSample wind = DynamicWindManager.sampleWind(level, samplePos);
                float windStrength = Mth.clamp(wind.strength(), 0.0F, 3.0F);
                float squall = dynamicSqualls
                        ? ToucanEasing.smoothstep(Mth.clamp(wind.gustStrength() * 2.4F, 0.0F, 1.0F))
                                * squallStrength * stormActivity
                        : 0.0F;
                float lullFade = dynamicSqualls ? ToucanEasing.smoothstep(lull) * squallStrength : 0.0F;
                float baseDensity = Mth.clamp(
                        0.68F + rainLevel * 0.20F + thunder * 0.08F
                                + squall * 0.12F - lullFade * 0.18F,
                        0.38F,
                        1.0F
                );
                float density = Mth.clamp(baseDensity * profile.densityScale(), 0.0F, 1.0F);
                float opacity = Math.max(0.52F, 1.0F + squall * 0.16F - lullFade * 0.28F);

                for (int lane = 0; lane < flakesPerColumn; lane++) {
                    long hash = flakeHash(x, z, lane);
                    if (unitFloat(hash ^ 0xA0761D6478BD642FL) > density) {
                        continue;
                    }

                    double baseX = x + 0.12D + unitFloat(hash ^ 0xE7037ED1A0B428DBL) * 0.76D;
                    double baseZ = z + 0.12D + unitFloat(hash ^ 0x8EBC6AF09C88C6E3L) * 0.76D;
                    float fallSpeed = Mth.lerp(
                            unitFloat(hash ^ 0x589965CC75374CC3L),
                            0.036F,
                            0.074F
                    );
                    double rawY = unitFloat(hash ^ 0x1D8E4E27C47D124FL) * verticalSpan
                            - flakeAnimationTime * fallSpeed;
                    double minimumY = camY - halfVerticalSpan;
                    double flakeY = minimumY + positiveModulo(
                            rawY - minimumY,
                            verticalSpan
                    );
                    float phase = Mth.clamp(
                            (float) ((camY + halfVerticalSpan - flakeY) / verticalSpan),
                            0.0F,
                            1.0F
                    );

                    float driftRange = windDriven
                            ? 0.45F + windStrength * 2.3F + thunder * 0.85F + squall * 0.75F
                            : 0.0F;
                    float centeredFall = phase - 0.5F;
                    float flutterPhase = (float) animationTime * 0.055F
                            + unitFloat(hash ^ 0xEB44ACCAB455D165L) * Mth.TWO_PI;
                    float flutter = windDriven
                            ? Mth.sin(flutterPhase)
                                    * (0.08F + wind.turbulence() * 0.30F + windStrength * 0.05F)
                            : 0.0F;
                    double flakeX = baseX
                            + wind.directionX() * centeredFall * driftRange
                            - wind.directionZ() * flutter;
                    double flakeZ = baseZ
                            + wind.directionZ() * centeredFall * driftRange
                            + wind.directionX() * flutter;

                    float dx = (float) (flakeX - camX);
                    float dz = (float) (flakeZ - camZ);
                    float horizontalDistance = Mth.sqrt(dx * dx + dz * dz);
                    float edgeVisibility = radialVisibility(horizontalDistance / radius);
                    if (edgeVisibility <= 0.01F) {
                        continue;
                    }

                    float verticalDistance = (float) Math.abs(flakeY - camY);
                    float verticalVisibility = ToucanEasing.smoothstep(Mth.clamp(
                            (halfVerticalSpan - verticalDistance) / VERTICAL_FADE_DEPTH,
                            0.0F,
                            1.0F
                    ));
                    if (verticalVisibility <= 0.01F) {
                        continue;
                    }

                    int flakeBlockX = Mth.floor(flakeX);
                    int flakeBlockZ = Mth.floor(flakeZ);
                    int surfaceY = surfaceHeight(
                            level,
                            flakeBlockX,
                            flakeBlockZ,
                            centerX,
                            centerZ,
                            surfaceCacheRadius,
                            surfaceCacheSize,
                            surfaceCache
                    );
                    if (flakeY <= surfaceY + 0.02D) {
                        continue;
                    }

                    samplePos.set(flakeBlockX, Mth.floor(flakeY), flakeBlockZ);
                    if (level.getBiome(samplePos).value().getPrecipitationAt(samplePos)
                            != Biome.Precipitation.SNOW) {
                        continue;
                    }

                    if (buffer == null) {
                        buffer = tesselator.begin(
                                VertexFormat.Mode.QUADS,
                                DefaultVertexFormat.PARTICLE
                        );
                    }

                    float size = Mth.lerp(
                            unitFloat(hash ^ 0xC6BC279692B5CC83L),
                            0.10F,
                            0.19F
                    ) * profile.sizeScale();
                    float tilt = unitFloat(hash ^ 0xD1B54A32D192ED03L) * Mth.TWO_PI
                            + Mth.sin(flutterPhase * 0.73F) * 0.32F;
                    float alpha = Mth.clamp(
                            rainLevel
                                    * Mth.lerp(
                                            unitFloat(hash ^ 0x94D049BB133111EBL),
                                            0.48F,
                                            0.88F
                                    )
                                    * opacity
                                    * edgeVisibility
                                    * verticalVisibility,
                            0.0F,
                            0.92F
                    );
                    int light = LevelRenderer.getLightColor(level, samplePos);
                    int blockLight = light >> 16 & 65535;
                    int skyLight = light & 65535;
                    int softenedSkyLight = (skyLight * 3 + 240) / 4;
                    int softenedBlockLight = (blockLight * 3 + 224) / 4;
                    FlakeUv uv = FLAKE_UVS[Math.floorMod(
                            (int) (hash ^ hash >>> 32),
                            FLAKE_UVS.length
                    )];
                    addFlakeQuad(
                            buffer,
                            left,
                            up,
                            flakeX - camX,
                            flakeY - camY,
                            flakeZ - camZ,
                            size,
                            tilt,
                            alpha,
                            softenedSkyLight,
                            softenedBlockLight,
                            uv
                    );
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

    private static void addFlakeQuad(
            BufferBuilder buffer,
            Vector3f left,
            Vector3f up,
            double centerX,
            double centerY,
            double centerZ,
            float halfSize,
            float tilt,
            float alpha,
            int skyLight,
            int blockLight,
            FlakeUv uv
    ) {
        float cos = Mth.cos(tilt);
        float sin = Mth.sin(tilt);
        float widthX = (left.x() * cos + up.x() * sin) * halfSize;
        float widthY = (left.y() * cos + up.y() * sin) * halfSize;
        float widthZ = (left.z() * cos + up.z() * sin) * halfSize;
        float heightScale = 1.0F + halfSize * 1.8F;
        float heightX = (up.x() * cos - left.x() * sin) * halfSize * heightScale;
        float heightY = (up.y() * cos - left.y() * sin) * halfSize * heightScale;
        float heightZ = (up.z() * cos - left.z() * sin) * halfSize * heightScale;

        addFlakeVertex(buffer, centerX - widthX - heightX, centerY - widthY - heightY,
                centerZ - widthZ - heightZ, uv.u0(), uv.v1(), alpha, skyLight, blockLight);
        addFlakeVertex(buffer, centerX + widthX - heightX, centerY + widthY - heightY,
                centerZ + widthZ - heightZ, uv.u1(), uv.v1(), alpha, skyLight, blockLight);
        addFlakeVertex(buffer, centerX + widthX + heightX, centerY + widthY + heightY,
                centerZ + widthZ + heightZ, uv.u1(), uv.v0(), alpha, skyLight, blockLight);
        addFlakeVertex(buffer, centerX - widthX + heightX, centerY - widthY + heightY,
                centerZ - widthZ + heightZ, uv.u0(), uv.v0(), alpha, skyLight, blockLight);
    }

    private static void addFlakeVertex(
            BufferBuilder buffer,
            double x,
            double y,
            double z,
            float u,
            float v,
            float alpha,
            int skyLight,
            int blockLight
    ) {
        buffer.addVertex((float) x, (float) y, (float) z)
                .setUv(u, v)
                .setColor(0.94F, 0.97F, 1.0F, alpha)
                .setUv2(skyLight, blockLight);
    }

    static SnowfallProfile configuredProfile(float stormActivity) {
        float blend = Mth.clamp(stormActivity, 0.0F, 1.0F);
        float amount = blendedConfig(
                ClientConfig.SNOWFLAKE_AMOUNT, ClientConfig.BLIZZARD_SNOWFLAKE_AMOUNT, blend
        );
        return new SnowfallProfile(
                amount / (float) ClientConfig.SNOWFLAKE_AMOUNT_MAX,
                blendedConfig(ClientConfig.SNOWFLAKE_SIZE, ClientConfig.BLIZZARD_SNOWFLAKE_SIZE, blend),
                blendedConfig(ClientConfig.SNOWFLAKE_SPEED, ClientConfig.BLIZZARD_SNOWFLAKE_SPEED, blend)
        );
    }

    private static float blendedConfig(
            net.neoforged.neoforge.common.ModConfigSpec.DoubleValue ordinary,
            net.neoforged.neoforge.common.ModConfigSpec.DoubleValue blizzard,
            float blend
    ) {
        return Mth.lerp(
                blend, (float) ordinary.getAsDouble(), (float) blizzard.getAsDouble()
        );
    }

    private static float radialVisibility(float normalizedDistance) {
        float edge = (normalizedDistance - EDGE_FADE_START) / (1.0F - EDGE_FADE_START);
        return 1.0F - ToucanEasing.smoothstep(edge);
    }

    private static double positiveModulo(double value, double modulus) {
        return value - Math.floor(value / modulus) * modulus;
    }

    private static int surfaceHeight(
            ClientLevel level,
            int x,
            int z,
            int centerX,
            int centerZ,
            int cacheRadius,
            int cacheSize,
            int[] cache
    ) {
        int localX = x - centerX + cacheRadius;
        int localZ = z - centerZ + cacheRadius;
        if (localX < 0 || localX >= cacheSize || localZ < 0 || localZ >= cacheSize) {
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        }

        int index = localZ * cacheSize + localX;
        int height = cache[index];
        if (height == Integer.MIN_VALUE) {
            height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            cache[index] = height;
        }
        return height;
    }

    private static long flakeHash(int x, int z, int lane) {
        long hash = x * 0x9E3779B97F4A7C15L;
        hash ^= z * 0xC2B2AE3D27D4EB4FL;
        hash ^= (long) lane * 0x165667B19E3779F9L;
        hash ^= hash >>> 29;
        return hash;
    }

    private static FlakeUv flakeUv(int centerX, int centerY) {
        return new FlakeUv(
                (centerX - 2.5F) / 64.0F,
                (centerY - 2.5F) / 256.0F,
                (centerX + 2.5F) / 64.0F,
                (centerY + 2.5F) / 256.0F
        );
    }

    record SnowfallProfile(float densityScale, float sizeScale, float speedScale) {
    }

    private record FlakeUv(float u0, float v0, float u1, float v1) {
    }
}
