package com.jvn.wherewindsblow.client.weather;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

/** Owns the core shader used by the persistent GPU desert-storm mesh. */
public final class DesertStormShaders {
    private static final ResourceLocation GPU_DESERT_STORM_SHADER =
            ResourceLocation.fromNamespaceAndPath(
                    WhereWindsBlow.MOD_ID,
                    "rendertype_gpu_desert_storm"
            );
    @Nullable
    private static ShaderInstance shader;

    private DesertStormShaders() {
    }

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            GPU_DESERT_STORM_SHADER,
                            DefaultVertexFormat.POSITION
                    ),
                    loadedShader -> shader = loadedShader
            );
        } catch (IOException | RuntimeException exception) {
            shader = null;
            WhereWindsBlow.LOGGER.warn(
                    "Failed to load the GPU desert storm shader; desert storms will be disabled.",
                    exception
            );
        }
    }

    @Nullable
    public static ShaderInstance shader() {
        return shader;
    }
}
