package com.jvn.wherewindsblow.client.wind;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliage;
import com.jvn.wherewindsblow.config.ClientConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Shared direction-aware shelter cache. Samples use two-block cells and expire quickly so model
 * rebuilds, block changes, and gradual wind turns cannot leave stale shelter values behind.
 */
public final class WindExposureCache {
    private static final int CELL_SIZE = 2;
    private static final int DIRECTION_BUCKETS = 16;
    private static final int MAX_ENTRIES = 4096;
    private static final long ENTRY_TTL_MILLIS = 3000L;
    private static final Map<Long, Entry> CACHE = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Entry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private WindExposureCache() {
    }

    public static float exposureAt(BlockAndTintGetter level, BlockPos pos, float windX, float windZ) {
        if (!ClientConfig.ENABLE_LOCAL_WIND_EXPOSURE.getAsBoolean()) {
            return 1.0F;
        }

        int radius = Mth.clamp((int) Math.round(ClientConfig.WIND_EXPOSURE_RADIUS.getAsDouble()), 2, 8);
        int directionBucket = directionBucket(windX, windZ);
        int cellX = Math.floorDiv(pos.getX(), CELL_SIZE);
        int cellY = Math.floorDiv(pos.getY(), CELL_SIZE);
        int cellZ = Math.floorDiv(pos.getZ(), CELL_SIZE);
        long key = cacheKey(cellX, cellY, cellZ, directionBucket, radius);
        long now = Util.getMillis();
        synchronized (CACHE) {
            Entry cached = CACHE.get(key);
            if (cached != null && now - cached.createdAtMillis() <= ENTRY_TTL_MILLIS) {
                return cached.exposure();
            }
        }

        BlockPos sampleOrigin = new BlockPos(
                cellX * CELL_SIZE + CELL_SIZE / 2,
                cellY * CELL_SIZE + CELL_SIZE / 2,
                cellZ * CELL_SIZE + CELL_SIZE / 2
        );
        float exposure = calculateExposure(level, sampleOrigin, windX, windZ, radius);
        synchronized (CACHE) {
            CACHE.put(key, new Entry(exposure, now));
        }
        return exposure;
    }

    public static float altitudeMultiplier(BlockAndTintGetter level, double y) {
        float influence = (float) ClientConfig.ALTITUDE_WIND_INFLUENCE.getAsDouble();
        if (influence <= 0.0F) {
            return 1.0F;
        }

        float heightRange = Math.max(1.0F, level.getHeight());
        float normalizedHeight = Mth.clamp((float) ((y - level.getMinBuildHeight()) / heightRange), 0.0F, 1.0F);
        float exposedHeight = smooth(Mth.clamp((normalizedHeight - 0.30F) / 0.70F, 0.0F, 1.0F));
        return 1.0F + exposedHeight * influence * 0.25F;
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    public static int size() {
        synchronized (CACHE) {
            return CACHE.size();
        }
    }

    private static float calculateExposure(
            BlockAndTintGetter level,
            BlockPos origin,
            float windX,
            float windZ,
            int radius
    ) {
        float length = Mth.sqrt(windX * windX + windZ * windZ);
        float directionX = length > 0.001F ? windX / length : 0.0F;
        float directionZ = length > 0.001F ? windZ / length : -1.0F;
        float upwindX = -directionX;
        float upwindZ = -directionZ;
        float crossX = -directionZ;
        float crossZ = directionX;

        float center = traceOpening(level, origin, upwindX, upwindZ, 0.0F, radius);
        float left = traceOpening(level, origin, upwindX, upwindZ, -0.62F, radius);
        float right = traceOpening(level, origin, upwindX, upwindZ, 0.62F, radius);
        float sideLeft = traceOpening(level, origin, crossX, crossZ, 0.0F, Math.max(2, radius / 2));
        float sideRight = traceOpening(level, origin, -crossX, -crossZ, 0.0F, Math.max(2, radius / 2));
        float directionalOpening = center * 0.54F + left * 0.18F + right * 0.18F
                + Math.max(sideLeft, sideRight) * 0.10F;

        float localSky = skyAccess(level, origin);
        BlockPos.MutableBlockPos upwindSample = new BlockPos.MutableBlockPos();
        upwindSample.set(
                origin.getX() + Math.round(upwindX * radius),
                origin.getY(),
                origin.getZ() + Math.round(upwindZ * radius)
        );
        float upwindSky = skyAccess(level, upwindSample);
        float roofAccess = localSky * 0.72F + upwindSky * directionalOpening * 0.28F;
        float exposure = directionalOpening * 0.58F + roofAccess * 0.42F;
        return Mth.clamp(smooth(exposure), 0.0F, 1.0F);
    }

    private static float traceOpening(
            BlockAndTintGetter level,
            BlockPos origin,
            float directionX,
            float directionZ,
            float crossOffset,
            int radius
    ) {
        float crossX = -directionZ;
        float crossZ = directionX;
        float transmission = 1.0F;
        float openness = 0.0F;
        BlockPos.MutableBlockPos sample = new BlockPos.MutableBlockPos();
        for (int distance = 1; distance <= radius; distance++) {
            float spread = crossOffset * Math.min(distance, 3);
            sample.set(
                    origin.getX() + Math.round(directionX * distance + crossX * spread),
                    origin.getY(),
                    origin.getZ() + Math.round(directionZ * distance + crossZ * spread)
            );
            transmission *= windTransmission(level, sample);
            openness += transmission;
            if (transmission < 0.025F) {
                break;
            }
        }
        return Mth.clamp(openness / radius, 0.0F, 1.0F);
    }

    private static float windTransmission(BlockAndTintGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty()) {
            return 1.0F;
        }
        if (ResponsiveFoliage.isLeaf(state)) {
            return 0.62F;
        }
        if (ResponsiveFoliage.isInteractive(state)) {
            return 0.82F;
        }
        return state.canOcclude() ? 0.02F : 0.24F;
    }

    private static float skyAccess(BlockAndTintGetter level, BlockPos pos) {
        return Mth.clamp(level.getBrightness(LightLayer.SKY, pos) / 15.0F, 0.0F, 1.0F);
    }

    private static int directionBucket(float windX, float windZ) {
        float angle = (float) Math.atan2(windZ, windX);
        return Math.floorMod(Math.round(angle * DIRECTION_BUCKETS / Mth.TWO_PI), DIRECTION_BUCKETS);
    }

    private static long cacheKey(int x, int y, int z, int directionBucket, int radius) {
        long value = BlockPos.asLong(x, y, z);
        value ^= (long) directionBucket * 0x9e3779b97f4a7c15L;
        value ^= (long) radius * 0xc2b2ae3d27d4eb4fL;
        value ^= value >>> 29;
        return value;
    }

    private static float smooth(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);
        return value * value * (3.0F - 2.0F * value);
    }

    private record Entry(float exposure, long createdAtMillis) {
    }
}
