package com.jvn.wherewindsblow.worldgen;

import com.jvn.wherewindsblow.block.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

public final class FlowerPatchFeature extends Feature<FlowerPatchConfiguration> {
    public FlowerPatchFeature(Codec<FlowerPatchConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<FlowerPatchConfiguration> context) {
        FlowerPatchConfiguration configuration = context.config();
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        BlockState flower = configuration.toPlace().getState(random, origin);
        GrassPatchShape shape = GrassPatchShape.create(random, 2.5D, 4.0D, 2.5D, 4.0D);
        int bounds = shape.bounds();
        boolean placedAny = false;

        for (int offsetX = -bounds; offsetX <= bounds; offsetX++) {
            for (int offsetZ = -bounds; offsetZ <= bounds; offsetZ++) {
                int x = origin.getX() + offsetX;
                int z = origin.getZ() + offsetZ;
                double distance = shape.distance(offsetX, offsetZ, x, z);
                if (distance > 1.0D || random.nextFloat() >= 0.72D * (1.0D - distance * 0.6D)) {
                    continue;
                }
                placedAny |= placeFlower(level, x, z, flower, configuration);
            }
        }

        return placedAny;
    }

    static boolean placeFlower(WorldGenLevel level, int x, int z, BlockState flower,
                               FlowerPatchConfiguration configuration) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        if (level.isOutsideBuildHeight(pos) || !level.ensureCanWrite(pos)
                || !level.getFluidState(pos).isEmpty() || !configuration.biomes().contains(level.getBiome(pos))) {
            return false;
        }
        BlockState previous = level.getBlockState(pos);
        if (!previous.isAir() && !previous.is(Blocks.SHORT_GRASS)
                && !previous.is(ModBlocks.FLAT_GRASS.get())) {
            return false;
        }
        return flower.canSurvive(level, pos) && level.setBlock(pos, flower, 2);
    }
}
