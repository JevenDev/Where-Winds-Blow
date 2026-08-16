package com.jvn.wherewindsblow.mixin.client.compat.acedium;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.ColorU8;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.minecraft.util.Mth;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/** Preserves WWB's alpha marker in bits unused by Acedium 0.4.1's compact format. */
@Mixin(targets = "me.cortex.nvidium.sodiumCompat.NvidiumCompactChunkVertex", remap = false)
public abstract class AcediumCompactChunkVertexMixin {
    @Unique
    private static final int wherewindsblow$positionMaxValue = 65536;
    @Unique
    private static final int wherewindsblow$textureMaxValue = 32768;
    @Unique
    private static final float wherewindsblow$modelOrigin = 8.0F;
    @Unique
    private static final float wherewindsblow$modelRange = 32.0F;

    /**
     * @author jvn
     * @reason Acedium discards color alpha, which WWB uses as a per-vertex animation marker.
     */
    @Overwrite
    public ChunkVertexEncoder getEncoder() {
        return (ptr, material, vertices, sectionIndex) -> {
            boolean encodeMarker = ResponsiveFoliageShaders.shouldPatchAcediumShaders();
            float centerU = 0.0F;
            float centerV = 0.0F;
            for (ChunkVertexEncoder.Vertex vertex : vertices) {
                centerU += vertex.u;
                centerV += vertex.v;
            }
            centerU *= 0.25F;
            centerV *= 0.25F;

            for (ChunkVertexEncoder.Vertex vertex : vertices) {
                int marker = ColorABGR.unpackAlpha(vertex.color);
                int light = wherewindsblow$compactLight(vertex.light);
                int blockLightWithMarker = encodeMarker
                        ? (light & 0xF8) | ((marker >>> 5) & 0x07)
                        : light & 0xFF;
                int packedMaterial = encodeMarker
                        ? (material & 0x07) | ((marker & 0x1F) << 3)
                        : material & 0xFF;
                int u = wherewindsblow$encodeTexture(centerU, vertex.u);
                int v = wherewindsblow$encodeTexture(centerV, vertex.v);

                MemoryUtil.memPutInt(ptr, wherewindsblow$encodePosition(vertex.x)
                        | (wherewindsblow$encodePosition(vertex.y) << 16));
                MemoryUtil.memPutInt(ptr + 4, wherewindsblow$encodePosition(vertex.z)
                        | (packedMaterial << 16)
                        | (blockLightWithMarker << 24));
                MemoryUtil.memPutInt(ptr + 8, wherewindsblow$encodeColor(vertex.color, vertex.ao)
                        | (((light >>> 8) & 0xFF) << 24));
                MemoryUtil.memPutInt(ptr + 12, wherewindsblow$packTexture(u, v));
                ptr += 16;
            }

            return ptr;
        };
    }

    @Unique
    private static int wherewindsblow$compactLight(int light) {
        int sky = Mth.clamp((light >>> 16) & 0xFF, 8, 248);
        int block = Mth.clamp(light & 0xFF, 8, 248);
        return block | (sky << 8);
    }

    @Unique
    private static int wherewindsblow$encodePosition(float value) {
        return (int) (((wherewindsblow$modelOrigin + value) / wherewindsblow$modelRange)
                * wherewindsblow$positionMaxValue);
    }

    @Unique
    private static int wherewindsblow$encodeColor(int color, float brightness) {
        int red = ColorU8.normalizedFloatToByte(ColorU8.byteToNormalizedFloat(ColorABGR.unpackRed(color)) * brightness);
        int green = ColorU8.normalizedFloatToByte(ColorU8.byteToNormalizedFloat(ColorABGR.unpackGreen(color)) * brightness);
        int blue = ColorU8.normalizedFloatToByte(ColorU8.byteToNormalizedFloat(ColorABGR.unpackBlue(color)) * brightness);
        return ColorABGR.pack(red, green, blue, 0);
    }

    @Unique
    private static int wherewindsblow$encodeTexture(float center, float value) {
        int bias = value < center ? 1 : -1;
        int quantized = Math.round(value * wherewindsblow$textureMaxValue) + bias;
        return (quantized & 0x7FFF) | ((bias >>> 31) << 15);
    }

    @Unique
    private static int wherewindsblow$packTexture(int u, int v) {
        return (u & 0xFFFF) | ((v & 0xFFFF) << 16);
    }
}
