package com.jvn.wherewindsblow.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class SnowyDoublePlantBlock extends DoublePlantBlock {
    public static final MapCodec<SnowyDoublePlantBlock> CODEC = simpleCodec(SnowyDoublePlantBlock::new);

    public SnowyDoublePlantBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SnowyDoublePlantBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.SNOW_BLOCK) || super.mayPlaceOn(state, level, pos);
    }
}
