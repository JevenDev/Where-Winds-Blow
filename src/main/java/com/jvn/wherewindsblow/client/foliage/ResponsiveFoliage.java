package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.block.ModBlocks;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class ResponsiveFoliage {
    private static final float PLANE_EPSILON = 0.01F;
    private static final int MIN_WIND_EXPOSED_SKY_LIGHT = 14;
    private static final int WIND_SIDE_ACCESS_RADIUS = 5;
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    private ResponsiveFoliage() {
    }

    public static boolean isInteractive(BlockState state) {
        Block block = state.getBlock();
        return block instanceof BushBlock
                || block instanceof CropBlock
                || block instanceof DoublePlantBlock
                || state.is(Blocks.SUGAR_CANE)
                || state.is(ModBlocks.OVERGROWN_GRASS.get())
                || state.is(ModBlocks.WILD_WHEAT.get());
    }

    public static boolean isLeaf(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.getBlock() instanceof LeavesBlock;
    }

    public static FoliageModelData.ColumnSegment columnSegment(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.OVERGROWN_GRASS.get()) || state.is(Blocks.SUGAR_CANE)) {
            Block columnBlock = state.getBlock();
            BlockPos root = pos;
            while (level.getBlockState(root.below()).is(columnBlock)) {
                root = root.below();
            }

            int height = 0;
            while (level.getBlockState(root.above(height)).is(columnBlock)) {
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

    public static boolean isWindExposed(BlockAndTintGetter level, BlockPos pos) {
        if (hasWindSkylight(level, pos)) {
            return true;
        }

        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            for (int distance = 1; distance <= WIND_SIDE_ACCESS_RADIUS; distance++) {
                samplePos.set(
                        pos.getX() + direction.getStepX() * distance,
                        pos.getY(),
                        pos.getZ() + direction.getStepZ() * distance
                );

                if (!canWindPassThrough(level, samplePos)) {
                    break;
                }

                if (hasWindSkylight(level, samplePos)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean hasWindSkylight(BlockAndTintGetter level, BlockPos pos) {
        return level.getBrightness(LightLayer.SKY, pos) >= MIN_WIND_EXPOSED_SKY_LIGHT;
    }

    private static boolean canWindPassThrough(BlockAndTintGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return isInteractive(state)
                || isLeaf(state)
                || state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty();
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
