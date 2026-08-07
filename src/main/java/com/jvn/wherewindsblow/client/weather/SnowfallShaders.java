package com.jvn.wherewindsblow.client.weather;

import com.jvn.toucanlib.neoforge.client.ToucanShaders;
import com.jvn.wherewindsblow.WhereWindsBlow;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

/** Owns the core shader used by the persistent GPU snowfall mesh. */
public final class SnowfallShaders {
    private static final ResourceLocation GPU_SNOWFALL_SHADER = WhereWindsBlow.IDS.id("rendertype_gpu_snowfall");
    @Nullable
    private static ShaderInstance shader;

    private SnowfallShaders() {
    }

    public static void register(RegisterShadersEvent event) {
        ToucanShaders.registerOptional(
                event,
                GPU_SNOWFALL_SHADER,
                DefaultVertexFormat.POSITION,
                loadedShader -> shader = loadedShader,
                exception -> {
                    shader = null;
                    WhereWindsBlow.LOGGER.warn(
                            "Failed to load the GPU snowfall shader; snowfall will use the CPU fallback.",
                            exception
                    );
                }
        );
    }

    @Nullable
    public static ShaderInstance shader() {
        return shader;
    }
}
