package com.jvn.wherewindsblow.mixin.client.compat.iris;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.irisshaders.iris.pipeline.programs.SodiumPrograms", remap = false)
public abstract class IrisSodiumProgramsMixin {
    private static final Pattern WHEREWINDSBLOW$IRIS_VERTEX_POSITION_FUNCTION_PATTERN = Pattern.compile(
            "vec4\\s+getVertexPosition\\s*\\(\\s*\\)\\s*\\{",
            Pattern.MULTILINE
    );
    private static final Pattern WHEREWINDSBLOW$IRIS_VERTEX_POSITION_RETURN_PATTERN = Pattern.compile(
            "return\\s+vec4\\s*\\(\\s*_vert_position\\s*\\+\\s*u_RegionOffset\\s*\\+\\s*_get_draw_translation\\s*\\(\\s*_draw_id\\s*\\)\\s*,\\s*1\\.0\\s*\\)\\s*;",
            Pattern.MULTILINE
    );

    private static final String WHEREWINDSBLOW$IRIS_WIND_HELPERS = """
            uniform float u_WwbTime;
            uniform float u_WwbWeatherWindPower;
            uniform float u_WwbPlantSwayStrength;
            uniform float u_WwbLeafSwayStrength;
            uniform float u_WwbLanternSwayStrength;
            uniform vec2 u_WwbWindDirection;
            uniform vec3 u_WwbCameraPosition;
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

            bool wwb_is_plant_wind_vertex(float alpha) {
                float encoded = alpha * 255.0;
                return encoded >= 16.5 && encoded <= 44.5;
            }

            bool wwb_is_leaf_wind_vertex(float alpha) {
                float encoded = alpha * 255.0;
                return encoded >= 44.5 && encoded <= 72.5;
            }

            bool wwb_is_lantern_wind_vertex(float alpha) {
                float encoded = alpha * 255.0;
                return encoded >= 72.5 && encoded <= 88.5;
            }
            bool wwb_is_chain_wind_vertex(float alpha) {
                float encoded = alpha * 255.0;
                return encoded >= 88.5 && encoded <= 104.5;
            }

            bool wwb_is_foliage_wind_vertex(float alpha) {
                return wwb_is_plant_wind_vertex(alpha) || wwb_is_leaf_wind_vertex(alpha);
            }

            float wwb_plant_alpha_code(float alpha) {
                return clamp(floor(alpha * 255.0 + 0.5) - 17.0, 0.0, 27.0);
            }

            float wwb_decode_plant_wind_alpha(float alpha) {
                return mod(wwb_plant_alpha_code(alpha), 7.0) / 6.0;
            }

            float wwb_decode_plant_interaction_alpha(float alpha) {
                return floor(wwb_plant_alpha_code(alpha) / 7.0) / 3.0;
            }

            bool wwb_has_foliage_material() {
                return ((_material_params >> 1u) & 3u) != 0u;
            }

            bool wwb_should_apply_foliage_wind(float alpha) {
                return wwb_has_foliage_material() && wwb_is_foliage_wind_vertex(alpha);
            }
            bool wwb_should_apply_lantern_wind(float alpha) {
                return (wwb_is_lantern_wind_vertex(alpha) || wwb_is_chain_wind_vertex(alpha)) && u_WwbLanternSwayStrength > 0.0;
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

                return wwb_decode_plant_wind_alpha(alpha);
            }
            float wwb_decode_lantern_wind_alpha(float alpha) {
                return clamp((floor(alpha * 255.0 + 0.5) - 73.0) / 15.0, 0.0, 1.0);
            }
            float wwb_decode_chain_wind_alpha(float alpha) {
                return clamp((floor(alpha * 255.0 + 0.5) - 89.0) / 15.0, 0.0, 1.0);
            }
            float wwb_sway_strength_for_alpha(float alpha) {
                return wwb_is_leaf_wind_vertex(alpha) ? u_WwbLeafSwayStrength : u_WwbPlantSwayStrength;
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
            vec3 wwb_apply_foliage_interactors(vec3 position, vec2 anchor, float bend) {
                if (u_WwbInteractorCount <= 0 || bend <= 0.0) {
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
                    vec2 delta = anchor - interactor.xz;
                    float horizontalDistance = length(delta);
                    float horizontalInfluence = wwb_smooth_curve(1.0 - horizontalDistance / radius);
                    if (horizontalInfluence <= 0.0) {
                        continue;
                    }

                    vec2 direction = horizontalDistance > 0.001 ? delta / horizontalDistance : vec2(1.0, 0.0);
                    totalOffset += direction * horizontalInfluence * wwb_foliage_interactor_strength_at(index);
                }

                float offsetLength = length(totalOffset);
                if (offsetLength > 1.0) {
                    totalOffset /= offsetLength;
                }

                position.xz += totalOffset * bend * 0.34;
                return position;
            }
            vec3 wwb_apply_foliage_interaction(vec3 currentPosition, vec3 basePosition, vec2 anchor, float alpha) {
                if (!wwb_has_foliage_material() || !wwb_is_plant_wind_vertex(alpha)) {
                    return currentPosition;
                }

                float bend = wwb_smooth_curve(wwb_decode_plant_interaction_alpha(alpha));
                vec3 interactedPosition = wwb_apply_foliage_interactors(basePosition, anchor, bend);
                currentPosition.xz += interactedPosition.xz - basePosition.xz;
                return currentPosition;
            }
            float wwb_grass_variation_seed(vec2 cell, vec2 salt) {
                return fract(sin(dot(cell, salt)) * 43758.5453);
            }
            vec3 wwb_rotate_around_axis(vec3 value, vec3 axis, float angle) {
                float c = cos(angle);
                float s = sin(angle);
                return value * c + cross(axis, value) * s + axis * dot(axis, value) * (1.0 - c);
            }
            vec3 wwb_apply_foliage_wind(vec3 position, float alpha) {
                if (!wwb_should_apply_foliage_wind(alpha)) {
                    return position;
                }

                float bend = wwb_smooth_curve(wwb_decode_wind_alpha(alpha));
                float weatherStrength = 1.0 + u_WwbWeatherWindPower * 0.55;
                float t = u_WwbTime;
                vec3 windPosition = position + u_WwbCameraPosition;
                vec2 windDir = normalize(u_WwbWindDirection);
                vec2 crossDir = vec2(-windDir.y, windDir.x);
                float along = dot(windPosition.xz, windDir);
                float across = dot(windPosition.xz, crossDir);
                float plantWind = wwb_is_plant_wind_vertex(alpha) ? 1.0 : 0.0;
                vec2 gustCell = floor(windPosition.xz * 0.58);
                vec2 bladeCell = floor(windPosition.xz * 2.7);
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
                float shimmer = sin(windPosition.x * 2.17 + windPosition.z * 1.63 + t * 2.1 + phaseDrift + bladeSeed * 2.8) * 0.012;
                float strength = (0.018 + wave * 0.145 * amplitudeDrift + ripple * gust * 0.055 + shimmer) * bend * weatherStrength * wwb_sway_strength_for_alpha(alpha);
                float directionNoise = sin(across * 0.22 + t * 0.55 * tempoDrift + phaseDrift * 0.35 + localPhase) * (0.18 + plantWind * 0.08);
                vec2 dir = normalize(windDir + crossDir * directionNoise);
                position.xz += dir * strength;
                return position;
            }
            vec3 wwb_apply_lantern_wind(vec3 position, float alpha) {
                if (!wwb_should_apply_lantern_wind(alpha)) {
                    return position;
                }

                bool lanternMarker = wwb_is_lantern_wind_vertex(alpha);
                bool chainMarker = wwb_is_chain_wind_vertex(alpha);
                float markerStrength = lanternMarker ? wwb_decode_lantern_wind_alpha(alpha) : wwb_decode_chain_wind_alpha(alpha);

                float t = u_WwbTime;
                vec3 windPosition = position + u_WwbCameraPosition;
                vec2 windAnchor = floor(windPosition.xz) + vec2(0.5);
                vec2 windDir = normalize(u_WwbWindDirection);
                vec2 crossDir = vec2(-windDir.y, windDir.x);
                float along = dot(windAnchor, windDir);
                float across = dot(windAnchor, crossDir);
                float seed = wwb_grass_variation_seed(floor(windAnchor), vec2(91.7, 53.3));
                float phase = seed * 6.2831853;
                float linkLag = 0.24;
                float swingTime = t - linkLag;
                float primarySwing = sin(swingTime * 1.08 + along * 0.13 + phase)
                        + sin(swingTime * 1.62 + across * 0.17 + phase * 1.71) * 0.34;
                float crossSwing = sin(swingTime * 0.82 + across * 0.12 + phase * 1.37) * 0.46;
                float recoil = sin((t + linkLag * 0.65) * 2.24 + along * 0.055 + phase * 2.17) * 0.13;
                float gust = 0.68 + 0.32 * wwb_smooth_curve(sin(along * 0.10 - t * 0.36 + phase * 0.5) * 0.5 + 0.5);
                float directionNoise = sin(across * 0.18 + t * 0.42 + phase) * 0.16;
                vec2 baseDir = normalize(windDir + crossDir * directionNoise);
                vec2 swing = baseDir * (primarySwing + recoil) + crossDir * crossSwing * 0.48;
                float swingLength = length(swing);
                if (swingLength <= 0.001) {
                    return position;
                }

                vec2 swingDir = swing / swingLength;
                vec3 axis = normalize(vec3(-swingDir.y, 0.0, swingDir.x));
                float chainAngle = clamp(swingLength * (0.072 + u_WwbWeatherWindPower * 0.026) * u_WwbLanternSwayStrength * gust, 0.0, 0.18);
                float bodyAngle = clamp(swingLength * (0.094 + u_WwbWeatherWindPower * 0.034) * u_WwbLanternSwayStrength * gust, 0.0, 0.24);
                vec3 chainEndOffset = wwb_rotate_around_axis(vec3(0.0, -1.0, 0.0), axis, chainAngle) - vec3(0.0, -1.0, 0.0);

                float localY = fract(windPosition.y);
                if (chainMarker) {
                    if (markerStrength <= 0.0) {
                        return position;
                    }

                    position += chainEndOffset * markerStrength;
                    return position;
                }

                vec3 local = vec3(windPosition.x - windAnchor.x, localY - 1.0, windPosition.z - windAnchor.y);
                vec3 rotated = wwb_rotate_around_axis(local, axis, bodyAngle * markerStrength);

                float yaw = sin(swingTime * 1.46 + phase * 2.3 + across * 0.05) * (0.020 + u_WwbWeatherWindPower * 0.005) * u_WwbLanternSwayStrength * markerStrength;
                float yawCos = cos(yaw);
                float yawSin = sin(yaw);
                rotated.xz = vec2(
                        rotated.x * yawCos - rotated.z * yawSin,
                        rotated.x * yawSin + rotated.z * yawCos
                );

                position += chainEndOffset;
                position += rotated - local;
                position.y += 0.03125 * markerStrength;
                return position;
            }
            """;

