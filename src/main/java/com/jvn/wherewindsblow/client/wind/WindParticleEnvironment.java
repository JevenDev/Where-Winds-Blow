package com.jvn.wherewindsblow.client.wind;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliage;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
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
    static final Random RANDOM = new Random();

    private WindParticleEnvironment() {
    }

    static double distanceTo(Vec3 cameraPos, Point point) {
        double dx = point.x() - cameraPos.x();
        double dy = point.y() - cameraPos.y();
        double dz = point.z() - cameraPos.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static TerrainFlow terrainFlow(
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

    static Point keepAboveTerrainAndCollision(ClientLevel level, double x, double y, double z, double clearance) {
        double safeY = Math.max(y, terrainHeight(level, x, z) + clearance);
        BlockPos pos = BlockPos.containing(x, safeY, z);
        if (pointInsideCollision(level, x, safeY, z)) {
            safeY = Math.max(safeY, collisionTop(level, pos) + clearance);
        }

        return new Point(x, safeY, z);
    }

    static boolean windPathBlocked(ClientLevel level, double x, double y, double z, Point destination) {
        Vec3 start = new Vec3(x, y, z);
        Vec3 end = new Vec3(destination.x(), destination.y(), destination.z());
        return !isValidLeafSpace(level, destination.x(), destination.y(), destination.z(), LEAF_TERRAIN_CLEARANCE)
                || lineHitsCollision(level, start, end);
    }

    static boolean isOpenSkyAir(ClientLevel level, double x, double y, double z) {
        return isValidWindSpace(level, x, y, z, 0.0D);
    }

    static boolean isValidWindSpace(ClientLevel level, double x, double y, double z, double terrainClearance) {
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

    static boolean isValidLeafSpace(ClientLevel level, double x, double y, double z, double terrainClearance) {
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

    static boolean isLeafBlockWindExposed(ClientLevel level, BlockPos pos) {
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

    static boolean lineHitsCollision(ClientLevel level, Point start, Point end) {
        return lineHitsCollision(level, new Vec3(start.x(), start.y(), start.z()), new Vec3(end.x(), end.y(), end.z()));
    }

    static boolean lineHitsCollision(ClientLevel level, Vec3 start, Vec3 end) {
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

    static double terrainHeight(ClientLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
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
