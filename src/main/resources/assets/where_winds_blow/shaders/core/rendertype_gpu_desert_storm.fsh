#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 paletteCoordA;
in vec2 paletteCoordB;
in float materialMix;
in float paletteMix;

out vec4 fragColor;

void main() {
    vec3 sandA = texture(Sampler1, paletteCoordA).rgb;
    vec3 sandB = texture(Sampler1, paletteCoordB).rgb;
    vec3 redSandA = texture(Sampler2, paletteCoordA).rgb;
    vec3 redSandB = texture(Sampler2, paletteCoordB).rgb;
    vec3 paletteA = mix(sandA, redSandA, materialMix);
    vec3 paletteB = mix(sandB, redSandB, materialMix);
    vec3 palette = mix(paletteA, paletteB, paletteMix);

    vec4 color = vec4(palette, 1.0) * vertexColor * ColorModulator;
    if (color.a < 0.01) {
        discard;
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
