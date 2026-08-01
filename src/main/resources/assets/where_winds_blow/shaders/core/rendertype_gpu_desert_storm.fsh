#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord;
in vec2 paletteCoordA;
in vec2 paletteCoordB;
in float materialMix;
in float paletteMix;

out vec4 fragColor;

void main() {
    vec4 dust = texture(Sampler0, texCoord);
    if (dust.a < 0.01) {
        discard;
    }

    vec3 sandA = texture(Sampler2, paletteCoordA).rgb;
    vec3 sandB = texture(Sampler2, paletteCoordB).rgb;
    vec3 redSandA = texture(Sampler3, paletteCoordA).rgb;
    vec3 redSandB = texture(Sampler3, paletteCoordB).rgb;
    vec3 paletteA = mix(sandA, redSandA, materialMix);
    vec3 paletteB = mix(sandB, redSandB, materialMix);
    vec3 palette = mix(paletteA, paletteB, paletteMix);

    // The source texture is intentionally sparse and softly antialiased. Strengthen only its
    // non-transparent grains so ordinary rain produces solid sand without exposing quad edges.
    float grainAlpha = 1.0 - pow(1.0 - clamp(dust.a, 0.0, 1.0), 1.7);
    float grainLuminance = max(dust.r, max(dust.g, dust.b));
    vec3 grainShade = vec3(mix(0.72, 1.0, grainLuminance));
    vec4 color = vec4(palette * grainShade, grainAlpha)
        * vertexColor * ColorModulator;
    if (color.a < 0.01) {
        discard;
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
