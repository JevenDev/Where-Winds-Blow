package com.jvn.wherewindsblow.client.wind;

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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

public final class WindStreakRenderer {
    private static final int MAX_STREAKS = 20;
    private static final int BODY_SEGMENTS = 5;
    private static final double WIND_X = 0.821188D;
    private static final double WIND_Z = 0.570658D;
    private static final double CROSS_X = -WIND_Z;
    private static final double CROSS_Z = WIND_X;
    private static final double MAX_DISTANCE_FROM_PLAYER = 56.0D;
    private static final int RED = 232;
    private static final int GREEN = 246;
    private static final int BLUE = 255;
    private static final RenderLevelStageEvent.Stage RENDER_STAGE = RenderLevelStageEvent.Stage.AFTER_WEATHER;
    private static final Random RANDOM = new Random();
    private static final WindStreak[] STREAKS = new WindStreak[MAX_STREAKS];
    private static final ByteBufferBuilder LINE_BUFFER = new ByteBufferBuilder(65536);
    private static double cachedLineWidth = -1.0D;
    private static RenderType cachedLineRenderType;

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
        if (level == null || player == null || !ClientConfig.ENABLE_WIND_STREAKS.getAsBoolean() || !shouldRenderAround(level, player)) {
            clear();
            return;
        }

