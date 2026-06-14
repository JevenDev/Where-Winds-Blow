package com.jvn.wherewindsblow.client.foliage;

import net.minecraft.resources.ResourceLocation;

public final class SodiumFoliageShaderSource {
    private static final String SODIUM_NAMESPACE = "sodium";
    private static final String SODIUM_TERRAIN_VERTEX_SHADER = "blocks/block_layer_opaque.vsh";
    private static final String MAIN_METHOD = "void main() {";
    private static final String LIGHT_SAMPLER = "uniform sampler2D u_LightTex;";
    private static final String POSITION_LINE = "    vec3 position = _vert_position + translation;";
    private static final String COLOR_LINE = "    v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);";
    private static final String SHINE_BASE_COLOR_LINE = "    v_Color = _vert_color * texture(u_LightTex, shineLightCoord);";
    private static final String SHINE_COLORED_LIGHT_LINE = "    v_Color = shine_apply_colored_light(position, v_Color, shineLightCoord);";

    private static final String WIND_UNIFORMS = """

            uniform float u_WwbTime;
            uniform float u_WwbWeatherWindPower;
            uniform float u_WwbPlantSwayStrength;
            uniform float u_WwbLeafSwayStrength;
            uniform float u_WwbPlantSheenStrength;
            uniform float u_WwbLeafSheenStrength;
            uniform int u_WwbInteractorCount;
            uniform vec4 u_WwbInteractor0;
            uniform vec4 u_WwbInteractor1;
            uniform vec4 u_WwbInteractor2;
            uniform vec4 u_WwbInteractor3;
            uniform vec4 u_WwbInteractor4;
            uniform vec4 u_WwbInteractor5;
            uniform vec4 u_WwbInteractor6;
            uniform vec4 u_WwbInteractor7;
            uniform vec4 u_WwbInteractor8;
            uniform vec4 u_WwbInteractor9;
            uniform vec4 u_WwbInteractor10;
            uniform vec4 u_WwbInteractor11;
            uniform vec4 u_WwbInteractor12;
            uniform vec4 u_WwbInteractor13;
            uniform vec4 u_WwbInteractor14;
            uniform vec4 u_WwbInteractor15;
            uniform vec4 u_WwbInteractorStrengths0;
            uniform vec4 u_WwbInteractorStrengths1;
            uniform vec4 u_WwbInteractorStrengths2;
            uniform vec4 u_WwbInteractorStrengths3;
            """;

