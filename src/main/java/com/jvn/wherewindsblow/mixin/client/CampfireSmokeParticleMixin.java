package com.jvn.wherewindsblow.mixin.client;

import com.jvn.wherewindsblow.client.smoke.CampfireSmokePlumes;
import com.jvn.wherewindsblow.client.smoke.CampfireSmokePlumes.SpawnContext;
import com.jvn.wherewindsblow.client.smoke.CampfireSmokeRenderTypes;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliagePhysics;
import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.WindSample;
import com.jvn.wherewindsblow.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CampfireSmokeParticle.class)
public abstract class CampfireSmokeParticleMixin extends TextureSheetParticle {
    @Unique
    private static final double wherewindsblow$TWO_PI = Math.PI * 2.0D;

    @Unique
    private double wherewindsblow$sourceY;
    @Unique
    private double wherewindsblow$turbulenceSeedX;
    @Unique
    private double wherewindsblow$turbulenceSeedZ;
    @Unique
    private double wherewindsblow$turbulenceSeedPhase;
    @Unique
    private SpawnContext wherewindsblow$plumeContext;
    @Unique
    private float wherewindsblow$initialQuadSize;
    @Unique
    private float wherewindsblow$initialAlpha;
    @Unique
    private float wherewindsblow$initialRed;
    @Unique
    private float wherewindsblow$initialGreen;
    @Unique
    private float wherewindsblow$initialBlue;
    @Unique
    private float wherewindsblow$sizeVariance;
    @Unique
    private boolean wherewindsblow$capturedVisualState;
    @Unique
    private boolean wherewindsblow$capturedProviderAlpha;

