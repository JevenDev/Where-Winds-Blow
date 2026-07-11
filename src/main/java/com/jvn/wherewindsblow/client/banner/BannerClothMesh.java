package com.jvn.wherewindsblow.client.banner;

import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.joml.Vector3f;

/** A reusable low-resolution surface matching the vanilla 20 by 40 pixel banner cloth. */
final class BannerClothMesh {
    private static final int COLUMNS = 5;
    private static final int ROWS = 10;
    private static final int STRIDE = COLUMNS + 1;
    private static final float FRONT_U_MIN = 1.0F / 64.0F;
    private static final float FRONT_U_MAX = 21.0F / 64.0F;
    private static final float BACK_U_MIN = 22.0F / 64.0F;
    private static final float BACK_U_MAX = 42.0F / 64.0F;
    private static final float V_MIN = 1.0F / 64.0F;
    private static final float V_MAX = 41.0F / 64.0F;
    private static final float HALF_THICKNESS = 0.5F;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private BannerClothMesh() {
    }

    static void renderStanding(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            DyeColor baseColor,
            BannerPatternLayers patterns,
            BannerWindStateCache.State state,
            float partialTick
    ) {
        Scratch scratch = SCRATCH.get();
        deformStanding(scratch, state, partialTick);
        renderLayers(poseStack, bufferSource, packedLight, packedOverlay, baseColor, patterns, scratch);
    }

    private static void deformStanding(Scratch scratch, BannerWindStateCache.State state, float partialTick) {
        float extension = state.extension(partialTick);
        float crosswind = state.crosswind(partialTick);
        float outward = state.outwardWind(partialTick);
        float gust = state.gust(partialTick);
        float turbulence = state.turbulence(partialTick);
        float exposure = state.exposure(partialTick);
        float time = DynamicWindManager.simulationTime();
        float phase = state.phase();
        float sagStrength = (float) ClientConfig.BANNER_SAG_STRENGTH.getAsDouble();
        float flutterStrength = (float) ClientConfig.BANNER_FLUTTER_STRENGTH.getAsDouble();
        float horizontal = Mth.clamp(extension * 0.78F, 0.0F, 0.82F);
        float verticalScale = Mth.sqrt(Math.max(0.25F, 1.0F - horizontal * horizontal));
        float directionLength = Mth.sqrt(crosswind * crosswind + outward * outward);
        float sideDirection = directionLength > 0.0001F ? crosswind / directionLength : 0.0F;
        float outwardDirection = directionLength > 0.0001F ? outward / directionLength : 1.0F;

        for (int row = 0; row <= ROWS; row++) {
            float v = row / (float) ROWS;
            float anchorWeight = v * v * (3.0F - 2.0F * v);
            float length = v * 40.0F;
            float broadWave = Mth.sin(phase + time * (1.25F + gust * 0.7F) - v * 5.4F)
                    * (0.35F + extension * 1.25F + gust * 0.28F)
                    * anchorWeight;
            float sag = v * v * (1.0F - extension * 0.68F) * 1.65F * sagStrength;

            for (int column = 0; column <= COLUMNS; column++) {
                float u = column / (float) COLUMNS;
                float xNorm = u * 2.0F - 1.0F;
                float cornerWeight = 0.35F + 0.65F * Math.abs(xNorm);
                float flutter = Mth.sin(phase * 1.7F + time * (5.8F + turbulence * 2.4F) - v * 12.0F + xNorm * 2.2F)
                        * v * v * v
                        * cornerWeight
                        * (extension * 0.42F + gust * 0.22F + turbulence * 0.5F)
                        * flutterStrength;
                float twist = crosswind * xNorm * anchorWeight * (1.15F + turbulence * 0.35F);
                int index = index(column, row);
                scratch.x[index] = -10.0F + u * 20.0F + sideDirection * length * horizontal;
                scratch.y[index] = -32.0F + length * verticalScale + sag
                        + Mth.sin(phase + time * 1.7F + xNorm * 2.4F) * anchorWeight * turbulence * 0.22F;
                scratch.z[index] = -1.5F - outwardDirection * length * horizontal
                        + broadWave + flutter + twist;
            }
        }
        calculateNormals(scratch);
    }

    private static void calculateNormals(Scratch scratch) {
        for (int row = 0; row <= ROWS; row++) {
            int rowBefore = Math.max(0, row - 1);
            int rowAfter = Math.min(ROWS, row + 1);
            for (int column = 0; column <= COLUMNS; column++) {
                int columnBefore = Math.max(0, column - 1);
                int columnAfter = Math.min(COLUMNS, column + 1);
                int left = index(columnBefore, row);
                int right = index(columnAfter, row);
                int above = index(column, rowBefore);
                int below = index(column, rowAfter);
                float tx = scratch.x[right] - scratch.x[left];
                float ty = scratch.y[right] - scratch.y[left];
                float tz = scratch.z[right] - scratch.z[left];
                float bx = scratch.x[below] - scratch.x[above];
                float by = scratch.y[below] - scratch.y[above];
                float bz = scratch.z[below] - scratch.z[above];
                float nx = ty * bz - tz * by;
                float ny = tz * bx - tx * bz;
                float nz = tx * by - ty * bx;
                float inverseLength = Mth.invSqrt(Math.max(0.000001F, nx * nx + ny * ny + nz * nz));
                int current = index(column, row);
                scratch.nx[current] = nx * inverseLength;
                scratch.ny[current] = ny * inverseLength;
                scratch.nz[current] = nz * inverseLength;
            }
        }
    }

