package com.jvn.wherewindsblow.client.lantern;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import com.jvn.wherewindsblow.config.ClientConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

final class LanternSwayModel extends BakedModelWrapper<BakedModel> {
    private static final int LANTERN_ALPHA_MIN = 73;
    private static final int LANTERN_ALPHA_MAX = 88;
    private static final int LANTERN_ALPHA_LEVELS = LANTERN_ALPHA_MAX - LANTERN_ALPHA_MIN + 1;
    private static final float PIVOT_Y = 0.94F;
    private static final float SWAY_HEIGHT_RANGE = 0.84F;
    private final Map<BakedQuad, BakedQuad> quadCache = new ConcurrentHashMap<>();

    LanternSwayModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
        List<BakedQuad> quads = originalModel.getQuads(state, side, rand, extraData, renderType);
        if (!shouldSway(state)) {
            return quads;
        }

        List<BakedQuad> transformed = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            transformed.add(quadCache.computeIfAbsent(quad, LanternSwayModel::transformQuad));
        }

        return transformed;
    }

    private static boolean shouldSway(@Nullable BlockState state) {
        return state != null
                && state.hasProperty(LanternBlock.HANGING)
                && state.getValue(LanternBlock.HANGING)
                && ResponsiveFoliageShaders.shouldUseCustomFoliageShaders()
                && ClientConfig.ENABLE_WIND_LANTERN_SWAY.getAsBoolean();
    }

    private static BakedQuad transformQuad(BakedQuad quad) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float swayWeight = Mth.clamp((PIVOT_Y - y) / SWAY_HEIGHT_RANGE, 0.0F, 1.0F);
            vertices[offset + 3] = packLanternAlpha(vertices[offset + 3], swayWeight);
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

    private static int packLanternAlpha(int color, float swayWeight) {
        int alpha = Math.round(LANTERN_ALPHA_MIN + Mth.clamp(swayWeight, 0.0F, 1.0F) * (LANTERN_ALPHA_LEVELS - 1));
        return (color & 0x00FFFFFF) | (Mth.clamp(alpha, LANTERN_ALPHA_MIN, LANTERN_ALPHA_MAX) << 24);
    }
}
