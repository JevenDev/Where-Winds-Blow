package com.jvn.wherewindsblow.client.wind;

import com.mojang.blaze3d.systems.RenderSystem;
import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliage;
import com.jvn.wherewindsblow.client.weather.BlizzardWeatherEffects;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.Tags;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

public final class WindStreakRenderer {
    private static final int MAX_STREAKS = 20;
    private static final int MAX_LEAVES = 18;
    private static final int MAX_FLOWER_PETALS = 18;
    private static final int LEAF_TEXTURES_PER_TYPE = 12;
    private static final String[] LEAF_TEXTURE_TYPES = {
            "oak", "spruce", "birch", "jungle", "acacia",
            "dark_oak", "mangrove", "cherry", "azalea", "flowering_azalea"
    };
    private static final int LEAF_TEXTURE_COUNT = LEAF_TEXTURES_PER_TYPE * LEAF_TEXTURE_TYPES.length;
    private static final int ROSE_PETAL_TEXTURE_COUNT = 11;
    private static final int DANDELION_PETAL_TEXTURE_COUNT = 3;
    private static final int AZURE_BLUET_PETAL_TEXTURE_COUNT = 6;
    private static final int FLOWER_PETAL_TEXTURE_COUNT = ROSE_PETAL_TEXTURE_COUNT
            + DANDELION_PETAL_TEXTURE_COUNT
            + AZURE_BLUET_PETAL_TEXTURE_COUNT;
    private static final int BODY_SEGMENTS = 24;
    private static final int LEAF_BLOCK_SPAWN_ATTEMPTS = 22;
    private static final double MAX_DISTANCE_FROM_PLAYER = 56.0D;
    private static final double TERRAIN_LOOKAHEAD = 6.5D;
    private static final double TERRAIN_SIDE_SAMPLE = 3.6D;
    private static final double TERRAIN_FLOW_SMOOTHING = 0.56D;
    private static final double TERRAIN_SIDE_PUSH = 0.026D;
    private static final double STREAK_TERRAIN_CLEARANCE = 1.45D;
    private static final double STREAK_TERRAIN_LIFT_STRENGTH = 0.18D;
    private static final double STREAK_PATH_ADVECTION = 0.34D;
    private static final double STREAK_MIN_STROKE_SCALE = 0.58D;
    private static final double STREAK_HALO_WIDTH_SCALE = 1.35D;
    private static final double STREAK_CORE_WIDTH_SCALE = 0.56D;
    private static final float STREAK_HALO_OPACITY = 0.2F;
    private static final double STREAK_MIN_HORIZONTAL_SPACING = 7.5D;
    private static final double STREAK_MIN_VERTICAL_SPACING = 2.8D;
    private static final double LEAF_TERRAIN_CLEARANCE = 0.78D;
    private static final double LEAF_TERRAIN_LIFT_STRENGTH = 0.28D;
    private static final int LEAF_SIDE_ACCESS_RADIUS = 5;
    private static final double MAX_TERRAIN_LIFT = 0.58D;
    private static final double MAX_TERRAIN_SIDE_FLOW = 0.18D;
    private static final int SPAWN_ATTEMPTS = 12;
    private static final int STREAK_FADE_IN_TICKS = 26;
    private static final int FADE_OUT_TICKS = 18;
    private static final int WIND_SAMPLE_INTERVAL_TICKS = 2;
    private static final int MIN_OPEN_SKY_LIGHT = 14;
    private static final double SURFACE_TOLERANCE = 0.08D;
    private static final int RED = 232;
    private static final int GREEN = 246;
    private static final int BLUE = 255;
    private static final RenderLevelStageEvent.Stage RENDER_STAGE = RenderLevelStageEvent.Stage.AFTER_LEVEL;
    private static final Random RANDOM = new Random();
    private static final WindStreak[] STREAKS = new WindStreak[MAX_STREAKS];
    private static final WindLeaf[] LEAVES = new WindLeaf[MAX_LEAVES];
    private static final WindLeaf[] FLOWER_PETALS = new WindLeaf[MAX_FLOWER_PETALS];
    private static final RenderType[] LEAF_RENDER_TYPES = new RenderType[LEAF_TEXTURE_COUNT];
    private static final RenderType[] FLOWER_PETAL_RENDER_TYPES = new RenderType[FLOWER_PETAL_TEXTURE_COUNT];
    private static final ByteBufferBuilder LINE_BUFFER = new ByteBufferBuilder(65536);
    private static final ByteBufferBuilder LEAF_BUFFER = new ByteBufferBuilder(65536);
    private static final Map<Double, RenderType> LINE_RENDER_TYPES = new HashMap<>();
    private static final RenderStateShard.ShaderStateShard PARTICLE_SHADER =
            new RenderStateShard.ShaderStateShard(GameRenderer::getParticleShader);

    static {
        for (int index = 0; index < STREAKS.length; index++) {
            STREAKS[index] = new WindStreak();
        }

        for (int index = 0; index < LEAVES.length; index++) {
            LEAVES[index] = new WindLeaf();
        }

        for (int index = 0; index < FLOWER_PETALS.length; index++) {
            FLOWER_PETALS[index] = new WindLeaf();
        }

        for (int index = 0; index < LEAF_RENDER_TYPES.length; index++) {
            LEAF_RENDER_TYPES[index] = windLeafTexture(leafTexture(index));
        }

        for (int index = 0; index < FLOWER_PETAL_RENDER_TYPES.length; index++) {
            FLOWER_PETAL_RENDER_TYPES[index] = windLeafTexture(flowerPetalTexture(index));
        }
    }

