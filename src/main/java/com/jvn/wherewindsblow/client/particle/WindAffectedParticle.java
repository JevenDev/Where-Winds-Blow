package com.jvn.wherewindsblow.client.particle;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public interface WindAffectedParticle {
    void wherewindsblow$setParticleType(ResourceLocation particleId);

    void wherewindsblow$applyWind();

    @Nullable
    default Vec3 wherewindsblow$windRenderPosition(float partialTick) {
        return null;
    }
}
