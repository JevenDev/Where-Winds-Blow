package com.jvn.wherewindsblow.mixin.client;

import com.jvn.wherewindsblow.config.ClientConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.WaterDropParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WaterDropParticle.class)
public abstract class WaterDropParticleMixin extends TextureSheetParticle {
    protected WaterDropParticleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wherewindsblow$tintRainDrop(ClientLevel level, double x, double y, double z, CallbackInfo ci) {
        if (isVanillaRainDrop() && ClientConfig.ENABLE_WIND_REACTIVE_PRECIPITATION.getAsBoolean()) {
            this.setColor(0.82F, 0.86F, 0.72F);
            this.setAlpha(0.72F);
        }
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void wherewindsblow$renderRainDropTranslucently(CallbackInfoReturnable<ParticleRenderType> cir) {
        if (isVanillaRainDrop() && ClientConfig.ENABLE_WIND_REACTIVE_PRECIPITATION.getAsBoolean()) {
            cir.setReturnValue(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT);
        }
    }

    private boolean isVanillaRainDrop() {
        return ((Object) this).getClass() == WaterDropParticle.class;
    }
}
