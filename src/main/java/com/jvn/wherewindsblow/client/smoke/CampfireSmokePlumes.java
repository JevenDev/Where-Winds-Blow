package com.jvn.wherewindsblow.client.smoke;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class CampfireSmokePlumes {
    private static final int MAX_CLUSTER_SIZE = 64;
    private static final int BEEHIVE_SMOKE_DISTANCE = 5;
    private static final int PARTICLE_SOURCE_SEARCH_RADIUS = 4;
    private static final int PARTICLE_SOURCE_SEARCH_DEPTH = 5;
    private static final ThreadLocal<SpawnContext> ACTIVE_SPAWN_CONTEXT = new ThreadLocal<>();
    private static final Map<BlockPos, SmokeCluster> CLUSTER_CACHE = new HashMap<>();
    private static WeakReference<Level> clusterCacheLevel = new WeakReference<>(null);
    private static long clusterCacheGameTime = Long.MIN_VALUE;

    private CampfireSmokePlumes() {
    }

    public static boolean canReplaceSmoke(Level level, BlockPos pos, BlockState state) {
        return level.isClientSide
                && CampfireBlock.isLitCampfire(state)
                && isOpenSmokeSource(level, pos);
    }

    public static SmokeCluster findCluster(Level level, BlockPos pos) {
        if (!isOpenLitCampfire(level, pos)) {
            return null;
        }

        refreshClusterCache(level);
        SmokeCluster cachedCluster = CLUSTER_CACHE.get(pos);
        if (cachedCluster != null) {
            return cachedCluster;
        }

        List<BlockPos> sources = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = pos.immutable();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty() && sources.size() < MAX_CLUSTER_SIZE) {
            BlockPos current = queue.removeFirst();
            sources.add(current);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = current.relative(direction).immutable();
                if (!visited.contains(neighbor) && isOpenLitCampfire(level, neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        SmokeCluster cluster = createCluster(level, sources);
        if (cluster != null) {
            for (BlockPos source : sources) {
                CLUSTER_CACHE.put(source, cluster);
            }
        }
        return cluster;
    }

    public static SpawnContext findSpawnContextNear(Level level, double x, double y, double z) {
        BlockPos origin = BlockPos.containing(x, y, z);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dy = 0; dy <= PARTICLE_SOURCE_SEARCH_DEPTH; dy++) {
            int sourceY = origin.getY() - dy;
            for (int dx = -PARTICLE_SOURCE_SEARCH_RADIUS; dx <= PARTICLE_SOURCE_SEARCH_RADIUS; dx++) {
                for (int dz = -PARTICLE_SOURCE_SEARCH_RADIUS; dz <= PARTICLE_SOURCE_SEARCH_RADIUS; dz++) {
                    BlockPos candidate = new BlockPos(origin.getX() + dx, sourceY, origin.getZ() + dz);
                    if (!isOpenLitCampfire(level, candidate)) {
                        continue;
                    }

                    double xDistance = x - ((double) candidate.getX() + 0.5D);
                    double yDistance = y - ((double) candidate.getY() + 0.8D);
                    double zDistance = z - ((double) candidate.getZ() + 0.5D);
                    double distance = xDistance * xDistance + zDistance * zDistance + yDistance * yDistance * 0.35D;
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }

        SmokeCluster cluster = best == null ? null : findCluster(level, best);
        return cluster == null ? null : cluster.spawnContext();
    }

    public static SpawnContext activeSpawnContext() {
        return ACTIVE_SPAWN_CONTEXT.get();
    }

    public static void spawnMergedSmoke(Level level, SmokeCluster cluster) {
        RandomSource random = level.getRandom();
        SimpleParticleType smokeType = cluster.signalFire()
                ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE
                : ParticleTypes.CAMPFIRE_COSY_SMOKE;
        int attempts = cluster.spawnAttempts();
        double spread = cluster.spawnSpread();
        double baseY = cluster.baseY() + 0.82D;

        for (int i = 0; i < attempts; i++) {
            if (random.nextInt(10) != 0) {
                continue;
            }

            double x = cluster.centerX() + (random.nextDouble() - random.nextDouble()) * spread * 0.5D;
            double y = baseY + random.nextDouble() * (cluster.clustered() ? 0.16D : 0.22D);
            double z = cluster.centerZ() + (random.nextDouble() - random.nextDouble()) * spread * 0.5D;
            double horizontalJitter = cluster.clustered() ? 0.018D : 0.012D;
            double xSpeed = (random.nextDouble() - 0.5D) * horizontalJitter;
            double ySpeed = cluster.riseSpeed() * (0.9D + random.nextDouble() * 0.22D);
            double zSpeed = (random.nextDouble() - 0.5D) * horizontalJitter;

            ACTIVE_SPAWN_CONTEXT.set(cluster.spawnContext());
            try {
                level.addAlwaysVisibleParticle(smokeType, true, x, y, z, xSpeed, ySpeed, zSpeed);
            } finally {
                ACTIVE_SPAWN_CONTEXT.remove();
            }
        }
    }

    public static void spawnCookingSmoke(Level level, SmokeCluster cluster) {
        for (BlockPos source : cluster.sources()) {
            BlockEntity blockEntity = level.getBlockEntity(source);
            if (blockEntity instanceof CampfireBlockEntity campfireBlockEntity) {
                spawnCookingSmoke(level, source, level.getBlockState(source), campfireBlockEntity);
            }
        }
    }

    private static void spawnCookingSmoke(Level level, BlockPos pos, BlockState state, CampfireBlockEntity blockEntity) {
        if (!state.hasProperty(CampfireBlock.FACING)) {
            return;
        }

        RandomSource random = level.getRandom();
        int facing = state.getValue(CampfireBlock.FACING).get2DDataValue();

        for (int slot = 0; slot < blockEntity.getItems().size(); slot++) {
            ItemStack stack = blockEntity.getItems().get(slot);
            if (stack.isEmpty() || random.nextFloat() >= 0.2F) {
                continue;
            }

            Direction direction = Direction.from2DDataValue(Math.floorMod(slot + facing, 4));
            double x = (double) pos.getX()
                    + 0.5D
                    - (double) direction.getStepX() * 0.3125D
                    + (double) direction.getClockWise().getStepX() * 0.3125D;
            double y = (double) pos.getY() + 0.5D;
            double z = (double) pos.getZ()
                    + 0.5D
                    - (double) direction.getStepZ() * 0.3125D
                    + (double) direction.getClockWise().getStepZ() * 0.3125D;

            for (int i = 0; i < 4; i++) {
                level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0D, 5.0E-4D, 0.0D);
            }
        }
    }

    private static SmokeCluster createCluster(Level level, List<BlockPos> sources) {
        if (sources.isEmpty()) {
            return null;
        }

        BlockPos leader = sources.stream()
                .min(Comparator.comparingInt((BlockPos source) -> source.getX())
                        .thenComparingInt(source -> source.getZ())
                        .thenComparingInt(source -> source.getY()))
                .orElse(sources.getFirst());
        double centerX = 0.0D;
        double centerY = 0.0D;
        double centerZ = 0.0D;
        boolean signalFire = false;
        boolean soulFire = false;

        for (BlockPos source : sources) {
            BlockState state = level.getBlockState(source);
            centerX += (double) source.getX() + 0.5D;
            centerY += (double) source.getY();
            centerZ += (double) source.getZ() + 0.5D;
            signalFire |= isSignalFire(level, source, state);
            soulFire |= state.is(Blocks.SOUL_CAMPFIRE);
        }

        int size = sources.size();
        return new SmokeCluster(
                List.copyOf(sources),
                leader,
                size,
                centerX / (double) size,
                centerY / (double) size,
                centerZ / (double) size,
                signalFire,
                soulFire
        );
    }

    private static void refreshClusterCache(Level level) {
        long gameTime = level.getGameTime();
        if (clusterCacheLevel.get() == level && clusterCacheGameTime == gameTime) {
            return;
        }

        CLUSTER_CACHE.clear();
        clusterCacheLevel = new WeakReference<>(level);
        clusterCacheGameTime = gameTime;
    }

    private static boolean isOpenLitCampfire(Level level, BlockPos pos) {
        return CampfireBlock.isLitCampfire(level.getBlockState(pos)) && isOpenSmokeSource(level, pos);
    }

    private static boolean isOpenSmokeSource(Level level, BlockPos pos) {
        return !hasBeehiveAbove(level, pos) && level.isEmptyBlock(pos.above());
    }

    private static boolean hasBeehiveAbove(Level level, BlockPos pos) {
        for (int dy = 1; dy <= BEEHIVE_SMOKE_DISTANCE; dy++) {
            BlockState above = level.getBlockState(pos.above(dy));
            if (above.is(Blocks.BEEHIVE) || above.is(Blocks.BEE_NEST)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isSignalFire(Level level, BlockPos pos, BlockState state) {
        return state.hasProperty(CampfireBlock.SIGNAL_FIRE) && state.getValue(CampfireBlock.SIGNAL_FIRE)
                || level.getBlockState(pos.below()).is(Blocks.HAY_BLOCK);
    }

    public record SmokeCluster(
            List<BlockPos> sources,
            BlockPos leader,
            int size,
            double centerX,
            double baseY,
            double centerZ,
            boolean signalFire,
            boolean soulFire
    ) {
        public boolean clustered() {
            return this.size > 1;
        }

        public SpawnContext spawnContext() {
            return new SpawnContext(
                    this.size,
                    this.signalFire,
                    this.soulFire,
                    this.baseY,
                    this.centerX,
                    this.centerZ,
                    this.lifetimeMultiplier(),
                    this.visualScaleMultiplier(),
                    this.turbulenceMultiplier()
            );
        }

        private int spawnAttempts() {
            if (!this.clustered()) {
                return this.signalFire ? 5 : 3;
            }

            int perCampfire = this.signalFire ? 12 : 8;
            return Math.min(perCampfire * this.size + Math.min(this.size * 2, 12), 96);
        }

        private double spawnSpread() {
            if (!this.clustered()) {
                return this.signalFire ? 0.68D : 0.58D;
            }

            double extra = (double) this.size - 1.0D;
            double spread = (this.signalFire ? 0.75D : 0.65D) + Math.sqrt(extra) * 0.55D + extra * 0.06D;
            return Mth.clamp(spread, 0.65D, this.signalFire ? 4.6D : 3.8D);
        }

        private double riseSpeed() {
            double base = this.signalFire ? 0.105D : 0.075D;
            double clusterBoost = this.clustered() ? Math.min((double) (this.size - 1) * 0.0035D, 0.035D) : 0.0D;
            return base + clusterBoost;
        }

        private float lifetimeMultiplier() {
            if (!this.clustered()) {
                return this.signalFire ? 1.05F : 1.0F;
            }

            float extra = (float) this.size - 1.0F;
            return this.signalFire
                    ? Math.min(1.22F + extra * 0.035F, 1.85F)
                    : Math.min(1.48F + extra * 0.055F, 2.35F);
        }

        private float visualScaleMultiplier() {
            if (!this.clustered()) {
                return this.signalFire ? 3.6F : 2.35F;
            }

            float extra = (float) this.size - 1.0F;
            float scale = (this.signalFire ? 7.5F : 6.0F) + (float) Math.sqrt(extra) * 1.15F + extra * 0.18F;
            return Math.min(scale, this.signalFire ? 14.0F : 11.0F);
        }

        private float turbulenceMultiplier() {
            if (!this.clustered()) {
                return 1.0F;
            }

            return Math.min(1.25F + ((float) this.size - 1.0F) * 0.08F, 2.4F);
        }
    }

    public record SpawnContext(
            int clusterSize,
            boolean signalFire,
            boolean soulFire,
            double sourceY,
            double centerX,
            double centerZ,
            float lifetimeMultiplier,
            float visualScaleMultiplier,
            float turbulenceMultiplier
    ) {
        public boolean clustered() {
            return this.clusterSize > 1;
        }

        public int adjacentCampfires() {
            return Math.max(0, this.clusterSize - 1);
        }
    }
}