    private static final String WHEREWINDSBLOW$IRIS_WIND_VERTEX_POSITION_RETURN = """
                vec3 wwbPosition = _vert_position + u_RegionOffset + _get_draw_translation(_draw_id);
                float wwbFoliageAlpha = _vert_color.a;
                vec2 wwbPlantAnchor = floor(_vert_position.xz) + vec2(0.5) + u_RegionOffset.xz + _get_draw_translation(_draw_id).xz;
                vec3 wwbFoliageBase = wwbPosition;
                if (wwb_should_apply_foliage_wind(wwbFoliageAlpha) || wwb_should_apply_lantern_wind(wwbFoliageAlpha)) {
                    _vert_color.a = 1.0;
                }
                wwbPosition = wwb_apply_foliage_wind(wwbPosition, wwbFoliageAlpha);
                wwbPosition = wwb_apply_lantern_wind(wwbPosition, wwbFoliageAlpha);
                wwbPosition = wwb_apply_foliage_interaction(wwbPosition, wwbFoliageBase, wwbPlantAnchor, wwbFoliageAlpha);
                return vec4(wwbPosition, 1.0);""";

    @Inject(method = "transformShaders", at = @At("RETURN"), require = 0)
    private void wherewindsblow$patchIrisSodiumTerrainWind(CallbackInfoReturnable<Map<Object, String>> cir) {
        if (!ResponsiveFoliageShaders.shouldPatchSodiumShaders()) {
            return;
        }

        Map<Object, String> sources = cir.getReturnValue();
        if (sources == null || sources.isEmpty()) {
            return;
        }

        for (Map.Entry<Object, String> entry : sources.entrySet()) {
            String source = entry.getValue();
            if (!"VERTEX".equals(String.valueOf(entry.getKey())) || source == null || source.contains("wwb_apply_foliage_wind")) {
                continue;
            }

            try {
                String patched = wherewindsblow$patchVertexPosition(source);
                if (!patched.equals(source)) {
                    entry.setValue(patched);
                }
            } catch (RuntimeException exception) {
                ResponsiveFoliageShaders.disableSodiumShaderPatch("Failed to patch Iris Sodium terrain shader source.", exception);
                return;
            }
        }
    }

    private static String wherewindsblow$patchVertexPosition(String source) {
        if (!source.contains("_material_params")) {
            return source;
        }

        Matcher returnMatcher = WHEREWINDSBLOW$IRIS_VERTEX_POSITION_RETURN_PATTERN.matcher(source);
        if (!returnMatcher.find()) {
            return source;
        }

        String patched = returnMatcher.replaceFirst(Matcher.quoteReplacement(WHEREWINDSBLOW$IRIS_WIND_VERTEX_POSITION_RETURN));
        Matcher functionMatcher = WHEREWINDSBLOW$IRIS_VERTEX_POSITION_FUNCTION_PATTERN.matcher(patched);
        if (!functionMatcher.find()) {
            return source;
        }

        return patched.substring(0, functionMatcher.start()) + WHEREWINDSBLOW$IRIS_WIND_HELPERS + patched.substring(functionMatcher.start());
    }
}
