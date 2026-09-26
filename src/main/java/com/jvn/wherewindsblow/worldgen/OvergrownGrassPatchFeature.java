package com.jvn.wherewindsblow.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class OvergrownGrassPatchFeature extends Feature<NoneFeatureConfiguration> {
    public OvergrownGrassPatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        float roll = random.nextFloat();
        int peakHeight = roll < 0.5F ? 4 : roll < 0.9F ? 5 : 6;
        double radius = (peakHeight - 1) * 1.5D;
        GrassPatchShape shape = GrassPatchShape.create(random, radius + 1.0D, radius + 2.5D, radius, radius + 1.25D);
        boolean placedAny = false;
        int bounds = shape.bounds();

        for (int offsetX = -bounds; offsetX <= bounds; offsetX++) {
            for (int offsetZ = -bounds; offsetZ <= bounds; offsetZ++) {
                int worldX = origin.getX() + offsetX;
                int worldZ = origin.getZ() + offsetZ;
                int height = shape.grassHeight(offsetX, offsetZ, worldX, worldZ, peakHeight);
                if (height > 0) {
                    placedAny |= GrassPatchPlacement.placeGrass(level, random, origin.offset(offsetX, 0, offsetZ), height);
                }
            }
        }

        return placedAny;
    }
}
