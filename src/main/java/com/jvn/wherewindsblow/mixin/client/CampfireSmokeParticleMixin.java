package com.jvn.wherewindsblow.mixin.client;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import com.jvn.wherewindsblow.client.wind.WindDirection;
import com.jvn.wherewindsblow.config.ClientConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CampfireSmokeParticle.class)
public abstract class CampfireSmokeParticleMixin extends TextureSheetParticle {
    @Unique
    private double wherewindsblow$sourceY;
    @Unique
    private double wherewindsblow$windSeed;

    protected CampfireSmokeParticleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void wherewindsblow$initWindSmoke(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            boolean signal,
            CallbackInfo ci
    ) {
        this.wherewindsblow$sourceY = y;
        this.wherewindsblow$windSeed = this.random.nextDouble() * Math.PI * 2.0D;
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/CampfireSmokeParticle;move(DDD)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void wherewindsblow$flowWithWind(CallbackInfo ci) {
        if (!ClientConfig.ENABLE_WIND_SMOKE.getAsBoolean()) {
            return;
        }

        double strength = ClientConfig.WIND_SMOKE_STRENGTH.getAsDouble();
        if (strength <= 0.0D) {
            return;
        }

        float windTime = ResponsiveFoliageShaders.windTime();
        float weatherWindPower = ResponsiveFoliageShaders.weatherWindPower();
        double ageRamp = wherewindsblow$smooth(Mth.clamp((double) this.age / 55.0D, 0.0D, 1.0D));
        double heightRamp = wherewindsblow$smooth(Mth.clamp((this.y - this.wherewindsblow$sourceY) / 5.5D, 0.0D, 1.0D));
        double windGrab = 0.22D + Math.max(ageRamp, heightRamp) * 0.78D;
        double weatherBoost = Mth.clamp((double) weatherWindPower / 2.0D, 0.0D, 1.0D);
        double targetSpeed = (0.006D + weatherBoost * 0.014D) * strength * windGrab;
        double curl = Math.sin(this.wherewindsblow$windSeed + this.age * 0.055D + windTime * 0.7F)
                * (0.18D + ageRamp * 0.28D);
        double targetX = (WindDirection.xDouble() + WindDirection.crossXDouble() * curl) * targetSpeed;
        double targetZ = (WindDirection.zDouble() + WindDirection.crossZDouble() * curl) * targetSpeed;
        double response = 0.035D + weatherBoost * 0.025D;
        this.xd += (targetX - this.xd) * response;
        this.zd += (targetZ - this.zd) * response;
    }

    @Unique
    private static double wherewindsblow$smooth(double value) {
        return value * value * (3.0D - 2.0D * value);
    }
}
