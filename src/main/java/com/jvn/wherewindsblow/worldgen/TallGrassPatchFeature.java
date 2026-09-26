package com.jvn.wherewindsblow.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class TallGrassPatchFeature extends Feature<NoneFeatureConfiguration> {
    public TallGrassPatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        GrassPatchShape shape = GrassPatchShape.create(random, 3.25D, 5.0D, 2.25D, 3.5D);
        boolean placedAny = false;
        int bounds = shape.bounds();

        for (int offsetX = -bounds; offsetX <= bounds; offsetX++) {
            for (int offsetZ = -bounds; offsetZ <= bounds; offsetZ++) {
                int worldX = origin.getX() + offsetX;
                int worldZ = origin.getZ() + offsetZ;
                int height = shape.grassHeight(offsetX, offsetZ, worldX, worldZ, 2);
                if (height > 0) {
                    placedAny |= GrassPatchPlacement.placeGrass(level, random, origin.offset(offsetX, 0, offsetZ), height);
                }
            }
        }

        return placedAny;
    }
}
