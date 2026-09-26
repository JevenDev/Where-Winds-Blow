package com.jvn.wherewindsblow.client.wind;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliage;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

final class WindParticleEnvironment {
    static final double MAX_DISTANCE_FROM_PLAYER = 56.0D;
    private static final double TERRAIN_LOOKAHEAD = 6.5D;
    private static final double TERRAIN_SIDE_SAMPLE = 3.6D;
    private static final double TERRAIN_FLOW_SMOOTHING = 0.56D;
    private static final double TERRAIN_SIDE_PUSH = 0.026D;
    static final double LEAF_TERRAIN_CLEARANCE = 0.78D;
    private static final int LEAF_SIDE_ACCESS_RADIUS = 5;
    private static final double MAX_TERRAIN_LIFT = 0.58D;
    private static final double MAX_TERRAIN_SIDE_FLOW = 0.18D;
    static final int SPAWN_ATTEMPTS = 12;
    static final int FADE_OUT_TICKS = 18;
    static final int WIND_SAMPLE_INTERVAL_TICKS = 2;
    private static final int MIN_OPEN_SKY_LIGHT = 14;
    private static final double SURFACE_TOLERANCE = 0.08D;
    private static final double LINE_CLEARANCE = 0.08D;
    private static final int MAX_CACHED_COLUMNS = 2048;
    private static final Long2IntOpenHashMap OUTDOOR_HEIGHTS = new Long2IntOpenHashMap();
    private static final Long2IntOpenHashMap TERRAIN_HEIGHTS = new Long2IntOpenHashMap();
    private static LevelReader sampledLevel;
    static final Random RANDOM = new Random();

    private WindParticleEnvironment() {
    }

    static void beginTick(LevelReader level) {
        clear();
        sampledLevel = level;
    }

    static void clear() {
        sampledLevel = null;
        OUTDOOR_HEIGHTS.clear();
        TERRAIN_HEIGHTS.clear();
    }

