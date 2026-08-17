package com.jvn.wherewindsblow.worldgen;

import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class TallGrassPatchFeature extends Feature<NoneFeatureConfiguration> {
    private static final double TALL_GRASS_LIMIT = 0.82D;
    private static final double SHORT_GRASS_LIMIT = 1.08D;

    public TallGrassPatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        GrassPatchShape shape = GrassPatchShape.create(random, 3.25D, 5.0D, 2.25D, 3.5D);
        Set<Long> tallColumns = new HashSet<>();
        boolean placedAny = false;
        int bounds = shape.bounds();

        for (int offsetX = -bounds; offsetX <= bounds; offsetX++) {
            for (int offsetZ = -bounds; offsetZ <= bounds; offsetZ++) {
                int worldX = origin.getX() + offsetX;
                int worldZ = origin.getZ() + offsetZ;
                double distance = shape.distance(offsetX, offsetZ, worldX, worldZ);
                if (distance > TALL_GRASS_LIMIT) {
                    continue;
                }

                double coherence = shape.coherence(worldX, worldZ);
                double score = 0.70D - distance + coherence * 0.18D;
                double chance = Math.clamp(0.28D + score, 0.12D, 0.72D);
                if (score < -0.08D || random.nextDouble() >= chance) {
                    continue;
                }

                BlockPos anchor = origin.offset(offsetX, 0, offsetZ);
                if (GrassPatchPlacement.placeTallGrass(level, anchor)) {
                    tallColumns.add(columnKey(worldX, worldZ));
                    placedAny = true;
                }
            }
        }

        for (int offsetX = -bounds; offsetX <= bounds; offsetX++) {
            for (int offsetZ = -bounds; offsetZ <= bounds; offsetZ++) {
                int worldX = origin.getX() + offsetX;
                int worldZ = origin.getZ() + offsetZ;
                if (tallColumns.contains(columnKey(worldX, worldZ))) {
                    continue;
                }

                double distance = shape.distance(offsetX, offsetZ, worldX, worldZ);
                if (distance > SHORT_GRASS_LIMIT) {
                    continue;
                }

                double coherence = shape.coherence(worldX, worldZ);
                double edgeFade = Math.max(0.0D, distance - 0.66D);
                double chance = Math.clamp(0.50D - edgeFade * 0.78D + coherence * 0.10D, 0.12D, 0.56D);
                if (random.nextDouble() >= chance) {
                    continue;
                }

                BlockPos anchor = origin.offset(offsetX, 0, offsetZ);
                placedAny |= GrassPatchPlacement.placeShortGrass(level, random, anchor);
            }
        }

        return placedAny;
    }

    private static long columnKey(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }
}
