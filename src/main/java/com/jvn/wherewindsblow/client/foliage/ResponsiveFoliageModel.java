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
    private static final float MAX_DISPLACEMENT = 0.34F;
    private static final int WIND_ALPHA_MIN = 96;
    private static final int WIND_ALPHA_MAX = 254;

    ResponsiveFoliageModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        return originalModel.getModelData(level, pos, state, modelData)
                .derive()
                .with(FoliageModelData.COLUMN_SEGMENT, ResponsiveFoliage.columnSegment(level, pos, state))
                .build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
        List<BakedQuad> quads = originalModel.getQuads(state, side, rand, extraData, renderType);
        if (state == null || !ResponsiveFoliage.isInteractive(state) || !extraData.has(FoliageModelData.COLUMN_SEGMENT)) {
            return quads;
        }

        FoliageModelData.ColumnSegment segment = extraData.get(FoliageModelData.COLUMN_SEGMENT);
        FoliageLeanState.LeanVector lean = FoliageLeanState.get(segment.rootPos());
        float displacement = lean == null ? 0.0F : Mth.clamp(lean.intensity(), 0.0F, 1.0F) * MAX_DISPLACEMENT;
        float offsetX = lean == null ? 0.0F : lean.dirX() * displacement;
        float offsetZ = lean == null ? 0.0F : lean.dirZ() * displacement;
        List<BakedQuad> transformed = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            transformed.add(transformQuad(quad, segment, offsetX, offsetZ));
        }

        return transformed;
    }

    private static BakedQuad transformQuad(BakedQuad quad, FoliageModelData.ColumnSegment segment, float offsetX, float offsetZ) {
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
            float weight = rawWeight;
            weight *= weight * (3.0F - 2.0F * weight);
            float x = Float.intBitsToFloat(vertices[offset]);
            float z = Float.intBitsToFloat(vertices[offset + 2]);
            vertices[offset] = Float.floatToRawIntBits(x + offsetX * weight);
            vertices[offset + 2] = Float.floatToRawIntBits(z + offsetZ * weight);
            vertices[offset + 3] = packWindAlpha(vertices[offset + 3], rawWeight);
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

    private static int packWindData(float bendWeight) {
        int encodedWeight = encodeUnit(bendWeight);
        return (129 & 0xFF) | ((129 & 0xFF) << 8) | ((encodedWeight & 0xFF) << 16);
    }

    private static int packWindAlpha(int color, float bendWeight) {
        int alpha = Mth.clamp(Math.round(WIND_ALPHA_MIN + Mth.clamp(bendWeight, 0.0F, 1.0F) * (WIND_ALPHA_MAX - WIND_ALPHA_MIN)), WIND_ALPHA_MIN, WIND_ALPHA_MAX);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static int encodeUnit(float value) {
        return Mth.clamp(Math.round(Mth.clamp(value, 0.0F, 1.0F) * 254.0F - 127.0F), -127, 127);
    }
}
