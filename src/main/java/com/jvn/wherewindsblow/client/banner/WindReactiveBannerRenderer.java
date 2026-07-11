package com.jvn.wherewindsblow.client.banner;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BannerBlockEntity;

/**
 * Owns the optional cloth-only banner rendering path. Until its low-resolution cloth mesh is
 * enabled, the mixin deliberately falls through to the complete vanilla renderer.
 */
public final class WindReactiveBannerRenderer {
    private WindReactiveBannerRenderer() {
    }

    public static boolean tryRender(
            BannerBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            ModelPart flag,
            ModelPart pole,
            ModelPart bar
    ) {
        return false;
    }

    public static void reset() {
    }
}
