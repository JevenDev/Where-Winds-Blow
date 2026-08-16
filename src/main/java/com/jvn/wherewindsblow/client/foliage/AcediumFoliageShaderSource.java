package com.jvn.wherewindsblow.client.foliage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

/** Patches Acedium's mesh shaders with the same deformation used by Sodium terrain. */
public final class AcediumFoliageShaderSource {
    private static final String NVIDIUM_NAMESPACE = "nvidium";
    private static final String TERRAIN_MESH_SHADER = "terrain/mesh.glsl";
    private static final String TRANSLUCENT_TERRAIN_MESH_SHADER = "terrain/translucent/mesh.glsl";
    private static final Pattern LIGHT_SAMPLER_PATTERN = Pattern.compile(
            "(?m)^\\s*layout\\s*\\(\\s*binding\\s*=\\s*1\\s*\\)\\s*uniform\\s+sampler2D\\s+tex_light\\s*;.*$"
    );
    private static final Pattern EMIT_VERTEX_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:Vertex|void)\\s+emitVertex\\s*\\(.*$"
    );
    private static final Pattern POSITION_PATTERN = Pattern.compile(
            "(?m)^\\s*vec3\\s+pos\\s*=\\s*decodeVertexPosition\\s*\\(\\s*V\\s*\\)\\s*\\+\\s*[^;]+;.*$"
    );
    private static final Pattern TINT_PATTERN = Pattern.compile(
            "(?m)^\\s*tint\\s*\\*=\\s*tint\\.w\\s*;.*$"
    );
    private static final Pattern LIGHT_COORDINATE_PATTERN = Pattern.compile(
            "return\\s+vec2\\s*\\(\\s*light\\s*\\)\\s*/\\s*256\\.0\\s*;"
    );

    private static final String ACEDIUM_VERTEX_HELPERS = """

            uint _material_params;

            float wwb_decode_acedium_marker(Vertex vertex) {
                uint packedMaterial = (vertex.y >> 16u) & 0xFFu;
                uint packedBlockLight = (vertex.y >> 24u) & 0xFFu;
                uint marker = ((packedMaterial >> 3u) & 0x1Fu) | ((packedBlockLight & 0x7u) << 5u);
                return float(marker) / 255.0;
            }
            """;

    private static final String WIND_POSITION_INJECTION = """
                _material_params = decodeVertexMaterial(V);
                float wwbFoliageAlpha = wwb_decode_acedium_marker(V);
                vec3 wwbDecodedPosition = decodeVertexPosition(V);
                pos += subchunkOffset.xyz;
                float wwbLocalGustStrength = 0.0;
                float wwbLocalTurbulence = u_WwbWindTurbulence;
                float wwbGustLeadingEdge = 0.0;
                vec2 wwbLocalWindDirection = normalize(u_WwbWindDirection);
                if (wwb_should_apply_foliage_wind(wwbFoliageAlpha)) {
                    vec2 wwbMotionPosition = wwb_foliage_motion_position(pos, wwbFoliageAlpha);
                    wwbLocalWindDirection = wwb_sample_dynamic_wind(
                            wwbMotionPosition,
                            wwbLocalGustStrength,
                            wwbLocalTurbulence,
                            wwbGustLeadingEdge
                    );
                }
                float wwbWindSheen = wwb_foliage_wind_sheen(
                        pos,
                        wwbFoliageAlpha,
                        wwbLocalWindDirection,
                        wwbLocalGustStrength,
                        wwbLocalTurbulence,
                        wwbGustLeadingEdge
                );
                vec2 wwbInteractionSample = pos.xz;
                vec2 wwbPlantAnchor = floor(wwbDecodedPosition.xz) + vec2(0.5) + (pos.xz - wwbDecodedPosition.xz);
                vec3 wwbFoliageBase = pos;
                pos = wwb_apply_foliage_wind(
                        pos,
                        wwbFoliageAlpha,
                        wwbLocalWindDirection,
                        wwbLocalGustStrength,
                        wwbLocalTurbulence,
                        wwbGustLeadingEdge
                );
                pos = wwb_apply_lantern_wind(pos, wwbFoliageAlpha);
                pos = wwb_apply_foliage_interaction(pos, wwbFoliageBase, wwbInteractionSample, wwbFoliageAlpha);
                pos -= subchunkOffset.xyz;""";

    private static final String WIND_COLOR_INJECTION = """
                if (wwb_is_plant_wind_vertex(wwbFoliageAlpha) && u_WwbFoliageColorVariationStrength > 0.0) {
                    float wwbVariation = clamp(
                            wwb_foliage_color_variation(wwbPlantAnchor + u_WwbCameraPosition.xz) * u_WwbFoliageColorVariationStrength,
                            -0.26,
                            0.32
                    );
                    float wwbLuminance = dot(tint.rgb, vec3(0.2126, 0.7152, 0.0722));
                    tint.rgb = mix(vec3(wwbLuminance), tint.rgb, clamp(1.0 + wwbVariation * 1.35, 0.70, 1.34));
                    tint.rgb *= 1.0 + wwbVariation * 0.82;
                }
                float wwbLightLevel = clamp(max(max(tint.r, tint.g), tint.b), 0.0, 1.0);
                float wwbSheen = clamp(wwbWindSheen, 0.0, 0.32);
                vec3 wwbSheenTarget = tint.rgb * vec3(1.42, 1.54, 1.30) + vec3(0.025, 0.060, 0.012) * wwbLightLevel;
                tint.rgb = mix(tint.rgb, wwbSheenTarget, wwbSheen);""";

    private AcediumFoliageShaderSource() {
    }

    public static boolean isTerrainMeshShader(ResourceLocation name) {
        if (!NVIDIUM_NAMESPACE.equals(name.getNamespace())) {
            return false;
        }

        return TERRAIN_MESH_SHADER.equals(name.getPath())
                || TRANSLUCENT_TERRAIN_MESH_SHADER.equals(name.getPath());
    }

    public static String patch(ResourceLocation name, String source) {
        if (source == null || source.isBlank() || source.contains("wwb_decode_acedium_marker")) {
            return source;
        }

        if (!hasRequiredMarkers(source)) {
            ResponsiveFoliageShaders.disableAcediumShaderPatch(
                    "Could not patch Acedium foliage shader " + name + "; expected source markers were missing.",
                    null
            );
            return source;
        }

        String patched = LIGHT_COORDINATE_PATTERN.matcher(source).replaceFirst(
                "return vec2(light & uvec2(0xF8u, 0xFFu)) / 256.0;"
        );
        patched = insertAfter(patched, LIGHT_SAMPLER_PATTERN, SodiumFoliageShaderSource.WIND_UNIFORMS);
        patched = insertBefore(
                patched,
                EMIT_VERTEX_PATTERN,
                ACEDIUM_VERTEX_HELPERS + SodiumFoliageShaderSource.WIND_FUNCTIONS
        );
        patched = insertAfter(patched, POSITION_PATTERN, WIND_POSITION_INJECTION);
        return insertAfter(patched, TINT_PATTERN, WIND_COLOR_INJECTION);
    }

    private static boolean hasRequiredMarkers(String source) {
        return LIGHT_SAMPLER_PATTERN.matcher(source).find()
                && EMIT_VERTEX_PATTERN.matcher(source).find()
                && POSITION_PATTERN.matcher(source).find()
                && TINT_PATTERN.matcher(source).find()
                && LIGHT_COORDINATE_PATTERN.matcher(source).find()
                && source.contains("decodeVertexMaterial")
                && source.contains("decodeVertexPosition");
    }

    private static String insertAfter(String source, Pattern marker, String addition) {
        Matcher matcher = marker.matcher(source);
        if (!matcher.find()) {
            return source;
        }

        int lineEnd = source.indexOf('\n', matcher.end());
        int insertion = lineEnd < 0 ? matcher.end() : lineEnd;
        return source.substring(0, insertion) + addition + source.substring(insertion);
    }

    private static String insertBefore(String source, Pattern marker, String addition) {
        Matcher matcher = marker.matcher(source);
        if (!matcher.find()) {
            return source;
        }

        return source.substring(0, matcher.start()) + addition + source.substring(matcher.start());
    }
}
