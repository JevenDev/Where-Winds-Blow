package com.jvn.wherewindsblow.client.lantern;

import com.jvn.toucanlib.client.ToucanEasing;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliage;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

final class LanternSwayModel extends BakedModelWrapper<BakedModel> {
    private static final int LANTERN_ALPHA_MIN = 73;
    private static final int LANTERN_ALPHA_MAX = 88;
    private static final int CHAIN_ALPHA_MIN = 89;
    private static final int CHAIN_ALPHA_MAX = 104;
    private static final int CHAIN_ALPHA_LEVELS = CHAIN_ALPHA_MAX - CHAIN_ALPHA_MIN + 1;
    private static final float CHAIN_BOTTOM_ENDPOINT_MARGIN = 0.1875F;
    private static final float ENCLOSED_SWAY_SCALE = 0.12F;
    private final Subject subject;
    private final Map<LanternQuadKey, BakedQuad> lanternQuadCache = new ConcurrentHashMap<>();
    private final Map<ChainQuadKey, BakedQuad> chainQuadCache = new ConcurrentHashMap<>();

    LanternSwayModel(BakedModel originalModel, Subject subject) {
        super(originalModel);
        this.subject = subject;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        ModelData originalData = originalModel.getModelData(level, pos, state, modelData);
        if (!ResponsiveFoliageShaders.shouldUseCustomFoliageShaders()) {
            return originalData;
        }

        if (subject == Subject.LANTERN && isHangingLantern(state)) {
            return originalData.derive()
                    .with(LanternModelData.LANTERN_CHAIN_ATTACHED, hasVerticalChainAbove(level, pos))
                    .with(LanternModelData.WIND_EXPOSURE, quantizeExposure(ResponsiveFoliage.windExposure(level, pos)))
                    .build();
        }

        if (subject == Subject.CHAIN && isVerticalChain(state)) {
            return originalData.derive()
                    .with(LanternModelData.CHAIN_SEGMENT, chainSegment(level, pos))
                    .build();
        }

        return originalData;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
        List<BakedQuad> quads = originalModel.getQuads(state, side, rand, extraData, renderType);
        if (Boolean.TRUE.equals(extraData.get(LanternModelData.RENDERING_ASSEMBLY))) {
            return quads;
        }

        if (!shouldSway(state)) {
            return quads;
        }

        List<BakedQuad> transformed = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            transformed.add(switch (subject) {
                case LANTERN -> {
                    if (Boolean.TRUE.equals(extraData.get(LanternModelData.LANTERN_CHAIN_ATTACHED))) {
                        yield null;
                    }

                    yield lanternQuadCache.computeIfAbsent(
                            new LanternQuadKey(quad, Float.floatToIntBits(windExposure(extraData))),
                            LanternSwayModel::transformLanternQuad
                    );
                }
                case CHAIN -> {
                    LanternModelData.ChainSegment segment = extraData.get(LanternModelData.CHAIN_SEGMENT);
                    if (segment == null
                            || SwingingLanternAssemblyRenderer.ownsChainAssembly(segment)) {
                        yield null;
                    }

                    if (!segment.hangingLanternAttached()
                            && !ClientConfig.ENABLE_STANDALONE_CHAIN_SWAY.getAsBoolean()) {
                        yield quad;
                    }

                    yield chainQuadCache.computeIfAbsent(
                            new ChainQuadKey(
                                    quad,
                                    segment.offsetFromTop(),
                                    segment.height(),
                                    segment.hangingLanternAttached(),
                                    Float.floatToIntBits(quantizeExposure(segment.windExposure()))
                            ),
                            LanternSwayModel::transformChainQuad
                    );
                }
            });
        }

