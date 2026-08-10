package com.jvn.wherewindsblow.client.foliage;

import com.jvn.toucanlib.client.ToucanEasing;
import com.jvn.toucanlib.util.ToucanBoundedCache;
import com.jvn.wherewindsblow.config.ClientConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private static final float TALL_INTERACTION_DAMP_START_HEIGHT = 2.0F;
    private static final float TALL_INTERACTION_DAMPING_SCALE = 1.9F;
    private static final float TALL_INTERACTION_MIN_DAMPING = 0.34F;
    private static final float TALL_INTERACTION_MAX_DAMPING = 0.68F;
    private static final int PLANT_ALPHA_MIN = 17;
    private static final int PLANT_ALPHA_MAX = 44;
    private static final int PLANT_WIND_ALPHA_LEVELS = 7;
    private static final int PLANT_INTERACTION_ALPHA_LEVELS = 4;
    private static final int LEAF_WIND_ALPHA_MIN = 45;
    private static final int LEAF_WIND_ALPHA_MAX = 72;
    private static final float LEAF_BEND_MIN = 0.10F;
    private static final float LEAF_BEND_MAX = 0.36F;
    private static final int MAX_LEAF_QUAD_CACHE_ENTRIES = 128;
    private static final int MAX_PLANT_QUAD_CACHE_ENTRIES = 1024;
    @Nullable
    private volatile Map<LeafQuadKey, BakedQuad> leafQuadCache;
    @Nullable
    private volatile Map<PlantQuadKey, BakedQuad> plantQuadCache;

    ResponsiveFoliageModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        ModelData originalData = originalModel.getModelData(level, pos, state, modelData);
        FoliageSwayProfile profile = FoliageSwayProfiles.resolve(state);
        if (profile == null) {
            return originalData;
        }

        boolean encodeVertexMarkers = ResponsiveFoliageShaders.shouldEncodeFoliageVertexMarkers();
        boolean plantFoliage = profile.type().isPlant();
        FoliageModelData.InteractionImpulse interactionImpulse = plantFoliage && profile.interactive() && !encodeVertexMarkers
                ? ResponsiveFoliagePhysics.interactionAt(pos)
                : null;
        if (!encodeVertexMarkers && interactionImpulse == null) {
            return originalData;
        }

        var builder = originalData.derive();
        if (encodeVertexMarkers) {
            builder.with(FoliageModelData.WIND_EXPOSURE, quantizeExposure(ResponsiveFoliage.windExposure(level, pos)));
        }
        if (plantFoliage) {
            builder.with(FoliageModelData.COLUMN_SEGMENT, ResponsiveFoliage.columnSegment(level, pos, state, profile));
            if (interactionImpulse != null) {
                builder.with(FoliageModelData.INTERACTION_IMPULSE, interactionImpulse);
            }
        }

        return builder.build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
        List<BakedQuad> quads = originalModel.getQuads(state, side, rand, extraData, renderType);
        if (state == null) {
            return quads;
        }
        FoliageSwayProfile profile = FoliageSwayProfiles.resolve(state);
        if (profile == null) {
            return quads;
        }

        boolean encodeVertexMarkers = ResponsiveFoliageShaders.shouldEncodeFoliageVertexMarkers();
        FoliageModelData.InteractionImpulse interactionImpulse = interactionImpulse(extraData);
        if (!encodeVertexMarkers && interactionImpulse == null) {
            return quads;
        }

        float windExposure = encodeVertexMarkers ? windExposure(extraData) : 0.0F;
        if (profile.type().isLeaves()) {
            if (!encodeVertexMarkers || windExposure <= 0.0F) {
                return quads;
            }

            List<BakedQuad> transformed = new ArrayList<>(quads.size());
            float leafWindExposure = windExposure * profile.swayStrengthMultiplier();
            for (BakedQuad quad : quads) {
                transformed.add(leafQuadCache().computeIfAbsent(
                        new LeafQuadKey(quad, Float.floatToIntBits(leafWindExposure)),
                        ResponsiveFoliageModel::transformLeafQuad
                ));
            }

            return transformed;
        }

        if (!extraData.has(FoliageModelData.COLUMN_SEGMENT)) {
            return quads;
        }
        boolean encodeInteractionMarker = encodeVertexMarkers
                && profile.interactive()
                && ClientConfig.ENABLE_FOLIAGE_INTERACTIVITY.getAsBoolean();
        if (windExposure <= 0.0F && !encodeInteractionMarker && interactionImpulse == null) {
            return quads;
        }

        FoliageModelData.ColumnSegment segment = extraData.get(FoliageModelData.COLUMN_SEGMENT);
        int swayStartHeightBits = Float.floatToIntBits(
                plantSwayStartHeight() * profile.swayStartHeightMultiplier()
        );
        int swayStrengthBits = Float.floatToIntBits(profile.swayStrengthMultiplier());
        int interactionStrengthBits = Float.floatToIntBits(profile.interactionStrengthMultiplier());
        List<BakedQuad> transformed = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            if (interactionImpulse != null) {
                transformed.add(transformPlantQuad(
                        quad,
                        segment.offset(),
                        segment.height(),
                        segment.hangsFromTop(),
                        segment.localHeightScale(),
                        profile.interactionHeightScaleMultiplier(),
                        Float.intBitsToFloat(swayStartHeightBits),
                        windExposure * Float.intBitsToFloat(swayStrengthBits),
                        Float.intBitsToFloat(interactionStrengthBits),
                        false,
                        interactionImpulse
                ));
            } else {
                PlantQuadKey key = new PlantQuadKey(
                        quad,
                        segment.offset(),
                        segment.height(),
                        segment.hangsFromTop(),
                        Float.floatToIntBits(segment.localHeightScale()),
                        Float.floatToIntBits(profile.interactionHeightScaleMultiplier()),
                        swayStartHeightBits,
                        Float.floatToIntBits(windExposure),
                        swayStrengthBits,
                        interactionStrengthBits,
                        encodeInteractionMarker
                );
                transformed.add(plantQuadCache().computeIfAbsent(key, ResponsiveFoliageModel::transformPlantQuad));
            }
        }

        return transformed;
    }

    private static BakedQuad transformPlantQuad(PlantQuadKey key) {
        return transformPlantQuad(
                key.quad(),
                key.segmentOffset(),
                key.segmentHeight(),
                key.hangsFromTop(),
                Float.intBitsToFloat(key.localHeightScaleBits()),
                Float.intBitsToFloat(key.interactionHeightScaleMultiplierBits()),
                Float.intBitsToFloat(key.swayStartHeightBits()),
                Float.intBitsToFloat(key.windExposureBits()) * Float.intBitsToFloat(key.swayStrengthBits()),
                Float.intBitsToFloat(key.interactionStrengthBits()),
                key.encodeInteractionMarker(),
                null
        );
    }

    private static BakedQuad transformPlantQuad(
            BakedQuad quad,
            int segmentOffset,
            int segmentHeight,
            boolean hangsFromTop,
            float localHeightScale,
            float interactionHeightScaleMultiplier,
            float swayStartHeight,
            float windExposure,
            float interactionStrength,
            boolean encodeInteractionMarker,
            @Nullable FoliageModelData.InteractionImpulse interactionImpulse
    ) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        boolean hasInteraction = interactionImpulse != null && interactionImpulse.isVisible();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float x = Float.intBitsToFloat(vertices[offset]);
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float z = Float.intBitsToFloat(vertices[offset + 2]);
            float distanceFromAnchor = hangsFromTop ? 1.0F - y : y;
            float windColumnY = segmentOffset + distanceFromAnchor * localHeightScale;
            float interactionColumnY = segmentOffset
                    + distanceFromAnchor * localHeightScale * interactionHeightScaleMultiplier;
            float windWeight = plantBendWeight(windColumnY, segmentHeight, swayStartHeight) * windExposure;
            float interactionWeight = Mth.clamp(
                    plantInteractionWeight(interactionColumnY, segmentHeight) * interactionStrength,
                    0.0F,
                    1.0F
            );
            float bakedInteractionWeight = hasInteraction ? ToucanEasing.smoothstep(interactionWeight) : 0.0F;
            float markerInteractionWeight = encodeInteractionMarker ? interactionWeight : 0.0F;
            if (windWeight <= 0.0F && bakedInteractionWeight <= 0.0F && markerInteractionWeight <= 0.0F) {
                continue;
            }

            if (bakedInteractionWeight > 0.0F) {
                vertices[offset] = Float.floatToRawIntBits(x + interactionImpulse.offsetX() * bakedInteractionWeight);
                vertices[offset + 2] = Float.floatToRawIntBits(z + interactionImpulse.offsetZ() * bakedInteractionWeight);
            }
            if (windWeight > 0.0F || markerInteractionWeight > 0.0F) {
                vertices[offset + 3] = packPlantAlpha(vertices[offset + 3], windWeight, markerInteractionWeight);
            }
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

    private static BakedQuad transformLeafQuad(LeafQuadKey key) {
        BakedQuad quad = key.quad();
        float windExposure = Float.intBitsToFloat(key.windExposureBits());
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float x = Float.intBitsToFloat(vertices[offset]);
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float z = Float.intBitsToFloat(vertices[offset + 2]);
            float bendWeight = leafBendWeight(x, y, z) * windExposure;
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

    private static float plantInteractionWeight(float columnY, int columnHeight) {
        float weight = plantBendWeight(columnY, columnHeight, INTERACTION_BASE_THRESHOLD);
        if (columnHeight <= 2 || columnY <= TALL_INTERACTION_DAMP_START_HEIGHT) {
            return weight;
        }

        float tallRange = Math.max(columnHeight - TALL_INTERACTION_DAMP_START_HEIGHT, MIN_PLANT_SWAY_HEIGHT_RANGE);
        float tallProgress = ToucanEasing.smoothstep((columnY - TALL_INTERACTION_DAMP_START_HEIGHT) / tallRange);
        float topDamping = Mth.clamp(
                TALL_INTERACTION_DAMPING_SCALE / columnHeight,
                TALL_INTERACTION_MIN_DAMPING,
                TALL_INTERACTION_MAX_DAMPING
        );
        return weight * Mth.lerp(tallProgress, 1.0F, topDamping);
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

    private static float windExposure(ModelData extraData) {
        return extraData.has(FoliageModelData.WIND_EXPOSURE)
                ? Mth.clamp(extraData.get(FoliageModelData.WIND_EXPOSURE), 0.0F, 1.0F)
                : 1.0F;
    }

    private static float quantizeExposure(float exposure) {
        return Math.round(Mth.clamp(exposure, 0.0F, 1.0F) * 7.0F) / 7.0F;
    }

    private static <K, V> Map<K, V> boundedCache(int maximumEntries) {
        return Collections.synchronizedMap(new ToucanBoundedCache<>(64, maximumEntries));
    }

    private Map<LeafQuadKey, BakedQuad> leafQuadCache() {
        Map<LeafQuadKey, BakedQuad> cache = leafQuadCache;
        if (cache == null) {
            synchronized (this) {
                cache = leafQuadCache;
                if (cache == null) {
                    cache = boundedCache(MAX_LEAF_QUAD_CACHE_ENTRIES);
                    leafQuadCache = cache;
                }
            }
        }
        return cache;
    }

    private Map<PlantQuadKey, BakedQuad> plantQuadCache() {
        Map<PlantQuadKey, BakedQuad> cache = plantQuadCache;
        if (cache == null) {
            synchronized (this) {
                cache = plantQuadCache;
                if (cache == null) {
                    cache = boundedCache(MAX_PLANT_QUAD_CACHE_ENTRIES);
                    plantQuadCache = cache;
                }
            }
        }
        return cache;
    }

    @Nullable
    private static FoliageModelData.InteractionImpulse interactionImpulse(ModelData extraData) {
        return extraData.has(FoliageModelData.INTERACTION_IMPULSE)
                ? extraData.get(FoliageModelData.INTERACTION_IMPULSE)
                : null;
    }

    private record LeafQuadKey(BakedQuad quad, int windExposureBits) {
    }

    private record PlantQuadKey(
            BakedQuad quad,
            int segmentOffset,
            int segmentHeight,
            boolean hangsFromTop,
            int localHeightScaleBits,
            int interactionHeightScaleMultiplierBits,
            int swayStartHeightBits,
            int windExposureBits,
            int swayStrengthBits,
            int interactionStrengthBits,
            boolean encodeInteractionMarker
    ) {
    }
}
