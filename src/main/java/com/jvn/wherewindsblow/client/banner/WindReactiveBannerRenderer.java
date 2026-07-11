package com.jvn.wherewindsblow.client.banner;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import com.jvn.wherewindsblow.config.ClientConfig;

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
        if (!ClientConfig.ENABLE_WIND_REACTIVE_BANNERS.getAsBoolean() || blockEntity.getLevel() == null) {
            return false;
        }

        BlockState blockState = blockEntity.getBlockState();
        boolean wall = blockState.getBlock() instanceof WallBannerBlock;
        float rendererYawDegrees;
        if (blockState.getBlock() instanceof BannerBlock) {
            rendererYawDegrees = -RotationSegment.convertToDegrees(blockState.getValue(BannerBlock.ROTATION));
        } else if (wall) {
            rendererYawDegrees = -blockState.getValue(WallBannerBlock.FACING).toYRot();
        } else {
            return false;
        }
        BannerWindStateCache.touch(blockEntity.getBlockPos(), wall, rendererYawDegrees);
        return false;
    }

    public static void reset() {
        BannerWindStateCache.reset();
    }
}