    protected CampfireSmokeParticleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void wherewindsblow$useMergedPlumeRenderType(CallbackInfoReturnable<ParticleRenderType> cir) {
        if (this.wherewindsblow$plumeContext != null && this.wherewindsblow$plumeContext.clustered()) {
            cir.setReturnValue(CampfireSmokeRenderTypes.MERGED_PLUME);
        }
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
        this.wherewindsblow$turbulenceSeedX = this.random.nextDouble() * wherewindsblow$TWO_PI;
        this.wherewindsblow$turbulenceSeedZ = this.random.nextDouble() * wherewindsblow$TWO_PI;
        this.wherewindsblow$turbulenceSeedPhase = this.random.nextDouble() * wherewindsblow$TWO_PI;
        this.wherewindsblow$sizeVariance = 1.0F;
        this.wherewindsblow$initialQuadSize = this.quadSize;
        this.wherewindsblow$initialRed = this.rCol;
        this.wherewindsblow$initialGreen = this.gCol;
        this.wherewindsblow$initialBlue = this.bCol;
        this.wherewindsblow$initialAlpha = this.alpha;

        if (!ClientConfig.ENABLE_WIND_SMOKE.getAsBoolean()) {
            return;
        }

        SpawnContext context = CampfireSmokePlumes.activeSpawnContext();
        if (context == null) {
            context = CampfireSmokePlumes.findSpawnContextNear(level, x, y, z);
        }

        if (context != null) {
            this.wherewindsblow$plumeContext = context;
            this.wherewindsblow$sourceY = context.sourceY();
            double lifetimeVariation = 0.9D + this.random.nextDouble() * 0.25D;
            int targetLifetime = (int) Math.round((double) this.lifetime * context.lifetimeMultiplier() * lifetimeVariation);
            int maxLifetime = context.signalFire() ? 620 : 360;
            this.lifetime = Math.max(this.lifetime, Mth.clamp(targetLifetime, this.lifetime, maxLifetime));

            if (context.clustered()) {
                this.yd += (this.random.nextDouble() - 0.5D) * 0.025D;
            }

            if (ClientConfig.WIND_SMOKE_STRENGTH.getAsDouble() > 0.0D) {
                wherewindsblow$captureVisualState(true);
                wherewindsblow$applyVisualShape(0.0F, false);
            }
        }
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
        if (Minecraft.getInstance().isPaused()) {
            return;
        }

        if (!ClientConfig.ENABLE_WIND_SMOKE.getAsBoolean()) {
            return;
        }

        double strength = ClientConfig.WIND_SMOKE_STRENGTH.getAsDouble();
        if (strength <= 0.0D) {
            return;
        }

        wherewindsblow$captureVisualState(false);

        float windTime = DynamicWindManager.simulationTime();
        WindSample wind = CampfireSmokePlumes.windSampleAt(
                this.level,
                this.wherewindsblow$plumeContext,
                this.x,
                this.y,
                this.z
        );
        double normalizedAge = Mth.clamp((double) this.age / (double) Math.max(this.lifetime, 1), 0.0D, 1.0D);
        double clusterRamp = wherewindsblow$clusterRamp();
        double ageRamp = wherewindsblow$smooth(Mth.clamp(normalizedAge * 1.35D, 0.0D, 1.0D));
        double plumeHeight = this.wherewindsblow$plumeContext != null && this.wherewindsblow$plumeContext.clustered() ? 8.0D : 5.5D;
        double heightRamp = wherewindsblow$smooth(Mth.clamp((this.y - this.wherewindsblow$sourceY) / plumeHeight, 0.0D, 1.0D));
        double windGrab = 0.22D + Math.max(ageRamp, heightRamp) * 0.78D;
        double signalBoost = this.wherewindsblow$plumeContext != null && this.wherewindsblow$plumeContext.signalFire() ? 0.12D : 0.0D;
        double targetSpeed = (0.001D + Mth.clamp((double) wind.strength(), 0.0D, 3.0D) * 0.009D)
                * strength
                * windGrab
                * (1.0D + clusterRamp * 0.32D + signalBoost);
        double curl = Math.sin(this.wherewindsblow$turbulenceSeedPhase + this.age * 0.055D + windTime * 0.7F)
                * wind.turbulence()
                * (0.4D + ageRamp * 0.42D + clusterRamp * 0.28D);
        double targetX = wind.directionX() * targetSpeed + wind.crossX() * curl * targetSpeed;
        double targetZ = wind.directionZ() * targetSpeed + wind.crossZ() * curl * targetSpeed;
        Vec3 localForce = ResponsiveFoliagePhysics.localForceAt(this.x, this.y, this.z);
        targetX += localForce.x * 0.015D;
        targetZ += localForce.z * 0.015D;
        double response = 0.035D
                + Mth.clamp((double) wind.strength() / 2.0D, 0.0D, 1.0D) * 0.025D
                + clusterRamp * 0.018D;
        this.xd += (targetX - this.xd) * response;
        this.zd += (targetZ - this.zd) * response;

        double turbulenceScale = this.wherewindsblow$plumeContext == null ? 1.0D : this.wherewindsblow$plumeContext.turbulenceMultiplier();
        double turbulenceStrength = (0.00012D + wind.turbulence() * 0.0011D)
                * strength
                * (0.25D + ageRamp)
                * turbulenceScale;
        double t = (double) this.age * 0.075D + (double) windTime * 0.9D;
        double turbulenceX = Math.sin(t + this.wherewindsblow$turbulenceSeedX) * turbulenceStrength
                + Math.sin(t * 1.9D + this.wherewindsblow$turbulenceSeedPhase) * turbulenceStrength * 0.45D;
        double turbulenceZ = Math.cos(t + this.wherewindsblow$turbulenceSeedZ) * turbulenceStrength
                + Math.cos(t * 2.1D + this.wherewindsblow$turbulenceSeedPhase * 1.3D) * turbulenceStrength * 0.45D;
        this.xd += turbulenceX;
        this.zd += turbulenceZ;

        if (this.wherewindsblow$plumeContext != null && this.wherewindsblow$plumeContext.clustered()) {
            double lift = (1.0D - normalizedAge) * clusterRamp * 0.0011D;
            double verticalTurbulence = Math.sin(t * 1.35D + this.wherewindsblow$turbulenceSeedPhase)
                    * turbulenceStrength
                    * 0.4D;
            this.yd += lift + verticalTurbulence;
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void wherewindsblow$shapePlume(CallbackInfo ci) {
        if (!ClientConfig.ENABLE_WIND_SMOKE.getAsBoolean()
                || ClientConfig.WIND_SMOKE_STRENGTH.getAsDouble() <= 0.0D) {
            return;
        }

        wherewindsblow$captureVisualState(false);

        float ageFactor = Mth.clamp((float) this.age / (float) Math.max(this.lifetime, 1), 0.0F, 1.0F);
        wherewindsblow$applyVisualShape(ageFactor, true);
    }

    @Unique
    private void wherewindsblow$applyVisualShape(float ageFactor, boolean applyAlpha) {
        float targetScale = this.wherewindsblow$initialQuadSize * wherewindsblow$targetScaleMultiplier();
        float growth = ageFactor * Mth.sqrt(ageFactor);
        this.quadSize = Mth.clamp(
                Mth.lerp(growth, this.wherewindsblow$initialQuadSize, targetScale),
                this.wherewindsblow$initialQuadSize,
                targetScale
        );

        if (applyAlpha) {
            this.alpha = this.wherewindsblow$initialAlpha * wherewindsblow$alphaFade(ageFactor);
        }

        wherewindsblow$tintSmoke(ageFactor);
    }

    @Unique
    private static double wherewindsblow$smooth(double value) {
        return value * value * (3.0D - 2.0D * value);
    }

    @Unique
    private void wherewindsblow$captureVisualState(boolean waitForProviderAlpha) {
        if (this.wherewindsblow$capturedVisualState) {
            wherewindsblow$captureProviderAlpha();
            return;
        }

        this.wherewindsblow$capturedVisualState = true;
        this.wherewindsblow$initialQuadSize = this.quadSize;
        this.wherewindsblow$initialAlpha = this.alpha;
        this.wherewindsblow$initialRed = this.rCol;
        this.wherewindsblow$initialGreen = this.gCol;
        this.wherewindsblow$initialBlue = this.bCol;
        this.wherewindsblow$sizeVariance = 0.72F + Mth.sqrt(this.random.nextFloat()) * 0.5F;
        this.wherewindsblow$capturedProviderAlpha = !waitForProviderAlpha;
    }

    @Unique
    private void wherewindsblow$captureProviderAlpha() {
        if (this.wherewindsblow$capturedProviderAlpha) {
            return;
        }

        this.wherewindsblow$capturedProviderAlpha = true;
        this.wherewindsblow$initialAlpha = this.alpha;
    }

    @Unique
    private double wherewindsblow$clusterRamp() {
        if (this.wherewindsblow$plumeContext == null || !this.wherewindsblow$plumeContext.clustered()) {
            return 0.0D;
        }

        return Mth.clamp(
                Math.log1p((double) this.wherewindsblow$plumeContext.adjacentCampfires()) / Math.log(9.0D),
                0.0D,
                1.35D
        );
    }

    @Unique
    private float wherewindsblow$targetScaleMultiplier() {
        if (this.wherewindsblow$plumeContext == null) {
            return 2.15F;
        }

        float multiplier = this.wherewindsblow$plumeContext.visualScaleMultiplier();
        return this.wherewindsblow$plumeContext.clustered()
                ? multiplier * this.wherewindsblow$sizeVariance
                : multiplier;
    }

    @Unique
    private float wherewindsblow$alphaFade(float ageFactor) {
        boolean clustered = this.wherewindsblow$plumeContext != null && this.wherewindsblow$plumeContext.clustered();
        float holdUntil = clustered ? 0.58F : 0.66F;
        if (ageFactor <= holdUntil) {
            return 1.0F;
        }

        float fade = (ageFactor - holdUntil) / (1.0F - holdUntil);
        return Math.max(0.0F, 1.0F - fade * fade);
    }

    @Unique
    private void wherewindsblow$tintSmoke(float ageFactor) {
        if (this.wherewindsblow$plumeContext != null && this.wherewindsblow$plumeContext.soulFire()) {
            float tint = this.wherewindsblow$plumeContext.clustered() ? 0.9F : 0.75F;
            float ageLerp = Mth.clamp(ageFactor / 0.4F, 0.0F, 1.0F);
            float darkBase = this.wherewindsblow$initialRed * 0.45F;
            float tintedRedGreen = Mth.lerp(ageLerp, darkBase, this.wherewindsblow$initialRed * 0.7F);
            float tintedBlue = Mth.lerp(ageLerp, darkBase, Math.min(this.wherewindsblow$initialBlue * 1.05F, 1.0F));
            this.rCol = Mth.lerp(tint, this.wherewindsblow$initialRed, tintedRedGreen);
            this.gCol = Mth.lerp(tint, this.wherewindsblow$initialGreen, tintedRedGreen);
            this.bCol = Mth.lerp(tint, this.wherewindsblow$initialBlue, tintedBlue);
            return;
        }

        float brighten = Mth.clamp(ageFactor / 0.3F, 0.0F, 1.0F);
        float warmBase = this.wherewindsblow$initialRed * 0.66F;
        float target = Math.min(this.wherewindsblow$initialRed * 1.08F, 1.0F);
        float color = Mth.lerp(brighten, warmBase, target);
        this.rCol = color;
        this.gCol = Mth.lerp(0.85F, this.wherewindsblow$initialGreen, color);
        this.bCol = Mth.lerp(0.85F, this.wherewindsblow$initialBlue, color);
    }
}
