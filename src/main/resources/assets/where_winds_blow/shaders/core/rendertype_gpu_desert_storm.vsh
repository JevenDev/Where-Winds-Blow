#version 150

#moj_import <fog.glsl>

in vec3 Position;

uniform sampler2D Sampler0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 CameraState;
uniform vec2 WorldCell;
uniform vec2 ScreenSize;
uniform vec4 Wind;
uniform vec4 Storm;
uniform float DustTime;
uniform float FlutterTime;
uniform float DustSize;
uniform float Opacity;
uniform float Radius;
uniform float VerticalSpan;
uniform float HeightBase;
uniform int HeightRadius;
uniform float StormLight;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 paletteCoordA;
out vec2 paletteCoordB;
out float materialMix;
out float paletteMix;

const float TWO_PI = 6.28318530717958647692;

float hashValue(vec3 value) {
    return fract(sin(dot(value, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

float smoothFade(float value) {
    value = clamp(value, 0.0, 1.0);
    return value * value * (3.0 - 2.0 * value);
}

void hideVertex() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    vertexDistance = 0.0;
    vertexColor = vec4(0.0);
    paletteCoordA = vec2(0.0);
    paletteCoordB = vec2(0.0);
    materialMix = 0.0;
    paletteMix = 0.0;
}

void main() {
    vec2 cell = Position.xz;
    float lane = Position.y;
    vec2 wrappedWorldCell = mod(WorldCell + cell + vec2(8192.0), vec2(8192.0));
    vec3 seedKey = vec3(wrappedWorldCell, lane * 17.0);
    float densitySeed = hashValue(seedKey + vec3(1.7, 9.2, 4.1));
    if (densitySeed > Storm.w) {
        hideVertex();
        return;
    }

    float xSeed = hashValue(seedKey + vec3(3.1, 5.7, 8.3));
    float zSeed = hashValue(seedKey + vec3(7.9, 2.6, 1.4));
    float phaseSeed = hashValue(seedKey + vec3(4.8, 8.1, 6.2));
    float speedSeed = hashValue(seedKey + vec3(9.4, 1.3, 5.9));
    float sizeSeed = hashValue(seedKey + vec3(6.6, 3.5, 7.2));
    float flutterSeed = hashValue(seedKey + vec3(2.2, 7.4, 9.8));
    float alphaSeed = hashValue(seedKey + vec3(8.7, 4.3, 2.5));

    vec2 baseFromCenter = cell + vec2(0.12) + vec2(xSeed, zSeed) * 0.76;
    float halfSpan = VerticalSpan * 0.5;
    float minimumY = CameraState.y - halfSpan;
    float dustY = minimumY + mod(
        phaseSeed * VerticalSpan - minimumY,
        VerticalSpan
    );

    float travelSpeed = mix(0.036, 0.074, speedSeed);
    float wrapRadius = Radius + 2.0;
    vec2 relativeHorizontal = baseFromCenter - CameraState.xz
        + Wind.xy * DustTime * travelSpeed;
    relativeHorizontal = mod(
        relativeHorizontal + vec2(wrapRadius),
        vec2(wrapRadius * 2.0)
    ) - vec2(wrapRadius);

    float flutterPhase = FlutterTime * 0.055 + flutterSeed * TWO_PI;
    float flutter = sin(flutterPhase) * (0.08 + Wind.w * 0.30 + Wind.z * 0.05);
    relativeHorizontal += vec2(-Wind.y, Wind.x) * flutter;
    vec2 finalFromCenter = relativeHorizontal + CameraState.xz;

    ivec2 heightCoordinate = ivec2(floor(finalFromCenter)) + ivec2(HeightRadius);
    int heightSize = HeightRadius * 2 + 1;
    if (heightCoordinate.x < 0 || heightCoordinate.y < 0
            || heightCoordinate.x >= heightSize || heightCoordinate.y >= heightSize) {
        hideVertex();
        return;
    }

    ivec4 heightBytes = ivec4(round(texelFetch(Sampler0, heightCoordinate, 0) * 255.0));
    ivec2 maximumHeightCoordinate = ivec2(heightSize - 1);
    ivec2 heightCoordinateX = min(heightCoordinate + ivec2(1, 0), maximumHeightCoordinate);
    ivec2 heightCoordinateZ = min(heightCoordinate + ivec2(0, 1), maximumHeightCoordinate);
    ivec2 heightCoordinateXZ = min(heightCoordinate + ivec2(1, 1), maximumHeightCoordinate);
    ivec4 heightBytesX = ivec4(round(texelFetch(Sampler0, heightCoordinateX, 0) * 255.0));
    ivec4 heightBytesZ = ivec4(round(texelFetch(Sampler0, heightCoordinateZ, 0) * 255.0));
    ivec4 heightBytesXZ = ivec4(round(texelFetch(Sampler0, heightCoordinateXZ, 0) * 255.0));
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
    float surfaceY = HeightBase + float(heightBytes.r + heightBytes.g * 256);
    if (terrainVisibility <= 0.001 || dustY <= surfaceY + 0.02) {
        hideVertex();
        return;
    }
    materialMix = clamp((float(heightBytes.b) - 127.0) / 128.0, 0.0, 1.0);

    float horizontalDistance = length(relativeHorizontal);
    float edgeVisibility = 1.0 - smoothFade((horizontalDistance / Radius - 0.72) / 0.28);
    float verticalDistance = abs(dustY - CameraState.y);
    float verticalVisibility = smoothFade(
        clamp((halfSpan - verticalDistance) / 2.0, 0.0, 1.0)
    );
    float visibility = edgeVisibility * verticalVisibility * terrainVisibility;
    if (visibility <= 0.01) {
        hideVertex();
        return;
    }

    vec3 center = vec3(relativeHorizontal.x, dustY - CameraState.y, relativeHorizontal.y);
    float configuredPixelSize = mix(1.0, 3.0, sizeSeed)
        * sqrt(max(DustSize, 0.0));
    float pixelSize = floor(clamp(configuredPixelSize, 1.0, 4.0) + 0.5);
    int cornerIndex = gl_VertexID % 4;
    vec2 pixelCorner;
    if (cornerIndex == 0) {
        pixelCorner = vec2(-1.0, -1.0);
    } else if (cornerIndex == 1) {
        pixelCorner = vec2(1.0, -1.0);
    } else if (cornerIndex == 2) {
        pixelCorner = vec2(1.0, 1.0);
    } else {
        pixelCorner = vec2(-1.0, 1.0);
    }

    vec4 clipPosition = ProjMat * ModelViewMat * vec4(center, 1.0);
    clipPosition.xy += pixelCorner * pixelSize
        / max(ScreenSize, vec2(1.0)) * clipPosition.w;

    vec2 paletteSeedA = vec2(
        hashValue(seedKey + vec3(2.4, 4.6, 8.8)),
        hashValue(seedKey + vec3(9.1, 3.7, 5.3))
    );
    vec2 paletteSeedB = vec2(
        hashValue(seedKey + vec3(6.3, 8.5, 1.2)),
        hashValue(seedKey + vec3(3.8, 0.9, 7.6))
    );
    paletteCoordA = (floor(paletteSeedA * 16.0) + vec2(0.5)) / 16.0;
    paletteCoordB = (floor(paletteSeedB * 16.0) + vec2(0.5)) / 16.0;
    paletteMix = smoothFade(hashValue(seedKey + vec3(1.1, 5.4, 9.7)));

    float brightness = mix(0.76, 1.0, hashValue(seedKey + vec3(4.2, 7.7, 2.9)));
    float alpha = clamp(
        mix(0.26, 0.58, alphaSeed) * Opacity * visibility,
        0.0,
        0.62
    );
    vertexColor = vec4(vec3(brightness * StormLight), alpha);
    vertexDistance = fog_distance(center, FogShape);
    gl_Position = clipPosition;
}
