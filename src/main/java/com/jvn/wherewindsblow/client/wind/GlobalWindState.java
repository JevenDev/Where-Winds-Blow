package com.jvn.wherewindsblow.client.wind;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable snapshot of the client wind simulation. The manager replaces this snapshot only when
 * the simulation advances, so render hot paths can safely share one coherent state.
 */
public record GlobalWindState(
        float baseDirectionX,
        float baseDirectionZ,
        float directionX,
        float directionZ,
        float targetDirectionX,
        float targetDirectionZ,
        float strength,
        float targetStrength,
        float ambientStrength,
        float gustStrength,
        float turbulence,
        float ambientTurbulence,
        float directionInstability,
        float weatherPower,
        float rainLevel,
        float thunderLevel,
        float lullAmount,
        float transitionProgress,
        ResourceLocation profileId
) {
    public float directionDegrees() {
        float degrees = (float) Math.toDegrees(Math.atan2(directionX, -directionZ));
        return (degrees % 360.0F + 360.0F) % 360.0F;
    }
}
