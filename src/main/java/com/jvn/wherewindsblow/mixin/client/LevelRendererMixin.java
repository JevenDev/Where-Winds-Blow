package com.jvn.wherewindsblow.mixin.client;

import com.jvn.wherewindsblow.client.weather.WindReactivePrecipitationRenderer;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow @Final private float[] rainSizeX;
    @Shadow @Final private float[] rainSizeZ;

    @WrapOperation(
            method = "renderSnowAndRain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;renderSnowAndRain(Lnet/minecraft/client/multiplayer/ClientLevel;IFLnet/minecraft/client/renderer/LightTexture;DDD)Z"
            )
    )
    private boolean wherewindsblow$renderWindReactiveRain(
            DimensionSpecialEffects effects, ClientLevel level, int ticks, float partialTick,
            LightTexture lightTexture, double camX, double camY, double camZ, Operation<Boolean> original
    ) {
        boolean handled = original.call(effects, level, ticks, partialTick, lightTexture, camX, camY, camZ);
        if (!handled && ClientConfig.ENABLE_WIND_REACTIVE_PRECIPITATION.getAsBoolean()) {
            WindReactivePrecipitationRenderer.render(
                    level, ticks, this.rainSizeX, this.rainSizeZ,
                    lightTexture, partialTick, camX, camY, camZ
            );
        }
        return handled;
    }

    @ModifyExpressionValue(
            method = "renderSnowAndRain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"
            )
    )
    private Biome.Precipitation wherewindsblow$skipReplacedRain(Biome.Precipitation precipitation) {
        return ClientConfig.ENABLE_WIND_REACTIVE_PRECIPITATION.getAsBoolean()
                && precipitation == Biome.Precipitation.RAIN
                ? Biome.Precipitation.NONE
                : precipitation;
    }
}
