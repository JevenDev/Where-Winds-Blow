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

    private static final String WIND_UNIFORMS = """

            uniform float u_WwbTime;
            uniform float u_WwbAmbientWindStrength;
            uniform float u_WwbWindTurbulence;
            uniform vec3 u_WwbWeatherState;
            uniform int u_WwbActiveGustCount;
            uniform vec4 u_WwbGustOriginTime0;
            uniform vec4 u_WwbGustDirectionSpeed0;
            uniform vec4 u_WwbGustStrength0;
            uniform vec4 u_WwbGustEnvelope0;
            uniform vec4 u_WwbGustOriginTime1;
            uniform vec4 u_WwbGustDirectionSpeed1;
            uniform vec4 u_WwbGustStrength1;
            uniform vec4 u_WwbGustEnvelope1;
            uniform vec4 u_WwbGustOriginTime2;
            uniform vec4 u_WwbGustDirectionSpeed2;
            uniform vec4 u_WwbGustStrength2;
            uniform vec4 u_WwbGustEnvelope2;
            uniform float u_WwbPlantSwayStrength;
            uniform float u_WwbLeafSwayStrength;
            uniform float u_WwbLanternSwayStrength;
            uniform float u_WwbPlantSheenStrength;
            uniform float u_WwbLeafSheenStrength;
            uniform float u_WwbFoliageColorVariationStrength;
            uniform float u_WwbFoliageAnimationStepRate;
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
            uniform vec4 u_WwbInteractorMotion0;
            uniform vec4 u_WwbInteractorMotion1;
            uniform vec4 u_WwbInteractorMotion2;
            uniform vec4 u_WwbInteractorMotion3;
            uniform vec4 u_WwbInteractorMotion4;
            uniform vec4 u_WwbInteractorMotion5;
            uniform vec4 u_WwbInteractorMotion6;
            uniform vec4 u_WwbInteractorMotion7;
            uniform vec4 u_WwbInteractorMotion8;
            uniform vec4 u_WwbInteractorMotion9;
            uniform vec4 u_WwbInteractorMotion10;
            uniform vec4 u_WwbInteractorMotion11;
            uniform vec4 u_WwbInteractorMotion12;
            uniform vec4 u_WwbInteractorMotion13;
            uniform vec4 u_WwbInteractorMotion14;
            uniform vec4 u_WwbInteractorMotion15;
            """;

    private static final String WIND_FUNCTIONS = """

            bool wwb_is_plant_wind_vertex(float alpha) {
                float encoded = alpha * 255.0;
                return (encoded >= 16.5 && encoded <= 44.5) || (encoded >= 104.5 && encoded <= 132.5);
            }

            bool wwb_is_column_wind_vertex(float alpha) {
                float encoded = alpha * 255.0;
                return encoded >= 104.5 && encoded <= 132.5;
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
                float alphaMin = wwb_is_column_wind_vertex(alpha) ? 105.0 : 17.0;
                return clamp(floor(alpha * 255.0 + 0.5) - alphaMin, 0.0, 27.0);
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

            vec4 wwb_gust_origin_time_at(int index) {
                if (index == 0) return u_WwbGustOriginTime0;
                if (index == 1) return u_WwbGustOriginTime1;
                return u_WwbGustOriginTime2;
            }

            vec4 wwb_gust_direction_speed_at(int index) {
                if (index == 0) return u_WwbGustDirectionSpeed0;
                if (index == 1) return u_WwbGustDirectionSpeed1;
                return u_WwbGustDirectionSpeed2;
            }

            vec4 wwb_gust_strength_at(int index) {
                if (index == 0) return u_WwbGustStrength0;
                if (index == 1) return u_WwbGustStrength1;
                return u_WwbGustStrength2;
            }

            vec4 wwb_gust_envelope_at(int index) {
                if (index == 0) return u_WwbGustEnvelope0;
                if (index == 1) return u_WwbGustEnvelope1;
                return u_WwbGustEnvelope2;
            }

            float wwb_gust_envelope(float progress, float attackEnd, float releaseStart) {
                if (progress < attackEnd) {
                    return wwb_smooth_curve(progress / max(attackEnd, 0.001));
                }
                if (progress <= releaseStart) {
                    return 1.0;
                }
                return wwb_smooth_curve(1.0 - (progress - releaseStart) / max(1.0 - releaseStart, 0.001));
            }

            vec2 wwb_foliage_motion_position(vec3 position, float alpha) {
                vec2 worldPosition = position.xz + u_WwbCameraPosition.xz;
                return wwb_is_column_wind_vertex(alpha) ? floor(worldPosition) + vec2(0.5) : worldPosition;
            }

            vec2 wwb_sample_dynamic_wind(vec2 worldPosition, out float gustStrength, out float turbulence, out float leadingEdge) {
                vec2 ambientDirection = normalize(u_WwbWindDirection);
                vec2 directionVector = ambientDirection * max(u_WwbAmbientWindStrength, 0.02);
                gustStrength = 0.0;
                turbulence = u_WwbWindTurbulence;
                leadingEdge = 0.0;

                for (int index = 0; index < 3; index++) {
                    if (index >= u_WwbActiveGustCount) {
                        break;
                    }

                    vec4 originTime = wwb_gust_origin_time_at(index);
                    vec4 directionSpeed = wwb_gust_direction_speed_at(index);
                    vec4 strengthData = wwb_gust_strength_at(index);
                    vec4 envelopeData = wwb_gust_envelope_at(index);
                    float age = u_WwbTime - originTime.z;
                    if (age < 0.0 || age >= originTime.w) {
                        continue;
                    }

                    vec2 gustDirection = normalize(directionSpeed.xy);
                    vec2 gustCross = vec2(-gustDirection.y, gustDirection.x);
                    vec2 relative = worldPosition - originTime.xy;
                    float along = dot(relative, gustDirection);
                    float across = dot(relative, gustCross);
                    float phase = strengthData.z;
                    float width = max(directionSpeed.w, 0.001);
                    float edgeNoise = sin(across * 0.055 + phase) * width * 0.17
                            + sin(across * 0.137 - phase * 1.61) * width * 0.07;
                    float normalizedDistance = (along - directionSpeed.z * age - edgeNoise) / width;
                    float spatialEnvelope = exp(-normalizedDistance * normalizedDistance * 1.65);
                    float timeEnvelope = wwb_gust_envelope(age / originTime.w, envelopeData.x, envelopeData.y);
                    float pocket = clamp(
                            0.84
                                + sin(across * 0.083 + age * 0.21 + phase) * 0.10
                                + sin(across * 0.031 - age * 0.13 + phase * 2.07) * 0.06,
                            0.62,
                            1.12
                    );
                    float localStrength = strengthData.x * timeEnvelope * spatialEnvelope * pocket;
                    float crossVariation = sin(across * 0.069 + age * 0.18 + phase) * strengthData.w;
                    vec2 localDirection = normalize(gustDirection + gustCross * crossVariation);
                    directionVector += localDirection * localStrength;
                    gustStrength += localStrength;
                    turbulence += strengthData.y * timeEnvelope * spatialEnvelope;
                    leadingEdge += localStrength * smoothstep(-0.22, 0.72, normalizedDistance);
                }

                return length(directionVector) > 0.001 ? normalize(directionVector) : ambientDirection;
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

            float wwb_sheen_strength_for_alpha(float alpha) {
                return wwb_is_leaf_wind_vertex(alpha) ? u_WwbLeafSheenStrength : u_WwbPlantSheenStrength;
            }

            float wwb_grass_variation_seed(vec2 cell, vec2 salt) {
                return fract(sin(dot(cell, salt)) * 43758.5453);
            }

            float wwb_foliage_value_noise(vec2 position, vec2 salt) {
                vec2 cell = floor(position);
                vec2 local = fract(position);
                local = local * local * (3.0 - 2.0 * local);
                float bottomLeft = wwb_grass_variation_seed(cell, salt);
                float bottomRight = wwb_grass_variation_seed(cell + vec2(1.0, 0.0), salt);
                float topLeft = wwb_grass_variation_seed(cell + vec2(0.0, 1.0), salt);
                float topRight = wwb_grass_variation_seed(cell + vec2(1.0, 1.0), salt);
                return mix(
                        mix(bottomLeft, bottomRight, local.x),
                        mix(topLeft, topRight, local.x),
                        local.y
                );
            }

            float wwb_foliage_color_variation(vec2 worldPosition) {
                float broad = wwb_foliage_value_noise(worldPosition * 0.045, vec2(127.1, 311.7));
                float local = wwb_foliage_value_noise(worldPosition * 0.18, vec2(269.5, 183.3));
                float accent = smoothstep(0.68, 0.90, broad);
                return (broad - 0.5) * 0.38 + (local - 0.5) * 0.08 + accent * 0.08;
            }

            float wwb_foliage_animation_time(float continuousTime, vec2 worldPosition) {
                if (u_WwbFoliageAnimationStepRate <= 0.0) {
                    return continuousTime;
                }
                float rate = max(u_WwbFoliageAnimationStepRate, 1.0);
                float phase = wwb_grass_variation_seed(floor(worldPosition * 2.0), vec2(41.7, 289.3)) / rate;
                return floor((continuousTime + phase) * rate) / rate - phase;
            }

            float wwb_foliage_wind_sheen(
                    vec3 position,
                    float alpha,
                    vec2 windDir,
                    float localGustStrength,
                    float localTurbulence,
                    float gustLeadingEdge
            ) {
                if (!wwb_should_apply_foliage_wind(alpha)) {
                    return 0.0;
                }

                float bend = wwb_smooth_curve(wwb_decode_wind_alpha(alpha));
                vec2 motionPosition = wwb_foliage_motion_position(position, alpha);
                float t = wwb_is_plant_wind_vertex(alpha) ? wwb_foliage_animation_time(u_WwbTime, motionPosition) : u_WwbTime;
                vec2 phaseDir = normalize(vec2(0.82, 0.57));
                vec2 phaseCross = vec2(-phaseDir.y, phaseDir.x);
                float along = dot(motionPosition, phaseDir);
                float across = dot(motionPosition, phaseCross);
                float plantWind = wwb_is_plant_wind_vertex(alpha) ? 1.0 : 0.0;
                vec2 gustCell = floor(motionPosition * 0.58);
                vec2 bladeCell = floor(motionPosition * 2.7);
                float gustSeed = wwb_grass_variation_seed(gustCell, vec2(127.1, 311.7));
                float bladeSeed = wwb_grass_variation_seed(bladeCell, vec2(269.5, 183.3));
                float localPhase = plantWind * ((gustSeed - 0.5) * 1.7 + (bladeSeed - 0.5) * 0.16);
                float localTempo = mix(1.0, 0.94 + gustSeed * 0.12, plantWind);
                float phaseDrift = sin(along * 0.13 - across * 0.09 + t * 0.11) * 0.48
                        + sin(along * -0.07 + across * 0.17 - t * 0.09) * 0.26;
                phaseDrift += localPhase * 0.38;
                float tempoDrift = (1.0 + sin(along * 0.052 + across * 0.041 + t * 0.09 + localPhase * 0.2) * 0.08) * localTempo;
                float broad = sin(along * 0.35 - t * 1.28 * tempoDrift + sin(across * 0.075 + t * 0.18) * 1.35 + phaseDrift + localPhase);
                float wave = pow(max(0.0, broad), 1.7);
                float proceduralGust = wwb_smooth_curve(sin(along * 0.10 - t * 0.42 * tempoDrift + across * 0.04 + phaseDrift * 0.35 + localPhase * 0.5) * 0.5 + 0.5);
                float motionBand = wwb_smooth_curve(wave) * (0.82 + proceduralGust * 0.18);
                float tipLift = wwb_smooth_curve(bend);
                float sheenLull = 1.0 - wwb_smooth_curve(u_WwbWeatherState.z) * mix(0.35, 0.72, plantWind);
                return clamp(
                        (motionBand * 0.22 + gustLeadingEdge * 0.08)
                                * tipLift
                                * wwb_sheen_strength_for_alpha(alpha)
                                * sheenLull,
                        0.0,
                        0.32
                );
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

            vec4 wwb_foliage_interactor_motion_at(int index) {
                if (index == 0) return u_WwbInteractorMotion0;
                if (index == 1) return u_WwbInteractorMotion1;
                if (index == 2) return u_WwbInteractorMotion2;
                if (index == 3) return u_WwbInteractorMotion3;
                if (index == 4) return u_WwbInteractorMotion4;
                if (index == 5) return u_WwbInteractorMotion5;
                if (index == 6) return u_WwbInteractorMotion6;
                if (index == 7) return u_WwbInteractorMotion7;
                if (index == 8) return u_WwbInteractorMotion8;
                if (index == 9) return u_WwbInteractorMotion9;
                if (index == 10) return u_WwbInteractorMotion10;
                if (index == 11) return u_WwbInteractorMotion11;
                if (index == 12) return u_WwbInteractorMotion12;
                if (index == 13) return u_WwbInteractorMotion13;
                if (index == 14) return u_WwbInteractorMotion14;
                return u_WwbInteractorMotion15;
            }

            vec3 wwb_foliage_interaction_offset(vec3 position, vec2 anchor, float bend) {
                if (u_WwbInteractorCount <= 0 || bend <= 0.0) {
                    return vec3(0.0);
                }

                vec2 totalOffset = vec2(0.0);
                for (int index = 0; index < 16; index++) {
                    if (index >= u_WwbInteractorCount) {
                        break;
                    }

                    vec4 interactor = wwb_foliage_interactor_at(index);
                    vec4 motion = wwb_foliage_interactor_motion_at(index);
                    float radius = max(interactor.w, 0.001);
                    float verticalDistance = max(max(interactor.y - position.y, position.y - motion.x), 0.0);
                    float verticalInfluence = wwb_smooth_curve(1.0 - verticalDistance / (0.30 + radius * 0.55));
                    if (verticalInfluence <= 0.0) {
                        continue;
                    }

                    vec2 trail = motion.yz;
                    float trailLengthSqr = dot(trail, trail);
                    float trailProgress = trailLengthSqr > 0.000001
                            ? clamp(dot(anchor - interactor.xz, trail) / trailLengthSqr, 0.0, 1.0)
                            : 0.0;
                    vec2 nearestPoint = interactor.xz + trail * trailProgress;
                    vec2 delta = anchor - nearestPoint;
                    float horizontalDistance = length(delta);
                    float horizontalInfluence = wwb_smooth_curve(1.0 - horizontalDistance / radius);
                    if (horizontalInfluence <= 0.0) {
                        continue;
                    }

                    vec2 direction = horizontalDistance > 0.001
                            ? delta / horizontalDistance
                            : (trailLengthSqr > 0.000001 ? -normalize(trail) : vec2(1.0, 0.0));
                    totalOffset += direction * horizontalInfluence * verticalInfluence * motion.w;
                }

                float offsetLength = length(totalOffset);
                if (offsetLength > 1.0) {
                    totalOffset /= offsetLength;
                    offsetLength = 1.0;
                }

                float pushBlend = wwb_smooth_curve(clamp(offsetLength * bend * 1.35, 0.0, 1.0));
                return vec3(totalOffset * bend * 0.34, pushBlend);
            }

            vec3 wwb_apply_foliage_interaction(vec3 currentPosition, vec3 basePosition, vec2 anchor, float alpha) {
                if (!wwb_has_foliage_material() || !wwb_is_plant_wind_vertex(alpha)) {
                    return currentPosition;
                }

                float bend = wwb_smooth_curve(wwb_decode_plant_interaction_alpha(alpha));
                vec3 interaction = wwb_foliage_interaction_offset(basePosition, anchor, bend);
                vec2 windOffset = currentPosition.xz - basePosition.xz;
                float windKeep = mix(1.0, 0.18, interaction.z);
                currentPosition.xz = basePosition.xz + interaction.xy + windOffset * windKeep;
                return currentPosition;
            }

            vec3 wwb_rotate_around_axis(vec3 value, vec3 axis, float angle) {
                float c = cos(angle);
                float s = sin(angle);
                return value * c + cross(axis, value) * s + axis * dot(axis, value) * (1.0 - c);
            }

            vec3 wwb_apply_foliage_wind(
                    vec3 position,
                    float alpha,
                    vec2 windDir,
                    float localGustStrength,
                    float localTurbulence,
                    float localGustLeadingEdge
            ) {
                if (!wwb_should_apply_foliage_wind(alpha)) {
                    return position;
                }

                float bend = wwb_smooth_curve(wwb_decode_wind_alpha(alpha));
                vec2 motionPosition = wwb_foliage_motion_position(position, alpha);
                float t = wwb_is_plant_wind_vertex(alpha) ? wwb_foliage_animation_time(u_WwbTime, motionPosition) : u_WwbTime;
                vec2 crossDir = vec2(-windDir.y, windDir.x);
                vec2 phaseDir = normalize(vec2(0.82, 0.57));
                vec2 phaseCross = vec2(-phaseDir.y, phaseDir.x);
                float along = dot(motionPosition, phaseDir);
                float across = dot(motionPosition, phaseCross);
                float plantWind = wwb_is_plant_wind_vertex(alpha) ? 1.0 : 0.0;
                vec2 gustCell = floor(motionPosition * 0.58);
                vec2 bladeCell = floor(motionPosition * 2.7);
                float gustSeed = wwb_grass_variation_seed(gustCell, vec2(127.1, 311.7));
                float bladeSeed = wwb_grass_variation_seed(bladeCell, vec2(269.5, 183.3));
                float localPhase = plantWind * ((gustSeed - 0.5) * 1.7 + (bladeSeed - 0.5) * 0.16);
                float localTempo = mix(1.0, 0.94 + gustSeed * 0.12, plantWind);
                float localAmplitude = mix(1.0, 0.82 + gustSeed * 0.30 + bladeSeed * 0.08, plantWind);
                float phaseDrift = sin(along * 0.13 - across * 0.09 + t * 0.11) * 0.48
                        + sin(along * -0.07 + across * 0.17 - t * 0.09) * 0.26;
                phaseDrift += localPhase * 0.38;
                float tempoDrift = (1.0 + sin(along * 0.052 + across * 0.041 + t * 0.09 + localPhase * 0.2) * 0.08) * localTempo;
                float amplitudeDrift = (0.84 + 0.22 * wwb_smooth_curve(sin(along * 0.21 + across * 0.14 - t * 0.16 + localPhase) * 0.5 + 0.5)) * localAmplitude;
                float broad = sin(along * 0.35 - t * 1.28 * tempoDrift + sin(across * 0.075 + t * 0.18) * 1.35 + phaseDrift + localPhase);
                float wave = pow(max(0.0, broad), 1.7);
                float ripple = sin(along * 1.08 - t * 3.6 * (1.0 + phaseDrift * 0.035) + across * 0.18 + phaseDrift * 0.7 + localPhase * 1.4) * 0.5 + 0.5;
                float proceduralGust = wwb_smooth_curve(sin(along * 0.10 - t * 0.42 * tempoDrift + across * 0.04 + phaseDrift * 0.35 + localPhase * 0.5) * 0.5 + 0.5);
                float precipitationLoad = clamp(u_WwbWeatherState.x + u_WwbWeatherState.y * 0.45, 0.0, 1.0);
                float stormEnergy = clamp(
                        u_WwbWeatherState.y + localGustStrength * 0.65 + localTurbulence * 0.25,
                        0.0,
                        1.5
                );
                float lullSoftening = 1.0 - wwb_smooth_curve(u_WwbWeatherState.z) * mix(0.35, 0.72, plantWind);
                float leafWind = 1.0 - plantWind;
                float dynamicStrength = (1.0
                        + clamp(u_WwbAmbientWindStrength, 0.0, 1.5) * 0.55
                        + clamp(localGustStrength, 0.0, 1.5) * 0.45) * lullSoftening;
                float aerodynamicResponse = mix(
                        0.68 + stormEnergy * 0.18,
                        1.0 - precipitationLoad * 0.12,
                        plantWind
                );
                float shimmer = sin(motionPosition.x * 2.17 + motionPosition.y * 1.63 + t * 2.1 + phaseDrift + bladeSeed * 2.8);
                float broadSway = mix(0.145, 0.225, plantWind);
                float fineSway = mix(0.055, 0.020, plantWind);
                float originalSway = 0.014 + wave * broadSway * amplitudeDrift
                        + ripple * proceduralGust * fineSway
                        + shimmer * mix(0.012, 0.003, plantWind);
                float impactFlutter = sin(t * 9.7 + bladeSeed * 11.0 + across * 0.31)
                        * sin(t * 6.3 + gustSeed * 7.0 - along * 0.17);
                float steadyWeatherLean = plantWind * precipitationLoad * (0.010 + stormEnergy * 0.014)
                        + localGustLeadingEdge * (0.018 + plantWind * 0.018);
                float leafFlutter = leafWind * impactFlutter
                        * (precipitationLoad * 0.006 + localTurbulence * 0.026 + stormEnergy * 0.012);
                float strength = clamp(
                        (originalSway * dynamicStrength * aerodynamicResponse + steadyWeatherLean + leafFlutter)
                                * bend * wwb_sway_strength_for_alpha(alpha),
                        -0.08,
                        0.42
                );
                float directionNoise = sin(across * 0.22 + t * 0.55 * tempoDrift + phaseDrift * 0.35 + localPhase)
                        * mix(0.18, 0.10, plantWind);
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
                float gustStrength;
                float turbulence;
                float leadingEdge;
                vec2 windDir = wwb_sample_dynamic_wind(windAnchor, gustStrength, turbulence, leadingEdge);
                vec2 crossDir = vec2(-windDir.y, windDir.x);
                float along = dot(windAnchor, windDir);
                float across = dot(windAnchor, crossDir);
                float seed = wwb_grass_variation_seed(floor(windAnchor), vec2(91.7, 53.3));
                float phase = seed * 6.2831853;
                float flutter = sin(t * 1.17 + across * 0.11 + phase * 1.71);
                float windPower = u_WwbAmbientWindStrength + gustStrength;
                float windForce = 0.48 + clamp(windPower, 0.0, 3.0) * 0.34 + flutter * turbulence * 0.12;
                float directionNoise = sin(across * 0.12 + t * 0.31 + phase) * (0.025 + turbulence * 0.15);
                vec2 baseDir = normalize(windDir + crossDir * directionNoise);
                float crossDrift = sin(t * 0.73 + across * 0.09 + phase * 1.37) * (0.025 + turbulence * 0.12);
                vec2 swingDir = normalize(baseDir + crossDir * crossDrift);
                vec3 axis = normalize(vec3(-swingDir.y, 0.0, swingDir.x));
                float chainAngle = clamp((0.006 + windPower * 0.046) * u_WwbLanternSwayStrength * windForce, 0.0, 0.22);
                float bodyAngle = clamp((0.008 + windPower * 0.052) * u_WwbLanternSwayStrength * windForce, 0.0, 0.25);
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

                float yaw = sin(t * 0.49 + phase * 2.3 + across * 0.05)
                        * (0.002 + turbulence * 0.012)
                        * u_WwbLanternSwayStrength
                        * markerStrength;
                float yawCos = cos(yaw);
                float yawSin = sin(yaw);
                rotated.xz = vec2(
                        rotated.x * yawCos - rotated.z * yawSin,
                        rotated.x * yawSin + rotated.z * yawCos
                );

                position += chainEndOffset * markerStrength;
                position += rotated - local;
                position.y += 0.03125 * markerStrength;
                return position;
            }
            """;

    private static final String WIND_POSITION_INJECTION = """
                float wwbFoliageAlpha = _vert_color.a;
                float wwbLocalGustStrength = 0.0;
                float wwbLocalTurbulence = u_WwbWindTurbulence;
                float wwbGustLeadingEdge = 0.0;
                vec2 wwbLocalWindDirection = normalize(u_WwbWindDirection);
                if (wwb_should_apply_foliage_wind(wwbFoliageAlpha)) {
                    vec2 wwbMotionPosition = wwb_foliage_motion_position(position, wwbFoliageAlpha);
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
                // Use the displaced vertex itself for interaction sampling. Flooring randomized
                // model positions can assign vertices from one plant to different block cells.
                vec2 wwbInteractionSample = position.xz;
                vec2 wwbPlantAnchor = floor(_vert_position.xz) + vec2(0.5) + (position.xz - _vert_position.xz);
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
                position = wwb_apply_foliage_interaction(position, wwbFoliageBase, wwbInteractionSample, wwbFoliageAlpha);""";

    private static final String WIND_COLOR_INJECTION = """
                if (wwb_is_plant_wind_vertex(wwbFoliageAlpha) && u_WwbFoliageColorVariationStrength > 0.0) {
                    float wwbVariation = clamp(
                            wwb_foliage_color_variation(wwbPlantAnchor + u_WwbCameraPosition.xz) * u_WwbFoliageColorVariationStrength,
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

        String patched = insertAfter(source, LIGHT_SAMPLER_PATTERN, WIND_UNIFORMS);
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
