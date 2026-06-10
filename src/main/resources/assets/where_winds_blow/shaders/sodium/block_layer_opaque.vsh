#version 330 core

#import <sodium:include/fog.glsl>
#import <sodium:include/chunk_vertex.glsl>
#import <sodium:include/chunk_matrices.glsl>
#import <sodium:include/chunk_material.glsl>

out vec4 v_Color;
out vec2 v_TexCoord;

out float v_MaterialMipBias;
#ifdef USE_FRAGMENT_DISCARD
out float v_MaterialAlphaCutoff;
#endif

#ifdef USE_FOG
out float v_FragDistance;
#endif

uniform int u_FogShape;
uniform vec3 u_RegionOffset;
uniform vec2 u_TexCoordShrink;
uniform float u_WwbTime;

uniform sampler2D u_LightTex;

uvec3 _get_relative_chunk_coord(uint pos) {
    return uvec3(pos) >> uvec3(5u, 0u, 2u) & uvec3(7u, 3u, 7u);
}

vec3 _get_draw_translation(uint pos) {
    return _get_relative_chunk_coord(pos) * vec3(16.0);
}

float wwb_smooth_curve(float value) {
    value = clamp(value, 0.0, 1.0);
    return value * value * (3.0 - 2.0 * value);
}

float wwb_decode_wind_alpha(float alpha) {
    return clamp((alpha * 255.0 - 96.0) / 158.0, 0.0, 1.0);
}

vec3 wwb_apply_foliage_wind(vec3 position, float alpha) {
    if (alpha >= 0.999 || alpha <= 0.32) {
        return position;
    }

    float bend = wwb_smooth_curve(wwb_decode_wind_alpha(alpha));
    float t = u_WwbTime;
    vec2 windDir = normalize(vec2(0.82, 0.57));
    vec2 crossDir = vec2(-windDir.y, windDir.x);
    float along = dot(position.xz, windDir);
    float across = dot(position.xz, crossDir);
    float broad = sin(along * 0.35 - t * 1.28 + sin(across * 0.075 + t * 0.18) * 1.35);
    float wave = pow(max(0.0, broad), 1.7);
    float ripple = sin(along * 1.08 - t * 3.6 + across * 0.18) * 0.5 + 0.5;
    float gust = wwb_smooth_curve(sin(along * 0.10 - t * 0.42 + across * 0.04) * 0.5 + 0.5);
    float shimmer = sin(position.x * 2.17 + position.z * 1.63 + t * 2.1) * 0.012;
    float strength = (0.018 + wave * 0.145 + ripple * gust * 0.055 + shimmer) * bend;
    float directionNoise = sin(across * 0.22 + t * 0.55) * 0.18;
    vec2 dir = normalize(windDir + crossDir * directionNoise);
    position.xz += dir * strength;
    return position;
}

void main() {
    _vert_init();

    vec3 translation = u_RegionOffset + _get_draw_translation(_draw_id);
    vec3 position = _vert_position + translation;
    position = wwb_apply_foliage_wind(position, _vert_color.a);

#ifdef USE_FOG
    v_FragDistance = getFragDistance(u_FogShape, position);
#endif

    gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);

    v_Color = vec4(_vert_color.rgb, 1.0) * texture(u_LightTex, _vert_tex_light_coord);
    v_TexCoord = (_vert_tex_diffuse_coord_bias * u_TexCoordShrink) + _vert_tex_diffuse_coord;

    v_MaterialMipBias = _material_mip_bias(_material_params);
#ifdef USE_FRAGMENT_DISCARD
    v_MaterialAlphaCutoff = _material_alpha_cutoff(_material_params);
#endif
}
