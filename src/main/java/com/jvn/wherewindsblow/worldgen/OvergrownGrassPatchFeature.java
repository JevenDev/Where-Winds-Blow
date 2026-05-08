package com.jvn.wherewindsblow.worldgen;

import com.jvn.wherewindsblow.block.ModBlocks;
import com.jvn.wherewindsblow.block.OvergrownGrassBlock;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class OvergrownGrassPatchFeature extends Feature<NoneFeatureConfiguration> {
    private static final int TRIES = 48;
    private static final int XZ_SPREAD = 7;
    private static final int Y_SPREAD = 2;

    public OvergrownGrassPatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        OvergrownGrassBlock block = ModBlocks.OVERGROWN_GRASS.get();
        int placedColumns = 0;

        for (int attempt = 0; attempt < TRIES; attempt++) {
            BlockPos candidate = origin.offset(
                    random.nextInt(XZ_SPREAD + 1) - random.nextInt(XZ_SPREAD + 1),
                    random.nextInt(Y_SPREAD + 1) - random.nextInt(Y_SPREAD + 1),
                    random.nextInt(XZ_SPREAD + 1) - random.nextInt(XZ_SPREAD + 1)
            );

            int maxHeight = maxPlaceableHeight(level, candidate, block);
            if (maxHeight < OvergrownGrassBlock.MIN_WORLDGEN_HEIGHT) {
                continue;
            }

            int height = OvergrownGrassBlock.MIN_WORLDGEN_HEIGHT
                    + random.nextInt(Math.min(OvergrownGrassBlock.MAX_HEIGHT, maxHeight) - OvergrownGrassBlock.MIN_WORLDGEN_HEIGHT + 1);
            if (block.placeColumn(level, candidate, height)) {
                placedColumns++;
            }
        }

        return placedColumns > 0;
    }

    private int maxPlaceableHeight(WorldGenLevel level, BlockPos bottomPos, OvergrownGrassBlock block) {
        int maxHeight = 0;
        while (maxHeight < OvergrownGrassBlock.MAX_HEIGHT && block.canPlaceColumn(level, bottomPos, maxHeight + 1)) {
            maxHeight++;
        }

        return maxHeight;
    }
}