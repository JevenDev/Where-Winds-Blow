package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

public final class ResponsiveFoliageShaders {
    private static final ResourceLocation WIND_SHADER = ResourceLocation.fromNamespaceAndPath(
            WhereWindsBlow.MOD_ID,
            "rendertype_responsive_foliage_cutout"
    );
    @Nullable
    private static ShaderInstance shader;

    private ResponsiveFoliageShaders() {
    }

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), WIND_SHADER, DefaultVertexFormat.BLOCK), loadedShader -> shader = loadedShader);
        } catch (IOException exception) {
            WhereWindsBlow.LOGGER.error("Failed to load responsive foliage wind shader.", exception);
        }
    }

    @Nullable
    public static ShaderInstance getShader() {
        return shader;
    }
}
