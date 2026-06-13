package com.jvn.wherewindsblow.client.foliage;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

final class ResponsiveFoliageModel extends BakedModelWrapper<BakedModel> {
    private static final float BASE_THRESHOLD = 0.08F;
    private static final int PLANT_WIND_ALPHA_MIN = 17;
    private static final int PLANT_WIND_ALPHA_MAX = 44;
    private static final int LEAF_WIND_ALPHA_MIN = 45;
    private static final int LEAF_WIND_ALPHA_MAX = 72;
    private static final float LEAF_BEND_MIN = 0.10F;
    private static final float LEAF_BEND_MAX = 0.36F;
    private final ResponsiveFoliageType foliageType;

    ResponsiveFoliageModel(BakedModel originalModel, ResponsiveFoliageType foliageType) {
        super(originalModel);
        this.foliageType = foliageType;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        if (!ResponsiveFoliageShaders.shouldUseCustomFoliageShaders()
                || !isResponsiveState(state)
                || foliageType != ResponsiveFoliageType.PLANT) {
            return originalModel.getModelData(level, pos, state, modelData);
        }

        return originalModel.getModelData(level, pos, state, modelData)
                .derive()
                .with(FoliageModelData.COLUMN_SEGMENT, ResponsiveFoliage.columnSegment(level, pos, state))
                .build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
        List<BakedQuad> quads = originalModel.getQuads(state, side, rand, extraData, renderType);
        if (!ResponsiveFoliageShaders.shouldUseCustomFoliageShaders()
                || state == null
                || !isResponsiveState(state)) {
            return quads;
        }

        if (foliageType == ResponsiveFoliageType.LEAF) {
            List<BakedQuad> transformed = new ArrayList<>(quads.size());
            for (BakedQuad quad : quads) {
                transformed.add(transformLeafQuad(quad));
            }

            return transformed;
        }

        if (!extraData.has(FoliageModelData.COLUMN_SEGMENT)) {
            return quads;
        }

        FoliageModelData.ColumnSegment segment = extraData.get(FoliageModelData.COLUMN_SEGMENT);
        List<BakedQuad> transformed = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            transformed.add(transformPlantQuad(quad, segment));
        }

        return transformed;
    }

    private static BakedQuad transformPlantQuad(BakedQuad quad, FoliageModelData.ColumnSegment segment) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float columnY = segment.offset() + y;
            float baseThreshold = segment.offset() == 0 ? BASE_THRESHOLD : 0.0F;
            if (columnY <= baseThreshold) {
                continue;
            }

            float columnHeight = segment.height();
            float rawWeight = Mth.clamp((columnY - BASE_THRESHOLD) / (columnHeight - BASE_THRESHOLD), 0.0F, 1.0F);
            vertices[offset + 3] = packWindAlpha(vertices[offset + 3], ResponsiveFoliageType.PLANT, rawWeight);
            vertices[offset + 7] = packWindData(rawWeight);
        }

        return new BakedQuad(
                vertices,
                quad.getTintIndex(),
                quad.getDirection(),
                quad.getSprite(),
                quad.isShade(),
                quad.hasAmbientOcclusion()
        );
    }

    private static BakedQuad transformLeafQuad(BakedQuad quad) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float x = Float.intBitsToFloat(vertices[offset]);
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float z = Float.intBitsToFloat(vertices[offset + 2]);
            float bendWeight = leafBendWeight(x, y, z);
            vertices[offset + 3] = packWindAlpha(vertices[offset + 3], ResponsiveFoliageType.LEAF, bendWeight);
            vertices[offset + 7] = packWindData(bendWeight);
        }

        return new BakedQuad(
                vertices,
                quad.getTintIndex(),
                quad.getDirection(),
                quad.getSprite(),
                quad.isShade(),
                quad.hasAmbientOcclusion()
        );
    }

    private static float leafBendWeight(float x, float y, float z) {
        float localX = Mth.clamp(x, 0.0F, 1.0F);
        float localY = Mth.clamp(y, 0.0F, 1.0F);
        float localZ = Mth.clamp(z, 0.0F, 1.0F);
        float edgeWeight = Math.max(Math.abs(localX - 0.5F), Math.abs(localZ - 0.5F)) * 2.0F;
        return Mth.clamp(0.12F + localY * 0.12F + edgeWeight * 0.10F, LEAF_BEND_MIN, LEAF_BEND_MAX);
    }

    private static int packWindData(float bendWeight) {
        int encodedWeight = encodeUnit(bendWeight);
        return (129 & 0xFF) | ((129 & 0xFF) << 8) | ((encodedWeight & 0xFF) << 16);
    }

    private static int packWindAlpha(int color, ResponsiveFoliageType foliageType, float bendWeight) {
        int minAlpha = foliageType == ResponsiveFoliageType.LEAF ? LEAF_WIND_ALPHA_MIN : PLANT_WIND_ALPHA_MIN;
        int maxAlpha = foliageType == ResponsiveFoliageType.LEAF ? LEAF_WIND_ALPHA_MAX : PLANT_WIND_ALPHA_MAX;
        int alpha = Mth.clamp(Math.round(minAlpha + Mth.clamp(bendWeight, 0.0F, 1.0F) * (maxAlpha - minAlpha)), minAlpha, maxAlpha);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static int encodeUnit(float value) {
        return Mth.clamp(Math.round(Mth.clamp(value, 0.0F, 1.0F) * 254.0F - 127.0F), -127, 127);
    }

    private boolean isResponsiveState(@Nullable BlockState state) {
        return state != null && (foliageType == ResponsiveFoliageType.PLANT || ResponsiveFoliage.isLeaf(state));
    }
}
