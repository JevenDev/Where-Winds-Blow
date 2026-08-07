package com.jvn.wherewindsblow.client.weather;

import com.jvn.toucanlib.neoforge.client.ToucanShaders;
import com.jvn.wherewindsblow.WhereWindsBlow;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

/** Owns the core shader used by the persistent GPU desert-storm mesh. */
public final class DesertStormShaders {
    private static final ResourceLocation GPU_DESERT_STORM_SHADER =
            WhereWindsBlow.IDS.id("rendertype_gpu_desert_storm");
    @Nullable
    private static ShaderInstance shader;

    private DesertStormShaders() {
    }

    public static void register(RegisterShadersEvent event) {
        ToucanShaders.registerOptional(
                event,
                GPU_DESERT_STORM_SHADER,
                DefaultVertexFormat.POSITION,
                loadedShader -> shader = loadedShader,
                exception -> {
                    shader = null;
                    WhereWindsBlow.LOGGER.warn(
                            "Failed to load the GPU desert storm shader; desert storms will be disabled.",
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
