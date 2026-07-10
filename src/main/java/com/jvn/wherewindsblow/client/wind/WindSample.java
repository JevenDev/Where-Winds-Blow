package com.jvn.wherewindsblow.client.wind;

/**
 * Immutable local wind result for systems that do not need the full simulation snapshot.
 */
public record WindSample(
        float directionX,
        float directionZ,
        float strength,
        float ambientStrength,
        float gustStrength,
        float turbulence,
        float exposure,
        float weatherPower
) {
    public float crossX() {
        return -directionZ;
    }

    public float crossZ() {
        return directionX;
    }
}