        transformed.removeIf(java.util.Objects::isNull);
        return transformed;
    }

    private boolean shouldSway(@Nullable BlockState state) {
        return state != null
                && (switch (subject) {
                    case LANTERN -> state.hasProperty(LanternBlock.HANGING)
                            && state.getValue(LanternBlock.HANGING);
                    case CHAIN -> isVerticalChain(state);
                })
                && ResponsiveFoliageShaders.shouldUseCustomFoliageShaders()
                && ClientConfig.ENABLE_WIND_LANTERN_SWAY.getAsBoolean();
    }

    private static BakedQuad transformLanternQuad(LanternQuadKey key) {
        BakedQuad quad = key.quad();
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            vertices[offset + 3] = packLanternAlpha(
                    vertices[offset + 3],
                    Float.intBitsToFloat(key.windExposureBits())
            );
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

    private static BakedQuad transformChainQuad(ChainQuadKey key) {
        BakedQuad quad = key.quad();
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        float exposureScale = windExposureScale(Float.intBitsToFloat(key.windExposureBits()));
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float distanceFromTop = key.offsetFromTop() + (1.0F - y);
            float effectiveHeight = Math.max(0.001F, key.height() - (key.hangingLanternAttached() ? CHAIN_BOTTOM_ENDPOINT_MARGIN : 0.0F));
            float progress = Mth.clamp(distanceFromTop / effectiveHeight, 0.0F, 1.0F);
            float longStack = Mth.clamp((key.height() - 3.0F) / 8.0F, 0.0F, 1.0F);
            float bendExponent = Mth.lerp(longStack, 1.45F, 1.05F);
            float swayWeight = (float) Math.pow(ToucanEasing.smoothstep(progress), bendExponent);
            vertices[offset + 3] = packChainAlpha(vertices[offset + 3], swayWeight * exposureScale);
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

    private static LanternModelData.ChainSegment chainSegment(BlockAndTintGetter level, BlockPos pos) {
        BlockPos top = pos;
        while (isVerticalChain(level.getBlockState(top.above()))) {
            top = top.above();
        }

        int height = 0;
        while (isVerticalChain(level.getBlockState(top.below(height)))) {
            height++;
        }

        return new LanternModelData.ChainSegment(
                top,
                top.getY() - pos.getY(),
                height,
                hasHangingLanternBelow(level, top, height),
                ResponsiveFoliage.windExposure(level, pos)
        );
    }

    private static boolean isVerticalChain(@Nullable BlockState state) {
        return state != null
                && state.is(Blocks.CHAIN)
                && state.hasProperty(ChainBlock.AXIS)
                && state.getValue(ChainBlock.AXIS) == Direction.Axis.Y;
    }

    private static boolean isHangingLantern(@Nullable BlockState state) {
        return state != null
                && (state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN))
                && state.hasProperty(LanternBlock.HANGING)
                && state.getValue(LanternBlock.HANGING);
    }

    private static boolean hasVerticalChainAbove(BlockAndTintGetter level, BlockPos pos) {
        return isVerticalChain(level.getBlockState(pos.above()));
    }

    private static boolean hasHangingLanternBelow(BlockAndTintGetter level, BlockPos top, int chainHeight) {
        BlockState state = level.getBlockState(top.below(chainHeight));
        return (state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN))
                && state.hasProperty(LanternBlock.HANGING)
                && state.getValue(LanternBlock.HANGING);
    }

    private static int packLanternAlpha(int color, float windExposure) {
        int alpha = Math.round(LANTERN_ALPHA_MIN + windExposureScale(windExposure) * (LANTERN_ALPHA_MAX - LANTERN_ALPHA_MIN));
        return (color & 0x00FFFFFF) | (Mth.clamp(alpha, LANTERN_ALPHA_MIN, LANTERN_ALPHA_MAX) << 24);
    }

    private static int packChainAlpha(int color, float swayWeight) {
        int alpha = Math.round(CHAIN_ALPHA_MIN + Mth.clamp(swayWeight, 0.0F, 1.0F) * (CHAIN_ALPHA_LEVELS - 1));
        return (color & 0x00FFFFFF) | (Mth.clamp(alpha, CHAIN_ALPHA_MIN, CHAIN_ALPHA_MAX) << 24);
    }

    private static float windExposure(ModelData extraData) {
        return extraData.has(LanternModelData.WIND_EXPOSURE)
                ? Mth.clamp(extraData.get(LanternModelData.WIND_EXPOSURE), 0.0F, 1.0F)
                : 1.0F;
    }

    private static float windExposureScale(float windExposure) {
        return Mth.lerp(Mth.clamp(windExposure, 0.0F, 1.0F), ENCLOSED_SWAY_SCALE, 1.0F);
    }

    private static float quantizeExposure(float exposure) {
        return Math.round(Mth.clamp(exposure, 0.0F, 1.0F) * 15.0F) / 15.0F;
    }

    enum Subject {
        LANTERN,
        CHAIN
    }

    private record LanternQuadKey(BakedQuad quad, int windExposureBits) {
    }

    private record ChainQuadKey(BakedQuad quad, int offsetFromTop, int height, boolean hangingLanternAttached, int windExposureBits) {
    }
}
