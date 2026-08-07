package com.jvn.wherewindsblow.client.wind;

import com.jvn.toucanlib.client.ToucanEasing;
import net.minecraft.util.Mth;

/**
 * Immutable description of one travelling gust band. A bounded set of these records is shared by
 * CPU sampling and shader uniform uploads.
 */
public record GustFrontState(
        float startTime,
        float duration,
        float attackFraction,
        float releaseStartFraction,
        float originX,
        float originZ,
        float directionX,
        float directionZ,
        float speed,
        float width,
        float peakStrength,
        float turbulence,
        float noisePhase,
        float crossDrift
) {
    public boolean isAlive(float simulationTime) {
        float age = simulationTime - startTime;
        return age >= 0.0F && age < duration;
    }

    public float strengthAt(double x, double z, float simulationTime) {
        float age = simulationTime - startTime;
        if (age < 0.0F || age >= duration) {
            return 0.0F;
        }

        float relativeX = (float) x - originX;
        float relativeZ = (float) z - originZ;
        float along = relativeX * directionX + relativeZ * directionZ;
        float across = relativeX * -directionZ + relativeZ * directionX;
        float edgeNoise = Mth.sin(across * 0.055F + noisePhase) * width * 0.17F
                + Mth.sin(across * 0.137F - noisePhase * 1.61F) * width * 0.07F;
        float frontDistance = along - speed * age - edgeNoise;
        float normalizedDistance = frontDistance / Math.max(width, 0.001F);
        float spatialEnvelope = (float) Math.exp(-normalizedDistance * normalizedDistance * 1.65F);
        float pocket = 0.84F
                + Mth.sin(across * 0.083F + age * 0.21F + noisePhase) * 0.10F
                + Mth.sin(across * 0.031F - age * 0.13F + noisePhase * 2.07F) * 0.06F;
        return peakStrength * temporalEnvelope(age / duration) * spatialEnvelope * Mth.clamp(pocket, 0.62F, 1.12F);
    }

    public float crossVariation(double x, double z, float simulationTime) {
        float relativeX = (float) x - originX;
        float relativeZ = (float) z - originZ;
        float across = relativeX * -directionZ + relativeZ * directionX;
        float age = simulationTime - startTime;
        return Mth.sin(across * 0.069F + age * 0.18F + noisePhase) * crossDrift;
    }

    private float temporalEnvelope(float progress) {
        if (progress < attackFraction) {
            return ToucanEasing.smoothstep(progress / attackFraction);
        }
        if (progress <= releaseStartFraction) {
            return 1.0F;
        }

        return ToucanEasing.smoothstep(1.0F - (progress - releaseStartFraction) / (1.0F - releaseStartFraction));
    }

}