    private static final String WIND_FUNCTIONS = """

            bool wwb_is_plant_wind_vertex(float alpha) {
                float encoded = alpha * 255.0;
                return encoded >= 16.5 && encoded <= 44.5;
            }

            bool wwb_is_leaf_wind_vertex(float alpha) {
                float encoded = alpha * 255.0;
                return encoded >= 44.5 && encoded <= 72.5;
            }

            bool wwb_is_foliage_wind_vertex(float alpha) {
                return wwb_is_plant_wind_vertex(alpha) || wwb_is_leaf_wind_vertex(alpha);
            }

            bool wwb_has_foliage_material() {
                return ((_material_params >> 1u) & 3u) != 0u;
            }

            bool wwb_should_apply_foliage_wind(float alpha) {
                return wwb_has_foliage_material() && wwb_is_foliage_wind_vertex(alpha);
            }

            float wwb_smooth_curve(float value) {
                value = clamp(value, 0.0, 1.0);
                return value * value * (3.0 - 2.0 * value);
            }

            float wwb_decode_wind_alpha(float alpha) {
                float encoded = alpha * 255.0;
                if (wwb_is_leaf_wind_vertex(alpha)) {
                    return clamp((encoded - 45.0) / 27.0, 0.0, 1.0);
                }

                return clamp((encoded - 17.0) / 27.0, 0.0, 1.0);
            }

            float wwb_sway_strength_for_alpha(float alpha) {
                return wwb_is_leaf_wind_vertex(alpha) ? u_WwbLeafSwayStrength : u_WwbPlantSwayStrength;
            }

            float wwb_sheen_strength_for_alpha(float alpha) {
                return wwb_is_leaf_wind_vertex(alpha) ? u_WwbLeafSheenStrength : u_WwbPlantSheenStrength;
            }

            float wwb_foliage_wind_sheen(vec3 position, float alpha) {
                if (!wwb_should_apply_foliage_wind(alpha)) {
                    return 0.0;
                }

                float bend = wwb_smooth_curve(wwb_decode_wind_alpha(alpha));
                float t = u_WwbTime;
                vec2 windDir = normalize(vec2(0.82, 0.57));
                vec2 crossDir = vec2(-windDir.y, windDir.x);
                float along = dot(position.xz, windDir);
                float across = dot(position.xz, crossDir);
                float phaseDrift = sin(along * 0.13 - across * 0.09 + t * 0.11) * 0.48
                        + sin(along * -0.07 + across * 0.17 - t * 0.09) * 0.26;
                float tempoDrift = 1.0 + sin(along * 0.052 + across * 0.041 + t * 0.09) * 0.08;
                float ripple = sin(along * 1.08 - t * 3.6 * (1.0 + phaseDrift * 0.035) + across * 0.18 + phaseDrift * 0.7) * 0.5 + 0.5;
                float gust = wwb_smooth_curve(sin(along * 0.10 - t * 0.42 * tempoDrift + across * 0.04 + phaseDrift * 0.35) * 0.5 + 0.5);
                float fieldWarp = sin(along * 0.075 + across * 0.115 + t * 0.21) * 0.75
                        + sin(along * 0.16 - across * 0.085 - t * 0.13) * 0.36;
                float wavePhase = along * 0.34 - t * 1.52 * tempoDrift + sin(across * 0.055 + t * 0.22) * 1.1 + fieldWarp + phaseDrift * 0.55;
                float waveFace = sin(wavePhase) * 0.5 + 0.5;
                float leadingCrest = wwb_smooth_curve(smoothstep(0.46, 0.86, waveFace));
                float trailingWash = pow(max(0.0, sin(wavePhase - 0.62)), 2.6) * 0.35;
                float patchBreakup = 0.58 + 0.42 * wwb_smooth_curve(sin(along * 0.23 + across * 0.31 - t * 0.34 + phaseDrift * 0.45) * 0.5 + 0.5);
                float crossFeather = 0.72 + 0.28 * sin(across * 0.19 + t * 0.47 + phaseDrift * 0.5);
                float sheenBand = max(leadingCrest, trailingWash) * patchBreakup * crossFeather;
                float tipLift = wwb_smooth_curve(bend);
                return clamp((sheenBand * 0.30 + ripple * gust * 0.035) * tipLift * wwb_sheen_strength_for_alpha(alpha), 0.0, 0.35);
            }

            vec4 wwb_foliage_interactor_at(int index) {
                if (index == 0) return u_WwbInteractor0;
                if (index == 1) return u_WwbInteractor1;
                if (index == 2) return u_WwbInteractor2;
                if (index == 3) return u_WwbInteractor3;
                if (index == 4) return u_WwbInteractor4;
                if (index == 5) return u_WwbInteractor5;
                if (index == 6) return u_WwbInteractor6;
                if (index == 7) return u_WwbInteractor7;
                if (index == 8) return u_WwbInteractor8;
                if (index == 9) return u_WwbInteractor9;
                if (index == 10) return u_WwbInteractor10;
                if (index == 11) return u_WwbInteractor11;
                if (index == 12) return u_WwbInteractor12;
                if (index == 13) return u_WwbInteractor13;
                if (index == 14) return u_WwbInteractor14;
                return u_WwbInteractor15;
            }

            float wwb_foliage_interactor_strength_at(int index) {
                if (index < 4) return u_WwbInteractorStrengths0[index];
                if (index < 8) return u_WwbInteractorStrengths1[index - 4];
                if (index < 12) return u_WwbInteractorStrengths2[index - 8];
                return u_WwbInteractorStrengths3[index - 12];
            }

            vec3 wwb_apply_foliage_interactors(vec3 position, float bend, float alpha) {
                if (!wwb_is_plant_wind_vertex(alpha) || u_WwbInteractorCount <= 0) {
                    return position;
                }

                vec2 totalOffset = vec2(0.0);
                for (int index = 0; index < 16; index++) {
                    if (index >= u_WwbInteractorCount) {
                        break;
                    }

                    vec4 interactor = wwb_foliage_interactor_at(index);
                    if (position.y < interactor.y - 0.15) {
                        continue;
                    }

                    float radius = max(interactor.w, 0.001);
                    vec2 delta = position.xz - interactor.xz;
                    float horizontalDistance = length(delta);
                    float horizontalInfluence = wwb_smooth_curve(1.0 - horizontalDistance / radius);
                    if (horizontalInfluence <= 0.0) {
                        continue;
                    }

                    vec2 direction = horizontalDistance > 0.001 ? delta / horizontalDistance : vec2(1.0, 0.0);
                    float strength = horizontalInfluence * wwb_foliage_interactor_strength_at(index);
                    totalOffset += direction * strength;
                }

                float offsetLength = length(totalOffset);
                if (offsetLength > 1.0) {
                    totalOffset /= offsetLength;
                }

                position.xz += totalOffset * bend * 0.34;
                return position;
            }

            float wwb_grass_variation_seed(vec2 cell, vec2 salt) {
                return fract(sin(dot(cell, salt)) * 43758.5453);
            }

            vec3 wwb_apply_foliage_wind(vec3 position, float alpha) {
                if (!wwb_should_apply_foliage_wind(alpha)) {
                    return position;
                }

                float bend = wwb_smooth_curve(wwb_decode_wind_alpha(alpha));
                float interactionBend = wwb_is_plant_wind_vertex(alpha) ? max(bend, 0.35) : bend;
                float weatherStrength = 1.0 + u_WwbWeatherWindPower * 0.55;
                float t = u_WwbTime;
                vec3 basePosition = position;
                vec2 windDir = normalize(vec2(0.82, 0.57));
                vec2 crossDir = vec2(-windDir.y, windDir.x);
                float along = dot(position.xz, windDir);
                float across = dot(position.xz, crossDir);
                float plantWind = wwb_is_plant_wind_vertex(alpha) ? 1.0 : 0.0;
                vec2 gustCell = floor(position.xz * 0.58);
                vec2 bladeCell = floor(position.xz * 2.7);
                float gustSeed = wwb_grass_variation_seed(gustCell, vec2(127.1, 311.7));
                float bladeSeed = wwb_grass_variation_seed(bladeCell, vec2(269.5, 183.3));
                float localPhase = plantWind * ((gustSeed - 0.5) * 3.2 + (bladeSeed - 0.5) * 0.7);
                float localTempo = mix(1.0, 0.82 + gustSeed * 0.36, plantWind);
                float localAmplitude = mix(1.0, 0.62 + gustSeed * 0.55 + bladeSeed * 0.18, plantWind);
                float phaseDrift = sin(along * 0.13 - across * 0.09 + t * 0.11) * 0.48
                        + sin(along * -0.07 + across * 0.17 - t * 0.09) * 0.26;
                phaseDrift += localPhase * 0.38;
                float tempoDrift = (1.0 + sin(along * 0.052 + across * 0.041 + t * 0.09 + localPhase * 0.2) * 0.08) * localTempo;
                float amplitudeDrift = (0.84 + 0.22 * wwb_smooth_curve(sin(along * 0.21 + across * 0.14 - t * 0.16 + localPhase) * 0.5 + 0.5)) * localAmplitude;
                float broad = sin(along * 0.35 - t * 1.28 * tempoDrift + sin(across * 0.075 + t * 0.18) * 1.35 + phaseDrift + localPhase);
                float wave = pow(max(0.0, broad), 1.7);
                float ripple = sin(along * 1.08 - t * 3.6 * (1.0 + phaseDrift * 0.035) + across * 0.18 + phaseDrift * 0.7 + localPhase * 1.4) * 0.5 + 0.5;
                float gust = wwb_smooth_curve(sin(along * 0.10 - t * 0.42 * tempoDrift + across * 0.04 + phaseDrift * 0.35 + localPhase * 0.5) * 0.5 + 0.5);
                float shimmer = sin(position.x * 2.17 + position.z * 1.63 + t * 2.1 + phaseDrift + bladeSeed * 2.8) * 0.012;
                float strength = (0.018 + wave * 0.145 * amplitudeDrift + ripple * gust * 0.055 + shimmer) * bend * weatherStrength * wwb_sway_strength_for_alpha(alpha);
                float directionNoise = sin(across * 0.22 + t * 0.55 * tempoDrift + phaseDrift * 0.35 + localPhase) * (0.18 + plantWind * 0.08);
                vec2 dir = normalize(windDir + crossDir * directionNoise);
                position.xz += dir * strength;
                vec3 interactedPosition = wwb_apply_foliage_interactors(basePosition, interactionBend, alpha);
                position.xz += interactedPosition.xz - basePosition.xz;
                return position;
            }
            """;