    static TerrainFlow terrainFlow(
            LevelReader level,
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
        if (!Double.isFinite(currentHeight)) {
            return new TerrainFlow(0.0D, 0.0D);
        }
        double nearHeight = loadedTerrainHeight(level, nearX, nearZ, currentHeight);
        double forwardHeight = loadedTerrainHeight(level, forwardX, forwardZ, currentHeight);
        double targetY = Math.max(Math.max(currentHeight, nearHeight), forwardHeight) + clearance;
        double heightError = targetY - y;
        double desiredLift = heightError > 0.0D
                ? Mth.clamp(heightError * liftStrength, 0.0D, MAX_TERRAIN_LIFT)
                : 0.0D;

        double leftHeight = loadedTerrainHeight(level, forwardX - crossX * TERRAIN_SIDE_SAMPLE, forwardZ - crossZ * TERRAIN_SIDE_SAMPLE, currentHeight);
        double rightHeight = loadedTerrainHeight(level, forwardX + crossX * TERRAIN_SIDE_SAMPLE, forwardZ + crossZ * TERRAIN_SIDE_SAMPLE, currentHeight);
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

    static Point keepAboveTerrainAndCollision(LevelReader level, double x, double y, double z, double clearance) {
        double safeY = Math.max(y, terrainHeight(level, x, z) + clearance);
        if (!Double.isFinite(safeY)) {
            return new Point(x, y, z);
        }
        BlockPos pos = BlockPos.containing(x, safeY, z);
        if (pointInsideCollision(level, x, safeY, z)) {
            safeY = Math.max(safeY, collisionTop(level, pos) + clearance);
        }

        return new Point(x, safeY, z);
    }

    static boolean windPathBlocked(LevelReader level, double x, double y, double z, Point destination) {
        Vec3 start = new Vec3(x, y, z);
        Vec3 end = new Vec3(destination.x(), destination.y(), destination.z());
        return !isValidLeafSpace(level, x, y, z, 0.0D)
                || !isValidLeafSpace(level, destination.x(), destination.y(), destination.z(), LEAF_TERRAIN_CLEARANCE)
                || !level.hasChunksAt(Mth.floor(Math.min(x, destination.x())), Mth.floor(Math.min(z, destination.z())),
                        Mth.floor(Math.max(x, destination.x())), Mth.floor(Math.max(z, destination.z())))
                || lineHitsCollision(level, start, end);
    }

    static boolean isValidWindSpace(LevelReader level, double x, double y, double z, double terrainClearance) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (level.isOutsideBuildHeight(pos)
                || !level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))
                || !level.getFluidState(pos).isEmpty()
                || level.getBrightness(LightLayer.SKY, pos) < MIN_OPEN_SKY_LIGHT
                || !hasSkyAccess(level, x, y, z)
                || pointInsideCollision(level, x, y, z)) {
            return false;
        }

        return y + SURFACE_TOLERANCE >= terrainHeight(level, x, z) + terrainClearance;
    }

    static boolean isValidLeafSpace(LevelReader level, double x, double y, double z, double terrainClearance) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (level.isOutsideBuildHeight(pos)
                || !level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))
                || !level.getFluidState(pos).isEmpty()
                || level.getBrightness(LightLayer.SKY, pos) < MIN_OPEN_SKY_LIGHT
                || !hasWindAccess(level, pos)
                || pointInsideCollision(level, x, y, z)) {
            return false;
        }

        return y + SURFACE_TOLERANCE >= terrainHeight(level, x, z) + terrainClearance;
    }

    static boolean isLeafBlockWindExposed(LevelReader level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos)
                && level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))
                && level.getBrightness(LightLayer.SKY, pos) >= MIN_OPEN_SKY_LIGHT && hasWindAccess(level, pos);
    }

    private static boolean hasWindAccess(LevelReader level, BlockPos pos) {
        if (hasSkyAccess(level, pos.getX(), pos.getY(), pos.getZ())) {
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

                if (level.getBrightness(LightLayer.SKY, samplePos) >= MIN_OPEN_SKY_LIGHT
                        && hasSkyAccess(level, samplePos.getX(), samplePos.getY(), samplePos.getZ())) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean canWindPassThrough(LevelReader level, BlockPos pos) {
        if (!level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))
                || !level.getFluidState(pos).isEmpty()) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return ResponsiveFoliage.isLeaf(state)
                || state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty();
    }

    static boolean lineHitsCollision(LevelReader level, Vec3 start, Vec3 end) {
        return clipCollision(level, start, end).getType() == HitResult.Type.BLOCK;
    }

    private static BlockHitResult clipCollision(LevelReader level, Vec3 start, Vec3 end) {
        return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
    }

    private static boolean pointInsideCollision(LevelReader level, double x, double y, double z) {
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

    private static double collisionTop(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());
        return shape.isEmpty() ? pos.getY() : pos.getY() + shape.max(Direction.Axis.Y);
    }

    private static boolean hasSkyAccess(LevelReader level, double x, double y, double z) {
        // skylight alone also passes beneath glass and other transparent roofs
        return y >= outdoorHeight(level, Mth.floor(x), Mth.floor(z));
    }

    static boolean isWindLineSpace(LevelReader level, AABB bounds, double terrainClearance) {
        double minY = bounds.minY - LINE_CLEARANCE;
        if (minY < level.getMinBuildHeight() || bounds.maxY + LINE_CLEARANCE >= level.getMaxBuildHeight()) {
            return false;
        }
        for (int x = Mth.floor(bounds.minX - LINE_CLEARANCE); x <= Mth.floor(bounds.maxX + LINE_CLEARANCE); x++) {
            for (int z = Mth.floor(bounds.minZ - LINE_CLEARANCE); z <= Mth.floor(bounds.maxZ + LINE_CLEARANCE); z++) {
                if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))
                        || minY < terrainHeight(level, x, z) + terrainClearance
                        || minY < outdoorHeight(level, x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    static double terrainHeight(LevelReader level, double x, double z) {
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        if (!level.hasChunk(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ))) {
            return Double.POSITIVE_INFINITY;
        }
        long key = BlockPos.asLong(blockX, 0, blockZ);
        if (level == sampledLevel && TERRAIN_HEIGHTS.containsKey(key)) {
            return TERRAIN_HEIGHTS.get(key);
        }
        // the no-leaves heightmap is not sent to clients, so derive it from the synchronized surface
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (height > level.getMinBuildHeight()) {
            pos.set(blockX, height - 1, blockZ);
            if (Heightmap.Types.MOTION_BLOCKING_NO_LEAVES.isOpaque().test(level.getBlockState(pos))) {
                break;
            }
            height--;
        }
        if (level == sampledLevel && TERRAIN_HEIGHTS.size() < MAX_CACHED_COLUMNS) {
            TERRAIN_HEIGHTS.put(key, height);
        }
        return height;
    }

    private static int outdoorHeight(LevelReader level, int x, int z) {
        long key = BlockPos.asLong(x, 0, z);
        if (level == sampledLevel && OUTDOOR_HEIGHTS.containsKey(key)) {
            return OUTDOOR_HEIGHTS.get(key);
        }
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = surface - 1; y >= height; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()
                    || !state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty()) {
                height = y + 1;
                break;
            }
        }
        if (level == sampledLevel && OUTDOOR_HEIGHTS.size() < MAX_CACHED_COLUMNS) {
            OUTDOOR_HEIGHTS.put(key, height);
        }
        return height;
    }

    private static double loadedTerrainHeight(LevelReader level, double x, double z, double fallback) {
        double height = terrainHeight(level, x, z);
        return Double.isFinite(height) ? height : fallback;
    }

    static int alphaByte(float alpha) {
        return Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
    }

    static float smoothFade(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    static double randomBetween(double min, double max) {
        return min + RANDOM.nextDouble() * (max - min);
    }

    static double triangularRandom() {
        return RANDOM.nextDouble() - RANDOM.nextDouble();
    }

    static double lerp(float amount, double start, double end) {
        return start + (end - start) * amount;
    }

    static WindSample sampleWind(ClientLevel level, double x, double y, double z) {
        return DynamicWindManager.sampleWind(level, BlockPos.containing(x, y, z));
    }

    static boolean acceptSpawnForWind(WindSample wind) {
        if (wind.strength() <= 0.015F) {
            return false;
        }

        double chance = Mth.clamp(0.12D + wind.strength() * 1.4D + wind.gustStrength() * 1.2D, 0.12D, 1.0D);
        return RANDOM.nextDouble() < chance;
    }

    static double windBoost(WindSample wind) {
        return Mth.clamp(wind.strength() / 2.0F, 0.0F, 1.0F);
    }

    record Point(double x, double y, double z) {
    }

    record TerrainFlow(double lift, double side) {
    }
}
