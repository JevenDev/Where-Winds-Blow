package com.jvn.wherewindsblow.client.particle;

import net.minecraft.resources.ResourceLocation;

public interface WindAffectedParticle {
    void wherewindsblow$setParticleType(ResourceLocation particleId);

    void wherewindsblow$applyWind();
}
