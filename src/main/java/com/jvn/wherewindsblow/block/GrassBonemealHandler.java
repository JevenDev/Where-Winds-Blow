package com.jvn.wherewindsblow.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;

public final class GrassBonemealHandler {
    private static final int INITIAL_OVERGROWN_HEIGHT = 3;

    private GrassBonemealHandler() {
    }

    public static void onBonemeal(BonemealEvent event) {
        BlockState targetState = event.getState();
        if (!targetState.is(Blocks.TALL_GRASS)) {
            return;
        }

        BlockPos targetPos = event.getPos();
        BlockPos bottomPos = targetState.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER
                ? targetPos.below()
                : targetPos;
        Level level = event.getLevel();
        if (!isCompleteTallGrass(level, bottomPos)) {
            return;
        }

        OvergrownGrassBlock overgrownGrass = ModBlocks.OVERGROWN_GRASS.get();
        if (!overgrownGrass.canPlaceColumn(level, bottomPos, INITIAL_OVERGROWN_HEIGHT)) {
            return;
        }

        if (!level.isClientSide()) {
            if (!overgrownGrass.placeColumn(level, bottomPos, INITIAL_OVERGROWN_HEIGHT)) {
                return;
            }
            event.getStack().shrink(1);
        }

        event.setSuccessful(true);
    }

    private static boolean isCompleteTallGrass(Level level, BlockPos bottomPos) {
        BlockState bottom = level.getBlockState(bottomPos);
        BlockState top = level.getBlockState(bottomPos.above());
        return bottom.is(Blocks.TALL_GRASS)
                && bottom.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER
                && top.is(Blocks.TALL_GRASS)
                && top.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
    }
}
