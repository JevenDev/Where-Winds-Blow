package com.jvn.wherewindsblow.mixin.client;

import com.jvn.wherewindsblow.client.weather.WindReactivePrecipitationRenderer;
import com.jvn.wherewindsblow.config.ClientConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow private ClientLevel level;
    @Shadow private int ticks;
    @Shadow @Final private float[] rainSizeX;
    @Shadow @Final private float[] rainSizeZ;

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void wherewindsblow$renderWindReactivePrecipitation(
            LightTexture lightTexture, float partialTick, double camX, double camY, double camZ, CallbackInfo ci
    ) {
        if (!ClientConfig.ENABLE_WIND_REACTIVE_PRECIPITATION.getAsBoolean()) {
            return;
        }
        if (this.level.effects().renderSnowAndRain(this.level, this.ticks, partialTick, lightTexture, camX, camY, camZ)) {
            ci.cancel();
            return;
        }
        WindReactivePrecipitationRenderer.render(
                this.level, this.ticks, this.rainSizeX, this.rainSizeZ,
                lightTexture, partialTick, camX, camY, camZ
        );
        ci.cancel();
    }
}
