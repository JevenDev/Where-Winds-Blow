#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in float windSheen;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.5) {
        discard;
    }

    float sheen = clamp(windSheen, 0.0, 0.32);
    vec3 sheenTarget = color.rgb * vec3(1.42, 1.54, 1.30) + vec3(0.025, 0.060, 0.012);
    color.rgb = mix(color.rgb, sheenTarget, sheen);
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
