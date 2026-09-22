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

#define u_WwbTime WindTime
#define u_WwbAmbientWindStrength AmbientWindStrength
#define u_WwbWindTurbulence WindTurbulence
#define u_WwbWeatherState WeatherState
#define u_WwbActiveGustCount ActiveGustCount
#define u_WwbGustOriginTime0 GustOriginTime0
#define u_WwbGustDirectionSpeed0 GustDirectionSpeed0
#define u_WwbGustStrength0 GustStrength0
#define u_WwbGustEnvelope0 GustEnvelope0
#define u_WwbGustOriginTime1 GustOriginTime1
#define u_WwbGustDirectionSpeed1 GustDirectionSpeed1
#define u_WwbGustStrength1 GustStrength1
#define u_WwbGustEnvelope1 GustEnvelope1
#define u_WwbGustOriginTime2 GustOriginTime2
#define u_WwbGustDirectionSpeed2 GustDirectionSpeed2
#define u_WwbGustStrength2 GustStrength2
#define u_WwbGustEnvelope2 GustEnvelope2
#define u_WwbPlantSwayStrength PlantWindSwayStrength
#define u_WwbLeafSwayStrength LeafWindSwayStrength
#define u_WwbLanternSwayStrength LanternWindSwayStrength
#define u_WwbPlantSheenStrength PlantWindSheenStrength
#define u_WwbLeafSheenStrength LeafWindSheenStrength
#define u_WwbFoliageColorVariationStrength FoliageColorVariationStrength
#define u_WwbFoliageAnimationStepRate FoliageAnimationStepRate
#define u_WwbWindDirection WindDirection
#define u_WwbCameraPosition CameraPosition
#define u_WwbInteractorCount FoliageInteractorCount
#define u_WwbInteractor0 FoliageInteractor0
#define u_WwbInteractor1 FoliageInteractor1
#define u_WwbInteractor2 FoliageInteractor2
#define u_WwbInteractor3 FoliageInteractor3
#define u_WwbInteractor4 FoliageInteractor4
#define u_WwbInteractor5 FoliageInteractor5
#define u_WwbInteractor6 FoliageInteractor6
#define u_WwbInteractor7 FoliageInteractor7
#define u_WwbInteractor8 FoliageInteractor8
#define u_WwbInteractor9 FoliageInteractor9
#define u_WwbInteractor10 FoliageInteractor10
#define u_WwbInteractor11 FoliageInteractor11
#define u_WwbInteractor12 FoliageInteractor12
#define u_WwbInteractor13 FoliageInteractor13
#define u_WwbInteractor14 FoliageInteractor14
#define u_WwbInteractor15 FoliageInteractor15
#define u_WwbInteractorMotion0 FoliageInteractorMotion0
#define u_WwbInteractorMotion1 FoliageInteractorMotion1
#define u_WwbInteractorMotion2 FoliageInteractorMotion2
#define u_WwbInteractorMotion3 FoliageInteractorMotion3
#define u_WwbInteractorMotion4 FoliageInteractorMotion4
#define u_WwbInteractorMotion5 FoliageInteractorMotion5
#define u_WwbInteractorMotion6 FoliageInteractorMotion6
#define u_WwbInteractorMotion7 FoliageInteractorMotion7
#define u_WwbInteractorMotion8 FoliageInteractorMotion8
#define u_WwbInteractorMotion9 FoliageInteractorMotion9
#define u_WwbInteractorMotion10 FoliageInteractorMotion10
#define u_WwbInteractorMotion11 FoliageInteractorMotion11
#define u_WwbInteractorMotion12 FoliageInteractorMotion12
#define u_WwbInteractorMotion13 FoliageInteractorMotion13
#define u_WwbInteractorMotion14 FoliageInteractorMotion14
#define u_WwbInteractorMotion15 FoliageInteractorMotion15
#define WWB_HAS_FOLIAGE_MATERIAL true

#moj_import <where_winds_blow:foliage_uniforms.glsl>
#moj_import <where_winds_blow:foliage_wind.glsl>

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out float windSheen;

void main() {
    vec3 pos = Position + ChunkOffset;
    windSheen = 0.0;

    bool windMarker = wwb_is_foliage_wind_vertex(Color.a);
    bool lanternWindMarker = wwb_is_lantern_wind_vertex(Color.a) || wwb_is_chain_wind_vertex(Color.a);
    float interactionBend = wwb_is_plant_wind_vertex(Color.a) ? wwb_smooth_curve(wwb_decode_plant_interaction_alpha(Color.a)) : 0.0;
    bool interactionMarker = interactionBend > 0.0;

    if (windMarker || lanternWindMarker || interactionMarker) {
        vec3 basePos = pos;
        if (windMarker) {
            vec2 motionPosition = wwb_foliage_motion_position(pos, Color.a);
            float localGustStrength;
            float localTurbulence;
            float gustLeadingEdge;
            vec2 windDir = wwb_sample_dynamic_wind(
                    motionPosition, localGustStrength, localTurbulence, gustLeadingEdge
            );
            windSheen = wwb_foliage_wind_sheen(
                    pos, Color.a, windDir, localGustStrength, localTurbulence, gustLeadingEdge
            );
            pos = wwb_apply_foliage_wind(
                    pos, Color.a, windDir, localGustStrength, localTurbulence, gustLeadingEdge
            );
        }

        if (lanternWindMarker) {
            pos = wwb_apply_lantern_wind(pos, Color.a);
        }

        if (interactionMarker) {
            vec2 interactionAnchor = wwb_wrapped_world_delta(
                    wwb_foliage_motion_position(basePos, Color.a) - CameraPosition.xz
            );
            pos = wwb_apply_foliage_interaction(pos, basePos, interactionAnchor, Color.a);
        }
    }

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vec4 lightmapColor = minecraft_sample_lightmap(Sampler2, UV2);
    vertexColor = vec4(Color.rgb, 1.0) * lightmapColor;
    if (wwb_is_plant_wind_vertex(Color.a) && FoliageColorVariationStrength > 0.0) {
        vec2 colorAnchor = wwb_foliage_motion_position(Position + ChunkOffset, Color.a);
        float variation = clamp(
                wwb_foliage_color_variation(colorAnchor) * FoliageColorVariationStrength,
                -0.26,
                0.32
        );
        float luminance = dot(vertexColor.rgb, vec3(0.2126, 0.7152, 0.0722));
        vertexColor.rgb = mix(vec3(luminance), vertexColor.rgb, clamp(1.0 + variation * 1.35, 0.70, 1.34));
        vertexColor.rgb *= 1.0 + variation * 0.82;
    }
    windSheen *= clamp(max(max(lightmapColor.r, lightmapColor.g), lightmapColor.b), 0.0, 1.0);
    texCoord0 = UV0;
}
