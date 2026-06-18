package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.config.ClientConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final float INTERACTION_BASE_THRESHOLD = 0.08F;
    private static final float MIN_PLANT_SWAY_HEIGHT_RANGE = 0.001F;
    private static final int PLANT_ALPHA_MIN = 17;
    private static final int PLANT_ALPHA_MAX = 44;
    private static final int PLANT_WIND_ALPHA_LEVELS = 7;
    private static final int PLANT_INTERACTION_ALPHA_LEVELS = 4;
    private static final int LEAF_WIND_ALPHA_MIN = 45;
    private static final int LEAF_WIND_ALPHA_MAX = 72;
    private static final float LEAF_BEND_MIN = 0.10F;
    private static final float LEAF_BEND_MAX = 0.36F;
    private final ResponsiveFoliageType foliageType;
    private final Map<BakedQuad, BakedQuad> leafQuadCache = new ConcurrentHashMap<>();
    private final Map<PlantQuadKey, BakedQuad> plantQuadCache = new ConcurrentHashMap<>();

    ResponsiveFoliageModel(BakedModel originalModel, ResponsiveFoliageType foliageType) {
        super(originalModel);
        this.foliageType = foliageType;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        if (!ResponsiveFoliageShaders.shouldUseCustomFoliageShaders()
                || !isResponsiveState(state)) {
            return originalModel.getModelData(level, pos, state, modelData);
        }

        var builder = originalModel.getModelData(level, pos, state, modelData)
                .derive()
                .with(FoliageModelData.WIND_EXPOSED, ResponsiveFoliage.isWindExposed(level, pos));
        if (isPlantFoliage()) {
            builder.with(FoliageModelData.COLUMN_SEGMENT, ResponsiveFoliage.columnSegment(level, pos, state));
        }

        return builder.build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
        List<BakedQuad> quads = originalModel.getQuads(state, side, rand, extraData, renderType);
        if (!ResponsiveFoliageShaders.shouldUseCustomFoliageShaders()
                || state == null
                || !isResponsiveState(state)) {
            return quads;
        }

        boolean windExposed = windExposed(extraData);
        if (foliageType == ResponsiveFoliageType.LEAF) {
            if (!windExposed) {
                return quads;
            }

            List<BakedQuad> transformed = new ArrayList<>(quads.size());
            for (BakedQuad quad : quads) {
                transformed.add(leafQuadCache.computeIfAbsent(quad, ResponsiveFoliageModel::transformLeafQuad));
            }

            return transformed;
        }

        if (!extraData.has(FoliageModelData.COLUMN_SEGMENT)) {
            return quads;
        }

        FoliageModelData.ColumnSegment segment = extraData.get(FoliageModelData.COLUMN_SEGMENT);
        int swayStartHeightBits = Float.floatToIntBits(plantSwayStartHeight());
        List<BakedQuad> transformed = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            PlantQuadKey key = new PlantQuadKey(quad, segment.offset(), segment.height(), swayStartHeightBits, windExposed);
            transformed.add(plantQuadCache.computeIfAbsent(key, ResponsiveFoliageModel::transformPlantQuad));
        }

        return transformed;
    }

    private static BakedQuad transformPlantQuad(PlantQuadKey key) {
        return transformPlantQuad(key.quad(), key.segmentOffset(), key.segmentHeight(), Float.intBitsToFloat(key.swayStartHeightBits()), key.windExposed());
    }

    private static BakedQuad transformPlantQuad(BakedQuad quad, int segmentOffset, int segmentHeight, float swayStartHeight, boolean windExposed) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float columnY = segmentOffset + y;
            float windWeight = windExposed ? plantBendWeight(columnY, segmentHeight, swayStartHeight) : 0.0F;
            float interactionWeight = plantBendWeight(columnY, segmentHeight, INTERACTION_BASE_THRESHOLD);
            if (windWeight <= 0.0F && interactionWeight <= 0.0F) {
                continue;
            }

            vertices[offset + 3] = packPlantAlpha(vertices[offset + 3], windWeight, interactionWeight);
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
            vertices[offset + 3] = packLeafWindAlpha(vertices[offset + 3], bendWeight);
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

    private static float plantBendWeight(float columnY, float columnHeight, float startHeight) {
        float bendHeightRange = Math.max(columnHeight - startHeight, MIN_PLANT_SWAY_HEIGHT_RANGE);
        return Mth.clamp((columnY - startHeight) / bendHeightRange, 0.0F, 1.0F);
    }

    private static int packPlantAlpha(int color, float windWeight, float interactionWeight) {
        int windLevel = encodeLevel(windWeight, PLANT_WIND_ALPHA_LEVELS);
        int interactionLevel = encodeLevel(interactionWeight, PLANT_INTERACTION_ALPHA_LEVELS);
        int alpha = PLANT_ALPHA_MIN + interactionLevel * PLANT_WIND_ALPHA_LEVELS + windLevel;
        return (color & 0x00FFFFFF) | (Mth.clamp(alpha, PLANT_ALPHA_MIN, PLANT_ALPHA_MAX) << 24);
    }

    private static int packLeafWindAlpha(int color, float bendWeight) {
        int alpha = Mth.clamp(
                Math.round(LEAF_WIND_ALPHA_MIN + Mth.clamp(bendWeight, 0.0F, 1.0F) * (LEAF_WIND_ALPHA_MAX - LEAF_WIND_ALPHA_MIN)),
                LEAF_WIND_ALPHA_MIN,
                LEAF_WIND_ALPHA_MAX
        );
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static int encodeLevel(float value, int levels) {
        return Mth.clamp(Math.round(Mth.clamp(value, 0.0F, 1.0F) * (levels - 1)), 0, levels - 1);
    }

    private static float plantSwayStartHeight() {
        return Mth.clamp((float) ClientConfig.WIND_PLANT_SWAY_START_HEIGHT.getAsDouble(), 0.0F, 1.0F);
    }

    private boolean isPlantFoliage() {
        return foliageType == ResponsiveFoliageType.PLANT
                || (foliageType == ResponsiveFoliageType.AUTO_PLANT && ClientConfig.ENABLE_AUTODETECTED_FOLIAGE_MODELS.getAsBoolean());
    }

    private boolean isResponsiveState(@Nullable BlockState state) {
        return state != null && (isPlantFoliage() || ResponsiveFoliage.isLeaf(state));
    }

    private static boolean windExposed(ModelData extraData) {
        return !extraData.has(FoliageModelData.WIND_EXPOSED) || Boolean.TRUE.equals(extraData.get(FoliageModelData.WIND_EXPOSED));
    }

    private record PlantQuadKey(BakedQuad quad, int segmentOffset, int segmentHeight, int swayStartHeightBits, boolean windExposed) {
    }
}
