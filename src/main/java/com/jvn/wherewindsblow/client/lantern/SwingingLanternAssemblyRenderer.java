package com.jvn.wherewindsblow.client.lantern;

import com.jvn.toucanlib.client.ToucanEasing;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.WindSample;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;

public final class SwingingLanternAssemblyRenderer {
    private static final RenderLevelStageEvent.Stage RENDER_STAGE = RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES;
    private static final int CHUNK_RENDER_RADIUS = 5;
    private static final int MAX_CHUNK_CACHE_SCANS_PER_FRAME = 2;
    private static final int MAX_LANTERN_CHUNK_CACHE_SIZE = 512;
    private static final int MAX_SWING_STATES = 4096;
    private static final int MAX_CHAIN_HEIGHT = 32;
    private static final int COLLISION_BINARY_SEARCH_STEPS = 7;
    private static final int COLLISION_CHECK_INTERVAL_TICKS = 2;
    private static final double COLLISION_GRID_EPSILON = 1.0E-7D;
    private static final double CHAIN_COLLISION_DIAMETER = 0.10D;
    private static final double LANTERN_COLLISION_WIDTH = 0.42D;
    private static final double LANTERN_COLLISION_HEIGHT = 0.72D;
    private static final float ENCLOSED_SWAY_SCALE = 0.12F;
    private static final float MAX_TARGET_SWAY_DEGREES = 14.0F;
    private static final float MAX_SIMULATED_SWAY_DEGREES = 18.0F;
    private static final ModelData ASSEMBLY_MODEL_DATA = ModelData.of(LanternModelData.RENDERING_ASSEMBLY, Boolean.TRUE);
    private static final Map<Long, SwingState> SWING_STATES = new ConcurrentHashMap<>();
    private static final Map<Long, LanternChunkCache> LANTERN_CHUNK_CACHE = new ConcurrentHashMap<>();
    private static ClientLevel activeLevel;
    private static net.minecraft.resources.ResourceKey<Level> activeDimension;
    private static int chunkCacheScansThisFrame;

    private SwingingLanternAssemblyRenderer() {
    }

    public static void reset() {
        SWING_STATES.clear();
        LANTERN_CHUNK_CACHE.clear();
        activeLevel = null;
        activeDimension = null;
        chunkCacheScansThisFrame = 0;
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RENDER_STAGE
                || !ClientConfig.ENABLE_WIND_LANTERN_SWAY.getAsBoolean()
                || !ResponsiveFoliageShaders.shouldUseCustomFoliageShaders()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }

        if (activeLevel != level || activeDimension != level.dimension()) {
            reset();
            activeLevel = level;
            activeDimension = level.dimension();
        }
        if (LANTERN_CHUNK_CACHE.size() > MAX_LANTERN_CHUNK_CACHE_SIZE) {
            LANTERN_CHUNK_CACHE.clear();
        }
        if (SWING_STATES.size() > MAX_SWING_STATES) {
            SWING_STATES.clear();
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        ChunkPos cameraChunk = new ChunkPos(camera.getBlockPosition());
        BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        chunkCacheScansThisFrame = 0;

        for (int chunkZ = cameraChunk.z - CHUNK_RENDER_RADIUS; chunkZ <= cameraChunk.z + CHUNK_RENDER_RADIUS; chunkZ++) {
            for (int chunkX = cameraChunk.x - CHUNK_RENDER_RADIUS; chunkX <= cameraChunk.x + CHUNK_RENDER_RADIUS; chunkX++) {
                ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk instanceof LevelChunk levelChunk) {
                    renderChunkLanterns(level, levelChunk, blockRenderer, bufferSource, event.getPoseStack(), cameraPos, event);
                }
            }
        }

