package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.WhereWindsBlow;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public final class SodiumFoliageShaderSource {
    public static final String SHADER_PATH = "sodium/block_layer_opaque.vsh";
    public static final ResourceLocation SHADER_LOCATION = ResourceLocation.fromNamespaceAndPath(
            WhereWindsBlow.MOD_ID,
            SHADER_PATH
    );

    private static final String RESOURCE_PATH = "/assets/" + WhereWindsBlow.MOD_ID + "/shaders/" + SHADER_PATH;
    private static Optional<String> cachedSource;

    private SodiumFoliageShaderSource() {
    }

    public static boolean shouldPatchSodiumShaders() {
        return ResponsiveFoliageShaders.shouldPatchSodiumShaders() && source().isPresent();
    }

    public static Optional<String> source() {
        Optional<String> source = cachedSource;
        if (source == null) {
            source = loadSource();
            cachedSource = source;
        }

        return source;
    }

    private static Optional<String> loadSource() {
        try (InputStream stream = SodiumFoliageShaderSource.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                ResponsiveFoliageShaders.disableSodiumShaderPatch("Missing Sodium foliage shader resource " + RESOURCE_PATH + ".", null);
                return Optional.empty();
            }

            return Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            ResponsiveFoliageShaders.disableSodiumShaderPatch("Failed to read Sodium foliage shader resource " + RESOURCE_PATH + ".", exception);
            return Optional.empty();
        }
    }
}