    private static final String WIND_POSITION_INJECTION = """
                float wwbWindAlpha = _vert_color.a;
                float wwbWindSheen = wwb_foliage_wind_sheen(position, wwbWindAlpha);
                position = wwb_apply_foliage_wind(position, wwbWindAlpha);""";

    private static final String WIND_COLOR_INJECTION = """
                if (wwb_should_apply_foliage_wind(wwbWindAlpha)) {
                    v_Color.a = 1.0;
                }
                v_Color.rgb += vec3(wwbWindSheen * 0.44);""";

    private SodiumFoliageShaderSource() {
    }

    public static boolean isSodiumTerrainVertexShader(ResourceLocation name) {
        return SODIUM_NAMESPACE.equals(name.getNamespace()) && SODIUM_TERRAIN_VERTEX_SHADER.equals(name.getPath());
    }

    public static String patch(ResourceLocation name, String source) {
        if (source.isBlank() || source.contains("wwb_apply_foliage_wind")) {
            return source;
        }

        String colorAnchor = findColorAnchor(source);
        if (!containsRequiredMarkers(source, colorAnchor, name)) {
            return source;
        }

        String patched = insertAfter(source, LIGHT_SAMPLER, WIND_UNIFORMS);
        patched = insertBefore(patched, MAIN_METHOD, WIND_FUNCTIONS);
        patched = insertAfter(patched, POSITION_LINE, WIND_POSITION_INJECTION);
        patched = insertAfter(patched, colorAnchor, WIND_COLOR_INJECTION);
        return patched;
    }

