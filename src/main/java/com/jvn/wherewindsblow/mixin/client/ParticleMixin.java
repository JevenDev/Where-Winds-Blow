package com.jvn.wherewindsblow.mixin.client;

import com.jvn.wherewindsblow.client.particle.ParticleWindProfiles;
import com.jvn.wherewindsblow.client.particle.WindAffectedParticle;
import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.WindSample;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
