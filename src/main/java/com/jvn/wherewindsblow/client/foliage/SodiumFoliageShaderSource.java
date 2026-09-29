package com.jvn.wherewindsblow.client.foliage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

public final class SodiumFoliageShaderSource {
    private static final String SODIUM_NAMESPACE = "sodium";
    private static final String SODIUM_TERRAIN_VERTEX_SHADER = "blocks/block_layer_opaque.vsh";
    private static final String VERTEX_SHADER_EXTENSION = ".vsh";
    private static final Pattern MAIN_METHOD_PATTERN = Pattern.compile("(?m)^\\s*void\\s+main\\s*\\(\\s*\\)\\s*\\{.*$");
    private static final Pattern LIGHT_SAMPLER_PATTERN = Pattern.compile("(?m)^\\s*uniform\\s+sampler2D\\s+u_LightTex\\s*;.*$");
    private static final Pattern POSITION_LINE_PATTERN = Pattern.compile("(?m)^\\s*vec3\\s+position\\s*=\\s*_vert_position\\s*\\+\\s*[^;]+;.*$");
    private static final Pattern COLOR_LINE_PATTERN = Pattern.compile("(?m)^\\s*v_Color\\s*=\\s*_vert_color\\s*\\*\\s*texture\\s*\\(\\s*u_LightTex\\s*,\\s*_vert_tex_light_coord\\s*\\)\\s*;.*$");
    private static final Pattern SHINE_BASE_COLOR_LINE_PATTERN = Pattern.compile("(?m)^\\s*v_Color\\s*=\\s*_vert_color\\s*\\*\\s*texture\\s*\\(\\s*u_LightTex\\s*,\\s*shineLightCoord\\s*\\)\\s*;.*$");
    private static final Pattern SHINE_COLORED_LIGHT_LINE_PATTERN = Pattern.compile("(?m)^\\s*v_Color\\s*=\\s*shine_apply_colored_light\\s*\\(\\s*position\\s*,\\s*v_Color\\s*,\\s*shineLightCoord\\s*\\)\\s*;.*$");

    static final String WIND_FUNCTIONS = "#define WWB_HAS_FOLIAGE_MATERIAL (((_material_params >> 1u) & 3u) != 0u)\n"
            + FoliageShaderSource.FUNCTIONS;

    private static final String WIND_POSITION_INJECTION = """
                float wwbFoliageAlpha = _vert_color.a;
                float wwbLocalGustStrength = 0.0;
                float wwbLocalTurbulence = u_WwbWindTurbulence;
                float wwbGustLeadingEdge = 0.0;
                vec2 wwbLocalWindDirection = normalize(u_WwbWindDirection);
                vec2 wwbMotionPosition = wwb_foliage_motion_position(position, wwbFoliageAlpha);
                if (wwb_should_apply_foliage_wind(wwbFoliageAlpha)) {
                    wwbLocalWindDirection = wwb_sample_dynamic_wind(
                            wwbMotionPosition,
                            wwbLocalGustStrength,
                            wwbLocalTurbulence,
                            wwbGustLeadingEdge
                    );
                }
                float wwbWindSheen = wwb_foliage_wind_sheen(
                        position,
                        wwbFoliageAlpha,
                        wwbLocalWindDirection,
                        wwbLocalGustStrength,
                        wwbLocalTurbulence,
                        wwbGustLeadingEdge
                );
                vec2 wwbInteractionAnchor = wwb_wrapped_world_delta(
                        wwbMotionPosition - u_WwbCameraPosition.xz
                );
                vec2 wwbPlantAnchor = wwbMotionPosition;
                vec3 wwbFoliageBase = position;
                position = wwb_apply_foliage_wind(
                        position,
                        wwbFoliageAlpha,
                        wwbLocalWindDirection,
                        wwbLocalGustStrength,
                        wwbLocalTurbulence,
                        wwbGustLeadingEdge
                );
                position = wwb_apply_lantern_wind(position, wwbFoliageAlpha);
                position = wwb_apply_foliage_interaction(position, wwbFoliageBase, wwbInteractionAnchor, wwbFoliageAlpha);
                position = wwb_pixel_locked_position(position, wwbFoliageBase);""";

