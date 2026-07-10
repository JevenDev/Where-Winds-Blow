package com.jvn.wherewindsblow.client.sign;

import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.WindSample;
import com.jvn.wherewindsblow.config.ClientConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class HangingSignSway {
    private static final float ENCLOSED_SWAY_SCALE = 0.12F;
    private static final float MIN_WIND_ALIGNMENT = 0.35F;
    private static final float MAX_SWAY_DEGREES = 14.0F;

    private HangingSignSway() {
    }

    public static float angleDegrees(SignBlockEntity blockEntity) {
        if (!ClientConfig.ENABLE_WIND_HANGING_SIGN_SWAY.getAsBoolean()) {
            return 0.0F;
        }

        float configuredStrength = (float) ClientConfig.WIND_HANGING_SIGN_SWAY_STRENGTH.getAsDouble();
        Level level = blockEntity.getLevel();
        if (configuredStrength <= 0.0F || level == null) {
            return 0.0F;
        }

        BlockState state = blockEntity.getBlockState();
        if (!(state.getBlock() instanceof SignBlock signBlock)) {
            return 0.0F;
        }

        BlockPos pos = blockEntity.getBlockPos();
        WindSample wind = DynamicWindManager.sampleWind(level, pos);
        float exposureScale = Mth.lerp(wind.exposure(), ENCLOSED_SWAY_SCALE, 1.0F);
        float windTime = DynamicWindManager.simulationTime();

        float yRotation = -signBlock.getYRotationDegrees(state);
        float yRadians = yRotation * Mth.DEG_TO_RAD;
        float normalX = Mth.sin(yRadians);
        float normalZ = Mth.cos(yRadians);
        float windAlignment = Mth.lerp(
                Math.abs(wind.directionX() * normalX + wind.directionZ() * normalZ),
                MIN_WIND_ALIGNMENT,
                1.0F
        );

        float alongWind = pos.getX() * wind.directionX() + pos.getZ() * wind.directionZ();
        float phase = randomPhase(pos);
        float time = windTime * 0.9F;
        float primary = Mth.sin(time + alongWind * 0.12F + phase);
        float secondary = Mth.sin(time * 1.67F + phase * 1.83F)
                * (0.10F + Mth.clamp(wind.turbulence(), 0.0F, 1.0F) * 0.42F);
        float amplitude = (0.12F + wind.ambientStrength() * 1.8F + wind.gustStrength() * 3.0F)
                * configuredStrength
                * exposureScale
                * windAlignment;
        return Mth.clamp((primary + secondary) * amplitude, -MAX_SWAY_DEGREES, MAX_SWAY_DEGREES);
    }

    private static float randomPhase(BlockPos pos) {
        int hash = Mth.murmurHash3Mixer(pos.getX() * 73428767 ^ pos.getY() * 9122719 ^ pos.getZ() * 42317861);
        return (hash & 0xFFFF) / 65535.0F * Mth.TWO_PI;
    }
}
