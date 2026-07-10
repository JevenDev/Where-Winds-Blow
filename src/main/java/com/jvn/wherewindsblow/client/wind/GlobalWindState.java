package com.jvn.wherewindsblow.client.wind;

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
        float directionInstability,
        float weatherPower,
        float rainLevel,
        float thunderLevel,
        float lullAmount,
        float transitionProgress
) {
    public float directionDegrees() {
        float degrees = (float) Math.toDegrees(Math.atan2(directionX, -directionZ));
        return (degrees % 360.0F + 360.0F) % 360.0F;
    }
}
