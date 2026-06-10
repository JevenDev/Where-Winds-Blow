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
    private static final float MIN_VISIBLE_INTENSITY = 0.01F;

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
        if (lean == null || lean.intensity() < MIN_VISIBLE_INTENSITY) {
            return quads;
        }

        float displacement = Mth.clamp(lean.intensity(), 0.0F, 1.0F) * MAX_DISPLACEMENT;
        float offsetX = lean.dirX() * displacement;
        float offsetZ = lean.dirZ() * displacement;
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
            float weight = (columnY - BASE_THRESHOLD) / (columnHeight - BASE_THRESHOLD);
            weight = Mth.clamp(weight, 0.0F, 1.0F);
            weight *= weight * (3.0F - 2.0F * weight);
            float x = Float.intBitsToFloat(vertices[offset]);
            float z = Float.intBitsToFloat(vertices[offset + 2]);
            vertices[offset] = Float.floatToRawIntBits(x + offsetX * weight);
            vertices[offset + 2] = Float.floatToRawIntBits(z + offsetZ * weight);
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
}
