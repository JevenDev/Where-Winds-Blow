#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;
uniform float WindTime;
uniform float WeatherWindPower;
uniform float PlantWindSwayStrength;
uniform float LeafWindSwayStrength;
uniform float PlantWindSheenStrength;
uniform float LeafWindSheenStrength;
uniform vec3 CameraPosition;
uniform int FoliageInteractorCount;
uniform vec4 FoliageInteractor0;
uniform vec4 FoliageInteractor1;
uniform vec4 FoliageInteractor2;
uniform vec4 FoliageInteractor3;
uniform vec4 FoliageInteractor4;
uniform vec4 FoliageInteractor5;
uniform vec4 FoliageInteractor6;
uniform vec4 FoliageInteractor7;
uniform vec4 FoliageInteractor8;
uniform vec4 FoliageInteractor9;
uniform vec4 FoliageInteractor10;
uniform vec4 FoliageInteractor11;
uniform vec4 FoliageInteractor12;
uniform vec4 FoliageInteractor13;
uniform vec4 FoliageInteractor14;
uniform vec4 FoliageInteractor15;
uniform vec4 FoliageInteractorStrengths0;
uniform vec4 FoliageInteractorStrengths1;
uniform vec4 FoliageInteractorStrengths2;
uniform vec4 FoliageInteractorStrengths3;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out float windSheen;

bool isPlantWindAlpha(float alpha) {
    float encoded = alpha * 255.0;
    return encoded >= 16.5 && encoded <= 44.5;
}

bool isLeafWindAlpha(float alpha) {
    float encoded = alpha * 255.0;
    return encoded >= 44.5 && encoded <= 72.5;
}

bool isFoliageWindVertex(float alpha) {
    return isPlantWindAlpha(alpha) || isLeafWindAlpha(alpha);
}

float plantAlphaCode(float alpha) {
    return clamp(floor(alpha * 255.0 + 0.5) - 17.0, 0.0, 27.0);
}

float decodePlantInteractionAlpha(float alpha) {
    return floor(plantAlphaCode(alpha) / 7.0) / 3.0;
}

float decodeWindAlpha(float alpha) {
    float encoded = alpha * 255.0;
    if (isLeafWindAlpha(alpha)) {
        return clamp((encoded - 45.0) / 27.0, 0.0, 1.0);
    }

    return mod(plantAlphaCode(alpha), 7.0) / 6.0;
}

float windSwayStrengthForAlpha(float alpha) {
    return isLeafWindAlpha(alpha) ? LeafWindSwayStrength : PlantWindSwayStrength;
}

float windSheenStrengthForAlpha(float alpha) {
    return isLeafWindAlpha(alpha) ? LeafWindSheenStrength : PlantWindSheenStrength;
}

float smoothCurve(float value) {
    value = clamp(value, 0.0, 1.0);
    return value * value * (3.0 - 2.0 * value);
}

vec4 foliageInteractorAt(int index) {
    if (index == 0) return FoliageInteractor0;
    if (index == 1) return FoliageInteractor1;
    if (index == 2) return FoliageInteractor2;
    if (index == 3) return FoliageInteractor3;
    if (index == 4) return FoliageInteractor4;
    if (index == 5) return FoliageInteractor5;
    if (index == 6) return FoliageInteractor6;
    if (index == 7) return FoliageInteractor7;
    if (index == 8) return FoliageInteractor8;
    if (index == 9) return FoliageInteractor9;
    if (index == 10) return FoliageInteractor10;
    if (index == 11) return FoliageInteractor11;
    if (index == 12) return FoliageInteractor12;
    if (index == 13) return FoliageInteractor13;
    if (index == 14) return FoliageInteractor14;
    return FoliageInteractor15;
}

float foliageInteractorStrengthAt(int index) {
    if (index < 4) return FoliageInteractorStrengths0[index];
    if (index < 8) return FoliageInteractorStrengths1[index - 4];
    if (index < 12) return FoliageInteractorStrengths2[index - 8];
    return FoliageInteractorStrengths3[index - 12];
}

vec3 applyFoliageInteractors(vec3 pos, vec2 anchor, float bend) {
    if (FoliageInteractorCount <= 0 || bend <= 0.0) {
        return pos;
    }

    vec2 totalOffset = vec2(0.0);
    for (int index = 0; index < 16; index++) {
        if (index >= FoliageInteractorCount) {
            break;
        }

        vec4 interactor = foliageInteractorAt(index);
        if (pos.y < interactor.y - 0.15) {
            continue;
        }

        float radius = max(interactor.w, 0.001);
        vec2 delta = anchor - interactor.xz;
        float horizontalDistance = length(delta);
        float horizontalInfluence = smoothCurve(1.0 - horizontalDistance / radius);
        if (horizontalInfluence <= 0.0) {
            continue;
        }

        vec2 direction = horizontalDistance > 0.001 ? delta / horizontalDistance : vec2(1.0, 0.0);
        totalOffset += direction * horizontalInfluence * foliageInteractorStrengthAt(index);
    }

    float offsetLength = length(totalOffset);
    if (offsetLength > 1.0) {
        totalOffset /= offsetLength;
    }

    pos.xz += totalOffset * bend * 0.34;
    return pos;
}

float grassVariationSeed(vec2 cell, vec2 salt) {
    return fract(sin(dot(cell, salt)) * 43758.5453);
}

