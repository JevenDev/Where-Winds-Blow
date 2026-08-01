#version 150

#moj_import <fog.glsl>

in vec3 Position;

uniform sampler2D Sampler1;
uniform sampler2D Sampler4;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 CameraState;
uniform vec2 WorldCell;
uniform vec3 CameraLeft;
uniform vec3 CameraUp;
uniform vec4 Wind;
uniform vec4 Storm;
uniform float DustTime;
uniform float DustSize;
uniform float Opacity;
uniform float Radius;
uniform float VerticalSpan;
uniform float HeightBase;
uniform int HeightRadius;
uniform float CollisionBase;
uniform float StormLight;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord;
out vec2 paletteCoordA;
out vec2 paletteCoordB;
out float materialMix;
out float paletteMix;

float hashValue(vec3 value) {
    return fract(sin(dot(value, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

float smoothFade(float value) {
    value = clamp(value, 0.0, 1.0);
    return value * value * (3.0 - 2.0 * value);
}

float valueNoise(float time, float seed) {
    float cell = floor(time);
    float blend = smoothFade(fract(time));
    float startValue = hashValue(vec3(cell, seed, 11.7));
    float endValue = hashValue(vec3(cell + 1.0, seed, 11.7));
    return mix(startValue, endValue, blend);
}

bool collisionAt(vec2 fromCenter, float worldY) {
    int layer = int(floor(worldY - CollisionBase));
    if (layer < 0 || layer >= 32) {
        return false;
    }

    ivec2 coordinate = ivec2(floor(fromCenter)) + ivec2(HeightRadius);
    int collisionSize = HeightRadius * 2 + 1;
    if (coordinate.x < 0 || coordinate.y < 0
            || coordinate.x >= collisionSize || coordinate.y >= collisionSize) {
        return false;
    }

    ivec4 packedCollision = ivec4(round(texelFetch(Sampler4, coordinate, 0) * 255.0));
    int byteIndex = layer / 8;
    int packedByte = packedCollision[byteIndex];
    int bit = 1 << (layer - byteIndex * 8);
    return (packedByte & bit) != 0;
}

void hideVertex() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    vertexDistance = 0.0;
    vertexColor = vec4(0.0);
    texCoord = vec2(0.0);
    paletteCoordA = vec2(0.0);
    paletteCoordB = vec2(0.0);
    materialMix = 0.0;
    paletteMix = 0.0;
}

void main() {
    vec2 cell = Position.xz;
    float lane = Position.y;
    vec2 wrappedWorldCell = mod(WorldCell + cell + vec2(8192.0), vec2(8192.0));
    vec3 seedKey = vec3(wrappedWorldCell, lane * 19.0);
    float densitySeed = hashValue(seedKey + vec3(1.7, 9.2, 4.1));
    float densityVisibility = smoothFade((Storm.w - densitySeed) / 0.10);
    if (densityVisibility <= 0.001) {
        hideVertex();
        return;
    }

    float xSeed = hashValue(seedKey + vec3(3.1, 5.7, 8.3));
    float zSeed = hashValue(seedKey + vec3(7.9, 2.6, 1.4));
    float heightSeed = hashValue(seedKey + vec3(4.8, 8.1, 6.2));
    float speedSeed = hashValue(seedKey + vec3(9.4, 1.3, 5.9));
    float sizeSeed = hashValue(seedKey + vec3(6.6, 3.5, 7.2));
    float layerSeed = hashValue(seedKey + vec3(2.2, 7.4, 9.8));
    float alphaSeed = hashValue(seedKey + vec3(8.7, 4.3, 2.5));
    bool groundLayer = layerSeed < 0.34;

    vec2 baseFromCenter = cell + vec2(0.08) + vec2(xSeed, zSeed) * 0.84;
    // Live weather multipliers are integrated into DustTime on the CPU. Keeping this per-cluster
    // rate fixed prevents wind or gust changes from multiplying absolute time and causing bursts.
    float travelSpeed = mix(0.045, 0.085, speedSeed);
    float motionSeed = hashValue(seedKey + vec3(7.3, 2.1, 9.6)) * 1024.0;
    float slowLiftNoise = valueNoise(
        DustTime * mix(0.018, 0.034, zSeed),
        motionSeed
    );
    float detailLiftNoise = valueNoise(
        DustTime * mix(0.045, 0.075, xSeed),
        motionSeed + 37.2
    );
    float gustLiftNoise = valueNoise(
        DustTime * mix(0.012, 0.024, speedSeed),
        motionSeed + 83.7
    );

    float wrapRadius = Radius + 4.0;
    vec2 relativeHorizontal = baseFromCenter - CameraState.xz
        + Wind.xy * DustTime * travelSpeed;
    relativeHorizontal = mod(
        relativeHorizontal + vec2(wrapRadius),
        vec2(wrapRadius * 2.0)
    ) - vec2(wrapRadius);
    vec2 finalFromCenter = relativeHorizontal + CameraState.xz;

    ivec2 heightCoordinate = ivec2(floor(finalFromCenter)) + ivec2(HeightRadius);
    int heightSize = HeightRadius * 2 + 1;
    if (heightCoordinate.x < 0 || heightCoordinate.y < 0
            || heightCoordinate.x >= heightSize || heightCoordinate.y >= heightSize) {
        hideVertex();
        return;
    }

    vec4 heightSample = texelFetch(Sampler1, heightCoordinate, 0);
    ivec4 heightBytes = ivec4(round(heightSample * 255.0));
    ivec2 maximumHeightCoordinate = ivec2(heightSize - 1);
    ivec2 heightCoordinateX = min(
        heightCoordinate + ivec2(1, 0),
        maximumHeightCoordinate
    );
    ivec2 heightCoordinateZ = min(
        heightCoordinate + ivec2(0, 1),
        maximumHeightCoordinate
    );
    ivec2 heightCoordinateXZ = min(
        heightCoordinate + ivec2(1, 1),
        maximumHeightCoordinate
    );
    ivec4 heightBytesX = ivec4(round(texelFetch(Sampler1, heightCoordinateX, 0) * 255.0));
    ivec4 heightBytesZ = ivec4(round(texelFetch(Sampler1, heightCoordinateZ, 0) * 255.0));
    ivec4 heightBytesXZ = ivec4(round(texelFetch(Sampler1, heightCoordinateXZ, 0) * 255.0));
    vec2 terrainFraction = fract(finalFromCenter);
    float desertVisibilityNear = mix(
        step(63.5, float(heightBytes.b)),
        step(63.5, float(heightBytesX.b)),
        terrainFraction.x
    );
    float desertVisibilityFar = mix(
        step(63.5, float(heightBytesZ.b)),
        step(63.5, float(heightBytesXZ.b)),
        terrainFraction.x
    );
    float terrainVisibility = mix(
        desertVisibilityNear,
        desertVisibilityFar,
        terrainFraction.y
    );
    if (terrainVisibility <= 0.001) {
        hideVertex();
        return;
    }
    float surfaceY = HeightBase + float(heightBytes.r + heightBytes.g * 256);
    materialMix = clamp((float(heightBytes.b) - 127.0) / 128.0, 0.0, 1.0);

    float halfSpan = VerticalSpan * 0.5;
    float topY = CameraState.y + halfSpan;
    if (surfaceY >= topY) {
        hideVertex();
        return;
    }

    float verticalAmplitude = groundLayer
        ? 0.16 + Wind.w * 0.28 + Storm.z * 0.20
        : 0.30 + Wind.w * 0.48 + Storm.z * 0.34;
    float randomLift = (slowLiftNoise - 0.5) * 1.45
        + (detailLiftNoise - 0.5) * 0.55;
    float verticalMotion = randomLift * verticalAmplitude;
    float gustLift = smoothFade(gustLiftNoise)
        * Storm.z * (groundLayer ? 0.28 : 0.62);
    verticalMotion += gustLift;
    float dustY;
    if (groundLayer) {
        float groundHeight = mix(0.12, 1.45 + Storm.y * 0.85, heightSeed)
            + verticalMotion;
        dustY = surfaceY + max(0.08, groundHeight);
    } else {
        float bottomY = max(surfaceY + 0.18, CameraState.y - halfSpan);
        if (bottomY >= topY - 0.08) {
            hideVertex();
            return;
        }
        dustY = clamp(
            mix(bottomY, topY, heightSeed) + verticalMotion,
            bottomY + 0.04,
            topY - 0.04
        );
    }
    if (dustY <= surfaceY + 0.04) {
        hideVertex();
        return;
    }

    vec3 rayEnd = vec3(
        relativeHorizontal.x,
        dustY - CameraState.y,
        relativeHorizontal.y
    );
    int raySteps = int(clamp(ceil(length(rayEnd) / 0.58), 1.0, 28.0));
    for (int rayStep = 1; rayStep <= 28; rayStep++) {
        if (rayStep > raySteps) {
            break;
        }
        float rayProgress = float(rayStep) / float(raySteps);
        vec2 rayFromCenter = CameraState.xz + relativeHorizontal * rayProgress;
        float rayWorldY = mix(CameraState.y, dustY, rayProgress);
        if (collisionAt(rayFromCenter, rayWorldY)) {
            hideVertex();
            return;
        }
    }

    float horizontalDistance = length(relativeHorizontal);
    float edgeVisibility = 1.0 - smoothFade((horizontalDistance / Radius - 0.70) / 0.30);
    float verticalDistance = abs(dustY - CameraState.y);
    float verticalVisibility = smoothFade(
        clamp((halfSpan - verticalDistance) / 2.0, 0.0, 1.0)
    );
    float visibility = edgeVisibility * verticalVisibility
        * densityVisibility * terrainVisibility;
    if (visibility <= 0.01) {
        hideVertex();
        return;
    }

    int cornerIndex = gl_VertexID % 4;
    vec2 corner;
    vec2 uvCorner;
    if (cornerIndex == 0) {
        corner = vec2(-1.0, -1.0);
        uvCorner = vec2(0.0, 1.0);
    } else if (cornerIndex == 1) {
        corner = vec2(1.0, -1.0);
        uvCorner = vec2(1.0, 1.0);
    } else if (cornerIndex == 2) {
        corner = vec2(1.0, 1.0);
        uvCorner = vec2(1.0, 0.0);
    } else {
        corner = vec2(-1.0, 1.0);
        uvCorner = vec2(0.0, 0.0);
    }

    if (hashValue(seedKey + vec3(0.8, 1.9, 5.2)) > 0.5) {
        uvCorner.x = 1.0 - uvCorner.x;
    }
    if (hashValue(seedKey + vec3(7.4, 3.2, 0.6)) > 0.5) {
        uvCorner.y = 1.0 - uvCorner.y;
    }
    texCoord = uvCorner;

    vec3 horizontalWind = vec3(Wind.x, 0.0, Wind.y);
    if (length(horizontalWind) < 0.001) {
        horizontalWind = vec3(1.0, 0.0, 0.0);
    } else {
        horizontalWind = normalize(horizontalWind);
    }
    vec2 screenWind = vec2(dot(horizontalWind, CameraLeft), dot(horizontalWind, CameraUp));
    if (length(screenWind) < 0.08) {
        screenWind = vec2(1.0, 0.0);
    } else {
        screenWind = normalize(screenWind);
    }
    vec2 screenCrossWind = vec2(-screenWind.y, screenWind.x);
    float halfLength = groundLayer
        ? mix(1.15, 2.55, sizeSeed) * (1.0 + Storm.z * 0.28)
        : mix(0.55, 1.65, sizeSeed) * (1.0 + Wind.z * 0.14);
    float halfWidth = groundLayer
        ? mix(0.34, 0.82, alphaSeed)
        : mix(0.24, 0.66, alphaSeed);
    float sizePulse = 1.0 + (detailLiftNoise - 0.5) * 2.0
        * (0.035 + Storm.z * 0.045 + Wind.w * 0.025);
    halfLength *= DustSize;
    halfWidth *= DustSize * sizePulse;
    vec2 screenOffset = screenWind * corner.x * halfLength
        + screenCrossWind * corner.y * halfWidth;
    vec3 billboardOffset = CameraLeft * screenOffset.x + CameraUp * screenOffset.y;
    vec3 center = vec3(relativeHorizontal.x, dustY - CameraState.y, relativeHorizontal.y);
    vec3 vertexPosition = center + billboardOffset;

    vec2 paletteSeedA = vec2(
        hashValue(seedKey + vec3(2.4, 4.6, 8.8)),
        hashValue(seedKey + vec3(9.1, 3.7, 5.3))
    );
    vec2 paletteSeedB = vec2(
        hashValue(seedKey + vec3(6.3, 8.5, 1.2)),
        hashValue(seedKey + vec3(3.8, 0.9, 7.6))
    );
    // Center the lookups on texels for vanilla's 16x16 sand textures. Coordinates remain valid
    // for higher-resolution resource-pack replacements and still sample their actual palette.
    paletteCoordA = (floor(paletteSeedA * 16.0) + vec2(0.5)) / 16.0;
    paletteCoordB = (floor(paletteSeedB * 16.0) + vec2(0.5)) / 16.0;
    paletteMix = smoothFade(hashValue(seedKey + vec3(1.1, 5.4, 9.7)));

    float brightness = mix(0.68, 1.16, hashValue(seedKey + vec3(4.2, 7.7, 2.9)));
    float layerAlpha = groundLayer ? 0.92 : 0.78;
    float alpha = clamp(
        mix(0.74, 1.0, alphaSeed) * layerAlpha * Opacity * visibility,
        0.0,
        0.96
    );
    vertexColor = vec4(vec3(brightness * StormLight), alpha);
    vertexDistance = fog_distance(center, FogShape);
    gl_Position = ProjMat * ModelViewMat * vec4(vertexPosition, 1.0);
}