    private static final String WIND_COLOR_INJECTION = """
                if (wwb_is_plant_wind_vertex(wwbFoliageAlpha) && u_WwbFoliageColorVariationStrength > 0.0) {
                    float wwbVariation = clamp(
                            wwb_foliage_color_variation(wwbPlantAnchor) * u_WwbFoliageColorVariationStrength,
                            -0.26,
                            0.32
                    );
                    float wwbLuminance = dot(v_Color.rgb, vec3(0.2126, 0.7152, 0.0722));
                    v_Color.rgb = mix(vec3(wwbLuminance), v_Color.rgb, clamp(1.0 + wwbVariation * 1.35, 0.70, 1.34));
                    v_Color.rgb *= 1.0 + wwbVariation * 0.82;
                }
                if (wwb_is_foliage_wind_vertex(wwbFoliageAlpha) || wwb_is_lantern_wind_vertex(wwbFoliageAlpha) || wwb_is_chain_wind_vertex(wwbFoliageAlpha)) {
                    v_Color.a = 1.0;
                }
                float wwbLightLevel = clamp(max(max(v_Color.r, v_Color.g), v_Color.b), 0.0, 1.0);
                float wwbSheen = clamp(wwbWindSheen, 0.0, 0.32);
                vec3 wwbSheenTarget = v_Color.rgb * vec3(1.42, 1.54, 1.30) + vec3(0.025, 0.060, 0.012) * wwbLightLevel;
                v_Color.rgb = mix(v_Color.rgb, wwbSheenTarget, wwbSheen);""";

    private SodiumFoliageShaderSource() {
    }

    public static boolean isSodiumTerrainVertexShader(ResourceLocation name, String source) {
        if (!SODIUM_NAMESPACE.equals(name.getNamespace()) || !name.getPath().endsWith(VERTEX_SHADER_EXTENSION)) {
            return false;
        }

        return SODIUM_TERRAIN_VERTEX_SHADER.equals(name.getPath())
                || (source.contains("_vert_init")
                        && source.contains("_vert_position")
                        && source.contains("_material_params"));
    }

    public static String patch(ResourceLocation name, String source) {
        if (source.isBlank() || source.contains("wwb_apply_foliage_wind")) {
            return source;
        }

        Pattern colorAnchor = findColorAnchor(source);
        if (!containsRequiredMarkers(source, colorAnchor, name)) {
            return source;
        }

        String patched = insertAfter(source, LIGHT_SAMPLER_PATTERN, FoliageShaderSource.UNIFORMS);
        patched = insertBefore(patched, MAIN_METHOD_PATTERN, WIND_FUNCTIONS);
        patched = insertAfter(patched, POSITION_LINE_PATTERN, WIND_POSITION_INJECTION);
        patched = insertAfter(patched, colorAnchor, WIND_COLOR_INJECTION);
        return patched;
    }

    private static boolean containsRequiredMarkers(String source, Pattern colorAnchor, ResourceLocation name) {
        if (matches(source, LIGHT_SAMPLER_PATTERN)
                && matches(source, MAIN_METHOD_PATTERN)
                && matches(source, POSITION_LINE_PATTERN)
                && source.contains("_material_params")
                && colorAnchor != null) {
            return true;
        }

        if (!SODIUM_TERRAIN_VERTEX_SHADER.equals(name.getPath())) {
            return false;
        }

        ResponsiveFoliageShaders.disableSodiumShaderPatch(
                "Could not patch Sodium foliage shader " + name + "; expected source markers were missing.",
                null
        );
        return false;
    }

    private static Pattern findColorAnchor(String source) {
        if (matches(source, SHINE_COLORED_LIGHT_LINE_PATTERN)) {
            return SHINE_COLORED_LIGHT_LINE_PATTERN;
        }
        if (matches(source, SHINE_BASE_COLOR_LINE_PATTERN)) {
            return SHINE_BASE_COLOR_LINE_PATTERN;
        }
        if (matches(source, COLOR_LINE_PATTERN)) {
            return COLOR_LINE_PATTERN;
        }

        return null;
    }

    private static boolean matches(String source, Pattern pattern) {
        return pattern.matcher(source).find();
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
