package com.jvn.wherewindsblow.mixin.client.compat.iris;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.irisshaders.iris.pipeline.programs.SodiumPrograms", remap = false)
public abstract class IrisSodiumProgramsMixin {
    private static final Pattern WHEREWINDSBLOW$IRIS_VERTEX_POSITION_FUNCTION_PATTERN = Pattern.compile(
            "vec4\\s+getVertexPosition\\s*\\(\\s*\\)\\s*\\{",
            Pattern.MULTILINE
    );
    private static final Pattern WHEREWINDSBLOW$IRIS_VERTEX_POSITION_RETURN_PATTERN = Pattern.compile(
            "return\\s+vec4\\s*\\(\\s*_vert_position\\s*\\+\\s*u_RegionOffset\\s*\\+\\s*_get_draw_translation\\s*\\(\\s*_draw_id\\s*\\)\\s*,\\s*1\\.0\\s*\\)\\s*;",
            Pattern.MULTILINE
    );

    private static final String WHEREWINDSBLOW$IRIS_WIND_HELPERS = """
            uniform float u_WwbTime;
            uniform float u_WwbWeatherWindPower;
            uniform float u_WwbPlantSwayStrength;
            uniform float u_WwbLeafSwayStrength;

            bool wwb_is_plant_wind_vertex(float alpha) {
                float encoded = alpha * 255.0;
                return encoded >= 16.5 && encoded <= 44.5;
            }

            bool wwb_is_leaf_wind_vertex(float alpha) {
                float encoded = alpha * 255.0;
                return encoded >= 44.5 && encoded <= 72.5;
            }

            bool wwb_is_foliage_wind_vertex(float alpha) {
                return wwb_is_plant_wind_vertex(alpha) || wwb_is_leaf_wind_vertex(alpha);
            }

            float wwb_smooth_curve(float value) {
                value = clamp(value, 0.0, 1.0);
                return value * value * (3.0 - 2.0 * value);
            }
            float wwb_decode_wind_alpha(float alpha) {
                float encoded = alpha * 255.0;
                if (wwb_is_leaf_wind_vertex(alpha)) {
                    return clamp((encoded - 45.0) / 27.0, 0.0, 1.0);
                }

                return clamp((encoded - 17.0) / 27.0, 0.0, 1.0);
            }
            float wwb_sway_strength_for_alpha(float alpha) {
                return wwb_is_leaf_wind_vertex(alpha) ? u_WwbLeafSwayStrength : u_WwbPlantSwayStrength;
            }
            vec3 wwb_apply_foliage_wind(vec3 position, float alpha) {
                if (!wwb_is_foliage_wind_vertex(alpha)) {
                    return position;
                }

                float bend = wwb_smooth_curve(wwb_decode_wind_alpha(alpha));
                float weatherStrength = 1.0 + u_WwbWeatherWindPower * 0.55;
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
                float strength = (0.018 + wave * 0.145 + ripple * gust * 0.055 + shimmer) * bend * weatherStrength * wwb_sway_strength_for_alpha(alpha);
                float directionNoise = sin(across * 0.22 + t * 0.55) * 0.18;
                vec2 dir = normalize(windDir + crossDir * directionNoise);
                position.xz += dir * strength;
                return position;
            }
            """;

    private static final String WHEREWINDSBLOW$IRIS_WIND_VERTEX_POSITION_RETURN = """
                vec3 wwbPosition = _vert_position + u_RegionOffset + _get_draw_translation(_draw_id);
                float wwbWindAlpha = _vert_color.a;
                if (wwb_is_foliage_wind_vertex(wwbWindAlpha)) {
                    _vert_color.a = 1.0;
                }
                return vec4(wwb_apply_foliage_wind(wwbPosition, wwbWindAlpha), 1.0);""";

    @Inject(method = "transformShaders", at = @At("RETURN"), require = 0)
    private void wherewindsblow$patchIrisSodiumTerrainWind(CallbackInfoReturnable<Map<Object, String>> cir) {
        if (!ResponsiveFoliageShaders.shouldPatchSodiumShaders()) {
            return;
        }

        Map<Object, String> sources = cir.getReturnValue();
        if (sources == null || sources.isEmpty()) {
            return;
        }

        for (Map.Entry<Object, String> entry : sources.entrySet()) {
            String source = entry.getValue();
            if (!"VERTEX".equals(String.valueOf(entry.getKey())) || source == null || source.contains("wwb_apply_foliage_wind")) {
                continue;
            }

            try {
                String patched = wherewindsblow$patchVertexPosition(source);
                if (!patched.equals(source)) {
                    entry.setValue(patched);
                }
            } catch (RuntimeException exception) {
                ResponsiveFoliageShaders.disableSodiumShaderPatch("Failed to patch Iris Sodium terrain shader source.", exception);
                return;
            }
        }
    }

    private static String wherewindsblow$patchVertexPosition(String source) {
        Matcher returnMatcher = WHEREWINDSBLOW$IRIS_VERTEX_POSITION_RETURN_PATTERN.matcher(source);
        if (!returnMatcher.find()) {
            return source;
        }

        String patched = returnMatcher.replaceFirst(Matcher.quoteReplacement(WHEREWINDSBLOW$IRIS_WIND_VERTEX_POSITION_RETURN));
        Matcher functionMatcher = WHEREWINDSBLOW$IRIS_VERTEX_POSITION_FUNCTION_PATTERN.matcher(patched);
        if (!functionMatcher.find()) {
            return source;
        }

        return patched.substring(0, functionMatcher.start()) + WHEREWINDSBLOW$IRIS_WIND_HELPERS + patched.substring(functionMatcher.start());
    }
}