    private WindStreakRenderer() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || !ClientConfig.ENABLE_WIND_STREAKS.getAsBoolean()) {
            clear();
            return;
        }

        if (minecraft.isPaused()) {
            return;
        }

        boolean canSpawnWind = shouldRenderAround(level, player);
        WindSample playerWind = DynamicWindManager.sampleWind(level, player.blockPosition());
        int desiredCount = canSpawnWind ? desiredStreakCount(playerWind) : 0;
        int desiredLeafCount = canSpawnWind ? desiredLeafCount(playerWind) : 0;
        boolean canSpawnFlowerPetals = canSpawnWind
                && ClientConfig.ENABLE_WIND_FLOWER_PETALS.getAsBoolean()
                && isFlowerPetalBiome(level, player.blockPosition());
        int desiredFlowerPetalCount = canSpawnFlowerPetals ? desiredFlowerPetalCount(playerWind) : 0;
        for (int index = 0; index < STREAKS.length; index++) {
            WindStreak streak = STREAKS[index];
            if (index >= desiredCount) {
                beginFadeOut(streak);
                tickFadeOut(streak);
                continue;
            }

            if (!streak.active) {
                spawn(streak, player, level, playerWind, true);
            } else {
                tick(streak, player, level);
            }
        }

        for (int index = 0; index < LEAVES.length; index++) {
            WindLeaf leaf = LEAVES[index];
            if (index >= desiredLeafCount) {
                beginFadeOut(leaf);
                tickFadeOut(leaf);
                continue;
            }

            if (!leaf.active) {
                spawn(leaf, player, level, playerWind, true);
            } else {
                tick(leaf, player, level, playerWind);
            }
        }

        for (int index = 0; index < FLOWER_PETALS.length; index++) {
            WindLeaf petal = FLOWER_PETALS[index];
            if (index >= desiredFlowerPetalCount) {
                beginFadeOut(petal);
                tickFadeOut(petal);
                continue;
            }

            if (!petal.active) {
                spawnFlowerPetal(petal, player, level, playerWind, true);
            } else {
                tick(petal, player, level, playerWind);
            }
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RENDER_STAGE
                || !ClientConfig.ENABLE_WIND_STREAKS.getAsBoolean()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !hasActiveWind()) {
            return;
        }

        float visibility = (float) ClientConfig.WIND_STREAK_VISIBILITY.getAsDouble();
        if (visibility <= 0.0F) {
            return;
        }

        float lineOpacity = (float) ClientConfig.WIND_STREAK_OPACITY.getAsDouble();
        float leafOpacity = (float) ClientConfig.WIND_LEAF_OPACITY.getAsDouble();
        float flowerPetalOpacity = (float) ClientConfig.WIND_FLOWER_PETAL_OPACITY.getAsDouble();
        if (lineOpacity <= 0.0F && leafOpacity <= 0.0F && flowerPetalOpacity <= 0.0F) {
            return;
        }

        minecraft.getMainRenderTarget().bindWrite(false);

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();

        try {
            Camera camera = event.getCamera();
            Vec3 cameraPos = camera.getPosition();
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            BlizzardWeatherEffects.BlizzardFogProfile blizzardFog =
                    BlizzardWeatherEffects.fogProfile(camera, partialTick);
            float windTime = DynamicWindManager.simulationTime();
            Matrix4f pose = event.getPoseStack().last().pose();

            if (lineOpacity > 0.0F) {
                MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(LINE_BUFFER);
                for (WindStreak streak : STREAKS) {
                    if (streak.active) {
                        VertexConsumer halo = bufferSource.getBuffer(windStreakLines(
                                streak.strokeScale * STREAK_HALO_WIDTH_SCALE
                        ));
                        renderStreak(
                                halo,
                                pose,
                                minecraft.level,
                                streak,
                                cameraPos,
                                partialTick,
                                windTime,
                                lineOpacity * STREAK_HALO_OPACITY,
                                blizzardFog
                        );
                    }
                }

                for (WindStreak streak : STREAKS) {
                    if (streak.active) {
                        VertexConsumer core = bufferSource.getBuffer(windStreakLines(
                                streak.strokeScale * STREAK_CORE_WIDTH_SCALE
                        ));
                        renderStreak(
                                core, pose, minecraft.level, streak, cameraPos,
                                partialTick, windTime, lineOpacity, blizzardFog
                        );
                    }
                }

                bufferSource.endBatch();
            }

            if (leafOpacity > 0.0F) {
                renderLeaves(
                        minecraft.level, camera, pose, cameraPos,
                        partialTick, windTime, leafOpacity, blizzardFog
                );
            }

            if (flowerPetalOpacity > 0.0F) {
                renderFlowerPetals(
                        minecraft.level, camera, pose, cameraPos,
                        partialTick, windTime, flowerPetalOpacity, blizzardFog
                );
            }
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    public static void reset() {
        clear();
    }

    private static void clear() {
        for (WindStreak streak : STREAKS) {
            streak.active = false;
            streak.fadingOut = false;
            streak.fadeInAge = 0;
            streak.fadeOutAge = 0;
        }

        for (WindLeaf leaf : LEAVES) {
            leaf.active = false;
            leaf.fadingOut = false;
            leaf.fadeOutAge = 0;
        }

        for (WindLeaf petal : FLOWER_PETALS) {
            petal.active = false;
            petal.fadingOut = false;
            petal.fadeOutAge = 0;
        }
    }

    private static boolean hasActiveWind() {
        for (WindStreak streak : STREAKS) {
            if (streak.active) {
                return true;
            }
        }

        for (WindLeaf leaf : LEAVES) {
            if (leaf.active) {
                return true;
            }
        }

        for (WindLeaf petal : FLOWER_PETALS) {
            if (petal.active) {
                return true;
            }
        }

        return false;
    }

    private static boolean shouldRenderAround(ClientLevel level, LocalPlayer player) {
        return level.dimensionType().hasSkyLight()
                && !player.isUnderWater()
                && isValidWindSpace(level, player.getX(), player.getEyeY(), player.getZ(), 0.0D);
    }

    private static int desiredStreakCount(WindSample wind) {
        float visibility = (float) ClientConfig.WIND_STREAK_VISIBILITY.getAsDouble();
        if (visibility <= 0.0F || wind.strength() <= 0.015F) {
            return 0;
        }

        float lineDensity = (float) ClientConfig.WIND_LINE_DENSITY.getAsDouble();
        return Mth.clamp(
                Math.round((2.0F + wind.strength() * 8.0F + wind.gustStrength() * 7.0F)
                        * Math.min(visibility, 1.6F) * lineDensity),
                0,
                MAX_STREAKS
        );
    }

    private static int desiredLeafCount(WindSample wind) {
        if (!ClientConfig.ENABLE_WIND_LEAVES.getAsBoolean()) {
            return 0;
        }

        float visibility = (float) ClientConfig.WIND_STREAK_VISIBILITY.getAsDouble();
        if (visibility <= 0.0F || wind.strength() <= 0.02F) {
            return 0;
        }

        float leafDensity = (float) ClientConfig.WIND_LEAF_DENSITY.getAsDouble();
        return Mth.clamp(
                Math.round((1.0F + wind.strength() * 6.0F + wind.gustStrength() * 5.0F)
                        * Math.min(visibility, 1.8F) * leafDensity),
                0,
                MAX_LEAVES
        );
    }

    private static int desiredFlowerPetalCount(WindSample wind) {
        float visibility = (float) ClientConfig.WIND_STREAK_VISIBILITY.getAsDouble();
        if (visibility <= 0.0F || wind.strength() <= 0.02F) {
            return 0;
        }

        float petalDensity = (float) ClientConfig.WIND_FLOWER_PETAL_DENSITY.getAsDouble();
        return Mth.clamp(
                Math.round((1.0F + wind.strength() * 5.0F + wind.gustStrength() * 6.0F)
                        * Math.min(visibility, 1.8F) * petalDensity),
                0,
                MAX_FLOWER_PETALS
        );
    }

    private static boolean isFlowerPetalBiome(ClientLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        return !biome.is(Tags.Biomes.IS_SNOWY)
                && (biome.is(Tags.Biomes.IS_PLAINS) || biome.is(Tags.Biomes.IS_FLORAL));
    }

    private static RenderType windStreakLines(double strokeScale) {
        double maxLineWidth = ClientConfig.WIND_STREAK_THICKNESS.getAsDouble();
        double scaledLineWidth = maxLineWidth * Mth.clamp(strokeScale, 0.32D, 1.35D);
        double lineWidth = Math.round(Math.max(0.5D, scaledLineWidth) * 4.0D) / 4.0D;
        return LINE_RENDER_TYPES.computeIfAbsent(lineWidth, width -> RenderType.create(
                "where_winds_blow_wind_streaks_" + width,
                DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES,
                65536,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(WindVisualShaders.windStreakShaderState())
                        .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(width)))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false)
        ));
    }

    private static ResourceLocation leafTexture(int index) {
        int typeIndex = index / LEAF_TEXTURES_PER_TYPE;
        int variant = index % LEAF_TEXTURES_PER_TYPE;
        String path = "textures/particle/leaves/" + LEAF_TEXTURE_TYPES[typeIndex] + "_" + variant + ".png";
        return WhereWindsBlow.IDS.id(path);
    }

    private static ResourceLocation flowerPetalTexture(int index) {
        String path;
        if (index < ROSE_PETAL_TEXTURE_COUNT) {
            path = "textures/particle/rose_petal_" + index + ".png";
        } else {
            int flowerIndex = index - ROSE_PETAL_TEXTURE_COUNT;
            path = flowerIndex < DANDELION_PETAL_TEXTURE_COUNT
                    ? "textures/particle/dandelion_" + flowerIndex + ".png"
                    : "textures/particle/azure_bluet_"
                            + (flowerIndex - DANDELION_PETAL_TEXTURE_COUNT) + ".png";
        }
        return WhereWindsBlow.IDS.id(path);
    }

    private static RenderType windLeafTexture(ResourceLocation texture) {
        return RenderType.create(
                "where_winds_blow_wind_leaf_" + texture.getPath().replace('/', '_').replace('.', '_'),
                DefaultVertexFormat.PARTICLE,
                VertexFormat.Mode.QUADS,
                65536,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(PARTICLE_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false)
        );
    }

    private static void tick(WindStreak streak, LocalPlayer player, ClientLevel level) {
        streak.xOld = streak.x;
        streak.yOld = streak.y;
        streak.zOld = streak.z;

        if (streak.fadingOut) {
            tickFadeOut(streak);
            return;
        }

        streak.age++;
        refreshWind(streak, level);
        if (streak.fadingOut) {
            return;
        }
        if (streak.fadeInAge < STREAK_FADE_IN_TICKS) {
            streak.fadeInAge++;
        }

        if (!isOpenSkyAir(level, streak.x, streak.y, streak.z)) {
            beginFadeOut(streak);
            return;
        }

        if (streakBodyHitsCollision(level, streak, DynamicWindManager.simulationTime())) {
            beginFadeOut(streak);
            return;
        }

        double dx = streak.x - player.getX();
        double dy = streak.y - player.getEyeY();
        double dz = streak.z - player.getZ();
        if (streak.age >= streak.lifetime) {
            streak.active = false;
            return;
        }
        if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_FROM_PLAYER * MAX_DISTANCE_FROM_PLAYER) {
            beginFadeOut(streak);
            return;
        }

        TerrainFlow terrainFlow = terrainFlow(
                level,
                streak.x,
                streak.y,
                streak.z,
                STREAK_TERRAIN_CLEARANCE,
                STREAK_TERRAIN_LIFT_STRENGTH,
                streak.terrainLift,
                streak.terrainSideFlow,
                streak.windX,
                streak.windZ
        );
        streak.terrainLift = terrainFlow.lift();
        streak.terrainSideFlow = terrainFlow.side();
        double motionScale = Mth.clamp(0.34D + streak.windStrength * 1.3D + streak.gustStrength * 0.72D, 0.18D, 3.1D);
        // Move the supporting path with the air as well as sending the bright pulse
        // down it. A little slip keeps the stroke readable without making it look
        // painted onto the world.
        double driftSpeed = streak.driftSpeed * motionScale * STREAK_PATH_ADVECTION;
        double turbulenceScale = 0.55D + Mth.clamp(streak.turbulence * 2.4D, 0.0D, 1.8D);
        double sideDrift = streak.terrainSideFlow * STREAK_PATH_ADVECTION
                + Math.sin(streak.seed + (double) streak.age * 0.045D) * streak.crossDrift * turbulenceScale;
        Point next = keepAboveTerrainAndCollision(
                level,
                streak.x + streak.windX * driftSpeed + streak.crossX * sideDrift,
                streak.y + streak.terrainLift * STREAK_PATH_ADVECTION,
                streak.z + streak.windZ * driftSpeed + streak.crossZ * sideDrift,
                STREAK_TERRAIN_CLEARANCE
        );
        if (streakBodyHitsCollision(
                level,
                streak,
                next.x(),
                next.y(),
                next.z(),
                DynamicWindManager.simulationTime()
        )) {
            beginFadeOut(streak);
            return;
        }

        streak.x = next.x();
        streak.y = next.y();
        streak.z = next.z();
    }

    private static void spawn(WindStreak streak, LocalPlayer player, ClientLevel level, WindSample playerWind, boolean scatterAge) {
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            Point spawn = randomWindStreakPosition(streak, player, level, playerWind);
            if (spawn == null) {
                break;
            }

            WindSample localWind = sampleWind(level, spawn.x(), spawn.y(), spawn.z());
            if (!acceptSpawnForWind(localWind)) {
                continue;
            }
            applyWind(streak, localWind, true);
            double windBoost = windBoost(localWind);
            double gustBoost = Mth.clamp(localWind.gustStrength() / 1.6D, 0.0D, 1.0D);
            double flowEnergy = Mth.clamp(windBoost * 0.72D + gustBoost * 0.78D, 0.0D, 1.35D);

            streak.x = spawn.x();
            streak.y = spawn.y();
            streak.z = spawn.z();
            streak.xOld = streak.x;
            streak.yOld = streak.y;
            streak.zOld = streak.z;
            streak.length = randomBetween(6.2D, 10.4D) * (0.9D + flowEnergy * 0.24D);
            streak.arc = randomBetween(-0.34D, 0.34D) * (1.0D + localWind.turbulence() * 0.48D);
            streak.lift = randomBetween(0.02D, 0.12D) * (1.0D + localWind.turbulence() * 0.4D);
            streak.speed = randomBetween(0.021D, 0.033D) * (0.82D + flowEnergy * 0.42D);
            streak.driftSpeed = randomBetween(0.052D, 0.098D) * (0.84D + flowEnergy * 0.38D);
            streak.crossDrift = randomBetween(0.0012D, 0.004D) * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
            streak.curveStrength = randomBetween(0.018D, 0.105D) * (1.0D + localWind.turbulence() * 1.4D);
            streak.curvePhase = RANDOM.nextDouble() * Math.PI * 2.0D;
            streak.curveFrequency = randomBetween(0.55D, 0.95D);
            streak.curveSign = RANDOM.nextBoolean() ? 1.0D : -1.0D;
            streak.rippleStrength = randomBetween(0.006D, 0.025D) * (1.0D + localWind.turbulence() * 1.8D);
            streak.rippleFrequency = randomBetween(1.2D, 2.25D);
            streak.ripplePhase = RANDOM.nextDouble() * Math.PI * 2.0D;
            streak.verticalRipple = randomBetween(0.003D, 0.016D) * (1.0D + localWind.turbulence());
            streak.brushWidth = randomBetween(0.1D, 0.17D) * (1.0D + gustBoost * 0.18D);
            streak.wakeLength = randomBetween(0.38D, 0.58D) * (1.0D + flowEnergy * 0.16D);
            streak.shimmerStrength = randomBetween(0.018D, 0.052D);
            streak.strokeScale = randomBetween(STREAK_MIN_STROKE_SCALE, 0.9D)
                    * (1.0D + gustBoost * 0.12D);
            streak.seed = RANDOM.nextDouble() * Math.PI * 2.0D;
            streak.terrainLift = 0.0D;
            streak.terrainSideFlow = 0.0D;
            streak.lifetime = Math.max(
                    56,
                    (int) Math.ceil((1.18D + streak.wakeLength) / streak.speed)
            );
            streak.age = scatterAge ? RANDOM.nextInt(Math.max(1, streak.lifetime / 2)) : 0;
            streak.fadingOut = false;
            streak.fadeInAge = 0;
            streak.fadeOutAge = 0;
            streak.active = true;
            if (!isTooCloseToActiveStreak(streak, streak.x, streak.y, streak.z)
                    && !streakBodyHitsCollision(level, streak, DynamicWindManager.simulationTime())) {
                return;
            }
        }

        streak.active = false;
        streak.fadingOut = false;
    }

    private static Point randomWindStreakPosition(
            WindStreak target,
            LocalPlayer player,
            ClientLevel level,
            WindSample playerWind
    ) {
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double along = RANDOM.nextDouble() < 0.72D
                    ? randomBetween(-30.0D, -6.0D)
                    : randomBetween(-8.0D, 18.0D);
            double across = triangularRandom() * randomBetween(12.0D, 34.0D);
            double x = player.getX() + playerWind.directionX() * along + playerWind.crossX() * across;
            double z = player.getZ() + playerWind.directionZ() * along + playerWind.crossZ() * across;

            double terrainY = terrainHeight(level, x, z) + STREAK_TERRAIN_CLEARANCE;
            double groundLayer = terrainY + randomElevationAboveTerrain();
            double eyeLayer = player.getEyeY() + randomBetween(-0.9D, 4.8D);
            double blend = smoothFade((float) Math.pow(RANDOM.nextDouble(), 1.65D));
            double y = Mth.lerp(blend, groundLayer, Math.max(groundLayer, eyeLayer));

            Point spawn = keepAboveTerrainAndCollision(level, x, y, z, STREAK_TERRAIN_CLEARANCE);
            if (isValidWindSpace(level, spawn.x(), spawn.y(), spawn.z(), STREAK_TERRAIN_CLEARANCE)
                    && !isTooCloseToActiveStreak(target, spawn.x(), spawn.y(), spawn.z())) {
                return spawn;
            }
        }

        return null;
    }

    private static double randomElevationAboveTerrain() {
        double lift = 0.28D + Math.pow(RANDOM.nextDouble(), 1.7D) * 3.4D;
        if (RANDOM.nextDouble() < 0.08D) {
            lift += randomBetween(1.6D, 3.8D);
        }

        return lift;
    }

    private static boolean isTooCloseToActiveStreak(WindStreak target, double x, double y, double z) {
        double minHorizontalDistanceSquared = STREAK_MIN_HORIZONTAL_SPACING * STREAK_MIN_HORIZONTAL_SPACING;
        for (WindStreak streak : STREAKS) {
            if (streak == target || !streak.active) {
                continue;
            }

            double dx = streak.x - x;
            double dz = streak.z - z;
            if (dx * dx + dz * dz < minHorizontalDistanceSquared
                    && Math.abs(streak.y - y) < STREAK_MIN_VERTICAL_SPACING) {
                return true;
            }
        }

        return false;
    }

    private static void tick(WindLeaf leaf, LocalPlayer player, ClientLevel level, WindSample playerWind) {
        leaf.xOld = leaf.x;
        leaf.yOld = leaf.y;
        leaf.zOld = leaf.z;

        if (leaf.fadingOut) {
            tickFadeOut(leaf);
            return;
        }

        leaf.age++;
        refreshWind(leaf, level);

        if (!isValidLeafSpace(level, leaf.x, leaf.y, leaf.z, 0.0D)) {
            beginFadeOut(leaf);
            return;
        }

        double motionScale = Mth.clamp(0.42D + leaf.windStrength * 1.18D + leaf.gustStrength * 0.78D, 0.2D, 3.25D);
        double speed = leaf.speed * motionScale;
        double leafTime = (double) leaf.age + leaf.seed;
        double turbulenceMotion = 0.72D + Mth.clamp(leaf.turbulence * 3.2D, 0.0D, 2.1D);
        double weave = (
                Math.sin(leafTime * 0.09D)
                        + Math.sin(leaf.swirlPhase + leafTime * 0.027D) * 0.46D
        ) * leaf.crossDrift * turbulenceMotion;
        double swirlAngle = leaf.swirlPhase + leafTime * leaf.swirlSpeed;
        double loopAngle = leaf.loopPhase + leafTime * leaf.loopSpeed;
        double swirl = Math.sin(swirlAngle) * leaf.swirlStrength * turbulenceMotion;
        double loop = Math.sin(loopAngle) * leaf.loopStrength * turbulenceMotion;
        double loopLift = Math.cos(loopAngle) * leaf.loopStrength * 0.72D * turbulenceMotion;
        TerrainFlow terrainFlow = terrainFlow(
                level,
                leaf.x,
                leaf.y,
                leaf.z,
                LEAF_TERRAIN_CLEARANCE,
                LEAF_TERRAIN_LIFT_STRENGTH,
                leaf.terrainLift,
                leaf.terrainSideFlow,
                leaf.windX,
                leaf.windZ
        );
        leaf.terrainLift = terrainFlow.lift();
        leaf.terrainSideFlow = terrainFlow.side();
        double flutter = (
                Math.sin(leafTime * 0.084D)
                        + Math.sin(leaf.flipPhase + leafTime * 0.17D) * 0.54D
        ) * leaf.verticalDrift * turbulenceMotion;
        double forwardPulse = Math.cos(swirlAngle) * leaf.swirlStrength * 0.16D;
        double sideDrift = weave + swirl + loop + leaf.terrainSideFlow;
        double lullDescent = Mth.lerp(Mth.clamp(leaf.windStrength / 0.32D, 0.0D, 1.0D), 1.72D, 0.82D);
        double fall = leaf.fallSpeed * lullDescent;
        Point next = keepAboveTerrainAndCollision(
                level,
                leaf.x + leaf.windX * (speed + forwardPulse) + leaf.crossX * sideDrift,
                leaf.y + leaf.terrainLift + flutter + Math.cos(swirlAngle) * leaf.swirlStrength * 0.42D + loopLift - fall,
                leaf.z + leaf.windZ * (speed + forwardPulse) + leaf.crossZ * sideDrift,
                LEAF_TERRAIN_CLEARANCE
        );
        if (windPathBlocked(level, leaf.x, leaf.y, leaf.z, next)) {
            beginFadeOut(leaf);
            return;
        }

        leaf.x = next.x();
        leaf.y = next.y();
        leaf.z = next.z();

        double dx = leaf.x - player.getX();
        double dy = leaf.y - player.getEyeY();
        double dz = leaf.z - player.getZ();
        if (leaf.age >= leaf.lifetime
                || dx * dx + dy * dy + dz * dz > MAX_DISTANCE_FROM_PLAYER * MAX_DISTANCE_FROM_PLAYER) {
            if (leaf.flowerPetal) {
                spawnFlowerPetal(leaf, player, level, playerWind, false);
            } else {
                spawn(leaf, player, level, playerWind, false);
            }
        }
    }

    private static void spawn(WindLeaf leaf, LocalPlayer player, ClientLevel level, WindSample playerWind, boolean scatterAge) {
        LeafBlockSpawn leafBlockSpawn = RANDOM.nextDouble() < 0.58D
                ? randomLeafBlockSpawn(player, level, playerWind)
                : null;
        Point spawn = leafBlockSpawn != null ? leafBlockSpawn.position() : randomOpenAirPosition(
                player,
                level,
                playerWind,
                -26.0D,
                18.0D,
                -28.0D,
                28.0D,
                -0.8D,
                6.2D,
                0.3D,
                1.8D,
                LEAF_TERRAIN_CLEARANCE,
                0.05D,
                1.15D
        );
        if (spawn == null) {
            leaf.active = false;
            return;
        }

        WindSample localWind = sampleWind(level, spawn.x(), spawn.y(), spawn.z());
        if (!acceptSpawnForWind(localWind)) {
            leaf.active = false;
            return;
        }
        applyWind(leaf, localWind, true);
        double windBoost = windBoost(localWind);

        leaf.x = spawn.x();
        leaf.y = spawn.y();
        leaf.z = spawn.z();
        leaf.xOld = leaf.x;
        leaf.yOld = leaf.y;
        leaf.zOld = leaf.z;
        leaf.flowerPetal = false;
        BlockPos leafColorPos = leafBlockSpawn != null
                ? leafBlockSpawn.source()
                : BlockPos.containing(leaf.x, player.getY(), leaf.z);
        BlockState sourceLeafState = leafBlockSpawn != null
                ? level.getBlockState(leafBlockSpawn.source())
                : level.getBiome(leafColorPos).is(Tags.Biomes.IS_SAVANNA)
                        ? Blocks.ACACIA_LEAVES.defaultBlockState()
                        : Blocks.OAK_LEAVES.defaultBlockState();
        int leafType = leafTextureType(sourceLeafState);
        leaf.textureIndex = leafType * LEAF_TEXTURES_PER_TYPE + RANDOM.nextInt(LEAF_TEXTURES_PER_TYPE);
        boolean fromLeafBlock = leafBlockSpawn != null;
        leaf.size = randomBetween(fromLeafBlock ? 0.08D : 0.09D, fromLeafBlock ? 0.19D : 0.23D) * (1.0D + windBoost * 0.12D);
        leaf.speed = randomBetween(fromLeafBlock ? 0.095D : 0.16D, fromLeafBlock ? 0.235D : 0.31D);
        leaf.crossDrift = randomBetween(fromLeafBlock ? -0.028D : -0.018D, fromLeafBlock ? 0.028D : 0.018D) * (1.0D + localWind.turbulence() * 2.1D);
        leaf.verticalDrift = randomBetween(fromLeafBlock ? 0.006D : 0.003D, fromLeafBlock ? 0.017D : 0.012D) * (1.0D + localWind.turbulence() * 1.2D);
        leaf.fallSpeed = randomBetween(fromLeafBlock ? 0.012D : 0.002D, fromLeafBlock ? 0.032D : 0.009D);
        leaf.bob = randomBetween(fromLeafBlock ? 0.032D : 0.025D, fromLeafBlock ? 0.088D : 0.075D) * (1.0D + localWind.turbulence());
        leaf.swirlStrength = randomBetween(fromLeafBlock ? 0.012D : 0.006D, fromLeafBlock ? 0.048D : 0.035D) * (1.0D + localWind.turbulence() * 2.4D);
        leaf.swirlSpeed = randomBetween(0.105D, 0.225D) * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        leaf.swirlPhase = RANDOM.nextDouble() * Math.PI * 2.0D;
        leaf.loopStrength = (RANDOM.nextDouble() < 0.44D ? randomBetween(0.018D, 0.065D) : randomBetween(0.0D, 0.018D))
                * (1.0D + localWind.turbulence() * 2.7D);
        leaf.loopSpeed = randomBetween(0.072D, 0.152D) * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        leaf.loopPhase = RANDOM.nextDouble() * Math.PI * 2.0D;
        leaf.terrainLift = 0.0D;
        leaf.terrainSideFlow = 0.0D;
        leaf.tilt = randomBetween(-Math.PI, Math.PI);
        leaf.tiltSpeed = randomBetween(0.045D, 0.145D) * (1.0D + localWind.turbulence() * 1.8D)
                * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        leaf.flipPhase = RANDOM.nextDouble() * Math.PI * 2.0D;
        leaf.flipSpeed = randomBetween(0.115D, 0.265D) * (1.0D + localWind.turbulence() * 1.6D)
                * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        leaf.tumbleStrength = randomBetween(0.12D, 0.42D) * (1.0D + localWind.turbulence() * 2.0D);
        leaf.seed = RANDOM.nextDouble() * Math.PI * 2.0D;
        leaf.lifetime = Math.max(64, Math.round((94 + RANDOM.nextInt(70)) / (float) (1.0D + windBoost * 0.28D)));
        leaf.age = scatterAge ? RANDOM.nextInt(Math.max(1, leaf.lifetime / 2)) : 0;
        leaf.fadingOut = false;
        leaf.fadeOutAge = 0;
        int color = sampleLeafColor(level, sourceLeafState, leafColorPos);
        leaf.red = color >> 16 & 255;
        leaf.green = color >> 8 & 255;
        leaf.blue = color & 255;
        leaf.active = true;
    }

    private static void spawnFlowerPetal(
            WindLeaf petal,
            LocalPlayer player,
            ClientLevel level,
            WindSample playerWind,
            boolean scatterAge
    ) {
        Point spawn = randomOpenAirPosition(
                player, level, playerWind,
                -26.0D, 18.0D, -28.0D, 28.0D, -0.4D, 6.4D,
                0.35D, 1.9D, LEAF_TERRAIN_CLEARANCE, 0.05D, 1.2D
        );
        if (spawn == null) {
            petal.active = false;
            return;
        }

        BlockPos spawnPos = BlockPos.containing(spawn.x(), spawn.y(), spawn.z());
        if (!isFlowerPetalBiome(level, spawnPos)) {
            petal.active = false;
            return;
        }

        WindSample localWind = sampleWind(level, spawn.x(), spawn.y(), spawn.z());
        if (!acceptSpawnForWind(localWind)) {
            petal.active = false;
            return;
        }
        applyWind(petal, localWind, true);
        double windBoost = windBoost(localWind);

        petal.x = spawn.x();
        petal.y = spawn.y();
        petal.z = spawn.z();
        petal.xOld = petal.x;
        petal.yOld = petal.y;
        petal.zOld = petal.z;
        petal.flowerPetal = true;
        var biome = level.getBiome(spawnPos);
        double roseChance = biome.is(Tags.Biomes.IS_FLOWER_FOREST)
                ? 0.62D
                : biome.is(Tags.Biomes.IS_FLORAL) ? 0.44D : 0.28D;
        double azureBluetChance = biome.is(Tags.Biomes.IS_FLOWER_FOREST)
                ? 0.24D
                : biome.is(Tags.Biomes.IS_FLORAL) ? 0.34D : 0.30D;
        double textureRoll = RANDOM.nextDouble();
        if (textureRoll < roseChance) {
            petal.textureIndex = RANDOM.nextInt(ROSE_PETAL_TEXTURE_COUNT);
        } else if (textureRoll < roseChance + azureBluetChance) {
            petal.textureIndex = ROSE_PETAL_TEXTURE_COUNT
                    + DANDELION_PETAL_TEXTURE_COUNT
                    + RANDOM.nextInt(AZURE_BLUET_PETAL_TEXTURE_COUNT);
        } else {
            petal.textureIndex = ROSE_PETAL_TEXTURE_COUNT
                    + RANDOM.nextInt(DANDELION_PETAL_TEXTURE_COUNT);
        }
        petal.size = randomBetween(0.12D, 0.23D) * (1.0D + windBoost * 0.08D);
        petal.speed = randomBetween(0.14D, 0.29D);
        petal.crossDrift = randomBetween(-0.026D, 0.026D) * (1.0D + localWind.turbulence() * 2.5D);
        petal.verticalDrift = randomBetween(0.006D, 0.018D) * (1.0D + localWind.turbulence() * 1.6D);
        petal.fallSpeed = randomBetween(0.003D, 0.012D);
        petal.bob = randomBetween(0.035D, 0.095D) * (1.0D + localWind.turbulence() * 1.2D);
        petal.swirlStrength = randomBetween(0.012D, 0.052D) * (1.0D + localWind.turbulence() * 2.8D);
        petal.swirlSpeed = randomBetween(0.12D, 0.26D) * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        petal.swirlPhase = RANDOM.nextDouble() * Math.PI * 2.0D;
        petal.loopStrength = randomBetween(0.012D, 0.07D) * (1.0D + localWind.turbulence() * 2.9D);
        petal.loopSpeed = randomBetween(0.085D, 0.18D) * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        petal.loopPhase = RANDOM.nextDouble() * Math.PI * 2.0D;
        petal.terrainLift = 0.0D;
        petal.terrainSideFlow = 0.0D;
        petal.tilt = randomBetween(-Math.PI, Math.PI);
        petal.tiltSpeed = randomBetween(0.075D, 0.19D) * (1.0D + localWind.turbulence() * 2.0D)
                * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        petal.flipPhase = RANDOM.nextDouble() * Math.PI * 2.0D;
        petal.flipSpeed = randomBetween(0.17D, 0.34D) * (1.0D + localWind.turbulence() * 1.8D)
                * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        petal.tumbleStrength = randomBetween(0.2D, 0.56D) * (1.0D + localWind.turbulence() * 2.2D);
        petal.seed = RANDOM.nextDouble() * Math.PI * 2.0D;
        petal.lifetime = Math.max(58, Math.round((82 + RANDOM.nextInt(66)) / (float) (1.0D + windBoost * 0.25D)));
        petal.age = scatterAge ? RANDOM.nextInt(Math.max(1, petal.lifetime / 2)) : 0;
        petal.fadingOut = false;
        petal.fadeOutAge = 0;
        petal.red = 255;
        petal.green = 255;
        petal.blue = 255;
        petal.active = true;
    }

    private static LeafBlockSpawn randomLeafBlockSpawn(LocalPlayer player, ClientLevel level, WindSample playerWind) {
        BlockPos.MutableBlockPos leafPos = new BlockPos.MutableBlockPos();
        int baseY = Mth.floor(player.getY());
        for (int attempt = 0; attempt < LEAF_BLOCK_SPAWN_ATTEMPTS; attempt++) {
            double along = randomBetween(-22.0D, 18.0D);
            double across = randomBetween(-24.0D, 24.0D);
            int x = Mth.floor(player.getX() + playerWind.directionX() * along + playerWind.crossX() * across);
            int z = Mth.floor(player.getZ() + playerWind.directionZ() * along + playerWind.crossZ() * across);
            int startY = baseY + 11 - RANDOM.nextInt(4);
            int endY = baseY - 2;

            for (int y = startY; y >= endY; y--) {
                leafPos.set(x, y, z);
                if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                    break;
                }

                BlockState state = level.getBlockState(leafPos);
                if (!ResponsiveFoliage.isLeaf(state) || !isLeafBlockWindExposed(level, leafPos)) {
                    continue;
                }

                LeafBlockSpawn spawn = spawnNearLeafBlock(level, leafPos);
                if (spawn != null) {
                    return spawn;
                }
            }
        }

        return null;
    }

    private static LeafBlockSpawn spawnNearLeafBlock(ClientLevel level, BlockPos source) {
        WindSample wind = DynamicWindManager.sampleWind(level, source);
        for (int attempt = 0; attempt < 4; attempt++) {
            double side = randomBetween(-0.34D, 0.34D);
            double outward = randomBetween(0.34D, 0.72D);
            double downward = RANDOM.nextDouble() < 0.72D ? randomBetween(0.1D, 0.86D) : randomBetween(-0.08D, 0.22D);
            double x = source.getX() + 0.5D + wind.directionX() * outward + wind.crossX() * side;
            double y = source.getY() + 0.92D - downward;
            double z = source.getZ() + 0.5D + wind.directionZ() * outward + wind.crossZ() * side;
            Point spawn = keepAboveTerrainAndCollision(level, x, y, z, LEAF_TERRAIN_CLEARANCE);
            if (isValidLeafSpace(level, spawn.x(), spawn.y(), spawn.z(), LEAF_TERRAIN_CLEARANCE)) {
                return new LeafBlockSpawn(spawn, source.immutable());
            }
        }

        return null;
    }

    private static void beginFadeOut(WindStreak streak) {
        if (!streak.active || streak.fadingOut) {
            return;
        }

        streak.fadingOut = true;
        streak.fadeOutAge = 0;
    }

    private static void beginFadeOut(WindLeaf leaf) {
        if (!leaf.active || leaf.fadingOut) {
            return;
        }

        leaf.fadingOut = true;
        leaf.fadeOutAge = 0;
        leaf.xOld = leaf.x;
        leaf.yOld = leaf.y;
        leaf.zOld = leaf.z;
    }

    private static void tickFadeOut(WindStreak streak) {
        if (!streak.active || !streak.fadingOut) {
            return;
        }

        double dx = streak.x - streak.xOld;
        double dy = streak.y - streak.yOld;
        double dz = streak.z - streak.zOld;
        streak.xOld = streak.x;
        streak.yOld = streak.y;
        streak.zOld = streak.z;
        double momentum = 1.0D - (double) streak.fadeOutAge / (double) FADE_OUT_TICKS;
        streak.x += dx * momentum;
        streak.y += dy * momentum;
        streak.z += dz * momentum;
        streak.fadeOutAge++;
        if (streak.fadeOutAge >= FADE_OUT_TICKS) {
            streak.active = false;
            streak.fadingOut = false;
        }
    }

    private static void tickFadeOut(WindLeaf leaf) {
        if (!leaf.active || !leaf.fadingOut) {
            return;
        }

        leaf.xOld = leaf.x;
        leaf.yOld = leaf.y;
        leaf.zOld = leaf.z;
        leaf.fadeOutAge++;
        if (leaf.fadeOutAge >= FADE_OUT_TICKS) {
            leaf.active = false;
            leaf.fadingOut = false;
        }
    }

    private static float fadeOutMultiplier(WindStreak streak, float partialTick) {
        if (!streak.fadingOut) {
            return 1.0F;
        }

        float remaining = 1.0F - Mth.clamp(((float) streak.fadeOutAge + partialTick) / (float) FADE_OUT_TICKS, 0.0F, 1.0F);
        return smoothFade(remaining);
    }

    private static float fadeOutMultiplier(WindLeaf leaf, float partialTick) {
        if (!leaf.fadingOut) {
            return 1.0F;
        }

        float remaining = 1.0F - Mth.clamp(((float) leaf.fadeOutAge + partialTick) / (float) FADE_OUT_TICKS, 0.0F, 1.0F);
        return smoothFade(remaining);
    }

    private static Point randomOpenAirPosition(
            LocalPlayer player,
            ClientLevel level,
            WindSample wind,
            double minAlong,
            double maxAlong,
            double minAcross,
            double maxAcross,
            double minEyeOffset,
            double maxEyeOffset,
            double minPlayerClearance,
            double maxPlayerClearance,
            double terrainClearance,
            double minTerrainExtraClearance,
            double maxTerrainExtraClearance
    ) {
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double along = randomBetween(minAlong, maxAlong);
            double across = randomBetween(minAcross, maxAcross);
            double x = player.getX() + wind.directionX() * along + wind.crossX() * across;
            double y = player.getEyeY() + randomBetween(minEyeOffset, maxEyeOffset);
            double z = player.getZ() + wind.directionZ() * along + wind.crossZ() * across;
            if (y < player.getY() + minPlayerClearance) {
                y = player.getY() + randomBetween(minPlayerClearance, maxPlayerClearance);
            }

            y = Math.max(
                    y,
                    terrainHeight(level, x, z) + terrainClearance + randomBetween(minTerrainExtraClearance, maxTerrainExtraClearance)
            );
            Point spawn = keepAboveTerrainAndCollision(level, x, y, z, terrainClearance);
            if (isValidWindSpace(level, spawn.x(), spawn.y(), spawn.z(), terrainClearance)) {
                return spawn;
            }
        }

        return null;
    }

    private static void renderStreak(
            VertexConsumer consumer,
            Matrix4f pose,
            ClientLevel level,
            WindStreak streak,
            Vec3 cameraPos,
            float partialTick,
            float windTime,
            float opacity,
            BlizzardWeatherEffects.BlizzardFogProfile blizzardFog
    ) {
        float life = ((float) streak.age + partialTick) / (float) streak.lifetime;
        float fadeIn = smoothFade(Mth.clamp(
                ((float) streak.fadeInAge + partialTick) / (float) STREAK_FADE_IN_TICKS,
                0.0F,
                1.0F
        ));
        float naturalEndFade = streak.fadingOut
                ? 1.0F
                : smoothFade(Mth.clamp((1.0F - life) / 0.34F, 0.0F, 1.0F));
        float fade = fadeIn * naturalEndFade;
        if (fade <= 0.01F) {
            return;
        }

        double baseX = lerp(partialTick, streak.xOld, streak.x);
        double baseY = lerp(partialTick, streak.yOld, streak.y);
        double baseZ = lerp(partialTick, streak.zOld, streak.z);
        if (!streak.fadingOut && !isOpenSkyAir(level, baseX, baseY, baseZ)) {
            return;
        }

        double cameraDistance = cameraPos.distanceTo(new Vec3(baseX, baseY, baseZ));
        float distanceFade = smoothFade(Mth.clamp((float) ((MAX_DISTANCE_FROM_PLAYER - cameraDistance) / 18.0D), 0.0F, 1.0F));
        float nearFade = smoothFade(Mth.clamp((float) ((cameraDistance - 1.75D) / 3.25D), 0.0F, 1.0F));
        float alpha = 0.88F * opacity * fade * nearFade * distanceFade * fadeOutMultiplier(streak, partialTick);
        if (alpha <= 0.006F) {
            return;
        }

        float brushPosition = (float) (((double) streak.age + partialTick) * streak.speed - 0.18D);
        for (int segment = 0; segment < BODY_SEGMENTS; segment++) {
            float t0 = (float) segment / (float) BODY_SEGMENTS;
            float t1 = (float) (segment + 1) / (float) BODY_SEGMENTS;
            drawSegment(
                    consumer, pose, level, streak, cameraPos,
                    baseX, baseY, baseZ, t0, t1, windTime, brushPosition, alpha, blizzardFog
            );
        }

    }

    private static void renderLeaves(
            ClientLevel level,
            Camera camera,
            Matrix4f pose,
            Vec3 cameraPos,
            float partialTick,
            float windTime,
            float opacity,
            BlizzardWeatherEffects.BlizzardFogProfile blizzardFog
    ) {
        renderWindParticles(
                level, camera, pose, cameraPos, partialTick, windTime, opacity,
                LEAVES, LEAF_RENDER_TYPES, blizzardFog
        );
    }

    private static void renderFlowerPetals(
            ClientLevel level,
            Camera camera,
            Matrix4f pose,
            Vec3 cameraPos,
            float partialTick,
            float windTime,
            float opacity,
            BlizzardWeatherEffects.BlizzardFogProfile blizzardFog
    ) {
        renderWindParticles(
                level, camera, pose, cameraPos, partialTick, windTime, opacity,
                FLOWER_PETALS, FLOWER_PETAL_RENDER_TYPES, blizzardFog
        );
    }

    private static void renderWindParticles(
            ClientLevel level,
            Camera camera,
            Matrix4f pose,
            Vec3 cameraPos,
            float partialTick,
            float windTime,
            float opacity,
            WindLeaf[] particles,
            RenderType[] renderTypes,
            BlizzardWeatherEffects.BlizzardFogProfile blizzardFog
    ) {
        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(LEAF_BUFFER);
        for (int textureIndex = 0; textureIndex < renderTypes.length; textureIndex++) {
            VertexConsumer consumer = null;
            for (WindLeaf particle : particles) {
                if (particle.active && particle.textureIndex == textureIndex) {
                    if (consumer == null) {
                        consumer = bufferSource.getBuffer(renderTypes[textureIndex]);
                    }
                    renderLeaf(
                            consumer, pose, particle, level, camera, cameraPos,
                            partialTick, windTime, opacity, blizzardFog
                    );
                }
            }
        }
        bufferSource.endBatch();
    }

    private static void renderLeaf(
            VertexConsumer consumer,
            Matrix4f pose,
            WindLeaf leaf,
            ClientLevel level,
            Camera camera,
            Vec3 cameraPos,
            float partialTick,
            float windTime,
            float opacity,
            BlizzardWeatherEffects.BlizzardFogProfile blizzardFog
    ) {
        float life = ((float) leaf.age + partialTick) / (float) leaf.lifetime;
        float fade = smoothFade(Mth.clamp(life / 0.24F, 0.0F, 1.0F))
                * smoothFade(Mth.clamp((1.0F - life) / 0.36F, 0.0F, 1.0F));
        if (fade <= 0.01F) {
            return;
        }

        double baseX = lerp(partialTick, leaf.xOld, leaf.x);
        double baseY = lerp(partialTick, leaf.yOld, leaf.y);
        double baseZ = lerp(partialTick, leaf.zOld, leaf.z);
        double turbulenceFlutter = 0.75D + Mth.clamp(leaf.turbulence * 2.6D, 0.0D, 1.7D);
        double leafTime = (double) leaf.age + partialTick + leaf.seed;
        double flutter = (
                Math.sin(leaf.seed + leafTime * 0.16D + windTime * 1.35D)
                        + Math.sin(leaf.swirlPhase + leafTime * leaf.swirlSpeed * 0.72D) * 0.38D
        ) * leaf.bob;
        double centerX = baseX + leaf.crossX * flutter * turbulenceFlutter;
        double centerY = baseY + Math.cos(leaf.seed * 0.7D + leafTime * 0.13D) * leaf.bob;
        double centerZ = baseZ + leaf.crossZ * flutter * turbulenceFlutter;
        centerY = Math.max(centerY, terrainHeight(level, centerX, centerZ) + LEAF_TERRAIN_CLEARANCE);
        if (!leaf.fadingOut && !isValidLeafSpace(level, centerX, centerY, centerZ, 0.0D)) {
            return;
        }

        double dx = centerX - cameraPos.x();
        double dy = centerY - cameraPos.y();
        double dz = centerZ - cameraPos.z();
        double cameraDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float distanceFade = smoothFade(Mth.clamp((float) ((MAX_DISTANCE_FROM_PLAYER - cameraDistance) / 18.0D), 0.0F, 1.0F));
        float alpha = 0.95F * opacity * fade * distanceFade
                * blizzardFog.visibility(cameraDistance)
                * fadeOutMultiplier(leaf, partialTick);
        if (alpha <= 0.006F) {
            return;
        }

        double liveTumble = 0.72D + Mth.clamp(leaf.turbulence * 3.0D, 0.0D, 1.9D);
        double flip = Math.cos(
                leaf.flipPhase + leafTime * leaf.flipSpeed
                        + Math.sin(leaf.swirlPhase + leafTime * 0.083D) * leaf.tumbleStrength * liveTumble
        );
        float tilt = (float) (leaf.tilt + leafTime * leaf.tiltSpeed
                + Math.sin(leaf.seed + windTime * 0.9D) * 0.18D
                + Math.sin(leaf.loopPhase + leafTime * leaf.loopSpeed) * leaf.tumbleStrength * liveTumble);
        float halfSize = (float) (leaf.size * (0.5D + Math.sin(leaf.seed * 1.3D + leafTime * 0.11D) * 0.035D));
        float flipScale = (float) Mth.clamp(0.18D + Math.abs(flip) * 0.82D, 0.18D, 1.0D);
        float stretchScale = (float) Mth.clamp(
                1.0D + Math.sin(leaf.swirlPhase + leafTime * leaf.swirlSpeed) * 0.13D,
                0.86D,
                1.14D
        );
        int alphaByte = alphaByte(alpha);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(centerX, centerY, centerZ));
        emitLeafQuad(
                consumer,
                pose,
                camera,
                cameraPos,
                centerX,
                centerY,
                centerZ,
                halfSize,
                tilt,
                flipScale,
                stretchScale,
                leaf.red,
                leaf.green,
                leaf.blue,
                alphaByte,
                light
        );
    }

    private static void drawSegment(
            VertexConsumer consumer,
            Matrix4f pose,
            ClientLevel level,
            WindStreak streak,
            Vec3 cameraPos,
            double baseX,
            double baseY,
            double baseZ,
            float t0,
            float t1,
            float windTime,
            float brushPosition,
            float alpha,
            BlizzardWeatherEffects.BlizzardFogProfile blizzardFog
    ) {
        Point start = pointOnBody(streak, baseX, baseY, baseZ, t0, windTime);
        Point end = pointOnBody(streak, baseX, baseY, baseZ, t1, windTime);
        if ((!streak.fadingOut && (!isOpenSkyAir(level, start.x(), start.y(), start.z())
                || !isOpenSkyAir(level, end.x(), end.y(), end.z())))
                || lineHitsCollision(level, start, end)) {
            return;
        }

        float taper0 = motionTaper(streak, t0, windTime, brushPosition);
        float taper1 = motionTaper(streak, t1, windTime, brushPosition);
        float strongestTaper = Math.max(taper0, taper1);
        if (strongestTaper <= 0.002F) {
            return;
        }

        float widthTaper0 = 0.42F + Mth.sqrt(Mth.clamp(taper0, 0.0F, 1.0F)) * 0.58F;
        float widthTaper1 = 0.42F + Mth.sqrt(Mth.clamp(taper1, 0.0F, 1.0F)) * 0.58F;
        float startVisibility = blizzardFog.visibility(distanceTo(cameraPos, start));
        float endVisibility = blizzardFog.visibility(distanceTo(cameraPos, end));
        emitLine(
                consumer,
                pose,
                cameraPos,
                streak,
                start,
                end,
                alpha * taper0 * startVisibility,
                alpha * taper1 * endVisibility,
                widthTaper0,
                widthTaper1
        );
    }

    private static Point pointOnBody(WindStreak streak, double baseX, double baseY, double baseZ, float t, float windTime) {
        double along = t * streak.length;
        double flow = streak.seed + windTime * (0.12D + streak.turbulence * 0.08D);
        double pathEnvelope = Math.sin(t * Math.PI);
        double tailEase = smoothFade(Mth.clamp(t / 0.22F, 0.0F, 1.0F));
        double headEase = smoothFade(Mth.clamp((1.0F - t) / 0.28F, 0.0F, 1.0F));
        double motionEnvelope = pathEnvelope * tailEase * headEase;
        double bodyCurve = pathEnvelope * streak.arc;
        double sCurve = Math.sin(t * Math.PI * streak.curveFrequency + streak.curvePhase)
                * streak.curveStrength
                * motionEnvelope
                * streak.curveSign;
        double softRipple = Math.sin(flow + t * Math.PI * 1.35D) * 0.012D * motionEnvelope;
        double fineRipple = Math.sin(streak.ripplePhase + t * Math.PI * streak.rippleFrequency)
                * streak.rippleStrength
                * motionEnvelope;
        double liveCurvature = 0.72D + Mth.clamp(streak.turbulence * 2.2D, 0.0D, 1.6D);
        double crossOffset = bodyCurve + (sCurve + softRipple + fineRipple) * liveCurvature;
        double x = baseX + streak.windX * along + streak.crossX * crossOffset;
        double y = baseY + pathEnvelope * streak.lift
                + Math.cos(streak.ripplePhase + t * Math.PI * 1.7D) * streak.verticalRipple * motionEnvelope;
        double z = baseZ + streak.windZ * along + streak.crossZ * crossOffset;
        return new Point(x, y, z);
    }

    private static float motionTaper(WindStreak streak, float t, float windTime, float brushPosition) {
        float shapeFade = smoothFade(Mth.clamp(t / 0.12F, 0.0F, 1.0F))
                * smoothFade(Mth.clamp((1.0F - t) / 0.2F, 0.0F, 1.0F));
        float leadingEdge = movingBrush(t, brushPosition, (float) streak.brushWidth);
        float trailingWake = trailingWake(t, brushPosition, (float) streak.wakeLength);
        float curveBoost = Mth.sin(t * Mth.PI) * 0.06F;
        float shimmer = (float) (1.0D - streak.shimmerStrength
                + streak.shimmerStrength * Mth.sin((float) streak.seed + windTime * 1.8F + t * Mth.PI * 3.0F));
        float fineBreakup = 0.965F
                + 0.035F * Mth.sin((float) streak.ripplePhase + windTime * 2.4F + t * Mth.PI * 6.0F);
        float flowingStroke = Math.max(leadingEdge, trailingWake * (0.68F + curveBoost));
        return shapeFade * shimmer * fineBreakup * flowingStroke;
    }

    private static boolean streakBodyHitsCollision(ClientLevel level, WindStreak streak, float windTime) {
        return streakBodyHitsCollision(level, streak, streak.x, streak.y, streak.z, windTime);
    }

    private static boolean streakBodyHitsCollision(ClientLevel level, WindStreak streak, double baseX, double baseY, double baseZ, float windTime) {
        Point previous = pointOnBody(streak, baseX, baseY, baseZ, 0.0F, windTime);
        for (int segment = 1; segment <= BODY_SEGMENTS; segment++) {
            float t = (float) segment / (float) BODY_SEGMENTS;
            Point next = pointOnBody(streak, baseX, baseY, baseZ, t, windTime);
            if (!isOpenSkyAir(level, next.x(), next.y(), next.z()) || lineHitsCollision(level, previous, next)) {
                return true;
            }

            previous = next;
        }

        return false;
    }

    private static double distanceTo(Vec3 cameraPos, Point point) {
        double dx = point.x() - cameraPos.x();
        double dy = point.y() - cameraPos.y();
        double dz = point.z() - cameraPos.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void emitLine(
            VertexConsumer consumer,
            Matrix4f pose,
            Vec3 cameraPos,
            WindStreak streak,
            Point start,
            Point end,
            float startAlpha,
            float endAlpha,
            float startWidthScale,
            float endWidthScale
    ) {
        int alpha0 = alphaByte(startAlpha);
        int alpha1 = alphaByte(endAlpha);
        if (alpha0 <= 0 && alpha1 <= 0) {
            return;
        }

        float normalX = (float) (end.x - start.x);
        float normalY = (float) (end.y - start.y);
        float normalZ = (float) (end.z - start.z);
        float length = Mth.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (length > 0.0001F) {
            normalX /= length;
            normalY /= length;
            normalZ /= length;
        } else {
            normalX = (float) streak.windX;
            normalY = 0.0F;
            normalZ = (float) streak.windZ;
        }

        consumer.addVertex(pose, (float) (start.x - cameraPos.x()), (float) (start.y - cameraPos.y()), (float) (start.z - cameraPos.z()))
                .setColor(RED, GREEN, BLUE, alpha0)
                .setNormal(normalX * startWidthScale, normalY * startWidthScale, normalZ * startWidthScale);
        consumer.addVertex(pose, (float) (end.x - cameraPos.x()), (float) (end.y - cameraPos.y()), (float) (end.z - cameraPos.z()))
                .setColor(RED, GREEN, BLUE, alpha1)
                .setNormal(normalX * endWidthScale, normalY * endWidthScale, normalZ * endWidthScale);
    }

    private static void emitLeafQuad(
            VertexConsumer consumer,
            Matrix4f pose,
            Camera camera,
            Vec3 cameraPos,
            double centerX,
            double centerY,
            double centerZ,
            float halfSize,
            float tilt,
            float flipScale,
            float stretchScale,
            int red,
            int green,
            int blue,
            int alpha,
            int light
    ) {
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        float tiltCos = Mth.cos(tilt);
        float tiltSin = Mth.sin(tilt);
        double axisXx = left.x() * tiltCos + up.x() * tiltSin;
        double axisXy = left.y() * tiltCos + up.y() * tiltSin;
        double axisXz = left.z() * tiltCos + up.z() * tiltSin;
        double axisYx = up.x() * tiltCos - left.x() * tiltSin;
        double axisYy = up.y() * tiltCos - left.y() * tiltSin;
        double axisYz = up.z() * tiltCos - left.z() * tiltSin;
        double halfWidth = halfSize * flipScale;
        double halfHeight = halfSize * stretchScale;

        emitLeafVertex(
                consumer,
                pose,
                cameraPos,
                centerX - axisXx * halfWidth - axisYx * halfHeight,
                centerY - axisXy * halfWidth - axisYy * halfHeight,
                centerZ - axisXz * halfWidth - axisYz * halfHeight,
                0.0F,
                1.0F,
                red,
                green,
                blue,
                alpha,
                light
        );
        emitLeafVertex(
                consumer,
                pose,
                cameraPos,
                centerX + axisXx * halfWidth - axisYx * halfHeight,
                centerY + axisXy * halfWidth - axisYy * halfHeight,
                centerZ + axisXz * halfWidth - axisYz * halfHeight,
                1.0F,
                1.0F,
                red,
                green,
                blue,
                alpha,
                light
        );
        emitLeafVertex(
                consumer,
                pose,
                cameraPos,
                centerX + axisXx * halfWidth + axisYx * halfHeight,
                centerY + axisXy * halfWidth + axisYy * halfHeight,
                centerZ + axisXz * halfWidth + axisYz * halfHeight,
                1.0F,
                0.0F,
                red,
                green,
                blue,
                alpha,
                light
        );
        emitLeafVertex(
                consumer,
                pose,
                cameraPos,
                centerX - axisXx * halfWidth + axisYx * halfHeight,
                centerY - axisXy * halfWidth + axisYy * halfHeight,
                centerZ - axisXz * halfWidth + axisYz * halfHeight,
                0.0F,
                0.0F,
                red,
                green,
                blue,
                alpha,
                light
        );
    }

    private static void emitLeafVertex(
            VertexConsumer consumer,
            Matrix4f pose,
            Vec3 cameraPos,
            double x,
            double y,
            double z,
            float u,
            float v,
            int red,
            int green,
            int blue,
            int alpha,
            int light
    ) {
        consumer.addVertex(pose, (float) (x - cameraPos.x()), (float) (y - cameraPos.y()), (float) (z - cameraPos.z()))
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setLight(light);
    }

    private static int leafTextureType(BlockState state) {
        if (state.is(Blocks.SPRUCE_LEAVES)) {
            return 1;
        }
        if (state.is(Blocks.BIRCH_LEAVES)) {
            return 2;
        }
        if (state.is(Blocks.JUNGLE_LEAVES)) {
            return 3;
        }
        if (state.is(Blocks.ACACIA_LEAVES)) {
            return 4;
        }
        if (state.is(Blocks.DARK_OAK_LEAVES)) {
            return 5;
        }
        if (state.is(Blocks.MANGROVE_LEAVES)) {
            return 6;
        }
        if (state.is(Blocks.CHERRY_LEAVES)) {
            return 7;
        }
        if (state.is(Blocks.AZALEA_LEAVES)) {
            return 8;
        }
        if (state.is(Blocks.FLOWERING_AZALEA_LEAVES)) {
            return 9;
        }
        return 0;
    }

    private static int sampleLeafColor(ClientLevel level, BlockState state, BlockPos pos) {
        int color = Minecraft.getInstance().getBlockColors().getColor(
                state,
                level,
                pos,
                0
        );
        return color == -1 ? 0xFFFFFF : color;
    }

    private static TerrainFlow terrainFlow(
            ClientLevel level,
            double x,
            double y,
            double z,
            double clearance,
            double liftStrength,
            double currentLift,
            double currentSide,
            double windX,
            double windZ
    ) {
        double crossX = -windZ;
        double crossZ = windX;
        double nearX = x + windX * (TERRAIN_LOOKAHEAD * 0.45D);
        double nearZ = z + windZ * (TERRAIN_LOOKAHEAD * 0.45D);
        double forwardX = x + windX * TERRAIN_LOOKAHEAD;
        double forwardZ = z + windZ * TERRAIN_LOOKAHEAD;
        double currentHeight = terrainHeight(level, x, z);
        double nearHeight = terrainHeight(level, nearX, nearZ);
        double forwardHeight = terrainHeight(level, forwardX, forwardZ);
        double targetY = Math.max(Math.max(currentHeight, nearHeight), forwardHeight) + clearance;
        double heightError = targetY - y;
        double desiredLift = heightError > 0.0D
                ? Mth.clamp(heightError * liftStrength, 0.0D, MAX_TERRAIN_LIFT)
                : 0.0D;

        double leftHeight = terrainHeight(level, forwardX - crossX * TERRAIN_SIDE_SAMPLE, forwardZ - crossZ * TERRAIN_SIDE_SAMPLE);
        double rightHeight = terrainHeight(level, forwardX + crossX * TERRAIN_SIDE_SAMPLE, forwardZ + crossZ * TERRAIN_SIDE_SAMPLE);
        double tallestNearbyTerrain = Math.max(Math.max(leftHeight, rightHeight), forwardHeight);
        double sideAwareness = smoothFade(Mth.clamp((float) ((tallestNearbyTerrain + clearance + 3.0D - y) / 5.5D), 0.0F, 1.0F));
        double desiredSide = Mth.clamp(
                (leftHeight - rightHeight) * TERRAIN_SIDE_PUSH * sideAwareness,
                -MAX_TERRAIN_SIDE_FLOW,
                MAX_TERRAIN_SIDE_FLOW
        );

        double response = 1.0D - TERRAIN_FLOW_SMOOTHING;
        return new TerrainFlow(
                currentLift * TERRAIN_FLOW_SMOOTHING + desiredLift * response,
                currentSide * TERRAIN_FLOW_SMOOTHING + desiredSide * response
        );
    }

    private static Point keepAboveTerrainAndCollision(ClientLevel level, double x, double y, double z, double clearance) {
        double safeY = Math.max(y, terrainHeight(level, x, z) + clearance);
        BlockPos pos = BlockPos.containing(x, safeY, z);
        if (pointInsideCollision(level, x, safeY, z)) {
            safeY = Math.max(safeY, collisionTop(level, pos) + clearance);
        }

        return new Point(x, safeY, z);
    }

    private static boolean windPathBlocked(ClientLevel level, double x, double y, double z, Point destination) {
        Vec3 start = new Vec3(x, y, z);
        Vec3 end = new Vec3(destination.x(), destination.y(), destination.z());
        return !isValidLeafSpace(level, destination.x(), destination.y(), destination.z(), LEAF_TERRAIN_CLEARANCE)
                || lineHitsCollision(level, start, end);
    }

    private static boolean isOpenSkyAir(ClientLevel level, double x, double y, double z) {
        return isValidWindSpace(level, x, y, z, 0.0D);
    }

    private static boolean isValidWindSpace(ClientLevel level, double x, double y, double z, double terrainClearance) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))
                || !level.getFluidState(pos).isEmpty()
                || level.getBrightness(LightLayer.SKY, pos) < MIN_OPEN_SKY_LIGHT
                || !hasSkyAccess(level, x, y, z)
                || pointInsideCollision(level, x, y, z)) {
            return false;
        }

        return y + SURFACE_TOLERANCE >= terrainHeight(level, x, z) + terrainClearance;
    }

    private static boolean isValidLeafSpace(ClientLevel level, double x, double y, double z, double terrainClearance) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))
                || !level.getFluidState(pos).isEmpty()
                || level.getBrightness(LightLayer.SKY, pos) < MIN_OPEN_SKY_LIGHT
                || !hasWindAccess(level, pos)
                || pointInsideCollision(level, x, y, z)) {
            return false;
        }

        return y + SURFACE_TOLERANCE >= terrainHeight(level, x, z) + terrainClearance;
    }

    private static boolean isLeafBlockWindExposed(ClientLevel level, BlockPos pos) {
        return level.getBrightness(LightLayer.SKY, pos) >= MIN_OPEN_SKY_LIGHT && hasWindAccess(level, pos);
    }

    private static boolean hasWindAccess(ClientLevel level, BlockPos pos) {
        if (level.canSeeSky(pos)) {
            return true;
        }

        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int distance = 1; distance <= LEAF_SIDE_ACCESS_RADIUS; distance++) {
                samplePos.set(
                        pos.getX() + direction.getStepX() * distance,
                        pos.getY(),
                        pos.getZ() + direction.getStepZ() * distance
                );

                if (!canWindPassThrough(level, samplePos)) {
                    break;
                }

                if (level.getBrightness(LightLayer.SKY, samplePos) >= MIN_OPEN_SKY_LIGHT && level.canSeeSky(samplePos)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean canWindPassThrough(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return ResponsiveFoliage.isLeaf(state)
                || state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty();
    }

    private static boolean lineHitsCollision(ClientLevel level, Point start, Point end) {
        return lineHitsCollision(level, new Vec3(start.x(), start.y(), start.z()), new Vec3(end.x(), end.y(), end.z()));
    }

    private static boolean lineHitsCollision(ClientLevel level, Vec3 start, Vec3 end) {
        return clipCollision(level, start, end).getType() == HitResult.Type.BLOCK;
    }

    private static BlockHitResult clipCollision(ClientLevel level, Vec3 start, Vec3 end) {
        return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
    }

    private static boolean pointInsideCollision(ClientLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());
        if (shape.isEmpty()) {
            return false;
        }

        double localX = x - pos.getX();
        double localY = y - pos.getY();
        double localZ = z - pos.getZ();
        for (AABB box : shape.toAabbs()) {
            if (box.contains(localX, localY, localZ)) {
                return true;
            }
        }

        return false;
    }

    private static double collisionTop(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());
        return shape.isEmpty() ? pos.getY() : pos.getY() + shape.max(Direction.Axis.Y);
    }

    private static boolean hasSkyAccess(ClientLevel level, double x, double y, double z) {
        return level.canSeeSky(BlockPos.containing(x, y, z));
    }

    private static double terrainHeight(ClientLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    private static int alphaByte(float alpha) {
        return Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
    }

    private static float smoothFade(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static float movingBrush(float position, float brushPosition, float width) {
        return smoothFade(Mth.clamp(1.0F - Math.abs(position - brushPosition) / width, 0.0F, 1.0F));
    }

    private static float trailingWake(float position, float brushPosition, float length) {
        float behind = brushPosition - position;
        if (behind < 0.0F || behind > length) {
            return 0.0F;
        }

        float tail = smoothFade(Mth.clamp(behind / 0.18F, 0.0F, 1.0F));
        float end = smoothFade(Mth.clamp((length - behind) / length, 0.0F, 1.0F));
        return tail * end;
    }

    private static double randomBetween(double min, double max) {
        return min + RANDOM.nextDouble() * (max - min);
    }

    private static double triangularRandom() {
        return RANDOM.nextDouble() - RANDOM.nextDouble();
    }

    private static double lerp(float amount, double start, double end) {
        return start + (end - start) * amount;
    }

    private static WindSample sampleWind(ClientLevel level, double x, double y, double z) {
        return DynamicWindManager.sampleWind(level, BlockPos.containing(x, y, z));
    }

    private static boolean acceptSpawnForWind(WindSample wind) {
        if (wind.strength() <= 0.015F) {
            return false;
        }

        double chance = Mth.clamp(0.12D + wind.strength() * 1.4D + wind.gustStrength() * 1.2D, 0.12D, 1.0D);
        return RANDOM.nextDouble() < chance;
    }

    private static double windBoost(WindSample wind) {
        return Mth.clamp(wind.strength() / 2.0F, 0.0F, 1.0F);
    }

    private static void refreshWind(WindStreak streak, ClientLevel level) {
        if (--streak.windSampleCountdown > 0) {
            return;
        }

        WindSample wind = sampleWind(level, streak.x, streak.y, streak.z);
        double alignment = streak.windX * wind.directionX() + streak.windZ * wind.directionZ();
        if (alignment < 0.92D) {
            beginFadeOut(streak);
            return;
        }

        // A live direction update used to rotate every point of an existing long
        // streak around its origin. Let old streamlines expire instead; newly
        // spawned ones naturally pick up the changed flow direction.
        double blend = 0.42D;
        streak.windStrength = Mth.lerp(blend, streak.windStrength, wind.strength());
        streak.gustStrength = Mth.lerp(blend, streak.gustStrength, wind.gustStrength());
        streak.turbulence = Mth.lerp(blend, streak.turbulence, wind.turbulence());
        streak.windSampleCountdown = WIND_SAMPLE_INTERVAL_TICKS;
    }

    private static void refreshWind(WindLeaf leaf, ClientLevel level) {
        if (--leaf.windSampleCountdown > 0) {
            return;
        }

        applyWind(leaf, sampleWind(level, leaf.x, leaf.y, leaf.z), false);
    }

    private static void applyWind(WindStreak streak, WindSample wind, boolean immediate) {
        double blend = immediate ? 1.0D : 0.22D;
        double x = Mth.lerp(blend, streak.windX, wind.directionX());
        double z = Mth.lerp(blend, streak.windZ, wind.directionZ());
        double length = Math.sqrt(x * x + z * z);
        if (length > 0.0001D) {
            x /= length;
            z /= length;
        }
        streak.windX = x;
        streak.windZ = z;
        streak.crossX = -z;
        streak.crossZ = x;
        streak.windStrength = Mth.lerp(blend, streak.windStrength, wind.strength());
        streak.gustStrength = Mth.lerp(blend, streak.gustStrength, wind.gustStrength());
        streak.turbulence = Mth.lerp(blend, streak.turbulence, wind.turbulence());
        streak.windSampleCountdown = WIND_SAMPLE_INTERVAL_TICKS;
    }

    private static void applyWind(WindLeaf leaf, WindSample wind, boolean immediate) {
        double blend = immediate ? 1.0D : 0.42D;
        double x = Mth.lerp(blend, leaf.windX, wind.directionX());
        double z = Mth.lerp(blend, leaf.windZ, wind.directionZ());
        double length = Math.sqrt(x * x + z * z);
        if (length > 0.0001D) {
            x /= length;
            z /= length;
        }
        leaf.windX = x;
        leaf.windZ = z;
        leaf.crossX = -z;
        leaf.crossZ = x;
        leaf.windStrength = Mth.lerp(blend, leaf.windStrength, wind.strength());
        leaf.gustStrength = Mth.lerp(blend, leaf.gustStrength, wind.gustStrength());
        leaf.turbulence = Mth.lerp(blend, leaf.turbulence, wind.turbulence());
        leaf.windSampleCountdown = WIND_SAMPLE_INTERVAL_TICKS;
    }

    private static final class WindStreak {
        private boolean active;
        private boolean fadingOut;
        private int age;
        private int lifetime;
        private int fadeInAge;
        private int fadeOutAge;
        private double x;
        private double y;
        private double z;
        private double xOld;
        private double yOld;
        private double zOld;
        private double length;
        private double arc;
        private double lift;
        private double speed;
        private double driftSpeed;
        private double crossDrift;
        private double terrainLift;
        private double terrainSideFlow;
        private double curveStrength;
        private double curvePhase;
        private double curveFrequency;
        private double curveSign;
        private double rippleStrength;
        private double rippleFrequency;
        private double ripplePhase;
        private double verticalRipple;
        private double brushWidth;
        private double wakeLength;
        private double shimmerStrength;
        private double strokeScale;
        private double seed;
        private int windSampleCountdown;
        private double windX;
        private double windZ;
        private double crossX;
        private double crossZ;
        private double windStrength;
        private double gustStrength;
        private double turbulence;
    }

    private static final class WindLeaf {
        private boolean active;
        private boolean fadingOut;
        private boolean flowerPetal;
        private int age;
        private int lifetime;
        private int fadeOutAge;
        private int textureIndex;
        private int red;
        private int green;
        private int blue;
        private double x;
        private double y;
        private double z;
        private double xOld;
        private double yOld;
        private double zOld;
        private double size;
        private double speed;
        private double crossDrift;
        private double verticalDrift;
        private double fallSpeed;
        private double terrainLift;
        private double terrainSideFlow;
        private double bob;
        private double swirlStrength;
        private double swirlSpeed;
        private double swirlPhase;
        private double loopStrength;
        private double loopSpeed;
        private double loopPhase;
        private double tilt;
        private double tiltSpeed;
        private double flipPhase;
        private double flipSpeed;
        private double tumbleStrength;
        private double seed;
        private int windSampleCountdown;
        private double windX;
        private double windZ;
        private double crossX;
        private double crossZ;
        private double windStrength;
        private double gustStrength;
        private double turbulence;
    }

    private record Point(double x, double y, double z) {
    }

    private record LeafBlockSpawn(Point position, BlockPos source) {
    }

    private record TerrainFlow(double lift, double side) {
    }
}
