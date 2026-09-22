package com.jvn.wherewindsblow.client.foliage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

final class FoliageShaderSource {
    static final String UNIFORMS = load("foliage_uniforms.glsl");
    static final String FUNCTIONS = load("foliage_wind.glsl");

    private FoliageShaderSource() {
    }

    private static String load(String name) {
        String path = "/assets/where_winds_blow/shaders/include/" + name;
        try (InputStream stream = FoliageShaderSource.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing foliage shader resource: " + path);
            }
            return "\n" + new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read foliage shader resource: " + path, exception);
        }
    }
}
