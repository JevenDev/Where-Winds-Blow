const float WWB_WORLD_POSITION_PERIOD = 8192.0;

bool wwb_is_plant_wind_vertex(float alpha) {
    float encoded = alpha * 255.0;
    return (encoded >= 16.5 && encoded <= 44.5) || (encoded >= 104.5 && encoded <= 216.5);
}

bool wwb_is_coherent_plant_wind_vertex(float alpha) {
    float encoded = alpha * 255.0;
    return encoded >= 104.5 && encoded <= 216.5;
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
    float code = floor(alpha * 255.0 + 0.5)
            - (wwb_is_coherent_plant_wind_vertex(alpha) ? 105.0 : 17.0);
    return wwb_is_coherent_plant_wind_vertex(alpha)
            ? mod(code, 28.0)
            : clamp(code, 0.0, 27.0);
}

float wwb_decode_plant_wind_alpha(float alpha) {
    return mod(wwb_plant_alpha_code(alpha), 7.0) / 6.0;
}

float wwb_decode_plant_interaction_alpha(float alpha) {
    return floor(wwb_plant_alpha_code(alpha) / 7.0) / 3.0;
}

bool wwb_has_foliage_material() {
    return WWB_HAS_FOLIAGE_MATERIAL;
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

float wwb_cell_with_parity(float coordinate, float expectedParity) {
    float cell = floor(coordinate);
    if (mod(cell, 2.0) != expectedParity) {
        cell += fract(coordinate) < 0.5 ? -1.0 : 1.0;
    }
    return cell;
}

vec2 wwb_wrapped_world_position(vec2 position) {
    return mod(mod(position, WWB_WORLD_POSITION_PERIOD) + WWB_WORLD_POSITION_PERIOD, WWB_WORLD_POSITION_PERIOD);
}

vec2 wwb_wrapped_world_delta(vec2 delta) {
    return mod(delta + WWB_WORLD_POSITION_PERIOD * 0.5, WWB_WORLD_POSITION_PERIOD)
            - WWB_WORLD_POSITION_PERIOD * 0.5;
}

vec2 wwb_foliage_motion_position(vec3 position, float alpha) {
    vec2 worldPosition = wwb_wrapped_world_position(position.xz + u_WwbCameraPosition.xz);
    if (!wwb_is_coherent_plant_wind_vertex(alpha)) {
        return worldPosition;
    }

    float parityCode = floor(
            (floor(alpha * 255.0 + 0.5) - 105.0) / 28.0
    );
    vec2 expectedParity = vec2(mod(parityCode, 2.0), floor(parityCode / 2.0));
    return vec2(
            wwb_cell_with_parity(worldPosition.x, expectedParity.x),
            wwb_cell_with_parity(worldPosition.y, expectedParity.y)
    ) + vec2(0.5);
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
        vec2 relative = wwb_wrapped_world_delta(worldPosition - originTime.xy);
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
        float frontCrest = exp(-pow((normalizedDistance - 0.55) * 2.8, 2.0));
        leadingEdge += localStrength * frontCrest;
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

vec2 wwb_plant_wind_motion(
        vec2 motionPosition,
        float localGustStrength,
        float localTurbulence,
        float gustLeadingEdge
) {
    float t = wwb_foliage_animation_time(u_WwbTime, motionPosition);
    float rain = wwb_smooth_curve(u_WwbWeatherState.x);
    float thunder = wwb_smooth_curve(u_WwbWeatherState.y);
    float lull = 1.0 - wwb_smooth_curve(u_WwbWeatherState.z) * 0.72;
    float ambient = clamp(u_WwbAmbientWindStrength, 0.0, 1.5);
    float gust = clamp(localGustStrength, 0.0, 2.0);
    float turbulence = clamp(localTurbulence, 0.0, 1.5);
    float windResponse = 1.0 - exp(-(ambient + gust * 0.85) * 1.5);
    float patchSeed = wwb_grass_variation_seed(floor(motionPosition * 0.58), vec2(127.1, 311.7));
    float bladeSeed = wwb_grass_variation_seed(floor(motionPosition * 2.7), vec2(269.5, 183.3));
    float localPhase = (patchSeed - 0.5) * 1.2 + (bladeSeed - 0.5) * 0.18;
    float amplitude = 0.88 + patchSeed * 0.18 + bladeSeed * 0.06;

    // keep field phases fixed while the sampled wind direction steers each plant
    vec2 phaseDir = normalize(vec2(0.82, 0.57));
    float along = dot(motionPosition, phaseDir);
    float across = dot(motionPosition, vec2(-phaseDir.y, phaseDir.x));
    float drift = sin(along * 0.09 + across * 0.07 - t * 0.17) * 0.55;
    float clearSway = sin(along * 0.23 + across * 0.08 - t * 0.71 + drift + localPhase) * 0.72
            + sin(along * 0.13 - across * 0.19 - t * 1.13 + localPhase * 0.7) * 0.28;
    float rainSway = sin(along * 0.31 + across * 0.11 - t * 1.67 + drift + localPhase) * 0.68
            + sin(along * 0.57 - across * 0.21 - t * 2.31 + localPhase * 1.3) * 0.32;
    float stormSway = sin(along * 0.38 + across * 0.16 - t * 2.83 + drift + localPhase) * 0.62
            + sin(along * 0.83 - across * 0.29 - t * 4.07 + localPhase * 1.7) * 0.38;

    // blend fixed rhythms so weather changes never rescale the running clock
    float sway = mix(clearSway * 0.14, rainSway * 0.047, rain);
    sway = mix(sway, stormSway * 0.062, thunder);
    float pressure = ambient * (0.035 + rain * 0.020 + thunder * 0.020)
            + gust * (0.085 + rain * 0.020 + thunder * 0.030)
            + gustLeadingEdge * 0.055;
    float flutter = sin(t * 6.13 + bladeSeed * 11.0 + across * 0.31)
            * sin(t * 4.31 + patchSeed * 7.0 - along * 0.17);
    float tipFlutter = flutter * (rain * 0.003 + turbulence * (0.008 + thunder * 0.012));
    float crossSway = sin(across * 0.20 + along * 0.16 - t * 0.61 + localPhase) * 0.7
            + sin(across * 0.37 - along * 0.09 - t * 1.09 + localPhase * 1.4) * 0.3;
    vec2 motion = vec2(
            pressure + (sway + tipFlutter) * windResponse,
            (crossSway * (0.008 + thunder * 0.012) + tipFlutter * 0.35) * windResponse
    ) * amplitude * lull * u_WwbPlantSwayStrength;

    // approach the bend limit smoothly so strong gusts still have room to move
    return motion / sqrt(1.0 + dot(motion, motion) / (0.42 * 0.42));
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
    if (wwb_is_plant_wind_vertex(alpha)) {
        vec2 motion = wwb_plant_wind_motion(motionPosition, localGustStrength, localTurbulence, gustLeadingEdge);
        return clamp((length(motion) * 0.48 + gustLeadingEdge * 0.08)
                * wwb_smooth_curve(bend) * u_WwbPlantSheenStrength, 0.0, 0.32);
    }

    float t = u_WwbTime;
    vec2 phaseDir = normalize(vec2(0.82, 0.57));
    vec2 phaseCross = vec2(-phaseDir.y, phaseDir.x);
    float along = dot(motionPosition, phaseDir);
    float across = dot(motionPosition, phaseCross);
    float phaseDrift = sin(along * 0.13 - across * 0.09 + t * 0.11) * 0.48
            + sin(along * -0.07 + across * 0.17 - t * 0.09) * 0.26;
    float tempoPhase = sin(along * 0.052 + across * 0.041 + t * 0.09);
    float broad = sin(along * 0.35 - t * 1.28 + sin(across * 0.075 + t * 0.18) * 1.35 + phaseDrift + tempoPhase);
    float wave = pow(max(0.0, broad), 1.7);
    float proceduralGust = wwb_smooth_curve(sin(along * 0.10 - t * 0.42 + across * 0.04 + phaseDrift * 0.35 + tempoPhase * 0.4) * 0.5 + 0.5);
    float motionBand = wwb_smooth_curve(wave) * (0.82 + proceduralGust * 0.18);
    float tipLift = wwb_smooth_curve(bend);
    float sheenLull = 1.0 - wwb_smooth_curve(u_WwbWeatherState.z) * 0.35;
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
        float radius = max(abs(interactor.w), 0.001);
        float verticalDistance = max(max(interactor.y - position.y, position.y - motion.x), 0.0);
        float verticalInfluence = wwb_smooth_curve(1.0 - verticalDistance / (0.30 + radius * 0.55));
        // negative radii mark radial forces, which have no feet cutoff
        if (interactor.w >= 0.0) {
            verticalInfluence *= wwb_smooth_curve((position.y - interactor.y) / 0.10);
        }
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

        vec2 direction = delta / max(horizontalDistance, 0.001);
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
    vec2 crossDir = vec2(-windDir.y, windDir.x);
    if (wwb_is_plant_wind_vertex(alpha)) {
        vec2 motion = wwb_plant_wind_motion(motionPosition, localGustStrength, localTurbulence, localGustLeadingEdge);
        position.xz += (windDir * motion.x + crossDir * motion.y) * bend;
        return position;
    }

    float t = u_WwbTime;
    // keep wave phase fixed in world space while wind steers displacement
    vec2 phaseDir = normalize(vec2(0.82, 0.57));
    vec2 phaseCross = vec2(-phaseDir.y, phaseDir.x);
    float along = dot(motionPosition, phaseDir);
    float across = dot(motionPosition, phaseCross);
    vec2 gustCell = floor(motionPosition * 0.58);
    vec2 bladeCell = floor(motionPosition * 2.7);
    float gustSeed = wwb_grass_variation_seed(gustCell, vec2(127.1, 311.7));
    float bladeSeed = wwb_grass_variation_seed(bladeCell, vec2(269.5, 183.3));
    float phaseDrift = sin(along * 0.13 - across * 0.09 + t * 0.11) * 0.48
            + sin(along * -0.07 + across * 0.17 - t * 0.09) * 0.26;
    float tempoPhase = sin(along * 0.052 + across * 0.041 + t * 0.09);
    float amplitudeDrift = 0.84 + 0.22 * wwb_smooth_curve(sin(along * 0.21 + across * 0.14 - t * 0.16) * 0.5 + 0.5);
    float broad = sin(along * 0.35 - t * 1.28 + sin(across * 0.075 + t * 0.18) * 1.35 + phaseDrift + tempoPhase);
    float wave = pow(max(0.0, broad), 1.7);
    float ripple = sin(along * 1.08 - t * 3.6 + across * 0.18 + phaseDrift * 1.4) * 0.5 + 0.5;
    float proceduralGust = wwb_smooth_curve(sin(along * 0.10 - t * 0.42 + across * 0.04 + phaseDrift * 0.35 + tempoPhase * 0.4) * 0.5 + 0.5);
    float precipitationLoad = clamp(u_WwbWeatherState.x + u_WwbWeatherState.y * 0.45, 0.0, 1.0);
    float stormEnergy = clamp(
            u_WwbWeatherState.y + localGustStrength * 0.65 + localTurbulence * 0.25,
            0.0,
            1.5
    );
    float lullSoftening = 1.0 - wwb_smooth_curve(u_WwbWeatherState.z) * 0.35;
    float dynamicStrength = (1.0
            + clamp(u_WwbAmbientWindStrength, 0.0, 1.5) * 0.55
            + clamp(localGustStrength, 0.0, 1.5) * 0.45) * lullSoftening;
    float aerodynamicResponse = 0.68 + stormEnergy * 0.18;
    float shimmer = sin(motionPosition.x * 2.17 + motionPosition.y * 1.63 + t * 2.1 + phaseDrift + bladeSeed * 2.8);
    float originalSway = 0.014 + wave * 0.145 * amplitudeDrift
            + ripple * proceduralGust * 0.055
            + shimmer * 0.012;
    float impactFlutter = sin(t * 9.7 + bladeSeed * 11.0 + across * 0.31)
            * sin(t * 6.3 + gustSeed * 7.0 - along * 0.17);
    float steadyWeatherLean = localGustLeadingEdge * 0.018;
    float leafFlutter = impactFlutter
            * (precipitationLoad * 0.006 + localTurbulence * 0.026 + stormEnergy * 0.012);
    float strength = clamp(
            (originalSway * dynamicStrength * aerodynamicResponse + steadyWeatherLean + leafFlutter)
                    * bend * wwb_sway_strength_for_alpha(alpha),
            -0.08,
            0.42
    );
    float directionNoise = sin(across * 0.22 + t * 0.55 + tempoPhase * 0.5 + phaseDrift * 0.35) * 0.18;
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
    vec2 wrappedPosition = wwb_wrapped_world_position(position.xz + u_WwbCameraPosition.xz);
    vec3 windPosition = vec3(wrappedPosition.x, position.y + u_WwbCameraPosition.y, wrappedPosition.y);
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
