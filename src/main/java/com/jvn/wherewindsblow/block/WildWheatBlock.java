package com.jvn.wherewindsblow.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WildWheatBlock extends CropBlock {
    public static final MapCodec<WildWheatBlock> CODEC = simpleCodec(WildWheatBlock::new);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final VoxelShape[] SHAPES = new VoxelShape[]{
            box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
            box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D),
            box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D),
            box(0.0D, 0.0D, 0.0D, 16.0D, 15.0D, 16.0D)
    };

    public WildWheatBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    public MapCodec<? extends WildWheatBlock> codec() {
        return CODEC;
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return 3;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return Items.WHEAT_SEEDS;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(ModBlockTags.WILD_WHEAT_PLANTABLE_ON);
    }

    @Override
    protected int getBonemealAgeIncrease(net.minecraft.world.level.Level level) {
        return 1 + level.random.nextInt(2);
    }

    @Override
    public int getAge(BlockState state) {
        return state.getValue(AGE);
    }

    @Override
    public BlockState getStateForAge(int age) {
        return this.defaultBlockState().setValue(AGE, Math.clamp(age, 0, getMaxAge()));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[this.getAge(state)];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(AGE);
    }
}