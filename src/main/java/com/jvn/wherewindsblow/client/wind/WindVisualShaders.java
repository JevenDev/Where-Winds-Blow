package com.jvn.wherewindsblow.client.wind;

import com.jvn.toucanlib.neoforge.client.ToucanShaders;
import com.jvn.wherewindsblow.WhereWindsBlow;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

public final class WindVisualShaders {
    private static final ResourceLocation WIND_STREAK_SHADER = WhereWindsBlow.IDS.id("rendertype_wind_streak_lines");
    private static final RenderStateShard.ShaderStateShard WIND_STREAK_SHADER_STATE = new RenderStateShard.ShaderStateShard(WindVisualShaders::windStreakShader);
    private static ShaderInstance windStreakShader;

    private WindVisualShaders() {
    }

    public static void register(RegisterShadersEvent event) {
        ToucanShaders.registerOptional(
                event,
                WIND_STREAK_SHADER,
                DefaultVertexFormat.POSITION_COLOR_NORMAL,
                loadedShader -> windStreakShader = loadedShader,
                exception -> WhereWindsBlow.LOGGER.warn(
                        "Failed to load wind streak shader; wind streaks will use the vanilla line shader fallback.",
                        exception
                )
        );
    }

    public static RenderStateShard.ShaderStateShard windStreakShaderState() {
        return WIND_STREAK_SHADER_STATE;
    }

    private static ShaderInstance windStreakShader() {
        ShaderInstance shader = windStreakShader;
        if (shader == null) {
            return GameRenderer.getRendertypeLinesShader();
        }

        return shader;
    }
}
