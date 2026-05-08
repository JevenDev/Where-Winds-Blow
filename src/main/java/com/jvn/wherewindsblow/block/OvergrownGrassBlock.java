package com.jvn.wherewindsblow.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class OvergrownGrassBlock extends BushBlock implements BonemealableBlock {
    public static final MapCodec<OvergrownGrassBlock> CODEC = simpleCodec(OvergrownGrassBlock::new);
    public static final EnumProperty<OvergrownGrassPart> PART = EnumProperty.create("part", OvergrownGrassPart.class);
    public static final int MIN_WORLDGEN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 6;

    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
    private static final ThreadLocal<Boolean> CASCADING_BREAK = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> BULK_PLACEMENT = ThreadLocal.withInitial(() -> false);

    public OvergrownGrassBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(PART, OvergrownGrassPart.UPPER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return !useContext.getItemInHand().is(ModBlocks.OVERGROWN_GRASS_ITEM.get()) && super.canBeReplaced(state, useContext);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = this.defaultBlockState();
        return state.canSurvive(level, pos) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(this) || super.canSurvive(state, level, pos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !BULK_PLACEMENT.get() && !state.is(oldState.getBlock())) {
            refreshColumn(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !CASCADING_BREAK.get()) {
            CASCADING_BREAK.set(true);
            try {
                clearAbove(level, pos);
                refreshColumn(level, pos.below());
            } finally {
                CASCADING_BREAK.set(false);
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos bottomPos = findBottom(level, pos);
        int height = columnHeight(level, bottomPos);
        return height < MAX_HEIGHT && canGrowInto(level.getBlockState(bottomPos.above(height)));
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return random.nextInt(4) == 0;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos bottomPos = findBottom(level, pos);
        int height = columnHeight(level, bottomPos);
        if (height >= MAX_HEIGHT) {
            return;
        }

        BlockPos nextPos = bottomPos.above(height);
        if (!canGrowInto(level.getBlockState(nextPos))) {
            return;
        }

        level.setBlock(nextPos, this.defaultBlockState(), 3);
        refreshColumn(level, bottomPos);
    }

    public boolean placeColumn(LevelAccessor level, BlockPos bottomPos, int height) {
        int clampedHeight = Math.max(1, Math.min(height, MAX_HEIGHT));
        if (!canPlaceColumn(level, bottomPos, clampedHeight)) {
            return false;
        }

        BULK_PLACEMENT.set(true);
        try {
            for (int offset = 0; offset < clampedHeight; offset++) {
                level.setBlock(bottomPos.above(offset), this.defaultBlockState().setValue(PART, partFor(offset, clampedHeight)), 3);
            }
        } finally {
            BULK_PLACEMENT.set(false);
        }

        refreshColumn(level, bottomPos);

        return true;
    }

    public boolean canPlaceColumn(LevelReader level, BlockPos bottomPos, int height) {
        if (height <= 0 || height > MAX_HEIGHT) {
            return false;
        }

        if (level.getBlockState(bottomPos).is(this) || level.getBlockState(bottomPos.below()).is(this)) {
            return false;
        }

        for (int offset = 0; offset < height; offset++) {
            BlockPos current = bottomPos.above(offset);
            BlockState currentState = level.getBlockState(current);
            if (offset == 0) {
                if (!this.defaultBlockState().canSurvive(level, current) || (!currentState.isAir() && !currentState.canBeReplaced())) {
                    return false;
                }
            } else if (currentState.is(this) || !canGrowInto(currentState)) {
                return false;
            }
        }

        return true;
    }

    private void clearAbove(Level level, BlockPos pos) {
        BlockPos current = pos.above();
        while (level.getBlockState(current).is(this)) {
            BlockState stateAbove = level.getBlockState(current);
            level.levelEvent(2001, current, Block.getId(stateAbove));
            level.removeBlock(current, false);
            current = current.above();
        }
    }

    private void refreshColumn(LevelAccessor level, BlockPos pos) {
        BlockPos bottomPos = findBottom(level, pos);
        if (!level.getBlockState(bottomPos).is(this)) {
            return;
        }

        int height = columnHeight(level, bottomPos);
        for (int offset = 0; offset < height; offset++) {
            BlockPos current = bottomPos.above(offset);
            level.setBlock(current, this.defaultBlockState().setValue(PART, partFor(offset, height)), 3);
        }
    }

    private BlockPos findBottom(LevelReader level, BlockPos pos) {
        BlockPos current = pos;
        while (level.getBlockState(current).is(this) && level.getBlockState(current.below()).is(this)) {
            current = current.below();
        }

        return current;
    }

    private int columnHeight(LevelReader level, BlockPos bottomPos) {
        int height = 0;
        while (level.getBlockState(bottomPos.above(height)).is(this)) {
            height++;
        }

        return height;
    }

    private boolean canGrowInto(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    private OvergrownGrassPart partFor(int offset, int height) {
        if (height == 1) {
            return OvergrownGrassPart.UPPER;
        }

        if (offset == 0) {
            return OvergrownGrassPart.LOWER;
        }

        return offset == height - 1 ? OvergrownGrassPart.UPPER : OvergrownGrassPart.MIDDLE;
    }
}