void main() {
    vec3 pos = Position + ChunkOffset;
    windSheen = 0.0;

    bool windMarker = isFoliageWindVertex(Color.a);
    float interactionBend = isPlantWindAlpha(Color.a) ? smoothCurve(decodePlantInteractionAlpha(Color.a)) : 0.0;
    bool interactionMarker = interactionBend > 0.0;

    if (windMarker || interactionMarker) {
        vec3 basePos = pos;
        vec3 worldBasePos = basePos + CameraPosition;

        if (windMarker) {
            float windBend = smoothCurve(decodeWindAlpha(Color.a));
            float swayStrength = windSwayStrengthForAlpha(Color.a);
            float sheenStrength = windSheenStrengthForAlpha(Color.a);
            float weatherStrength = 1.0 + WeatherWindPower * 0.55;
            float t = WindTime;
            vec3 windPos = pos + CameraPosition;
            vec2 windDir = normalize(vec2(0.821188, 0.570658));
            vec2 crossDir = vec2(-windDir.y, windDir.x);
            float along = dot(windPos.xz, windDir);
            float across = dot(windPos.xz, crossDir);
            float plantWind = isPlantWindAlpha(Color.a) ? 1.0 : 0.0;
            vec2 gustCell = floor(windPos.xz * 0.58);
            vec2 bladeCell = floor(windPos.xz * 2.7);
            float gustSeed = grassVariationSeed(gustCell, vec2(127.1, 311.7));
            float bladeSeed = grassVariationSeed(bladeCell, vec2(269.5, 183.3));
            float localPhase = plantWind * ((gustSeed - 0.5) * 3.2 + (bladeSeed - 0.5) * 0.7);
            float localTempo = mix(1.0, 0.82 + gustSeed * 0.36, plantWind);
            float localAmplitude = mix(1.0, 0.62 + gustSeed * 0.55 + bladeSeed * 0.18, plantWind);
            float phaseDrift = sin(along * 0.13 - across * 0.09 + t * 0.11) * 0.48
                    + sin(along * -0.07 + across * 0.17 - t * 0.09) * 0.26;
            phaseDrift += localPhase * 0.38;
            float tempoDrift = (1.0 + sin(along * 0.052 + across * 0.041 + t * 0.09 + localPhase * 0.2) * 0.08) * localTempo;
            float amplitudeDrift = (0.84 + 0.22 * smoothCurve(sin(along * 0.21 + across * 0.14 - t * 0.16 + localPhase) * 0.5 + 0.5)) * localAmplitude;
            float broad = sin(along * 0.35 - t * 1.28 * tempoDrift + sin(across * 0.075 + t * 0.18) * 1.35 + phaseDrift + localPhase);
            float wave = pow(max(0.0, broad), 1.7);
            float ripple = sin(along * 1.08 - t * 3.6 * (1.0 + phaseDrift * 0.035) + across * 0.18 + phaseDrift * 0.7 + localPhase * 1.4) * 0.5 + 0.5;
            float gust = smoothCurve(sin(along * 0.10 - t * 0.42 * tempoDrift + across * 0.04 + phaseDrift * 0.35 + localPhase * 0.5) * 0.5 + 0.5);
            float fieldWarp = sin(along * 0.075 + across * 0.115 + t * 0.21) * 0.75
                    + sin(along * 0.16 - across * 0.085 - t * 0.13) * 0.36;
            float wavePhase = along * 0.34 - t * 1.52 * tempoDrift + sin(across * 0.055 + t * 0.22) * 1.1 + fieldWarp + phaseDrift * 0.55;
            float waveFace = sin(wavePhase) * 0.5 + 0.5;
            float leadingCrest = smoothCurve(smoothstep(0.46, 0.86, waveFace));
            float trailingWash = pow(max(0.0, sin(wavePhase - 0.62)), 2.6) * 0.35;
            float patchBreakup = 0.58 + 0.42 * smoothCurve(sin(along * 0.23 + across * 0.31 - t * 0.34 + phaseDrift * 0.45) * 0.5 + 0.5);
            float crossFeather = 0.72 + 0.28 * sin(across * 0.19 + t * 0.47 + phaseDrift * 0.5);
            float sheenBand = max(leadingCrest, trailingWash) * patchBreakup * crossFeather;
            float tipLift = smoothCurve(windBend);
            windSheen = clamp((sheenBand * 0.30 + ripple * gust * 0.035) * tipLift * sheenStrength, 0.0, 0.35);
            float shimmer = sin(windPos.x * 2.17 + windPos.z * 1.63 + t * 2.1 + phaseDrift + bladeSeed * 2.8) * 0.012;
            float strength = (0.018 + wave * 0.145 * amplitudeDrift + ripple * gust * 0.055 + shimmer) * windBend * weatherStrength * swayStrength;
            float directionNoise = sin(across * 0.22 + t * 0.55 * tempoDrift + phaseDrift * 0.35 + localPhase) * (0.18 + plantWind * 0.08);
            vec2 dir = normalize(windDir + crossDir * directionNoise);
            pos.xz += dir * strength;
        }

        if (interactionMarker) {
            vec2 plantAnchor = floor(Position.xz) + vec2(0.5) + ChunkOffset.xz + CameraPosition.xz;
            vec3 interactedPos = applyFoliageInteractors(worldBasePos, plantAnchor, interactionBend);
            pos.xz += interactedPos.xz - worldBasePos.xz;
        }
    }

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = vec4(Color.rgb, 1.0) * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
