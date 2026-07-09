package com.jvn.wherewindsblow.client.sign;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliage;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import com.jvn.wherewindsblow.client.wind.WindDirection;
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
        boolean windExposed = ResponsiveFoliage.isWindExposed(level, pos);
        float exposureScale = windExposed ? 1.0F : ENCLOSED_SWAY_SCALE;
        float windTime = ResponsiveFoliageShaders.windTime();
        float weatherPower = windExposed ? ResponsiveFoliageShaders.weatherWindPower() : 0.0F;
        WindDirection.WindVector wind = WindDirection.current();

        float yRotation = -signBlock.getYRotationDegrees(state);
        float yRadians = yRotation * Mth.DEG_TO_RAD;
        float normalX = Mth.sin(yRadians);
        float normalZ = Mth.cos(yRadians);
        float windAlignment = Mth.lerp(
                Math.abs(wind.xFloat() * normalX + wind.zFloat() * normalZ),
                MIN_WIND_ALIGNMENT,
                1.0F
        );

        float alongWind = pos.getX() * wind.xFloat() + pos.getZ() * wind.zFloat();
        float phase = randomPhase(pos);
        float time = windTime * 0.9F;
        float primary = Mth.sin(time + alongWind * 0.12F + phase);
        float secondary = Mth.sin(time * 1.67F + phase * 1.83F) * 0.28F;
        float gust = 0.72F + 0.28F * smoothStep(
                Mth.sin(alongWind * 0.09F - time * 0.31F + phase * 0.47F) * 0.5F + 0.5F
        );
        float amplitude = (0.9F + weatherPower * 2.3F)
                * configuredStrength
                * exposureScale
                * windAlignment
                * gust;
        return Mth.clamp((primary + secondary) * amplitude, -MAX_SWAY_DEGREES, MAX_SWAY_DEGREES);
    }

    private static float smoothStep(float value) {
        float x = Mth.clamp(value, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static float randomPhase(BlockPos pos) {
        int hash = Mth.murmurHash3Mixer(pos.getX() * 73428767 ^ pos.getY() * 9122719 ^ pos.getZ() * 42317861);
        return (hash & 0xFFFF) / 65535.0F * Mth.TWO_PI;
    }
}
