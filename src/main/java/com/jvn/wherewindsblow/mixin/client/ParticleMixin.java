package com.jvn.wherewindsblow.mixin.client;

import com.jvn.wherewindsblow.client.particle.ParticleWindProfiles;
import com.jvn.wherewindsblow.client.particle.WindAffectedParticle;
import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.PixelLockedWaving;
import com.jvn.wherewindsblow.client.wind.WindSample;
import com.jvn.wherewindsblow.config.ClientConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Particle.class)
public abstract class ParticleMixin implements WindAffectedParticle {
    @Shadow
    @Final
    protected ClientLevel level;
    @Shadow
    protected double x;
    @Shadow
    protected double y;
    @Shadow
    protected double z;
    @Shadow
    protected double xo;
    @Shadow
    protected double yo;
    @Shadow
    protected double zo;
    @Shadow
    protected boolean onGround;
    @Shadow
    protected boolean removed;

    @Unique
    private ResourceLocation wherewindsblow$particleType;

    @Shadow
    public abstract void move(double x, double y, double z);

    @Override
    public void wherewindsblow$setParticleType(ResourceLocation particleId) {
        this.wherewindsblow$particleType = particleId;
    }

    @Override
    public Vec3 wherewindsblow$windRenderPosition(float partialTick) {
        if (!PixelLockedWaving.enabled()) {
            return null;
        }
        boolean windSmoke = (Object) this instanceof CampfireSmokeParticle
                && ClientConfig.ENABLE_WIND_SMOKE.getAsBoolean()
                && ClientConfig.WIND_SMOKE_STRENGTH.getAsDouble() > 0.0D;
        if (!windSmoke && ParticleWindProfiles.strengthFor(this.wherewindsblow$particleType) <= 0.0F) {
            return null;
        }
        return new Vec3(Mth.lerp(partialTick, this.xo, this.x),
                Mth.lerp(partialTick, this.yo, this.y), Mth.lerp(partialTick, this.zo, this.z));
    }

    @Override
    public void wherewindsblow$applyWind() {
        float strength = ParticleWindProfiles.strengthFor(this.wherewindsblow$particleType);
        if (strength <= 0.0F || this.removed || this.onGround) {
            return;
        }

        WindSample wind = DynamicWindManager.sampleWind(this.level, BlockPos.containing(this.x, this.y, this.z));
        double drift = wind.strength() * strength * 0.03D;
        if (drift > 0.0D) {
            this.move(wind.directionX() * drift, 0.0D, wind.directionZ() * drift);
        }
    }
}
