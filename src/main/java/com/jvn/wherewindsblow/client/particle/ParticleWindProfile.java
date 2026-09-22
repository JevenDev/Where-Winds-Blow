package com.jvn.wherewindsblow.client.particle;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

record ParticleWindProfile(
        ResourceLocation id,
        Set<ResourceLocation> particles,
        int priority,
        float windStrengthMultiplier
) {
}
