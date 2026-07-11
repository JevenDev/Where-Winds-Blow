package com.jvn.wherewindsblow.client.banner;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;
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
        double animationDistance = ClientConfig.BANNER_ANIMATION_DISTANCE.getAsDouble();
        Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = blockEntity.getBlockPos().getX() + 0.5D - cameraPosition.x;
        double dy = blockEntity.getBlockPos().getY() + 0.5D - cameraPosition.y;
        double dz = blockEntity.getBlockPos().getZ() + 0.5D - cameraPosition.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance >= animationDistance) {
            return false;
        }
        float distanceFade = (float) Math.min(1.0D, (animationDistance - distance) / 8.0D);
        BannerWindStateCache.State response = BannerWindStateCache.touch(
                blockEntity.getBlockPos(), wall, rendererYawDegrees
        );
        if (wall) {
            renderWall(
                    blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay,
                    pole, bar, rendererYawDegrees, response, distanceFade
            );
            return true;
        }

        renderStanding(
                blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay,
                pole, bar, rendererYawDegrees, response, distanceFade
        );
        return true;
    }

    public static void reset() {
        BannerWindStateCache.reset();
    }

    private static void renderStanding(
            BannerBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            ModelPart pole,
            ModelPart bar,
            float rendererYawDegrees,
            BannerWindStateCache.State response,
            float distanceFade
    ) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(rendererYawDegrees));
        pole.visible = true;
        poseStack.pushPose();
        poseStack.scale(0.6666667F, -0.6666667F, -0.6666667F);
        VertexConsumer baseConsumer = ModelBakery.BANNER_BASE.buffer(bufferSource, RenderType::entitySolid);
        pole.render(poseStack, baseConsumer, packedLight, packedOverlay);
        bar.render(poseStack, baseConsumer, packedLight, packedOverlay);
        BannerClothMesh.renderStanding(
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay,
                blockEntity.getBaseColor(),
                blockEntity.getPatterns(),
                response,
                partialTick,
                distanceFade
        );
        poseStack.popPose();
        poseStack.popPose();
    }

    private static void renderWall(
            BannerBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            ModelPart pole,
            ModelPart bar,
            float rendererYawDegrees,
            BannerWindStateCache.State response,
            float distanceFade
    ) {
        poseStack.pushPose();
        poseStack.translate(0.5F, -0.16666667F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(rendererYawDegrees));
        poseStack.translate(0.0F, -0.3125F, -0.4375F);
        pole.visible = false;
        poseStack.pushPose();
        poseStack.scale(0.6666667F, -0.6666667F, -0.6666667F);
        VertexConsumer baseConsumer = ModelBakery.BANNER_BASE.buffer(bufferSource, RenderType::entitySolid);
        pole.render(poseStack, baseConsumer, packedLight, packedOverlay);
        bar.render(poseStack, baseConsumer, packedLight, packedOverlay);
        BannerClothMesh.renderWall(
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay,
                blockEntity.getBaseColor(),
                blockEntity.getPatterns(),
                response,
                partialTick,
                distanceFade
        );
        poseStack.popPose();
        poseStack.popPose();
    }
}
