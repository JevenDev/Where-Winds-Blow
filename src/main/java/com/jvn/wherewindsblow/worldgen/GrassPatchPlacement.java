package com.jvn.wherewindsblow.worldgen;

import com.jvn.wherewindsblow.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

final class GrassPatchPlacement {
    private static final int SURFACE_SCAN_UP = 3;
    private static final int SURFACE_SCAN_DOWN = 5;
    private static final float FLAT_GRASS_CHANCE = 0.28F;

    private GrassPatchPlacement() {
    }

    static boolean placeTallGrass(WorldGenLevel level, BlockPos anchor) {
        BlockState lower = Blocks.TALL_GRASS.defaultBlockState()
                .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
        BlockPos bottom = findPlantingPosition(level, anchor, lower, true);
        if (bottom == null) {
            return false;
        }

        BlockState upper = Blocks.TALL_GRASS.defaultBlockState()
                .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
        level.setBlock(bottom, lower, 3);
        level.setBlock(bottom.above(), upper, 3);
        return true;
    }

    static boolean placeShortGrass(WorldGenLevel level, RandomSource random, BlockPos anchor) {
        BlockState preferred = random.nextFloat() < FLAT_GRASS_CHANCE
                ? ModBlocks.FLAT_GRASS.get().defaultBlockState()
                : Blocks.SHORT_GRASS.defaultBlockState();
        BlockPos position = findPlantingPosition(level, anchor, preferred, false);
        if (position == null) {
            return false;
        }

        level.setBlock(position, preferred, 3);
        return true;
    }

    private static BlockPos findPlantingPosition(
            WorldGenLevel level,
            BlockPos anchor,
            BlockState plant,
            boolean needsUpperSpace
    ) {
        for (int offsetY = SURFACE_SCAN_UP; offsetY >= -SURFACE_SCAN_DOWN; offsetY--) {
            BlockPos candidate = anchor.offset(0, offsetY, 0);
            if (canPlacePlant(level, candidate, plant, needsUpperSpace)) {
                return candidate;
            }
        }

        return null;
    }

    private static boolean canPlacePlant(
            WorldGenLevel level,
            BlockPos position,
            BlockState plant,
            boolean needsUpperSpace
    ) {
        if (!isOpenPlantSpace(level, position) || !plant.canSurvive(level, position)) {
            return false;
        }

        return !needsUpperSpace || isOpenPlantSpace(level, position.above());
    }

    private static boolean isOpenPlantSpace(WorldGenLevel level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        return level.getFluidState(position).isEmpty()
                && !state.is(ModBlocks.OVERGROWN_GRASS.get())
                && (state.isAir() || state.canBeReplaced());
    }
}