    private static void renderLayers(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            DyeColor baseColor,
            BannerPatternLayers patterns,
            Scratch scratch
    ) {
        renderSurface(poseStack, ModelBakery.BANNER_BASE.buffer(bufferSource, RenderType::entitySolid),
                packedLight, packedOverlay, -1, scratch);
        renderSurface(poseStack, Sheets.BANNER_BASE.buffer(bufferSource, RenderType::entityNoOutline),
                packedLight, packedOverlay, baseColor.getTextureDiffuseColor(), scratch);

        for (int i = 0; i < 16 && i < patterns.layers().size(); i++) {
            BannerPatternLayers.Layer layer = patterns.layers().get(i);
            Material material = Sheets.getBannerMaterial(layer.pattern());
            renderSurface(poseStack, material.buffer(bufferSource, RenderType::entityNoOutline),
                    packedLight, packedOverlay, layer.color().getTextureDiffuseColor(), scratch);
        }
    }

    private static void renderSurface(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            int color,
            Scratch scratch
    ) {
        for (int row = 0; row < ROWS; row++) {
            float v0 = Mth.lerp(row / (float) ROWS, V_MIN, V_MAX);
            float v1 = Mth.lerp((row + 1) / (float) ROWS, V_MIN, V_MAX);
            for (int column = 0; column < COLUMNS; column++) {
                float u0 = column / (float) COLUMNS;
                float u1 = (column + 1) / (float) COLUMNS;
                int a = index(column, row);
                int b = index(column + 1, row);
                int c = index(column + 1, row + 1);
                int d = index(column, row + 1);
                emit(consumer, poseStack, scratch, b, -1.0F, Mth.lerp(u1, FRONT_U_MIN, FRONT_U_MAX), v0,
                        color, packedLight, packedOverlay);
                emit(consumer, poseStack, scratch, a, -1.0F, Mth.lerp(u0, FRONT_U_MIN, FRONT_U_MAX), v0,
                        color, packedLight, packedOverlay);
                emit(consumer, poseStack, scratch, d, -1.0F, Mth.lerp(u0, FRONT_U_MIN, FRONT_U_MAX), v1,
                        color, packedLight, packedOverlay);
                emit(consumer, poseStack, scratch, c, -1.0F, Mth.lerp(u1, FRONT_U_MIN, FRONT_U_MAX), v1,
                        color, packedLight, packedOverlay);

                emit(consumer, poseStack, scratch, a, 1.0F, Mth.lerp(u0, BACK_U_MAX, BACK_U_MIN), v0,
                        color, packedLight, packedOverlay);
                emit(consumer, poseStack, scratch, b, 1.0F, Mth.lerp(u1, BACK_U_MAX, BACK_U_MIN), v0,
                        color, packedLight, packedOverlay);
                emit(consumer, poseStack, scratch, c, 1.0F, Mth.lerp(u1, BACK_U_MAX, BACK_U_MIN), v1,
                        color, packedLight, packedOverlay);
                emit(consumer, poseStack, scratch, d, 1.0F, Mth.lerp(u0, BACK_U_MAX, BACK_U_MIN), v1,
                        color, packedLight, packedOverlay);
            }
        }
    }

    private static void emit(
            VertexConsumer consumer,
            PoseStack poseStack,
            Scratch scratch,
            int index,
            float normalDirection,
            float u,
            float v,
            int color,
            int packedLight,
            int packedOverlay
    ) {
        float nx = scratch.nx[index] * normalDirection;
        float ny = scratch.ny[index] * normalDirection;
        float nz = scratch.nz[index] * normalDirection;
        float offset = HALF_THICKNESS * normalDirection;
        poseStack.last().pose().transformPosition(
                (scratch.x[index] + scratch.nx[index] * offset) / 16.0F,
                (scratch.y[index] + scratch.ny[index] * offset) / 16.0F,
                (scratch.z[index] + scratch.nz[index] * offset) / 16.0F,
                scratch.position
        );
        poseStack.last().transformNormal(nx, ny, nz, scratch.normal);
        consumer.addVertex(
                scratch.position.x(), scratch.position.y(), scratch.position.z(), color, u, v,
                packedOverlay, packedLight, scratch.normal.x(), scratch.normal.y(), scratch.normal.z()
        );
    }

    private static int index(int column, int row) {
        return row * STRIDE + column;
    }

    private static final class Scratch {
        private final float[] x = new float[(COLUMNS + 1) * (ROWS + 1)];
        private final float[] y = new float[x.length];
        private final float[] z = new float[x.length];
        private final float[] nx = new float[x.length];
        private final float[] ny = new float[x.length];
        private final float[] nz = new float[x.length];
        private final Vector3f position = new Vector3f();
        private final Vector3f normal = new Vector3f();
    }
}
