package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.block.SnowLogging;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

final class SnowloggedFoliageModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Vec3> OFFSET = new ModelProperty<>();

    SnowloggedFoliageModel(BakedModel model) {
        super(model);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        ModelData original = originalModel.getModelData(level, pos, state, data);
        return SnowLogging.layers(state) > 0 ? original.derive().with(OFFSET, state.setValue(SnowLogging.LAYERS, 0).getOffset(level, pos)).build() : original;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        ChunkRenderTypeSet types = originalModel.getRenderTypes(state, rand, data);
        return SnowLogging.layers(state) > 0 ? ChunkRenderTypeSet.union(types, ChunkRenderTypeSet.of(RenderType.solid())) : types;
    }

    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        return SnowLogging.layers(state) > 0 && (renderType == null || renderType == RenderType.solid())
                ? TriState.TRUE : originalModel.useAmbientOcclusion(state, data, renderType);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
            ModelData data, @Nullable RenderType renderType) {
        if (state == null || SnowLogging.layers(state) == 0) {
            return originalModel.getQuads(state, side, rand, data, renderType);
        }
        List<BakedQuad> quads = new ArrayList<>();
        if (renderType == null || originalModel.getRenderTypes(state, rand, data).contains(renderType)) {
            Vec3 offset = data.get(OFFSET);
            for (BakedQuad quad : originalModel.getQuads(state, side, rand, data, renderType)) {
                quads.add(offset == null || offset.equals(Vec3.ZERO) ? quad : offsetPlant(quad, offset));
            }
        }
        if (renderType == null || renderType == RenderType.solid()) {
            BlockState snow = SnowLogging.snowState(state);
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(snow);
            quads.addAll(model.getQuads(snow, side, rand, ModelData.EMPTY, renderType));
        }
        return quads;
    }

    private static BakedQuad offsetPlant(BakedQuad quad, Vec3 offset) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int start = vertex * stride;
            vertices[start] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[start]) + (float) offset.x);
            vertices[start + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[start + 1]) + (float) offset.y);
            vertices[start + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[start + 2]) + (float) offset.z);
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
    }
}
