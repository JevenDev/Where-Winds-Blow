#version 150

#moj_import <fog.glsl>

in vec3 Position;

uniform sampler2D Sampler1;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 CameraState;
uniform vec2 WorldCell;
uniform vec3 CameraLeft;
uniform vec3 CameraUp;
uniform vec4 Wind;
uniform vec4 Weather;
uniform float SnowTime;
uniform float Radius;
uniform float VerticalSpan;
uniform float HeightBase;
uniform int HeightRadius;
uniform float SnowLight;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord;

const float PI = 3.14159265358979323846;
const float TWO_PI = PI * 2.0;
const vec2 TEXTURE_SIZE = vec2(64.0, 256.0);

float hashValue(vec3 value) {
    return fract(sin(dot(value, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

float smoothFade(float value) {
    value = clamp(value, 0.0, 1.0);
    return value * value * (3.0 - 2.0 * value);
}

vec2 flakeCenter(int index) {
    if (index == 0) return vec2(30.0, 45.0);
    if (index == 1) return vec2(25.0, 54.0);
    if (index == 2) return vec2(44.0, 66.0);
    if (index == 3) return vec2(12.0, 80.0);
    if (index == 4) return vec2(55.0, 84.0);
    if (index == 5) return vec2(13.0, 93.0);
    if (index == 6) return vec2(46.0, 105.0);
    if (index == 7) return vec2(41.0, 156.0);
    if (index == 8) return vec2(14.0, 174.0);
    if (index == 9) return vec2(42.0, 194.0);
    if (index == 10) return vec2(22.0, 209.0);
    return vec2(12.0, 231.0);
}

void hideVertex() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    vertexDistance = 0.0;
    vertexColor = vec4(0.0);
    texCoord = vec2(0.0);
}

void main() {
    vec2 cell = Position.xz;
    float lane = Position.y;
    vec2 wrappedWorldCell = mod(WorldCell + cell + vec2(8192.0), vec2(8192.0));
    vec3 seedKey = vec3(wrappedWorldCell, lane * 17.0);
    float densitySeed = hashValue(seedKey + vec3(1.7, 9.2, 4.1));
    if (densitySeed > Weather.z) {
        hideVertex();
        return;
    }

    float xSeed = hashValue(seedKey + vec3(3.1, 5.7, 8.3));
    float zSeed = hashValue(seedKey + vec3(7.9, 2.6, 1.4));
    float phaseSeed = hashValue(seedKey + vec3(4.8, 8.1, 6.2));
    float speedSeed = hashValue(seedKey + vec3(9.4, 1.3, 5.9));
    float sizeSeed = hashValue(seedKey + vec3(6.6, 3.5, 7.2));
    float tiltSeed = hashValue(seedKey + vec3(2.2, 7.4, 9.8));
    float alphaSeed = hashValue(seedKey + vec3(8.7, 4.3, 2.5));

    vec2 baseFromCenter = cell + vec2(0.12) + vec2(xSeed, zSeed) * 0.76;
    float halfSpan = VerticalSpan * 0.5;
    float fallSpeed = mix(0.036, 0.074, speedSeed);
    float minimumY = CameraState.y - halfSpan;
    float flakeY = minimumY + mod(
        phaseSeed * VerticalSpan - SnowTime * fallSpeed - minimumY,
        VerticalSpan
    );
    float fallPhase = clamp((CameraState.y + halfSpan - flakeY) / VerticalSpan, 0.0, 1.0);

    float driftRange = 0.45 + Wind.z * 2.3 + Weather.x * 0.85 + Weather.y * 0.75;
    float flutterPhase = SnowTime * 0.055 + tiltSeed * TWO_PI;
    float flutter = sin(flutterPhase) * (0.08 + Wind.w * 0.30 + Wind.z * 0.05);
    vec2 crossWind = vec2(-Wind.y, Wind.x);
    vec2 finalFromCenter = baseFromCenter
        + Wind.xy * (fallPhase - 0.5) * driftRange
        + crossWind * flutter;
    vec2 relativeHorizontal = finalFromCenter - CameraState.xz;

    ivec2 heightCoordinate = ivec2(floor(finalFromCenter)) + ivec2(HeightRadius);
    int heightSize = HeightRadius * 2 + 1;
    if (heightCoordinate.x < 0 || heightCoordinate.y < 0
            || heightCoordinate.x >= heightSize || heightCoordinate.y >= heightSize) {
        hideVertex();
        return;
    }

    vec4 heightSample = texelFetch(Sampler1, heightCoordinate, 0);
    ivec4 heightBytes = ivec4(round(heightSample * 255.0));
    float surfaceY = HeightBase + float(heightBytes.r + heightBytes.g * 256);
    if (heightBytes.b < 128 || flakeY <= surfaceY + 0.02) {
        hideVertex();
        return;
    }

    float horizontalDistance = length(relativeHorizontal);
    float edge = (horizontalDistance / Radius - 0.72) / 0.28;
    float edgeVisibility = 1.0 - smoothFade(edge);
    float verticalDistance = abs(flakeY - CameraState.y);
    float verticalVisibility = smoothFade(
        clamp((halfSpan - verticalDistance) / 2.0, 0.0, 1.0)
    );
    float visibility = edgeVisibility * verticalVisibility;
    if (visibility <= 0.01) {
        hideVertex();
        return;
    }

    int cornerIndex = gl_VertexID % 4;
    vec2 corner;
    vec2 uvCorner;
    if (cornerIndex == 0) {
        corner = vec2(-1.0, -1.0);
        uvCorner = vec2(-1.0, 1.0);
    } else if (cornerIndex == 1) {
        corner = vec2(1.0, -1.0);
        uvCorner = vec2(1.0, 1.0);
    } else if (cornerIndex == 2) {
        corner = vec2(1.0, 1.0);
        uvCorner = vec2(1.0, -1.0);
    } else {
        corner = vec2(-1.0, 1.0);
        uvCorner = vec2(-1.0, -1.0);
    }

    float size = mix(0.10, 0.19, sizeSeed);
    float tilt = tiltSeed * TWO_PI + sin(flutterPhase * 0.73) * 0.32;
    float tiltCos = cos(tilt);
    float tiltSin = sin(tilt);
    vec2 rotatedCorner = vec2(
        corner.x * tiltCos + corner.y * tiltSin,
        corner.y * tiltCos - corner.x * tiltSin
    );
    float heightScale = 1.0 + size * 1.8;
    vec3 billboardOffset = CameraLeft * rotatedCorner.x * size
        + CameraUp * rotatedCorner.y * size * heightScale;
    vec3 center = vec3(relativeHorizontal.x, flakeY - CameraState.y, relativeHorizontal.y);
    vec3 vertexPosition = center + billboardOffset;

    int flakeIndex = int(floor(
        hashValue(seedKey + vec3(5.5, 6.9, 3.7)) * 12.0
    ));
    vec2 atlasCenter = flakeCenter(clamp(flakeIndex, 0, 11));
    texCoord = (atlasCenter + uvCorner * 2.5) / TEXTURE_SIZE;

    float alpha = clamp(
        mix(0.48, 0.88, alphaSeed) * Weather.w * visibility,
        0.0,
        0.92
    );
    vertexColor = vec4(vec3(0.94, 0.97, 1.0) * SnowLight, alpha);
    vertexDistance = fog_distance(center, FogShape);
    gl_Position = ProjMat * ModelViewMat * vec4(vertexPosition, 1.0);
}
