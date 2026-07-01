package com.jvn.wherewindsblow.client.wind;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class WindStreakRenderer {
    private static final int MAX_STREAKS = 20;
    private static final int MAX_LEAVES = 18;
    private static final int LEAF_TEXTURE_COUNT = 12;
    private static final int BODY_SEGMENTS = 24;
    private static final double MAX_DISTANCE_FROM_PLAYER = 56.0D;
    private static final double TERRAIN_LOOKAHEAD = 6.5D;
    private static final double TERRAIN_SIDE_SAMPLE = 3.6D;
    private static final double TERRAIN_FLOW_SMOOTHING = 0.56D;
    private static final double TERRAIN_SIDE_PUSH = 0.026D;
    private static final double STREAK_TERRAIN_CLEARANCE = 1.45D;
    private static final double LEAF_TERRAIN_CLEARANCE = 0.78D;
    private static final double LEAF_TERRAIN_LIFT_STRENGTH = 0.28D;
    private static final double MAX_TERRAIN_LIFT = 0.58D;
    private static final double MAX_TERRAIN_SIDE_FLOW = 0.18D;
    private static final int SPAWN_ATTEMPTS = 12;
    private static final int FADE_OUT_TICKS = 18;
    private static final int MIN_OPEN_SKY_LIGHT = 14;
    private static final double SURFACE_TOLERANCE = 0.08D;
    private static final int RED = 232;
    private static final int GREEN = 246;
    private static final int BLUE = 255;
    private static final RenderLevelStageEvent.Stage RENDER_STAGE = RenderLevelStageEvent.Stage.AFTER_WEATHER;
    private static final Random RANDOM = new Random();
    private static final WindStreak[] STREAKS = new WindStreak[MAX_STREAKS];
    private static final WindLeaf[] LEAVES = new WindLeaf[MAX_LEAVES];
    private static final RenderType[] LEAF_RENDER_TYPES = new RenderType[LEAF_TEXTURE_COUNT];
    private static final ByteBufferBuilder LINE_BUFFER = new ByteBufferBuilder(65536);
    private static final ByteBufferBuilder LEAF_BUFFER = new ByteBufferBuilder(65536);
    private static double cachedLineWidth = -1.0D;
    private static RenderType cachedLineRenderType;

    static {
        for (int index = 0; index < STREAKS.length; index++) {
            STREAKS[index] = new WindStreak();
        }

        for (int index = 0; index < LEAVES.length; index++) {
            LEAVES[index] = new WindLeaf();
        }

        for (int index = 0; index < LEAF_RENDER_TYPES.length; index++) {
            LEAF_RENDER_TYPES[index] = windLeafTexture(leafTexture(index));
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

        boolean canSpawnWind = shouldRenderAround(level, player);
        int desiredCount = canSpawnWind ? desiredStreakCount() : 0;
        int desiredLeafCount = canSpawnWind ? desiredLeafCount() : 0;
        float weatherWindPower = ResponsiveFoliageShaders.weatherWindPower();
        for (int index = 0; index < STREAKS.length; index++) {
            WindStreak streak = STREAKS[index];
            if (index >= desiredCount) {
                beginFadeOut(streak);
                tickFadeOut(streak);
                continue;
            }

            if (!streak.active) {
                spawn(streak, player, level, weatherWindPower, true);
            } else {
                tick(streak, player, level, weatherWindPower);
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
                spawn(leaf, player, level, weatherWindPower, true);
            } else {
                tick(leaf, player, level, weatherWindPower);
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

        float opacity = (float) ClientConfig.WIND_STREAK_OPACITY.getAsDouble();
        if (opacity <= 0.0F) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float windTime = ResponsiveFoliageShaders.windTime();
        float weatherWindPower = ResponsiveFoliageShaders.weatherWindPower();
        Matrix4f pose = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(LINE_BUFFER);
        RenderType renderType = windStreakLines();
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        for (WindStreak streak : STREAKS) {
            if (streak.active) {
                renderStreak(consumer, pose, minecraft.level, streak, cameraPos, partialTick, windTime, weatherWindPower, opacity);
            }
        }

        bufferSource.endBatch(renderType);
        renderLeaves(minecraft.level, camera, pose, cameraPos, partialTick, windTime, weatherWindPower, opacity);
    }

    private static void clear() {
        for (WindStreak streak : STREAKS) {
            streak.active = false;
            streak.fadingOut = false;
            streak.fadeOutAge = 0;
        }

        for (WindLeaf leaf : LEAVES) {
            leaf.active = false;
            leaf.fadingOut = false;
            leaf.fadeOutAge = 0;
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

        return false;
    }

    private static boolean shouldRenderAround(ClientLevel level, LocalPlayer player) {
        return level.dimensionType().hasSkyLight()
                && !player.isUnderWater()
                && isValidWindSpace(level, player.getX(), player.getEyeY(), player.getZ(), 0.0D);
    }

    private static int desiredStreakCount() {
        float visibility = (float) ClientConfig.WIND_STREAK_VISIBILITY.getAsDouble();
        if (visibility <= 0.0F) {
            return 0;
        }

        float weatherWindPower = ResponsiveFoliageShaders.weatherWindPower();
        return Mth.clamp(Math.round((7.0F + weatherWindPower * 7.5F) * Math.min(visibility, 1.6F)), 0, MAX_STREAKS);
    }

    private static int desiredLeafCount() {
        float visibility = (float) ClientConfig.WIND_STREAK_VISIBILITY.getAsDouble();
        if (visibility <= 0.0F) {
            return 0;
        }

        float weatherWindPower = ResponsiveFoliageShaders.weatherWindPower();
        return Mth.clamp(Math.round((4.0F + weatherWindPower * 6.0F) * Math.min(visibility, 1.8F)), 0, MAX_LEAVES);
    }

    private static RenderType windStreakLines() {
        double lineWidth = Math.round(ClientConfig.WIND_STREAK_THICKNESS.getAsDouble() * 10.0D) / 10.0D;
        if (cachedLineRenderType == null || Math.abs(cachedLineWidth - lineWidth) > 0.001D) {
            cachedLineWidth = lineWidth;
            cachedLineRenderType = RenderType.create(
                    "where_winds_blow_wind_streaks_" + lineWidth,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL,
                    VertexFormat.Mode.LINES,
                    65536,
                    false,
                    false,
                    RenderType.CompositeState.builder()
                            .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                            .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(lineWidth)))
                            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                            .setCullState(RenderStateShard.NO_CULL)
                            .createCompositeState(false)
            );
        }

        return cachedLineRenderType;
    }

    private static ResourceLocation leafTexture(int index) {
        return ResourceLocation.fromNamespaceAndPath(WhereWindsBlow.MOD_ID, "textures/particle/leaf_" + index + ".png");
    }

    private static RenderType windLeafTexture(ResourceLocation texture) {
        return RenderType.create(
                "where_winds_blow_wind_leaf_" + texture.getPath().replace('/', '_').replace('.', '_'),
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS,
                65536,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false)
        );
    }

    private static void tick(WindStreak streak, LocalPlayer player, ClientLevel level, float weatherWindPower) {
        streak.xOld = streak.x;
        streak.yOld = streak.y;
        streak.zOld = streak.z;

        if (streak.fadingOut) {
            tickFadeOut(streak);
            return;
        }

        streak.age++;

        if (!isOpenSkyAir(level, streak.x, streak.y, streak.z)) {
            beginFadeOut(streak);
            return;
        }

        if (streakBodyHitsCollision(level, streak, ResponsiveFoliageShaders.windTime())) {
            beginFadeOut(streak);
            return;
        }

        double dx = streak.x - player.getX();
        double dy = streak.y - player.getEyeY();
        double dz = streak.z - player.getZ();
        if (streak.age >= streak.lifetime
                || dx * dx + dy * dy + dz * dz > MAX_DISTANCE_FROM_PLAYER * MAX_DISTANCE_FROM_PLAYER) {
            spawn(streak, player, level, weatherWindPower, false);
        }
    }

    private static void spawn(WindStreak streak, LocalPlayer player, ClientLevel level, float weatherWindPower, boolean scatterAge) {
        double weatherBoost = weatherBoost(weatherWindPower);
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            Point spawn = randomOpenAirPosition(
                    player,
                    level,
                    -27.0D,
                    19.0D,
                    -29.0D,
                    29.0D,
                    -1.8D,
                    8.6D,
                    0.35D,
                    2.2D,
                    STREAK_TERRAIN_CLEARANCE,
                    0.1D,
                    1.8D
            );
            if (spawn == null) {
                break;
            }

            streak.x = spawn.x();
            streak.y = spawn.y();
            streak.z = spawn.z();
            streak.xOld = streak.x;
            streak.yOld = streak.y;
            streak.zOld = streak.z;
            streak.length = randomBetween(6.2D, 12.4D) * (1.0D + weatherBoost * 0.36D);
            streak.arc = randomBetween(-0.42D, 0.42D) * (1.0D + weatherBoost * 0.34D);
            streak.lift = randomBetween(0.04D, 0.22D) * (1.0D + weatherBoost * 0.2D);
            streak.speed = randomBetween(0.029D, 0.045D) * (1.0D + weatherBoost * 0.22D);
            streak.curveStrength = randomBetween(0.08D, 0.44D) * (1.0D + weatherBoost * 0.12D);
            streak.curvePhase = RANDOM.nextDouble() * Math.PI * 2.0D;
            streak.curveFrequency = randomBetween(0.72D, 1.35D);
            streak.curveSign = RANDOM.nextBoolean() ? 1.0D : -1.0D;
            streak.seed = RANDOM.nextDouble() * Math.PI * 2.0D;
            streak.lifetime = Math.max(38, Math.round((48 + RANDOM.nextInt(30)) / (float) (1.0D + weatherBoost * 0.18D)));
            streak.age = scatterAge ? RANDOM.nextInt(Math.max(1, streak.lifetime / 2)) : 0;
            streak.fadingOut = false;
            streak.fadeOutAge = 0;
            streak.active = true;
            if (!streakBodyHitsCollision(level, streak, ResponsiveFoliageShaders.windTime())) {
                return;
            }
        }

        streak.active = false;
        streak.fadingOut = false;
    }

    private static void tick(WindLeaf leaf, LocalPlayer player, ClientLevel level, float weatherWindPower) {
        leaf.xOld = leaf.x;
        leaf.yOld = leaf.y;
        leaf.zOld = leaf.z;

        if (leaf.fadingOut) {
            tickFadeOut(leaf);
            return;
        }

        leaf.age++;

        if (!isOpenSkyAir(level, leaf.x, leaf.y, leaf.z)) {
            beginFadeOut(leaf);
            return;
        }

        double weatherBoost = weatherBoost(weatherWindPower);
        double speed = leaf.speed * (1.0D + weatherBoost * 1.15D);
        double weave = Math.sin((leaf.age + leaf.seed) * 0.09D) * leaf.crossDrift * (1.0D + weatherBoost * 0.8D);
        TerrainFlow terrainFlow = terrainFlow(
                level,
                leaf.x,
                leaf.y,
                leaf.z,
                LEAF_TERRAIN_CLEARANCE,
                LEAF_TERRAIN_LIFT_STRENGTH,
                leaf.terrainLift,
                leaf.terrainSideFlow
        );
        leaf.terrainLift = terrainFlow.lift();
        leaf.terrainSideFlow = terrainFlow.side();
        double flutter = Math.sin((leaf.age + leaf.seed) * 0.084D) * leaf.verticalDrift * (1.0D + weatherBoost * 0.4D);
        Point next = keepAboveTerrainAndCollision(
                level,
                leaf.x + windX() * speed + crossX() * (weave + leaf.terrainSideFlow),
                leaf.y + leaf.terrainLift + flutter,
                leaf.z + windZ() * speed + crossZ() * (weave + leaf.terrainSideFlow),
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
            spawn(leaf, player, level, weatherWindPower, false);
        }
    }

    private static void spawn(WindLeaf leaf, LocalPlayer player, ClientLevel level, float weatherWindPower, boolean scatterAge) {
        double weatherBoost = weatherBoost(weatherWindPower);
        Point spawn = randomOpenAirPosition(
                player,
                level,
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

        leaf.x = spawn.x();
        leaf.y = spawn.y();
        leaf.z = spawn.z();
        leaf.xOld = leaf.x;
        leaf.yOld = leaf.y;
        leaf.zOld = leaf.z;
        leaf.textureIndex = RANDOM.nextInt(LEAF_TEXTURE_COUNT);
        leaf.size = randomBetween(0.09D, 0.23D) * (1.0D + weatherBoost * 0.12D);
        leaf.speed = randomBetween(0.18D, 0.32D);
        leaf.crossDrift = randomBetween(-0.018D, 0.018D) * (1.0D + weatherBoost * 0.7D);
        leaf.verticalDrift = randomBetween(0.003D, 0.012D) * (1.0D + weatherBoost * 0.35D);
        leaf.bob = randomBetween(0.025D, 0.075D) * (1.0D + weatherBoost * 0.3D);
        leaf.terrainLift = 0.0D;
        leaf.terrainSideFlow = 0.0D;
        leaf.tilt = randomBetween(-Math.PI, Math.PI);
        leaf.tiltSpeed = randomBetween(0.045D, 0.105D) * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        leaf.seed = RANDOM.nextDouble() * Math.PI * 2.0D;
        leaf.lifetime = Math.max(64, Math.round((94 + RANDOM.nextInt(70)) / (float) (1.0D + weatherBoost * 0.28D)));
        leaf.age = scatterAge ? RANDOM.nextInt(Math.max(1, leaf.lifetime / 2)) : 0;
        leaf.fadingOut = false;
        leaf.fadeOutAge = 0;
        int color = sampleGrassColor(level, leaf.x, player.getY(), leaf.z);
        leaf.red = color >> 16 & 255;
        leaf.green = color >> 8 & 255;
        leaf.blue = color & 255;
        leaf.active = true;
    }

    private static double weatherBoost(float weatherWindPower) {
        return Mth.clamp(weatherWindPower / 2.0F, 0.0F, 1.0F);
    }

    private static void beginFadeOut(WindStreak streak) {
        if (!streak.active || streak.fadingOut) {
            return;
        }

        streak.fadingOut = true;
        streak.fadeOutAge = 0;
        streak.xOld = streak.x;
        streak.yOld = streak.y;
        streak.zOld = streak.z;
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

        streak.xOld = streak.x;
        streak.yOld = streak.y;
        streak.zOld = streak.z;
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
            double x = player.getX() + windX() * along + crossX() * across;
            double y = player.getEyeY() + randomBetween(minEyeOffset, maxEyeOffset);
            double z = player.getZ() + windZ() * along + crossZ() * across;
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
            float weatherWindPower,
            float opacity
    ) {
        float life = ((float) streak.age + partialTick) / (float) streak.lifetime;
        float fade = smoothFade(Mth.clamp(life / 0.24F, 0.0F, 1.0F))
                * smoothFade(Mth.clamp((1.0F - life) / 0.34F, 0.0F, 1.0F));
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
        float alpha = 0.92F * opacity * fade * distanceFade * fadeOutMultiplier(streak, partialTick);
        if (alpha <= 0.006F) {
            return;
        }

        float brushPosition = (float) (((double) streak.age + partialTick) * streak.speed - 0.24D);
        for (int segment = 0; segment < BODY_SEGMENTS; segment++) {
            float t0 = (float) segment / (float) BODY_SEGMENTS;
            float t1 = (float) (segment + 1) / (float) BODY_SEGMENTS;
            drawSegment(consumer, pose, level, streak, cameraPos, baseX, baseY, baseZ, t0, t1, windTime, brushPosition, alpha);
        }

    }

    private static void renderLeaves(
            ClientLevel level,
            Camera camera,
            Matrix4f pose,
            Vec3 cameraPos,
            float partialTick,
            float windTime,
            float weatherWindPower,
            float opacity
    ) {
        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(LEAF_BUFFER);
        for (int textureIndex = 0; textureIndex < LEAF_TEXTURE_COUNT; textureIndex++) {
            VertexConsumer consumer = null;
            for (WindLeaf leaf : LEAVES) {
                if (leaf.active && leaf.textureIndex == textureIndex) {
                    if (consumer == null) {
                        consumer = bufferSource.getBuffer(LEAF_RENDER_TYPES[textureIndex]);
                    }
                    renderLeaf(consumer, pose, leaf, level, camera, cameraPos, partialTick, windTime, weatherWindPower, opacity);
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
            float weatherWindPower,
            float opacity
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
        double weatherFlutterBoost = 1.0D + weatherBoost(weatherWindPower) * 0.35D;
        double flutter = Math.sin(leaf.seed + ((double) leaf.age + partialTick) * 0.16D + windTime * 1.35D) * leaf.bob;
        double centerX = baseX + crossX() * flutter * weatherFlutterBoost;
        double centerY = baseY + Math.cos(leaf.seed * 0.7D + ((double) leaf.age + partialTick) * 0.13D) * leaf.bob;
        double centerZ = baseZ + crossZ() * flutter * weatherFlutterBoost;
        centerY = Math.max(centerY, terrainHeight(level, centerX, centerZ) + LEAF_TERRAIN_CLEARANCE);
        if (!leaf.fadingOut && !isOpenSkyAir(level, centerX, centerY, centerZ)) {
            return;
        }

        double dx = centerX - cameraPos.x();
        double dy = centerY - cameraPos.y();
        double dz = centerZ - cameraPos.z();
        double cameraDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float distanceFade = smoothFade(Mth.clamp((float) ((MAX_DISTANCE_FROM_PLAYER - cameraDistance) / 18.0D), 0.0F, 1.0F));
        float alpha = 0.95F * opacity * fade * distanceFade * fadeOutMultiplier(leaf, partialTick);
        if (alpha <= 0.006F) {
            return;
        }

        float tilt = (float) (leaf.tilt + ((double) leaf.age + partialTick) * leaf.tiltSpeed
                + Math.sin(leaf.seed + windTime * 0.9D) * 0.18D);
        float halfSize = (float) (leaf.size * (0.5D + Math.sin(leaf.seed * 1.3D + ((double) leaf.age + partialTick) * 0.11D) * 0.035D));
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
            float alpha
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
        emitLine(consumer, pose, cameraPos, start, end, alpha * taper0, alpha * taper1);
    }

    private static Point pointOnBody(WindStreak streak, double baseX, double baseY, double baseZ, float t, float windTime) {
        double along = t * streak.length;
        double flow = streak.seed;
        double pathEnvelope = Math.sin(t * Math.PI);
        double bodyCurve = pathEnvelope * streak.arc;
        double sCurve = Math.sin(t * Math.PI * streak.curveFrequency + streak.curvePhase)
                * streak.curveStrength
                * pathEnvelope
                * streak.curveSign;
        double softRipple = Math.sin(flow + t * Math.PI * 2.0D) * 0.024D * pathEnvelope;
        double crossOffset = bodyCurve + sCurve + softRipple;
        double x = baseX + windX() * along + crossX() * crossOffset;
        double y = baseY + pathEnvelope * streak.lift + sCurve * 0.08D;
        double z = baseZ + windZ() * along + crossZ() * crossOffset;
        return new Point(x, y, z);
    }

    private static float motionTaper(WindStreak streak, float t, float windTime, float brushPosition) {
        float shapeFade = smoothFade(Mth.clamp(t / 0.12F, 0.0F, 1.0F))
                * smoothFade(Mth.clamp((1.0F - t) / 0.2F, 0.0F, 1.0F));
        float leadingEdge = movingBrush(t, brushPosition, 0.13F) * 0.92F;
        float trailingWake = trailingWake(t, brushPosition, 0.58F);
        float curveBoost = Mth.sin(t * Mth.PI) * 0.08F;
        float shimmer = 0.9F + 0.1F * Mth.sin((float) streak.seed + windTime * 3.6F + t * Mth.PI * 6.0F);
        return shapeFade * shimmer * (leadingEdge + trailingWake * (0.68F + curveBoost));
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

    private static void emitLine(VertexConsumer consumer, Matrix4f pose, Vec3 cameraPos, Point start, Point end, float startAlpha, float endAlpha) {
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
            normalX = (float) windX();
            normalY = 0.0F;
            normalZ = (float) windZ();
        }

        consumer.addVertex(pose, (float) (start.x - cameraPos.x()), (float) (start.y - cameraPos.y()), (float) (start.z - cameraPos.z()))
                .setColor(RED, GREEN, BLUE, alpha0)
                .setNormal(normalX, normalY, normalZ);
        consumer.addVertex(pose, (float) (end.x - cameraPos.x()), (float) (end.y - cameraPos.y()), (float) (end.z - cameraPos.z()))
                .setColor(RED, GREEN, BLUE, alpha1)
                .setNormal(normalX, normalY, normalZ);
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

        emitLeafVertex(
                consumer,
                pose,
                cameraPos,
                centerX - axisXx * halfSize - axisYx * halfSize,
                centerY - axisXy * halfSize - axisYy * halfSize,
                centerZ - axisXz * halfSize - axisYz * halfSize,
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
                centerX + axisXx * halfSize - axisYx * halfSize,
                centerY + axisXy * halfSize - axisYy * halfSize,
                centerZ + axisXz * halfSize - axisYz * halfSize,
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
                centerX + axisXx * halfSize + axisYx * halfSize,
                centerY + axisXy * halfSize + axisYy * halfSize,
                centerZ + axisXz * halfSize + axisYz * halfSize,
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
                centerX - axisXx * halfSize + axisYx * halfSize,
                centerY - axisXy * halfSize + axisYy * halfSize,
                centerZ - axisXz * halfSize + axisYz * halfSize,
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

    private static int sampleGrassColor(ClientLevel level, double x, double y, double z) {
        if (level == null) {
            return GrassColor.get(0.5D, 1.0D);
        }

        return BiomeColors.getAverageGrassColor(level, BlockPos.containing(x, y, z));
    }

    private static TerrainFlow terrainFlow(
            ClientLevel level,
            double x,
            double y,
            double z,
            double clearance,
            double liftStrength,
            double currentLift,
            double currentSide
    ) {
        double nearX = x + windX() * (TERRAIN_LOOKAHEAD * 0.45D);
        double nearZ = z + windZ() * (TERRAIN_LOOKAHEAD * 0.45D);
        double forwardX = x + windX() * TERRAIN_LOOKAHEAD;
        double forwardZ = z + windZ() * TERRAIN_LOOKAHEAD;
        double currentHeight = terrainHeight(level, x, z);
        double nearHeight = terrainHeight(level, nearX, nearZ);
        double forwardHeight = terrainHeight(level, forwardX, forwardZ);
        double targetY = Math.max(Math.max(currentHeight, nearHeight), forwardHeight) + clearance;
        double heightError = targetY - y;
        double desiredLift = heightError > 0.0D
                ? Mth.clamp(heightError * liftStrength, 0.0D, MAX_TERRAIN_LIFT)
                : 0.0D;

        double leftHeight = terrainHeight(level, forwardX - crossX() * TERRAIN_SIDE_SAMPLE, forwardZ - crossZ() * TERRAIN_SIDE_SAMPLE);
        double rightHeight = terrainHeight(level, forwardX + crossX() * TERRAIN_SIDE_SAMPLE, forwardZ + crossZ() * TERRAIN_SIDE_SAMPLE);
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
        return !isValidWindSpace(level, destination.x(), destination.y(), destination.z(), LEAF_TERRAIN_CLEARANCE)
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

    private static double lerp(float amount, double start, double end) {
        return start + (end - start) * amount;
    }

    private static double windX() {
        return WindDirection.xDouble();
    }

    private static double windZ() {
        return WindDirection.zDouble();
    }

    private static double crossX() {
        return WindDirection.crossXDouble();
    }

    private static double crossZ() {
        return WindDirection.crossZDouble();
    }

    private static final class WindStreak {
        private boolean active;
        private boolean fadingOut;
        private int age;
        private int lifetime;
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
        private double curveStrength;
        private double curvePhase;
        private double curveFrequency;
        private double curveSign;
        private double seed;
    }

    private static final class WindLeaf {
        private boolean active;
        private boolean fadingOut;
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
        private double terrainLift;
        private double terrainSideFlow;
        private double bob;
        private double tilt;
        private double tiltSpeed;
        private double seed;
    }

    private record Point(double x, double y, double z) {
    }

    private record TerrainFlow(double lift, double side) {
    }
}
