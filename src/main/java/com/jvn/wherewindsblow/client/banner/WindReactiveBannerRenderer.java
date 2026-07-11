package com.jvn.wherewindsblow.client.banner;

import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;
import com.jvn.wherewindsblow.config.ClientConfig;

/**
 * Owns the optional cloth-only banner rendering path and falls through to vanilla whenever the
 * feature is disabled or outside its animation distance.
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
                    flag, pole, bar, rendererYawDegrees, response, distanceFade
            );
            return true;
        }

        renderStanding(
                blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay,
                flag, pole, bar, rendererYawDegrees, response, distanceFade
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
            ModelPart flag,
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
        renderVanillaCloth(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay,
                flag, response, distanceFade, false);
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
            ModelPart flag,
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
        renderVanillaCloth(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay,
                flag, response, distanceFade, true);
        poseStack.popPose();
        poseStack.popPose();
    }

    /**
     * Keeps the banner visually identical to vanilla and lets wind act through the same single
     * top hinge. Broad rotations read as Minecraft animation; a deforming surface reads as a new
     * cloth model, especially once its lighting starts describing individual folds.
     */
    private static void renderVanillaCloth(
            BannerBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            ModelPart flag,
            BannerWindStateCache.State response,
            float distanceFade,
            boolean wall
    ) {
        float time = DynamicWindManager.simulationTime();
        BlockPos pos = blockEntity.getBlockPos();
        float vanillaPhase = (pos.getX() * 7 + pos.getY() * 9 + pos.getZ() * 13) * Mth.TWO_PI / 100.0F;
        float vanillaAngle = (-0.0125F + 0.01F * Mth.cos(vanillaPhase + time * Mth.TWO_PI / 5.0F)) * Mth.PI;

        float extension = Mth.clamp(response.extension(partialTick), 0.0F, 1.0F);
        float outwardWind = response.outwardWind(partialTick);
        float hingeResponse = response.hingeResponse(partialTick);
        float sideResponse = response.sideResponse(partialTick);
        float gust = Mth.clamp(response.gust(partialTick), 0.0F, 2.0F);
        float turbulence = Mth.clamp(response.turbulence(partialTick), 0.0F, 2.0F);
        float flutterStrength = (float) ClientConfig.BANNER_FLUTTER_STRENGTH.getAsDouble();
        float sagStrength = (float) ClientConfig.BANNER_SAG_STRENGTH.getAsDouble();

        // A banner remains mostly vertical in ordinary wind. Gusts can lift the whole vanilla
        // cuboid, but never bend it into a smooth, sail-like surface.
        float lift = Mth.clamp(hingeResponse * 0.55F, 0.0F, 0.64F);
        lift /= Math.max(0.35F, sagStrength);
        float broadSway = Mth.sin(response.phase() + time * (1.55F + gust * 0.18F))
                * (0.006F + extension * 0.012F + turbulence * 0.006F)
                * flutterStrength;
        float quickSway = Mth.sin(response.phase() * 1.73F + time * (3.8F + turbulence * 0.75F))
                * (0.003F + extension * 0.007F + turbulence * 0.004F)
                * flutterStrength;
        // Positive local outward wind is +Z after the banner render transform, which requires a
        // negative model-space X rotation. The pole blocks motion toward -Z, so back-facing wind
        // pins the cloth against its support instead of letting it pass through to the other side.
        float outwardDirection = wall
                ? 1.0F
                : Mth.clamp(outwardWind / Math.max(extension, 0.001F), 0.0F, 1.0F);
        float directionalLift = lift * outwardDirection;
        float windAngle = Mth.clamp(
                -directionalLift + broadSway + quickSway,
                -(wall ? 0.58F : 0.68F),
                0.02F
        );

        // These angles stay deliberately tiny: they communicate crosswind and turbulent sway
        // while the silhouette still reads as Minecraft's single rigid banner cuboid.
        float sidewaysNoise = Mth.sin(response.phase() * 0.83F + time * 2.1F) * turbulence * 0.004F;
        float yaw = Mth.clamp(sideResponse * 0.035F + sidewaysNoise, -0.045F, 0.045F);
        float roll = Mth.clamp(-sideResponse * 0.016F - sidewaysNoise * 0.45F, -0.022F, 0.022F);

        flag.xRot = Mth.lerp(distanceFade, vanillaAngle, windAngle);
        flag.yRot = yaw * distanceFade;
        flag.zRot = roll * distanceFade;
        flag.y = -32.0F;
        BannerRenderer.renderPatterns(
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay,
                flag,
                ModelBakery.BANNER_BASE,
                true,
                blockEntity.getBaseColor(),
                blockEntity.getPatterns()
        );
    }
}