        int desiredCount = desiredStreakCount();
        float weatherWindPower = ResponsiveFoliageShaders.weatherWindPower();
        for (int index = 0; index < STREAKS.length; index++) {
            WindStreak streak = STREAKS[index];
            if (index >= desiredCount) {
                streak.active = false;
                continue;
            }

            if (!streak.active) {
                spawn(streak, player, weatherWindPower, true);
            } else {
                tick(streak, player, weatherWindPower);
            }
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RENDER_STAGE
                || !ClientConfig.ENABLE_WIND_STREAKS.getAsBoolean()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !shouldRenderAround(minecraft.level, minecraft.player)) {
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
                renderStreak(consumer, pose, streak, cameraPos, partialTick, windTime, weatherWindPower, opacity);
            }
        }

        bufferSource.endBatch(renderType);
    }

    private static void clear() {
        for (WindStreak streak : STREAKS) {
            streak.active = false;
        }
    }

    private static boolean shouldRenderAround(ClientLevel level, LocalPlayer player) {
        return !player.isUnderWater() && level.getBrightness(LightLayer.SKY, player.blockPosition()) > 0;
    }

    private static int desiredStreakCount() {
        float visibility = (float) ClientConfig.WIND_STREAK_VISIBILITY.getAsDouble();
        if (visibility <= 0.0F) {
            return 0;
        }

        float weatherWindPower = ResponsiveFoliageShaders.weatherWindPower();
        return Mth.clamp(Math.round((7.0F + weatherWindPower * 7.5F) * Math.min(visibility, 1.6F)), 0, MAX_STREAKS);
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

    private static void tick(WindStreak streak, LocalPlayer player, float weatherWindPower) {
        streak.xOld = streak.x;
        streak.yOld = streak.y;
        streak.zOld = streak.z;
        streak.age++;

        double weatherBoost = weatherBoost(weatherWindPower);
        double speed = streak.speed * (1.0D + weatherBoost * 1.1D);
        double weave = Math.sin((streak.age + streak.seed) * 0.075D) * streak.crossDrift * (1.0D + weatherBoost * 0.65D);
        streak.x += WIND_X * speed + CROSS_X * weave;
        streak.y += streak.verticalDrift;
        streak.z += WIND_Z * speed + CROSS_Z * weave;

        double dx = streak.x - player.getX();
        double dy = streak.y - player.getEyeY();
        double dz = streak.z - player.getZ();
        if (streak.age >= streak.lifetime
                || dx * dx + dy * dy + dz * dz > MAX_DISTANCE_FROM_PLAYER * MAX_DISTANCE_FROM_PLAYER) {
            spawn(streak, player, weatherWindPower, false);
        }
    }

    private static void spawn(WindStreak streak, LocalPlayer player, float weatherWindPower, boolean scatterAge) {
        double weatherBoost = weatherBoost(weatherWindPower);
        double along = randomBetween(-34.0D, 24.0D);
        double across = randomBetween(-36.0D, 36.0D);
        streak.x = player.getX() + WIND_X * along + CROSS_X * across;
        streak.y = player.getEyeY() + randomBetween(-2.5D, 9.0D);
        streak.z = player.getZ() + WIND_Z * along + CROSS_Z * across;
        if (streak.y < player.getY() + 0.35D) {
            streak.y = player.getY() + randomBetween(0.35D, 2.2D);
        }
        streak.xOld = streak.x;
        streak.yOld = streak.y;
        streak.zOld = streak.z;
        streak.length = randomBetween(5.2D, 10.8D) * (1.0D + weatherBoost * 0.36D);
        streak.arc = randomBetween(-0.42D, 0.42D) * (1.0D + weatherBoost * 0.34D);
        streak.lift = randomBetween(-0.12D, 0.28D) * (1.0D + weatherBoost * 0.2D);
        streak.speed = randomBetween(0.2D, 0.36D);
        streak.crossDrift = randomBetween(-0.006D, 0.006D) * (1.0D + weatherBoost * 0.75D);
        streak.verticalDrift = randomBetween(-0.006D, 0.014D) * (1.0D + weatherBoost * 0.35D);
        streak.seed = RANDOM.nextDouble() * Math.PI * 2.0D;
        streak.lifetime = Math.max(48, Math.round((80 + RANDOM.nextInt(58)) / (float) (1.0D + weatherBoost * 0.32D)));
        streak.age = scatterAge ? RANDOM.nextInt(Math.max(1, streak.lifetime / 2)) : 0;
        streak.active = true;
    }

    private static double weatherBoost(float weatherWindPower) {
        return Mth.clamp(weatherWindPower / 2.0F, 0.0F, 1.0F);
    }

    private static void renderStreak(
            VertexConsumer consumer,
            Matrix4f pose,
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
        double cameraDistance = cameraPos.distanceTo(new Vec3(baseX, baseY, baseZ));
        float distanceFade = smoothFade(Mth.clamp((float) ((MAX_DISTANCE_FROM_PLAYER - cameraDistance) / 18.0D), 0.0F, 1.0F));
        float alpha = 0.82F * opacity * fade * distanceFade;
        if (alpha <= 0.006F) {
            return;
        }

        for (int segment = 0; segment < BODY_SEGMENTS; segment++) {
            float t0 = (float) segment / (float) BODY_SEGMENTS;
            float t1 = (float) (segment + 1) / (float) BODY_SEGMENTS;
            drawSegment(consumer, pose, streak, cameraPos, baseX, baseY, baseZ, t0, t1, windTime, alpha);
        }

    }

    private static void drawSegment(
            VertexConsumer consumer,
            Matrix4f pose,
            WindStreak streak,
            Vec3 cameraPos,
            double baseX,
            double baseY,
            double baseZ,
            float t0,
            float t1,
            float windTime,
            float alpha
    ) {
        Point start = pointOnBody(streak, baseX, baseY, baseZ, t0, windTime);
        Point end = pointOnBody(streak, baseX, baseY, baseZ, t1, windTime);
        float taper0 = Mth.sin(t0 * Mth.PI);
        float taper1 = Mth.sin(t1 * Mth.PI);
        emitLine(consumer, pose, cameraPos, start, end, alpha * taper0, alpha * taper1);
    }

    private static Point pointOnBody(WindStreak streak, double baseX, double baseY, double baseZ, float t, float windTime) {
        double along = t * streak.length;
        double bodyCurve = Math.sin(t * Math.PI) * streak.arc;
        double flutter = Math.sin(streak.seed + t * Math.PI * 2.0D + windTime * 0.7D) * 0.055D;
        double x = baseX + WIND_X * along + CROSS_X * (bodyCurve + flutter);
        double y = baseY + Math.sin(t * Math.PI + streak.seed) * streak.lift;
        double z = baseZ + WIND_Z * along + CROSS_Z * (bodyCurve + flutter);
        return new Point(x, y, z);
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
            normalX = (float) WIND_X;
            normalY = 0.0F;
            normalZ = (float) WIND_Z;
        }

        consumer.addVertex(pose, (float) (start.x - cameraPos.x()), (float) (start.y - cameraPos.y()), (float) (start.z - cameraPos.z()))
                .setColor(RED, GREEN, BLUE, alpha0)
                .setNormal(normalX, normalY, normalZ);
        consumer.addVertex(pose, (float) (end.x - cameraPos.x()), (float) (end.y - cameraPos.y()), (float) (end.z - cameraPos.z()))
                .setColor(RED, GREEN, BLUE, alpha1)
                .setNormal(normalX, normalY, normalZ);
    }

    private static int alphaByte(float alpha) {
        return Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
    }

    private static float smoothFade(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static double randomBetween(double min, double max) {
        return min + RANDOM.nextDouble() * (max - min);
    }

    private static double lerp(float amount, double start, double end) {
        return start + (end - start) * amount;
    }

    private static final class WindStreak {
        private boolean active;
        private int age;
        private int lifetime;
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
        private double crossDrift;
        private double verticalDrift;
        private double seed;
    }

    private record Point(double x, double y, double z) {
    }
}
