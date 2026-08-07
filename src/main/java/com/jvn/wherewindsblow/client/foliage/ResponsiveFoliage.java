package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.block.ModBlocks;
import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.GlobalWindState;
import com.jvn.wherewindsblow.client.wind.WindExposureCache;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class ResponsiveFoliage {
    private static final int MAX_MODELED_COLUMN_HEIGHT = 32;

    private static final float PLANE_EPSILON = 0.01F;

    private ResponsiveFoliage() {
    }

    public static boolean isInteractive(BlockState state) {
        Block block = state.getBlock();
        return !isRigidOrHorizontalBush(block)
                && (block instanceof BushBlock
                    || block instanceof CropBlock
                    || block instanceof DoublePlantBlock
                    || state.is(Blocks.SUGAR_CANE)
                    || isNetherVine(state)
                    || state.is(ModBlocks.OVERGROWN_GRASS.get())
                    || state.is(ModBlocks.WILD_WHEAT.get()));
    }

    private static boolean isRigidOrHorizontalBush(Block block) {
        // These inherit BushBlock for placement behavior, but their models are not
        // rooted crossed-plane foliage and distort when encoded as grass or flowers.
        return block instanceof AzaleaBlock
                || block instanceof SeaPickleBlock
                || block instanceof WaterlilyBlock;
    }

    public static boolean isLeaf(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.getBlock() instanceof LeavesBlock;
    }

    public static FoliageModelData.ColumnSegment columnSegment(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        if (isTwistingVine(state)) {
            return netherVineColumn(level, pos, false);
        }

        if (isWeepingVine(state)) {
            return netherVineColumn(level, pos, true);
        }

        if (state.is(ModBlocks.OVERGROWN_GRASS.get()) || state.is(Blocks.SUGAR_CANE)) {
            Block columnBlock = state.getBlock();
            BlockPos root = pos;
            int distanceToRoot = 0;
            while (distanceToRoot < MAX_MODELED_COLUMN_HEIGHT - 1
                    && level.getBlockState(root.below()).is(columnBlock)) {
                root = root.below();
                distanceToRoot++;
            }

            int height = 0;
            while (height < MAX_MODELED_COLUMN_HEIGHT
                    && level.getBlockState(root.above(height)).is(columnBlock)) {
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

        if (state.getBlock() instanceof PinkPetalsBlock) {
            // Vanilla petals are only three pixels tall. Normalize that height so
            // their tops reach the wind and interaction marker ranges.
            return new FoliageModelData.ColumnSegment(pos, 0, 1, false, 16.0F / 3.0F);
        }

        return new FoliageModelData.ColumnSegment(pos, 0, 1);
    }

    private static FoliageModelData.ColumnSegment netherVineColumn(
            BlockAndTintGetter level,
            BlockPos pos,
            boolean hangsFromTop
    ) {
        BlockPos root = pos;
        int distanceToRoot = 0;
        while (distanceToRoot < MAX_MODELED_COLUMN_HEIGHT - 1
                && sameNetherVine(
                        level.getBlockState(hangsFromTop ? root.above() : root.below()),
                        hangsFromTop
                )) {
            root = hangsFromTop ? root.above() : root.below();
            distanceToRoot++;
        }

        int height = 0;
        while (height < MAX_MODELED_COLUMN_HEIGHT
                && sameNetherVine(
                        level.getBlockState(hangsFromTop ? root.below(height) : root.above(height)),
                        hangsFromTop
                )) {
            height++;
        }

        int offset = hangsFromTop ? root.getY() - pos.getY() : pos.getY() - root.getY();
        return new FoliageModelData.ColumnSegment(root, offset, height, hangsFromTop, 1.0F);
    }

    private static boolean isNetherVine(BlockState state) {
        return isTwistingVine(state) || isWeepingVine(state);
    }

    private static boolean isTwistingVine(BlockState state) {
        return state.is(Blocks.TWISTING_VINES) || state.is(Blocks.TWISTING_VINES_PLANT);
    }

    private static boolean isWeepingVine(BlockState state) {
        return state.is(Blocks.WEEPING_VINES) || state.is(Blocks.WEEPING_VINES_PLANT);
    }

    private static boolean sameNetherVine(BlockState state, boolean weeping) {
        return weeping ? isWeepingVine(state) : isTwistingVine(state);
    }

    public static boolean isWindExposed(BlockAndTintGetter level, BlockPos pos) {
        return windExposure(level, pos) >= 0.5F;
    }

    public static float windExposure(BlockAndTintGetter level, BlockPos pos) {
        GlobalWindState wind = DynamicWindManager.currentState();
        return WindExposureCache.exposureAt(level, pos, wind.directionX(), wind.directionZ());
    }

    public static void wrapModels(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) -> {
            ResponsiveFoliageType foliageType = foliageType(location, model);
            return foliageType != null ? new ResponsiveFoliageModel(model, foliageType) : model;
        });
    }

    private static ResponsiveFoliageType foliageType(ModelResourceLocation location, BakedModel model) {
        ResourceLocation id = location.id();
        if (location.variant().equals(ModelResourceLocation.INVENTORY_VARIANT)
                || model instanceof ResponsiveFoliageModel) {
            return null;
        }

        BlockState defaultState = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        if (isLeaf(defaultState)) {
            return ResponsiveFoliageType.LEAF;
        }

        if (isInteractive(defaultState)) {
            return ResponsiveFoliageType.PLANT;
        }

        if (isPlantLikePlaneModel(location, model)) {
            return ResponsiveFoliageType.AUTO_PLANT;
        }

        return null;
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
                || path.contains("cane")
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
