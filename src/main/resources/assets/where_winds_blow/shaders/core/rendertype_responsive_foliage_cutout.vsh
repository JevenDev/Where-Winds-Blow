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
uniform float GameTime;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

float decodeUnit(float encoded) {
    return clamp(encoded * 0.5 + 0.5, 0.0, 1.0);
}

float decodeWindAlpha(float alpha) {
    return clamp((alpha * 255.0 - 96.0) / 158.0, 0.0, 1.0);
}

float smoothCurve(float value) {
    value = clamp(value, 0.0, 1.0);
    return value * value * (3.0 - 2.0 * value);
}

void main() {
    vec3 pos = Position + ChunkOffset;

    bool normalMarker = Normal.x < -0.98 && Normal.y < -0.98;
    bool alphaMarker = Color.a < 0.999 && Color.a > 0.32;

    if (normalMarker || alphaMarker) {
        float bend = normalMarker ? decodeUnit(Normal.z) : decodeWindAlpha(Color.a);
        bend = smoothCurve(bend);
        float t = GameTime * 1200.0;
        vec2 windDir = normalize(vec2(0.82, 0.57));
        vec2 crossDir = vec2(-windDir.y, windDir.x);
        float along = dot(pos.xz, windDir);
        float across = dot(pos.xz, crossDir);
        float broad = sin(along * 0.35 - t * 1.28 + sin(across * 0.075 + t * 0.18) * 1.35);
        float wave = pow(max(0.0, broad), 1.7);
        float ripple = sin(along * 1.08 - t * 3.6 + across * 0.18) * 0.5 + 0.5;
        float gust = smoothCurve(sin(along * 0.10 - t * 0.42 + across * 0.04) * 0.5 + 0.5);
        float shimmer = sin(pos.x * 2.17 + pos.z * 1.63 + t * 2.1) * 0.012;
        float strength = (0.018 + wave * 0.145 + ripple * gust * 0.055 + shimmer) * bend;
        float directionNoise = sin(across * 0.22 + t * 0.55) * 0.18;
        vec2 dir = normalize(windDir + crossDir * directionNoise);
        pos.xz += dir * strength;
    }

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = vec4(Color.rgb, 1.0) * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
