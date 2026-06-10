package com.jvn.wherewindsblow.mixin.client.compat.iris;

import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.irisshaders.iris.pipeline.programs.SodiumPrograms", remap = false)
public abstract class IrisSodiumProgramsMixin {
    private static final String WHEREWINDSBLOW$IRIS_GET_VERTEX_POSITION =
            "vec4 getVertexPosition() { return vec4(_vert_position + u_RegionOffset + _get_draw_translation(_draw_id), 1.0); }";

    private static final String WHEREWINDSBLOW$IRIS_WIND_VERTEX_POSITION = """
            uniform float u_WwbTime;
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
            vec4 getVertexPosition() {
                vec3 position = _vert_position + u_RegionOffset + _get_draw_translation(_draw_id);
                float wwbWindAlpha = _vert_color.a;
                _vert_color.a = 1.0;
                return vec4(wwb_apply_foliage_wind(position, wwbWindAlpha), 1.0);
            }
            """;

    @Inject(method = "transformShaders", at = @At("RETURN"), require = 0)
    private void wherewindsblow$patchIrisSodiumTerrainWind(CallbackInfoReturnable<Map<Object, String>> cir) {
        Map<Object, String> sources = cir.getReturnValue();
        if (sources == null || sources.isEmpty()) {
            return;
        }

        for (Map.Entry<Object, String> entry : sources.entrySet()) {
            String source = entry.getValue();
            if (!"VERTEX".equals(String.valueOf(entry.getKey())) || source == null || source.contains("wwb_apply_foliage_wind")) {
                continue;
            }

            if (source.contains(WHEREWINDSBLOW$IRIS_GET_VERTEX_POSITION)) {
                entry.setValue(source.replace(WHEREWINDSBLOW$IRIS_GET_VERTEX_POSITION, WHEREWINDSBLOW$IRIS_WIND_VERTEX_POSITION));
            }
        }
    }
}