    private static boolean containsRequiredMarkers(String source, String colorAnchor, ResourceLocation name) {
        if (source.contains(LIGHT_SAMPLER)
                && source.contains(MAIN_METHOD)
                && source.contains(POSITION_LINE)
                && colorAnchor != null) {
            return true;
        }

        ResponsiveFoliageShaders.disableSodiumShaderPatch(
                "Could not patch Sodium foliage shader " + name + "; expected source markers were missing.",
                null
        );
        return false;
    }

    private static String findColorAnchor(String source) {
        if (source.contains(SHINE_COLORED_LIGHT_LINE)) {
            return SHINE_COLORED_LIGHT_LINE;
        }
        if (source.contains(SHINE_BASE_COLOR_LINE)) {
            return SHINE_BASE_COLOR_LINE;
        }
        if (source.contains(COLOR_LINE)) {
            return COLOR_LINE;
        }

        return null;
    }

    private static String insertAfter(String source, String marker, String addition) {
        int index = source.indexOf(marker);
        if (index < 0) {
            return source;
        }

        int lineEnd = source.indexOf('\n', index);
        int insertion = lineEnd < 0 ? index + marker.length() : lineEnd;
        return source.substring(0, insertion) + addition + source.substring(insertion);
    }

    private static String insertBefore(String source, String marker, String addition) {
        int index = source.indexOf(marker);
        if (index < 0) {
            return source;
        }

        return source.substring(0, index) + addition + source.substring(index);
    }

}
