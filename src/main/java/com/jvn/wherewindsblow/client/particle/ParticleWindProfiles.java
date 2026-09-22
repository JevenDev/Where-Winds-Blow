package com.jvn.wherewindsblow.client.particle;

import com.jvn.wherewindsblow.WhereWindsBlow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class ParticleWindProfiles {
    private static volatile Map<ResourceLocation, Float> strengths = Map.of();

    private ParticleWindProfiles() {
    }

    public static float strengthFor(@Nullable ResourceLocation particleId) {
        return particleId == null ? 0.0F : strengths.getOrDefault(particleId, 0.0F);
    }

    static void apply(List<ParticleWindProfile> profiles) {
        List<ParticleWindProfile> ordered = new ArrayList<>(profiles);
        ordered.sort((left, right) -> {
            int priority = Integer.compare(right.priority(), left.priority());
            return priority != 0 ? priority : left.id().toString().compareTo(right.id().toString());
        });

        Map<ResourceLocation, Float> updatedStrengths = new HashMap<>();
        for (ParticleWindProfile profile : ordered) {
            for (ResourceLocation particle : profile.particles()) {
                updatedStrengths.putIfAbsent(particle, profile.windStrengthMultiplier());
            }
        }
        strengths = Map.copyOf(updatedStrengths);
        WhereWindsBlow.LOGGER.info("Loaded {} particle wind profiles.", ordered.size());
    }
}
