package com.jvn.wherewindsblow.client.wind;

import com.jvn.toucanlib.client.ToucanEasing;
import com.jvn.wherewindsblow.client.weather.BlizzardWeatherEffects;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.Tags;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Ambient dead bushes that roll along exposed desert and badlands terrain. */
public final class TumbleweedRenderer {
    private static final int MAX_TUMBLEWEEDS = 4;
    private static final int SPAWN_ATTEMPTS = 18;
    private static final int FADE_TICKS = 20;
    private static final int WIND_SAMPLE_INTERVAL = 4;
    private static final double MAX_DISTANCE = 62.0D;
    private static final RenderLevelStageEvent.Stage RENDER_STAGE = RenderLevelStageEvent.Stage.AFTER_LEVEL;
    private static final BlockState DEAD_BUSH_STATE = Blocks.DEAD_BUSH.defaultBlockState();
    private static final ByteBufferBuilder BUFFER = new ByteBufferBuilder(32768);
    private static final Random RANDOM = new Random();
    private static final Tumbleweed[] TUMBLEWEEDS = new Tumbleweed[MAX_TUMBLEWEEDS];
    private static int spawnCooldown = 40;

    static {
        for (int index = 0; index < TUMBLEWEEDS.length; index++) {
            TUMBLEWEEDS[index] = new Tumbleweed();
        }
    }