        bufferSource.endBatch();
    }

    public static boolean ownsLanternAssembly(BlockState state, @javax.annotation.Nullable net.minecraft.world.level.BlockAndTintGetter level, @javax.annotation.Nullable BlockPos pos) {
        return ClientConfig.ENABLE_WIND_LANTERN_SWAY.getAsBoolean()
                && ResponsiveFoliageShaders.shouldUseCustomFoliageShaders()
                && level != null
                && pos != null
                && isHangingLantern(state)
                && chainHeightAbove(level, pos) > 0;
    }

    public static boolean ownsChainAssembly(LanternModelData.ChainSegment segment) {
        return ClientConfig.ENABLE_WIND_LANTERN_SWAY.getAsBoolean()
                && ResponsiveFoliageShaders.shouldUseCustomFoliageShaders()
                && segment.hangingLanternAttached();
    }

    public static void invalidateChunk(BlockPos pos) {
        LANTERN_CHUNK_CACHE.remove(ChunkPos.asLong(pos));
    }

    public static void invalidateChunk(LevelChunk chunk) {
        LANTERN_CHUNK_CACHE.remove(chunk.getPos().toLong());
    }

    private static void renderChunkLanterns(
            ClientLevel level,
            LevelChunk chunk,
            BlockRenderDispatcher blockRenderer,
            MultiBufferSource bufferSource,
            PoseStack poseStack,
            Vec3 cameraPos,
            RenderLevelStageEvent event
    ) {
        List<BlockPos> lanternPositions = hangingLanternsForChunk(chunk);
        for (BlockPos pos : lanternPositions) {
            BlockState state = level.getBlockState(pos);
            if (!isHangingLantern(state)) {
                continue;
            }

            int chainHeight = chainHeightAbove(level, pos);
            if (chainHeight <= 0 || chainHeight > MAX_CHAIN_HEIGHT || !isVisible(event, pos, chainHeight)) {
                continue;
            }

            renderAssembly(level, pos, state, chainHeight, blockRenderer, bufferSource, poseStack, cameraPos, event);
        }
    }

    private static List<BlockPos> hangingLanternsForChunk(LevelChunk chunk) {
        long key = chunk.getPos().toLong();
        LanternChunkCache cached = LANTERN_CHUNK_CACHE.get(key);
        if (cached != null && cached.chunk() == chunk) {
            return cached.positions();
        }

        if (chunkCacheScansThisFrame >= MAX_CHUNK_CACHE_SCANS_PER_FRAME) {
            return cached != null ? cached.positions() : List.of();
        }

        chunkCacheScansThisFrame++;
        List<BlockPos> positions = new ArrayList<>();
        chunk.findBlocks(
                SwingingLanternAssemblyRenderer::isHangingLantern,
                (pos, state) -> positions.add(pos.immutable())
        );
        LanternChunkCache updated = new LanternChunkCache(chunk, List.copyOf(positions));
        LANTERN_CHUNK_CACHE.put(key, updated);
        return updated.positions();
    }

    private static void renderAssembly(
            ClientLevel level,
            BlockPos lanternPos,
            BlockState lanternState,
            int chainHeight,
            BlockRenderDispatcher blockRenderer,
            MultiBufferSource bufferSource,
            PoseStack poseStack,
            Vec3 cameraPos,
            RenderLevelStageEvent event
    ) {
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float tickTime = level.getGameTime() + partialTick;
        float windTime = DynamicWindManager.simulationTime();
        WindSample wind = DynamicWindManager.sampleWind(level, lanternPos);
        Swing targetSwing = windSwingFor(lanternPos, chainHeight, windTime, wind);
        Swing swing = updateSwingState(level, lanternPos, chainHeight, targetSwing, tickTime);

        poseStack.pushPose();
        poseStack.translate(
                lanternPos.getX() - cameraPos.x + 0.5D,
                lanternPos.getY() - cameraPos.y + chainHeight + 1.0D,
                lanternPos.getZ() - cameraPos.z + 0.5D
        );

        float previousBend = 0.0F;
        for (int chainIndex = chainHeight - 1; chainIndex >= 0; chainIndex--) {
            float progress = (float) (chainHeight - chainIndex) / (float) chainHeight;
            float bend = chainBend(progress, chainHeight);
            applySwingPose(poseStack, swing, bend - previousBend);
            previousBend = bend;

            BlockPos chainPos = lanternPos.above(chainIndex + 1);
            BlockState chainState = level.getBlockState(chainPos);
            if (isVerticalChain(chainState)) {
                poseStack.pushPose();
                poseStack.translate(-0.5D, -1.0D, -0.5D);
                int packedLight = LevelRenderer.getLightColor(level, chainState, chainPos);
                blockRenderer.renderSingleBlock(chainState, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY, ASSEMBLY_MODEL_DATA, null);
                poseStack.popPose();
            }

            poseStack.translate(0.0D, -1.0D, 0.0D);
        }

        applySwingPose(poseStack, swing, 1.0F - previousBend);
        poseStack.mulPose(new Quaternionf().rotationY(swing.yawRadians()));
        poseStack.pushPose();
        poseStack.translate(-0.5D, -1.0D + 0.03125D, -0.5D);
        int packedLight = LevelRenderer.getLightColor(level, lanternState, lanternPos);
        blockRenderer.renderSingleBlock(lanternState, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY, ASSEMBLY_MODEL_DATA, null);
        poseStack.popPose();
        poseStack.popPose();
    }

    private static Swing windSwingFor(BlockPos lanternPos, int chainHeight, float windTime, WindSample wind) {
        float windPower = wind.strength();
        float strength = (float) ClientConfig.WIND_LANTERN_SWAY_STRENGTH.getAsDouble()
                * Mth.lerp(wind.exposure(), ENCLOSED_SWAY_SCALE, 1.0F);
        float longChainDrive = Mth.clamp(0.9F + Math.min(chainHeight, 8) * 0.045F, 0.9F, 1.25F);
        float speedScale = Mth.clamp(1.0F / Mth.sqrt(1.0F + Math.min(chainHeight, 12) * 0.18F), 0.45F, 0.92F);

        float windX = wind.directionX();
        float windZ = wind.directionZ();
        float crossX = -windZ;
        float crossZ = windX;
        float along = lanternPos.getX() * windX + lanternPos.getZ() * windZ;
        float across = lanternPos.getX() * crossX + lanternPos.getZ() * crossZ;
        float phase = randomPhase(lanternPos);
        float t = windTime * speedScale;

        float flutter = Mth.sin(t * 1.17F + across * 0.11F + phase * 1.71F);
        float windForce = 0.82F + flutter * Mth.clamp(wind.turbulence(), 0.0F, 1.0F) * 0.16F;
        float directionNoise = Mth.sin(across * 0.12F + t * 0.31F + phase)
                * (0.07F + wind.turbulence() * 0.12F);
        float baseX = windX + crossX * directionNoise;
        float baseZ = windZ + crossZ * directionNoise;
        float baseLength = Mth.sqrt(baseX * baseX + baseZ * baseZ);
        if (baseLength > 0.001F) {
            baseX /= baseLength;
            baseZ /= baseLength;
        }

        float crossDrift = Mth.sin(t * 0.73F + across * 0.09F + phase * 1.37F)
                * (0.05F + wind.turbulence() * 0.11F);
        float swingX = baseX + crossX * crossDrift;
        float swingZ = baseZ + crossZ * crossDrift;
        float angle = Mth.clamp(
                (0.22F + windPower * 2.55F) * strength * longChainDrive * windForce,
                0.0F,
                MAX_TARGET_SWAY_DEGREES
        );
        float yaw = Mth.sin(t * 0.49F + phase * 2.3F + across * 0.05F)
                * (0.10F + windPower * 0.32F + wind.turbulence() * 0.45F)
                * strength;
        return new Swing(swingX, swingZ, angle, yaw);
    }

    private static Swing updateSwingState(ClientLevel level, BlockPos lanternPos, int chainHeight, Swing targetSwing, float tickTime) {
        SwingState state = SWING_STATES.computeIfAbsent(lanternPos.asLong(), ignored -> new SwingState(tickTime));
        float deltaTicks = state.advanceTime(tickTime);
        float targetX = targetSwing.tiltX();
        float targetZ = targetSwing.tiltZ();
        float targetYaw = targetSwing.yawDegrees();
        float suppression = Mth.clamp(state.windSuppression, 0.0F, 1.0F);
        float windResponse = Mth.lerp(suppression, 1.0F, 0.18F);
        targetX *= windResponse;
        targetZ *= windResponse;
        targetYaw *= windResponse;

        float lengthScale = Mth.sqrt(1.0F + Math.min(chainHeight, 12) * 0.18F);
        float spring = 0.020F / lengthScale;
        float yawSpring = 0.012F / lengthScale;
        state.velocityX += (targetX - state.angleX) * spring * deltaTicks;
        state.velocityZ += (targetZ - state.angleZ) * spring * deltaTicks;
        state.yawVelocity += (targetYaw - state.yaw) * yawSpring * deltaTicks;

        float damping = (float) Math.pow(Mth.lerp(Math.min(chainHeight, 12) / 12.0F, 0.94F, 0.958F), deltaTicks);
        state.velocityX *= damping;
        state.velocityZ *= damping;
        state.yawVelocity *= (float) Math.pow(0.93F, deltaTicks);
        state.angleX += state.velocityX * deltaTicks;
        state.angleZ += state.velocityZ * deltaTicks;
        state.yaw += state.yawVelocity * deltaTicks;
        state.windSuppression *= (float) Math.pow(0.90F, deltaTicks);
        limitSwing(state);

        Swing swing = swingFromTilt(state.angleX, state.angleZ, state.yaw);
        long gameTime = level.getGameTime();
        boolean collisionCheckDue = state.lastCollisionCheckTick == Long.MIN_VALUE
                || gameTime - state.lastCollisionCheckTick >= COLLISION_CHECK_INTERVAL_TICKS;
        if (collisionCheckDue) {
            state.lastCollisionCheckTick = gameTime;
        }
        if (collisionCheckDue
                && swing.angleDegrees() > 0.001F
                && collidesWithEnvironment(level, lanternPos, chainHeight, swing, 1.0F)) {
            float scale = findMaxNonCollidingScale(level, lanternPos, chainHeight, swing) * 0.995F;
            state.angleX *= scale;
            state.angleZ *= scale;
            state.yaw *= scale;
            float length = Mth.sqrt(state.angleX * state.angleX + state.angleZ * state.angleZ);
            if (length > 0.001F) {
                float normalX = state.angleX / length;
                float normalZ = state.angleZ / length;
                float outwardVelocity = state.velocityX * normalX + state.velocityZ * normalZ;
                if (outwardVelocity > 0.0F) {
                    state.velocityX -= normalX * outwardVelocity * 1.05F;
                    state.velocityZ -= normalZ * outwardVelocity * 1.05F;
                }

                float pushBack = Mth.clamp((1.0F - scale) * 0.025F, 0.0F, 0.010F);
                state.velocityX -= normalX * pushBack;
                state.velocityZ -= normalZ * pushBack;
            }

            state.yawVelocity *= 0.35F;
            state.windSuppression = Math.max(state.windSuppression, 0.55F);
            swing = swingFromTilt(state.angleX, state.angleZ, state.yaw);
        }

        return swing;
    }

    private static void limitSwing(SwingState state) {
        float angle = Mth.sqrt(state.angleX * state.angleX + state.angleZ * state.angleZ);
        if (angle <= MAX_SIMULATED_SWAY_DEGREES) {
            return;
        }

        float normalX = state.angleX / angle;
        float normalZ = state.angleZ / angle;
        float scale = MAX_SIMULATED_SWAY_DEGREES / angle;
        state.angleX *= scale;
        state.angleZ *= scale;
        float outwardVelocity = state.velocityX * normalX + state.velocityZ * normalZ;
        if (outwardVelocity > 0.0F) {
            state.velocityX -= normalX * outwardVelocity;
            state.velocityZ -= normalZ * outwardVelocity;
        }
    }

    private static Swing swingFromTilt(float tiltX, float tiltZ, float yaw) {
        float angle = Mth.sqrt(tiltX * tiltX + tiltZ * tiltZ);
        return new Swing(tiltX, tiltZ, angle, yaw);
    }

    private static float findMaxNonCollidingScale(ClientLevel level, BlockPos lanternPos, int chainHeight, Swing swing) {
        float clear = 0.0F;
        float blocked = 1.0F;
        for (int step = 0; step < COLLISION_BINARY_SEARCH_STEPS; step++) {
            float scale = (clear + blocked) * 0.5F;
            if (collidesWithEnvironment(level, lanternPos, chainHeight, swing, scale)) {
                blocked = scale;
            } else {
                clear = scale;
            }
        }

        return clear;
    }

    private static boolean collidesWithEnvironment(ClientLevel level, BlockPos lanternPos, int chainHeight, Swing swing, float scale) {
        float length = Mth.sqrt(swing.x() * swing.x() + swing.z() * swing.z());
        if (length <= 0.001F || scale <= 0.001F) {
            return false;
        }

        double dirX = swing.x() / length;
        double dirZ = swing.z() / length;
        double angle = swing.angleDegrees() * scale * Mth.DEG_TO_RAD;
        double x = lanternPos.getX() + 0.5D;
        double y = lanternPos.getY() + chainHeight + 1.0D;
        double z = lanternPos.getZ() + 0.5D;
        double restX = x;
        double restY = y;
        double restZ = z;

        for (int chainIndex = chainHeight - 1; chainIndex >= 0; chainIndex--) {
            float progress = (float) (chainHeight - chainIndex) / (float) chainHeight;
            double segmentAngle = angle * chainBend(progress, chainHeight);
            x += dirX * Math.sin(segmentAngle);
            y -= Math.cos(segmentAngle);
            z += dirZ * Math.sin(segmentAngle);
            restY -= 1.0D;

            AABB chainBounds = AABB.ofSize(new Vec3(x, y, z), CHAIN_COLLISION_DIAMETER, CHAIN_COLLISION_DIAMETER, CHAIN_COLLISION_DIAMETER);
            AABB restingChainBounds = AABB.ofSize(new Vec3(restX, restY, restZ), CHAIN_COLLISION_DIAMETER, CHAIN_COLLISION_DIAMETER, CHAIN_COLLISION_DIAMETER);
            if (intersectsEnvironment(level, chainBounds, restingChainBounds, lanternPos, chainHeight)) {
                return true;
            }
        }

        AABB lanternBounds = AABB.ofSize(
                new Vec3(x, y - 0.5D + 0.03125D, z),
                LANTERN_COLLISION_WIDTH,
                LANTERN_COLLISION_HEIGHT,
                LANTERN_COLLISION_WIDTH
        );
        AABB restingLanternBounds = AABB.ofSize(
                new Vec3(restX, restY - 0.5D + 0.03125D, restZ),
                LANTERN_COLLISION_WIDTH,
                LANTERN_COLLISION_HEIGHT,
                LANTERN_COLLISION_WIDTH
        );
        return intersectsEnvironment(level, lanternBounds, restingLanternBounds, lanternPos, chainHeight);
    }

    private static boolean intersectsEnvironment(ClientLevel level, AABB bounds, AABB restingBounds, BlockPos lanternPos, int chainHeight) {
        int minX = Mth.floor(bounds.minX);
        int minY = Mth.floor(bounds.minY);
        int minZ = Mth.floor(bounds.minZ);
        int maxX = Mth.floor(bounds.maxX - COLLISION_GRID_EPSILON);
        int maxY = Mth.floor(bounds.maxY - COLLISION_GRID_EPSILON);
        int maxZ = Mth.floor(bounds.maxZ - COLLISION_GRID_EPSILON);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    if (isAssemblyBlock(lanternPos, chainHeight, pos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }

                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty()) {
                        continue;
                    }

                    for (AABB shapeBounds : shape.toAabbs()) {
                        AABB movedShapeBounds = shapeBounds.move(pos);
                        if (bounds.intersects(movedShapeBounds)
                                && !restingBounds.intersects(movedShapeBounds)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean isAssemblyBlock(BlockPos lanternPos, int chainHeight, BlockPos pos) {
        return pos.getX() == lanternPos.getX()
                && pos.getZ() == lanternPos.getZ()
                && pos.getY() >= lanternPos.getY()
                && pos.getY() <= lanternPos.getY() + chainHeight + 1;
    }

    private static void applySwingPose(PoseStack poseStack, Swing swing, float bend) {
        if (bend <= 0.0F || swing.angleDegrees() <= 0.001F) {
            return;
        }

        float length = Mth.sqrt(swing.x() * swing.x() + swing.z() * swing.z());
        if (length <= 0.001F) {
            return;
        }

        float axisX = -swing.z() / length;
        float axisZ = swing.x() / length;
        poseStack.mulPose(new Quaternionf().fromAxisAngleDeg(axisX, 0.0F, axisZ, swing.angleDegrees() * bend));
    }

    private static float chainBend(float progress, int chainHeight) {
        float longStack = Mth.clamp((chainHeight - 3.0F) / 8.0F, 0.0F, 1.0F);
        float exponent = Mth.lerp(longStack, 1.45F, 1.05F);
        return (float) Math.pow(ToucanEasing.smoothstep(progress), exponent);
    }

    private static float randomPhase(BlockPos pos) {
        int hash = Mth.murmurHash3Mixer(pos.getX() * 73428767 ^ pos.getY() * 9122719 ^ pos.getZ() * 42317861);
        return (hash & 0xFFFF) / 65535.0F * Mth.TWO_PI;
    }

    private static boolean isVisible(RenderLevelStageEvent event, BlockPos lanternPos, int chainHeight) {
        AABB bounds = new AABB(lanternPos).expandTowards(0.0D, chainHeight + 1.0D, 0.0D).inflate(1.0D);
        return event.getFrustum().isVisible(bounds);
    }

    private static int chainHeightAbove(net.minecraft.world.level.BlockAndTintGetter level, BlockPos lanternPos) {
        int height = 0;
        while (height < MAX_CHAIN_HEIGHT && isVerticalChain(level.getBlockState(lanternPos.above(height + 1)))) {
            height++;
        }

        return height;
    }

    private static boolean isHangingLantern(BlockState state) {
        return (state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN))
                && state.hasProperty(LanternBlock.HANGING)
                && state.getValue(LanternBlock.HANGING);
    }

    private static boolean isVerticalChain(BlockState state) {
        return state.is(Blocks.CHAIN)
                && state.hasProperty(ChainBlock.AXIS)
                && state.getValue(ChainBlock.AXIS) == Direction.Axis.Y;
    }

    private record Swing(float x, float z, float angleDegrees, float yawDegrees) {
        float tiltX() {
            float length = Mth.sqrt(x * x + z * z);
            return length > 0.001F ? x / length * angleDegrees : 0.0F;
        }

        float tiltZ() {
            float length = Mth.sqrt(x * x + z * z);
            return length > 0.001F ? z / length * angleDegrees : 0.0F;
        }

        float yawRadians() {
            return yawDegrees * Mth.DEG_TO_RAD;
        }
    }

    private static final class SwingState {
        private float angleX;
        private float angleZ;
        private float yaw;
        private float velocityX;
        private float velocityZ;
        private float yawVelocity;
        private float windSuppression;
        private float lastTickTime;
        private long lastCollisionCheckTick = Long.MIN_VALUE;

        private SwingState(float tickTime) {
            lastTickTime = tickTime;
        }

        private float advanceTime(float tickTime) {
            float deltaTicks = tickTime - lastTickTime;
            lastTickTime = tickTime;
            if (deltaTicks < 0.0F) {
                return 0.0F;
            }

            return Mth.clamp(deltaTicks, 0.0F, 2.0F);
        }
    }

    private record LanternChunkCache(LevelChunk chunk, List<BlockPos> positions) {
    }
}
