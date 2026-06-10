package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.block.ModBlocks;
import com.jvn.wherewindsblow.block.OvergrownGrassBlock;
import com.jvn.wherewindsblow.block.OvergrownGrassPart;
import com.jvn.wherewindsblow.block.WildWheatBlock;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class ResponsiveFoliage {
    private static final float PLANE_EPSILON = 0.01F;

    private ResponsiveFoliage() {
    }

    public static boolean isInteractive(BlockState state) {
        Block block = state.getBlock();
        return block instanceof BushBlock
                || block instanceof CropBlock
                || block instanceof DoublePlantBlock
                || state.is(ModBlocks.OVERGROWN_GRASS.get())
                || state.is(ModBlocks.WILD_WHEAT.get());
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

        if (state.getBlock() instanceof DoublePlantBlock) {
            return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? 1.15F : 0.85F;
        }

        if (state.getBlock() instanceof BushBlock) {
            return 0.85F;
        }

        if (state.getBlock() instanceof CropBlock && state.hasProperty(BlockStateProperties.AGE_7)) {
            return 0.45F + state.getValue(BlockStateProperties.AGE_7) * 0.08F;
        }

        if (state.getBlock() instanceof CropBlock) {
            return 0.65F;
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

        if (state.getBlock() instanceof DoublePlantBlock) {
            DoubleBlockHalf half = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    ? state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    : DoubleBlockHalf.LOWER;
            BlockPos root = half == DoubleBlockHalf.UPPER ? pos.below() : pos;
            return new FoliageModelData.ColumnSegment(root, half == DoubleBlockHalf.UPPER ? 1 : 0, 2);
        }

        return new FoliageModelData.ColumnSegment(pos, 0, 1);
    }

    public static void wrapModels(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) -> {
            boolean modelDetectedFoliage = isPlantLikePlaneModel(location, model);
            return shouldWrap(location, model, modelDetectedFoliage) ? new ResponsiveFoliageModel(model, modelDetectedFoliage) : model;
        });
    }

    private static boolean shouldWrap(ModelResourceLocation location, BakedModel model, boolean modelDetectedFoliage) {
        ResourceLocation id = location.id();
        return !location.variant().equals(ModelResourceLocation.INVENTORY_VARIANT)
                && (isInteractive(BuiltInRegistries.BLOCK.get(id).defaultBlockState()) || modelDetectedFoliage)
                && !(model instanceof ResponsiveFoliageModel);
    }

    private static boolean isPlantLikePlaneModel(ModelResourceLocation location, BakedModel model) {
        if (!hasPlantLikeName(location.id().getPath())) {
            return false;
        }

        List<BakedQuad> quads;
        try {
            quads = model.getQuads(null, null, RandomSource.create(42L), ModelData.EMPTY, null);
        } catch (RuntimeException exception) {
            return false;
        }

        if (quads.isEmpty() || quads.size() > 16) {
            return false;
        }

        for (BakedQuad quad : quads) {
            if (!isVerticalPlane(quad)) {
                return false;
            }
        }

        return true;
    }

    private static boolean hasPlantLikeName(String path) {
        return path.contains("grass")
                || path.contains("flower")
                || path.contains("fern")
                || path.contains("bush")
                || path.contains("shrub")
                || path.contains("crop")
                || path.contains("wheat")
                || path.contains("plant")
                || path.contains("foliage")
                || path.contains("reed")
                || path.contains("cattail")
                || path.contains("clover")
                || path.contains("sprout")
                || path.contains("sapling")
                || path.contains("petal");
    }

    private static boolean isVerticalPlane(BakedQuad quad) {
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        float x0 = Float.intBitsToFloat(vertices[0]);
        float y0 = Float.intBitsToFloat(vertices[1]);
        float z0 = Float.intBitsToFloat(vertices[2]);
        float x1 = Float.intBitsToFloat(vertices[stride]);
        float y1 = Float.intBitsToFloat(vertices[stride + 1]);
        float z1 = Float.intBitsToFloat(vertices[stride + 2]);
        float x2 = Float.intBitsToFloat(vertices[stride * 2]);
        float y2 = Float.intBitsToFloat(vertices[stride * 2 + 1]);
        float z2 = Float.intBitsToFloat(vertices[stride * 2 + 2]);
        float edge1X = x1 - x0;
        float edge1Y = y1 - y0;
        float edge1Z = z1 - z0;
        float edge2X = x2 - x0;
        float edge2Y = y2 - y0;
        float edge2Z = z2 - z0;
        float normalX = edge1Y * edge2Z - edge1Z * edge2Y;
        float normalY = edge1Z * edge2X - edge1X * edge2Z;
        float normalZ = edge1X * edge2Y - edge1Y * edge2X;
        float normalLength = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (normalLength <= PLANE_EPSILON || Math.abs(normalY / normalLength) > 0.08F) {
            return false;
        }

        float minY = y0;
        float maxY = y0;
        for (int vertex = 1; vertex < 4; vertex++) {
            float y = Float.intBitsToFloat(vertices[vertex * stride + 1]);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        return maxY - minY > 0.2F;
    }
}
