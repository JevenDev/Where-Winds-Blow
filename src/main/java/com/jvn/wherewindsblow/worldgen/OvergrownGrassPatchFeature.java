package com.jvn.wherewindsblow.worldgen;

import com.jvn.wherewindsblow.block.ModBlocks;
import com.jvn.wherewindsblow.block.OvergrownGrassBlock;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class OvergrownGrassPatchFeature extends Feature<NoneFeatureConfiguration> {
    private static final double OVERGROWN_LIMIT = 0.58D;
    private static final double TALL_GRASS_LIMIT = 0.90D;
    private static final double SHORT_GRASS_LIMIT = 1.08D;
    private static final int SURFACE_SCAN_UP = 3;
    private static final int SURFACE_SCAN_DOWN = 5;

    public OvergrownGrassPatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        OvergrownGrassBlock block = ModBlocks.OVERGROWN_GRASS.get();
        GrassPatchShape shape = GrassPatchShape.create(random, 3.0D, 4.5D, 2.25D, 3.5D);
        Set<Long> overgrownColumns = new HashSet<>();
        Set<Long> tallColumns = new HashSet<>();
        boolean placedAny = false;
        int bounds = shape.bounds();

        for (int offsetX = -bounds; offsetX <= bounds; offsetX++) {
            for (int offsetZ = -bounds; offsetZ <= bounds; offsetZ++) {
                int worldX = origin.getX() + offsetX;
                int worldZ = origin.getZ() + offsetZ;
                double distance = shape.distance(offsetX, offsetZ, worldX, worldZ);
                if (distance > OVERGROWN_LIMIT) {
                    continue;
                }

                double score = 0.58D - distance + shape.coherence(worldX, worldZ) * 0.18D;
                double chance = Math.clamp(0.30D + score, 0.20D, 0.70D);
                if (score < -0.04D || random.nextDouble() >= chance) {
                    continue;
                }

                OvergrownCandidate candidate = findOvergrownCandidate(
                        level,
                        origin.offset(offsetX, 0, offsetZ),
                        block
                );
                if (candidate == null) {
                    continue;
                }

                int height = sampleColumnHeight(random, candidate.maxHeight(), distance);
                if (block.placeColumn(level, candidate.bottom(), height)) {
                    overgrownColumns.add(columnKey(worldX, worldZ));
                    placedAny = true;
                }
            }
        }

        for (int offsetX = -bounds; offsetX <= bounds; offsetX++) {
            for (int offsetZ = -bounds; offsetZ <= bounds; offsetZ++) {
                int worldX = origin.getX() + offsetX;
                int worldZ = origin.getZ() + offsetZ;
                long columnKey = columnKey(worldX, worldZ);
                if (overgrownColumns.contains(columnKey)) {
                    continue;
                }

                double distance = shape.distance(offsetX, offsetZ, worldX, worldZ);
                if (distance > TALL_GRASS_LIMIT) {
                    continue;
                }

                double transition = 1.0D - Math.abs(distance - 0.60D) / 0.42D;
                double chance = Math.clamp(
                        0.14D + Math.max(0.0D, transition) * 0.34D
                                + shape.coherence(worldX, worldZ) * 0.08D,
                        0.08D,
                        0.48D
                );
                if (random.nextDouble() >= chance) {
                    continue;
                }

                if (GrassPatchPlacement.placeTallGrass(level, origin.offset(offsetX, 0, offsetZ))) {
                    tallColumns.add(columnKey);
                    placedAny = true;
                }
            }
        }

        for (int offsetX = -bounds; offsetX <= bounds; offsetX++) {
            for (int offsetZ = -bounds; offsetZ <= bounds; offsetZ++) {
                int worldX = origin.getX() + offsetX;
                int worldZ = origin.getZ() + offsetZ;
                long columnKey = columnKey(worldX, worldZ);
                if (overgrownColumns.contains(columnKey) || tallColumns.contains(columnKey)) {
                    continue;
                }

                double distance = shape.distance(offsetX, offsetZ, worldX, worldZ);
                if (distance > SHORT_GRASS_LIMIT) {
                    continue;
                }

                double edgeFade = Math.max(0.0D, distance - 0.72D);
                double chance = Math.clamp(
                        0.45D - edgeFade * 0.70D + shape.coherence(worldX, worldZ) * 0.08D,
                        0.10D,
                        0.50D
                );
                if (random.nextDouble() < chance) {
                    placedAny |= GrassPatchPlacement.placeShortGrass(
                            level,
                            random,
                            origin.offset(offsetX, 0, offsetZ)
                    );
                }
            }
        }

        return placedAny;
    }

    private OvergrownCandidate findOvergrownCandidate(
            WorldGenLevel level,
            BlockPos anchor,
            OvergrownGrassBlock block
    ) {
        for (int offsetY = SURFACE_SCAN_UP; offsetY >= -SURFACE_SCAN_DOWN; offsetY--) {
            BlockPos bottom = anchor.offset(0, offsetY, 0);
            int maxHeight = maxPlaceableHeight(level, bottom, block);
            if (maxHeight >= OvergrownGrassBlock.MIN_WORLDGEN_HEIGHT) {
                return new OvergrownCandidate(bottom, maxHeight);
            }
        }

        return null;
    }

    private int maxPlaceableHeight(WorldGenLevel level, BlockPos bottom, OvergrownGrassBlock block) {
        int maxHeight = 0;
        while (maxHeight < OvergrownGrassBlock.MAX_HEIGHT
                && block.canPlaceColumn(level, bottom, maxHeight + 1)) {
            maxHeight++;
        }

        return maxHeight;
    }

    private int sampleColumnHeight(RandomSource random, int maxHeight, double distance) {
        int cappedHeight = Math.min(OvergrownGrassBlock.MAX_HEIGHT, maxHeight);
        if (cappedHeight <= OvergrownGrassBlock.MIN_WORLDGEN_HEIGHT) {
            return cappedHeight;
        }

        double centrality = 1.0D - Math.clamp(distance / OVERGROWN_LIMIT, 0.0D, 1.0D);
        double roll = random.nextDouble();
        if (cappedHeight >= 5 && roll < 0.08D + centrality * 0.24D) {
            if (cappedHeight >= 6 && centrality > 0.70D && random.nextDouble() < 0.22D) {
                return 6;
            }

            return 5;
        }

        if (cappedHeight >= 4 && roll < 0.35D + centrality * 0.25D) {
            return 4;
        }

        if (cappedHeight >= 3 && roll < 0.78D) {
            return 3;
        }

        return OvergrownGrassBlock.MIN_WORLDGEN_HEIGHT;
    }

    private static long columnKey(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }

    private record OvergrownCandidate(BlockPos bottom, int maxHeight) {
    }
}