    private TumbleweedRenderer() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || !ClientConfig.ENABLE_TUMBLEWEEDS.getAsBoolean()) {
            clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }

        boolean eligible = level.dimensionType().hasSkyLight()
                && !player.isUnderWater()
                && isDryBiome(level.getBiome(player.blockPosition()));
        WindSample playerWind = DynamicWindManager.sampleWind(level, player.blockPosition());
        int desiredCount = eligible ? desiredCount(level, playerWind) : 0;

        int activeCount = 0;
        for (int index = 0; index < TUMBLEWEEDS.length; index++) {
            Tumbleweed tumbleweed = TUMBLEWEEDS[index];
            if (!tumbleweed.active) {
                continue;
            }
            if (index >= desiredCount) {
                beginFadeOut(tumbleweed);
            }
            tick(tumbleweed, player, level, eligible);
            if (tumbleweed.active && !tumbleweed.fadingOut) {
                activeCount++;
            }
        }

        if (spawnCooldown > 0) {
            spawnCooldown--;
        }
        if (activeCount < desiredCount && spawnCooldown <= 0) {
            Tumbleweed inactive = firstInactive();
            if (inactive != null && spawn(inactive, player, level, playerWind)) {
                spawnCooldown = nextSpawnDelay(level, playerWind);
            } else {
                spawnCooldown = 40;
            }
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RENDER_STAGE || !ClientConfig.ENABLE_TUMBLEWEEDS.getAsBoolean()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || !hasActiveTumbleweed()) {
            return;
        }

        minecraft.getMainRenderTarget().bindWrite(false);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        try {
            Vec3 cameraPos = event.getCamera().getPosition();
            PoseStack poseStack = event.getPoseStack();
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            BlizzardWeatherEffects.BlizzardFogProfile stormFog =
                    BlizzardWeatherEffects.fogProfile(event.getCamera(), partialTick);
            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(BUFFER);
            BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
            for (Tumbleweed tumbleweed : TUMBLEWEEDS) {
                if (tumbleweed.active) {
                    render(
                            blockRenderer,
                            bufferSource,
                            poseStack,
                            level,
                            cameraPos,
                            tumbleweed,
                            partialTick,
                            stormFog
                    );
                }
            }
            bufferSource.endBatch();
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    public static void reset() {
        clear();
    }

    private static int desiredCount(ClientLevel level, WindSample wind) {
        float visibility = (float) ClientConfig.WIND_STREAK_VISIBILITY.getAsDouble();
        float density = (float) ClientConfig.TUMBLEWEED_DENSITY.getAsDouble();
        if (visibility <= 0.0F || density <= 0.0F || wind.strength() <= 0.08F) {
            return 0;
        }
        float rain = level.getRainLevel(1.0F);
        float thunder = level.getThunderLevel(1.0F);
        float weather = rain * 0.65F + thunder * 1.35F;
        float energy = wind.strength() + wind.gustStrength() * 0.95F + weather * 0.30F;
        return Mth.clamp(
                Math.round((energy * 0.82F + weather * 0.58F)
                        * Math.min(visibility, 1.8F) * density),
                0,
                MAX_TUMBLEWEEDS
        );
    }

    private static int nextSpawnDelay(ClientLevel level, WindSample wind) {
        float rain = level.getRainLevel(1.0F);
        float thunder = level.getThunderLevel(1.0F);
        float urgency = Mth.clamp(
                wind.strength() * 0.35F + wind.gustStrength() * 0.55F + rain * 0.45F + thunder * 1.15F,
                0.0F,
                2.6F
        );
        int minimum = Math.round(Mth.lerp(urgency / 2.6F, 150.0F, 34.0F));
        int maximum = Math.round(Mth.lerp(urgency / 2.6F, 360.0F, 96.0F));
        return minimum + RANDOM.nextInt(Math.max(1, maximum - minimum + 1));
    }

    private static void tick(Tumbleweed tumbleweed, LocalPlayer player, ClientLevel level, boolean playerEligible) {
        tumbleweed.xOld = tumbleweed.x;
        tumbleweed.yOld = tumbleweed.y;
        tumbleweed.zOld = tumbleweed.z;
        tumbleweed.rollOld = tumbleweed.roll;
        tumbleweed.yawOld = tumbleweed.yaw;

        if (tumbleweed.fadingOut) {
            tumbleweed.fadeAge++;
            if (tumbleweed.fadeAge >= FADE_TICKS) {
                tumbleweed.active = false;
            }
            return;
        }
        if (!playerEligible || tumbleweed.age++ >= tumbleweed.lifetime) {
            beginFadeOut(tumbleweed);
            return;
        }

        if (--tumbleweed.windSampleCountdown <= 0) {
            applyWind(tumbleweed, DynamicWindManager.sampleWind(level, BlockPos.containing(
                    tumbleweed.x, tumbleweed.y, tumbleweed.z
            )), false);
        }

        float rain = level.getRainLevel(1.0F);
        float thunder = level.getThunderLevel(1.0F);
        double weather = rain * 0.32D + thunder * 0.62D;
        double motionScale = Mth.clamp(
                0.48D + tumbleweed.windStrength * 1.42D + tumbleweed.gustStrength * 1.05D + weather,
                0.35D,
                3.2D
        );
        double speed = tumbleweed.baseSpeed * motionScale;
        double time = tumbleweed.age + tumbleweed.seed;
        double sideFlow = Math.sin(time * 0.115D) * tumbleweed.sideDrift
                * (1.0D + tumbleweed.turbulence * 2.2D + thunder * 0.7D);
        double nextX = tumbleweed.x + tumbleweed.windX * speed + tumbleweed.crossX * sideFlow;
        double nextZ = tumbleweed.z + tumbleweed.windZ * speed + tumbleweed.crossZ * sideFlow;
        Holder<Biome> nextBiome = level.getBiome(BlockPos.containing(nextX, tumbleweed.y, nextZ));
        if (!isDryBiome(nextBiome)) {
            beginFadeOut(tumbleweed);
            return;
        }

        double groundY = terrainHeight(level, nextX, nextZ) + tumbleweed.radius;
        if (groundY - tumbleweed.y > 1.05D) {
            beginFadeOut(tumbleweed);
            return;
        }

        tumbleweed.verticalVelocity -= 0.032D;
        double nextY = tumbleweed.y + tumbleweed.verticalVelocity;
        if (nextY <= groundY) {
            nextY = groundY;
            double bounceEnergy = 0.035D + speed * 0.28D
                    + tumbleweed.gustStrength * 0.055D + thunder * 0.045D;
            double bouncePulse = 0.72D + Math.abs(Math.sin(time * 0.37D)) * 0.48D;
            tumbleweed.verticalVelocity = bounceEnergy * bouncePulse;
        }

        tumbleweed.x = nextX;
        tumbleweed.y = nextY;
        tumbleweed.z = nextZ;
        tumbleweed.roll += speed / Math.max(tumbleweed.radius, 0.25D)
                * (0.82D + tumbleweed.turbulence * 0.38D);
        tumbleweed.yaw += sideFlow * 0.22D + Math.sin(time * 0.07D) * 0.006D;

        double dx = tumbleweed.x - player.getX();
        double dy = tumbleweed.y - player.getY();
        double dz = tumbleweed.z - player.getZ();
        if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE * MAX_DISTANCE) {
            tumbleweed.active = false;
        }
    }

    private static boolean spawn(
            Tumbleweed tumbleweed,
            LocalPlayer player,
            ClientLevel level,
            WindSample playerWind
    ) {
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double along = randomBetween(-36.0D, -18.0D);
            double across = randomBetween(-22.0D, 22.0D);
            double x = player.getX() + playerWind.directionX() * along + playerWind.crossX() * across;
            double z = player.getZ() + playerWind.directionZ() * along + playerWind.crossZ() * across;
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            BlockPos spawnPos = BlockPos.containing(x, surfaceY, z);
            if (!isDryBiome(level.getBiome(spawnPos))
                    || !level.getBlockState(spawnPos).isAir()
                    || !level.canSeeSky(spawnPos)) {
                continue;
            }

            WindSample localWind = DynamicWindManager.sampleWind(level, spawnPos);
            if (localWind.strength() <= 0.08F) {
                continue;
            }
            double radius = randomBetween(0.42D, 0.66D);
            tumbleweed.x = x;
            tumbleweed.y = surfaceY + radius;
            tumbleweed.z = z;
            tumbleweed.xOld = tumbleweed.x;
            tumbleweed.yOld = tumbleweed.y;
            tumbleweed.zOld = tumbleweed.z;
            tumbleweed.radius = radius;
            tumbleweed.baseSpeed = randomBetween(0.075D, 0.125D);
            tumbleweed.sideDrift = randomBetween(0.006D, 0.022D)
                    * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
            tumbleweed.verticalVelocity = randomBetween(0.025D, 0.075D);
            tumbleweed.roll = RANDOM.nextDouble() * Math.PI * 2.0D;
            tumbleweed.rollOld = tumbleweed.roll;
            tumbleweed.yaw = RANDOM.nextDouble() * Math.PI * 2.0D;
            tumbleweed.yawOld = tumbleweed.yaw;
            tumbleweed.seed = RANDOM.nextDouble() * Math.PI * 2.0D;
            tumbleweed.age = RANDOM.nextInt(20);
            tumbleweed.lifetime = 190 + RANDOM.nextInt(150);
            tumbleweed.fadeAge = 0;
            tumbleweed.fadingOut = false;
            tumbleweed.active = true;
            applyWind(tumbleweed, localWind, true);
            return true;
        }
        return false;
    }

    private static void render(
            BlockRenderDispatcher blockRenderer,
            MultiBufferSource.BufferSource bufferSource,
            PoseStack poseStack,
            ClientLevel level,
            Vec3 cameraPos,
            Tumbleweed tumbleweed,
            float partialTick,
            BlizzardWeatherEffects.BlizzardFogProfile stormFog
    ) {
        double x = Mth.lerp(partialTick, tumbleweed.xOld, tumbleweed.x);
        double y = Mth.lerp(partialTick, tumbleweed.yOld, tumbleweed.y);
        double z = Mth.lerp(partialTick, tumbleweed.zOld, tumbleweed.z);
        double dx = x - cameraPos.x();
        double dy = y - cameraPos.y();
        double dz = z - cameraPos.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float distanceFade = ToucanEasing.smoothstep(Mth.clamp((float) ((MAX_DISTANCE - distance) / 14.0D), 0.0F, 1.0F));
        float fadeIn = ToucanEasing.smoothstep(Mth.clamp((tumbleweed.age + partialTick) / 14.0F, 0.0F, 1.0F));
        float fadeOut = tumbleweed.fadingOut
                ? 1.0F - Mth.clamp((tumbleweed.fadeAge + partialTick) / FADE_TICKS, 0.0F, 1.0F)
                : ToucanEasing.smoothstep(Mth.clamp((tumbleweed.lifetime - tumbleweed.age) / 28.0F, 0.0F, 1.0F));
        float visibleScale = distanceFade * fadeIn * fadeOut * stormFog.visibility(distance);
        if (visibleScale <= 0.01F) {
            return;
        }

        float roll = (float) Mth.lerp(partialTick, tumbleweed.rollOld, tumbleweed.roll);
        float yaw = (float) Mth.lerp(partialTick, tumbleweed.yawOld, tumbleweed.yaw);
        float axisX = (float) tumbleweed.windZ;
        float axisZ = (float) -tumbleweed.windX;
        float modelScale = (float) (tumbleweed.radius * 2.0D) * visibleScale;
        BlockPos modelPos = BlockPos.containing(x, terrainHeight(level, x, z), z);

        poseStack.pushPose();
        poseStack.translate(x - cameraPos.x(), y - cameraPos.y(), z - cameraPos.z());
        poseStack.mulPose(new Quaternionf().rotationAxis(roll, axisX, 0.0F, axisZ));
        poseStack.mulPose(new Quaternionf().rotationY(yaw));
        poseStack.scale(modelScale, modelScale, modelScale);
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        RandomSource renderTypeRandom = RandomSource.create(Double.doubleToLongBits(tumbleweed.seed));
        for (RenderType renderType : blockRenderer.getBlockModel(DEAD_BUSH_STATE)
                .getRenderTypes(DEAD_BUSH_STATE, renderTypeRandom, ModelData.EMPTY)) {
            VertexConsumer consumer = bufferSource.getBuffer(renderType);
            blockRenderer.renderBatched(
                    DEAD_BUSH_STATE,
                    modelPos,
                    level,
                    poseStack,
                    consumer,
                    false,
                    RandomSource.create(Double.doubleToLongBits(tumbleweed.seed)),
                    ModelData.EMPTY,
                    renderType
            );
        }
        poseStack.popPose();
    }

    private static void applyWind(Tumbleweed tumbleweed, WindSample wind, boolean immediate) {
        double blend = immediate ? 1.0D : 0.38D;
        double x = Mth.lerp(blend, tumbleweed.windX, wind.directionX());
        double z = Mth.lerp(blend, tumbleweed.windZ, wind.directionZ());
        double length = Math.sqrt(x * x + z * z);
        if (length > 0.0001D) {
            x /= length;
            z /= length;
        }
        tumbleweed.windX = x;
        tumbleweed.windZ = z;
        tumbleweed.crossX = -z;
        tumbleweed.crossZ = x;
        tumbleweed.windStrength = Mth.lerp(blend, tumbleweed.windStrength, wind.strength());
        tumbleweed.gustStrength = Mth.lerp(blend, tumbleweed.gustStrength, wind.gustStrength());
        tumbleweed.turbulence = Mth.lerp(blend, tumbleweed.turbulence, wind.turbulence());
        tumbleweed.windSampleCountdown = WIND_SAMPLE_INTERVAL;
    }

    private static boolean isDryBiome(Holder<Biome> biome) {
        return biome.is(Tags.Biomes.IS_DESERT) || biome.is(Tags.Biomes.IS_BADLANDS);
    }

    private static double terrainHeight(ClientLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
    }

    private static void beginFadeOut(Tumbleweed tumbleweed) {
        if (!tumbleweed.fadingOut) {
            tumbleweed.fadingOut = true;
            tumbleweed.fadeAge = 0;
        }
    }

    private static Tumbleweed firstInactive() {
        for (Tumbleweed tumbleweed : TUMBLEWEEDS) {
            if (!tumbleweed.active) {
                return tumbleweed;
            }
        }
        return null;
    }

    private static boolean hasActiveTumbleweed() {
        for (Tumbleweed tumbleweed : TUMBLEWEEDS) {
            if (tumbleweed.active) {
                return true;
            }
        }
        return false;
    }

    private static void clear() {
        for (Tumbleweed tumbleweed : TUMBLEWEEDS) {
            tumbleweed.active = false;
            tumbleweed.fadingOut = false;
        }
        spawnCooldown = 40;
    }

    private static double randomBetween(double minimum, double maximum) {
        return Mth.lerp(RANDOM.nextDouble(), minimum, maximum);
    }

    private static final class Tumbleweed {
        private boolean active;
        private boolean fadingOut;
        private int age;
        private int lifetime;
        private int fadeAge;
        private int windSampleCountdown;
        private double x;
        private double y;
        private double z;
        private double xOld;
        private double yOld;
        private double zOld;
        private double radius;
        private double baseSpeed;
        private double sideDrift;
        private double verticalVelocity;
        private double roll;
        private double rollOld;
        private double yaw;
        private double yawOld;
        private double seed;
        private double windX;
        private double windZ;
        private double crossX;
        private double crossZ;
        private double windStrength;
        private double gustStrength;
        private double turbulence;
    }
}
