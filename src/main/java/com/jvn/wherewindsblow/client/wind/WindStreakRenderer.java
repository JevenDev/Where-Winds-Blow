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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class WindStreakRenderer {
    private static final int MAX_STREAKS = 20;
    private static final int MAX_LEAVES = 18;
    private static final int LEAF_TEXTURE_COUNT = 12;
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
        if (level == null || player == null || !ClientConfig.ENABLE_WIND_STREAKS.getAsBoolean() || !shouldRenderAround(level, player)) {
            clear();
            return;
        }

        int desiredCount = desiredStreakCount();
        int desiredLeafCount = desiredLeafCount();
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

        for (int index = 0; index < LEAVES.length; index++) {
            WindLeaf leaf = LEAVES[index];
            if (index >= desiredLeafCount) {
                leaf.active = false;
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
        renderLeaves(minecraft.level, camera, pose, cameraPos, partialTick, windTime, weatherWindPower, opacity);
    }

    private static void clear() {
        for (WindStreak streak : STREAKS) {
            streak.active = false;
        }

        for (WindLeaf leaf : LEAVES) {
            leaf.active = false;
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

    private static void tick(WindLeaf leaf, LocalPlayer player, ClientLevel level, float weatherWindPower) {
        leaf.xOld = leaf.x;
        leaf.yOld = leaf.y;
        leaf.zOld = leaf.z;
        leaf.age++;

        double weatherBoost = weatherBoost(weatherWindPower);
        double speed = leaf.speed * (1.0D + weatherBoost * 1.15D);
        double weave = Math.sin((leaf.age + leaf.seed) * 0.09D) * leaf.crossDrift * (1.0D + weatherBoost * 0.8D);
        leaf.x += WIND_X * speed + CROSS_X * weave;
        leaf.y += leaf.verticalDrift;
        leaf.z += WIND_Z * speed + CROSS_Z * weave;

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
        double along = randomBetween(-32.0D, 22.0D);
        double across = randomBetween(-34.0D, 34.0D);
        leaf.x = player.getX() + WIND_X * along + CROSS_X * across;
        leaf.y = player.getEyeY() + randomBetween(-1.6D, 6.6D);
        leaf.z = player.getZ() + WIND_Z * along + CROSS_Z * across;
        if (leaf.y < player.getY() + 0.3D) {
            leaf.y = player.getY() + randomBetween(0.3D, 1.8D);
        }
        leaf.xOld = leaf.x;
        leaf.yOld = leaf.y;
        leaf.zOld = leaf.z;
        leaf.textureIndex = RANDOM.nextInt(LEAF_TEXTURE_COUNT);
        leaf.size = randomBetween(0.12D, 0.19D) * (1.0D + weatherBoost * 0.12D);
        leaf.speed = randomBetween(0.13D, 0.25D);
        leaf.crossDrift = randomBetween(-0.018D, 0.018D) * (1.0D + weatherBoost * 0.7D);
        leaf.verticalDrift = randomBetween(-0.008D, 0.014D) * (1.0D + weatherBoost * 0.35D);
        leaf.bob = randomBetween(0.025D, 0.075D) * (1.0D + weatherBoost * 0.3D);
        leaf.tilt = randomBetween(-Math.PI, Math.PI);
        leaf.tiltSpeed = randomBetween(0.045D, 0.105D) * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        leaf.seed = RANDOM.nextDouble() * Math.PI * 2.0D;
        leaf.lifetime = Math.max(64, Math.round((94 + RANDOM.nextInt(70)) / (float) (1.0D + weatherBoost * 0.28D)));
        leaf.age = scatterAge ? RANDOM.nextInt(Math.max(1, leaf.lifetime / 2)) : 0;
        int color = sampleGrassColor(level, leaf.x, player.getY(), leaf.z);
        leaf.red = color >> 16 & 255;
        leaf.green = color >> 8 & 255;
        leaf.blue = color & 255;
        leaf.active = true;
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
        double centerX = baseX + CROSS_X * flutter * weatherFlutterBoost;
        double centerY = baseY + Math.cos(leaf.seed * 0.7D + ((double) leaf.age + partialTick) * 0.13D) * leaf.bob;
        double centerZ = baseZ + CROSS_Z * flutter * weatherFlutterBoost;
        double dx = centerX - cameraPos.x();
        double dy = centerY - cameraPos.y();
        double dz = centerZ - cameraPos.z();
        double cameraDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float distanceFade = smoothFade(Mth.clamp((float) ((MAX_DISTANCE_FROM_PLAYER - cameraDistance) / 18.0D), 0.0F, 1.0F));
        float alpha = 0.95F * opacity * fade * distanceFade;
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

    private static final class WindLeaf {
        private boolean active;
        private int age;
        private int lifetime;
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
        private double bob;
        private double tilt;
        private double tiltSpeed;
        private double seed;
    }

    private record Point(double x, double y, double z) {
    }
}
