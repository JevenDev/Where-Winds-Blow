package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.block.ModBlocks;
import com.jvn.wherewindsblow.block.OvergrownGrassBlock;
import com.jvn.wherewindsblow.block.OvergrownGrassPart;
import com.jvn.wherewindsblow.block.WildWheatBlock;
import java.util.Set;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class ResponsiveFoliage {
    private static final Set<String> RESPONSIVE_MODEL_PATHS = Set.of(
            "overgrown_grass",
            "wild_wheat",
            "short_grass",
            "tall_grass",
            "fern",
            "large_fern",
            "wheat"
    );

    private ResponsiveFoliage() {
    }

    public static boolean isInteractive(BlockState state) {
        return state.is(ModBlocks.OVERGROWN_GRASS.get())
                || state.is(ModBlocks.WILD_WHEAT.get())
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.WHEAT);
    }

    public static float swayMultiplier(BlockState state) {
        if (state.is(ModBlocks.OVERGROWN_GRASS.get())) {
            OvergrownGrassPart part = state.getValue(OvergrownGrassBlock.PART);
            return switch (part) {
                case LOWER -> 0.8F;
                case MIDDLE -> 1.0F;
                case UPPER -> 1.15F;
            };
        }

        if (state.is(ModBlocks.WILD_WHEAT.get())) {
            return 0.55F + state.getValue(WildWheatBlock.AGE) * 0.15F;
        }

        if (state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)) {
            return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? 1.15F : 0.85F;
        }

        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN)) {
            return 0.85F;
        }

        if (state.is(Blocks.WHEAT)) {
            return 0.45F + state.getValue(BlockStateProperties.AGE_7) * 0.08F;
        }

        return 1.0F;
    }

    public static float columnSwayMultiplier(BlockAndTintGetter level, FoliageModelData.ColumnSegment segment) {
        BlockState topState = level.getBlockState(segment.rootPos().above(segment.height() - 1));
        if (topState.is(ModBlocks.OVERGROWN_GRASS.get())) {
            return 0.85F + Math.min(segment.height(), 6) * 0.08F;
        }

        return swayMultiplier(topState);
    }

    public static FoliageModelData.ColumnSegment columnSegment(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.OVERGROWN_GRASS.get())) {
            BlockPos root = pos;
            while (level.getBlockState(root.below()).is(ModBlocks.OVERGROWN_GRASS.get())) {
                root = root.below();
            }

            int height = 0;
            while (level.getBlockState(root.above(height)).is(ModBlocks.OVERGROWN_GRASS.get())) {
                height++;
            }

            return new FoliageModelData.ColumnSegment(root, pos.getY() - root.getY(), height);
        }

        if (state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)) {
            DoubleBlockHalf half = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    ? state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    : DoubleBlockHalf.LOWER;
            BlockPos root = half == DoubleBlockHalf.UPPER ? pos.below() : pos;
            return new FoliageModelData.ColumnSegment(root, half == DoubleBlockHalf.UPPER ? 1 : 0, 2);
        }

        return new FoliageModelData.ColumnSegment(pos, 0, 1);
    }

    public static void wrapModels(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) -> shouldWrap(location, model) ? new ResponsiveFoliageModel(model) : model);
    }

    private static boolean shouldWrap(ModelResourceLocation location, BakedModel model) {
        ResourceLocation id = location.id();
        return !location.variant().equals(ModelResourceLocation.INVENTORY_VARIANT)
                && (id.getNamespace().equals(WhereWindsBlow.MOD_ID) || id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE))
                && RESPONSIVE_MODEL_PATHS.contains(id.getPath())
                && !(model instanceof ResponsiveFoliageModel);
    }
}
