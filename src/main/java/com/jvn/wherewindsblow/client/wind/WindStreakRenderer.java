package com.jvn.wherewindsblow.client.wind;

import com.mojang.blaze3d.systems.RenderSystem;
import com.jvn.wherewindsblow.client.weather.BlizzardWeatherEffects;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.FADE_OUT_TICKS;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.MAX_DISTANCE_FROM_PLAYER;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.Point;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.RANDOM;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.SPAWN_ATTEMPTS;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.TerrainFlow;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.WIND_SAMPLE_INTERVAL_TICKS;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.acceptSpawnForWind;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.alphaByte;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.distanceTo;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.isOpenSkyAir;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.isValidWindSpace;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.keepAboveTerrainAndCollision;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.lerp;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.lineHitsCollision;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.randomBetween;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.sampleWind;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.smoothFade;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.terrainFlow;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.terrainHeight;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.triangularRandom;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.windBoost;

public final class WindStreakRenderer {
    private static final int MAX_STREAKS = 20;
    private static final int BODY_SEGMENTS = 24;
    private static final double STREAK_TERRAIN_CLEARANCE = 1.45D;
    private static final double STREAK_TERRAIN_LIFT_STRENGTH = 0.18D;
    private static final double STREAK_PATH_ADVECTION = 0.34D;
    private static final double STREAK_MIN_STROKE_SCALE = 0.58D;
    private static final double STREAK_HALO_WIDTH_SCALE = 1.35D;
    private static final double STREAK_CORE_WIDTH_SCALE = 0.56D;
    private static final float STREAK_HALO_OPACITY = 0.2F;
    private static final double STREAK_MIN_HORIZONTAL_SPACING = 7.5D;
    private static final double STREAK_MIN_VERTICAL_SPACING = 2.8D;
    private static final int STREAK_FADE_IN_TICKS = 26;
    private static final int RED = 232;
    private static final int GREEN = 246;
    private static final int BLUE = 255;
    private static final RenderLevelStageEvent.Stage RENDER_STAGE = RenderLevelStageEvent.Stage.AFTER_LEVEL;
    private static final WindStreak[] STREAKS = new WindStreak[MAX_STREAKS];
    private static final ByteBufferBuilder LINE_BUFFER = new ByteBufferBuilder(65536);
    private static final Map<Double, RenderType> LINE_RENDER_TYPES = new HashMap<>();

    static {
        for (int index = 0; index < STREAKS.length; index++) {
            STREAKS[index] = new WindStreak();
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

        WindDebrisRenderer.tick(level, player, playerWind, canSpawnWind);
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
            double windTime = DynamicWindManager.simulationTime();
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
                WindDebrisRenderer.renderLeaves(
                        minecraft.level, camera, pose, cameraPos,
                        partialTick, windTime, leafOpacity, blizzardFog
                );
            }

            if (flowerPetalOpacity > 0.0F) {
                WindDebrisRenderer.renderFlowerPetals(
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

        WindDebrisRenderer.clear();
    }

    private static boolean hasActiveWind() {
        for (WindStreak streak : STREAKS) {
            if (streak.active) {
                return true;
            }
        }

        return WindDebrisRenderer.hasActiveParticles();
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

    private static void beginFadeOut(WindStreak streak) {
        if (!streak.active || streak.fadingOut) {
            return;
        }

        streak.fadingOut = true;
        streak.fadeOutAge = 0;
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

    private static float fadeOutMultiplier(WindStreak streak, float partialTick) {
        if (!streak.fadingOut) {
            return 1.0F;
        }

        float remaining = 1.0F - Mth.clamp(((float) streak.fadeOutAge + partialTick) / (float) FADE_OUT_TICKS, 0.0F, 1.0F);
        return smoothFade(remaining);
    }

    private static void renderStreak(
            VertexConsumer consumer,
            Matrix4f pose,
            ClientLevel level,
            WindStreak streak,
            Vec3 cameraPos,
            float partialTick,
            double windTime,
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
            double windTime,
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

    private static Point pointOnBody(WindStreak streak, double baseX, double baseY, double baseZ, float t, double windTime) {
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

    private static float motionTaper(WindStreak streak, float t, double windTime, float brushPosition) {
        float shapeFade = smoothFade(Mth.clamp(t / 0.12F, 0.0F, 1.0F))
                * smoothFade(Mth.clamp((1.0F - t) / 0.2F, 0.0F, 1.0F));
        float leadingEdge = movingBrush(t, brushPosition, (float) streak.brushWidth);
        float trailingWake = trailingWake(t, brushPosition, (float) streak.wakeLength);
        float curveBoost = Mth.sin(t * Mth.PI) * 0.06F;
        float shimmer = (float) (1.0D - streak.shimmerStrength
                + streak.shimmerStrength * (float) Math.sin(streak.seed + windTime * 1.8F + t * Mth.PI * 3.0F));
        float fineBreakup = 0.965F
                + 0.035F * (float) Math.sin(streak.ripplePhase + windTime * 2.4F + t * Mth.PI * 6.0F);
        float flowingStroke = Math.max(leadingEdge, trailingWake * (0.68F + curveBoost));
        return shapeFade * shimmer * fineBreakup * flowingStroke;
    }

    private static boolean streakBodyHitsCollision(ClientLevel level, WindStreak streak, double windTime) {
        return streakBodyHitsCollision(level, streak, streak.x, streak.y, streak.z, windTime);
    }

    private static boolean streakBodyHitsCollision(ClientLevel level, WindStreak streak, double baseX, double baseY, double baseZ, double windTime) {
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

        float normalX = (float) (end.x() - start.x());
        float normalY = (float) (end.y() - start.y());
        float normalZ = (float) (end.z() - start.z());
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

        consumer.addVertex(pose, (float) (start.x() - cameraPos.x()), (float) (start.y() - cameraPos.y()), (float) (start.z() - cameraPos.z()))
                .setColor(RED, GREEN, BLUE, alpha0)
                .setNormal(normalX * startWidthScale, normalY * startWidthScale, normalZ * startWidthScale);
        consumer.addVertex(pose, (float) (end.x() - cameraPos.x()), (float) (end.y() - cameraPos.y()), (float) (end.z() - cameraPos.z()))
                .setColor(RED, GREEN, BLUE, alpha1)
                .setNormal(normalX * endWidthScale, normalY * endWidthScale, normalZ * endWidthScale);
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
}
