package com.jvn.wherewindsblow.wind;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

public record BiomeWindProfile(
        ResourceLocation id,
        List<ResourceLocation> biomeIds,
        List<TagKey<Biome>> biomeTags,
        boolean matchesAll,
        int priority,
        float baseStrengthMultiplier,
        float gustStrengthMultiplier,
        float gustFrequencyMultiplier,
        float turbulenceMultiplier,
        float directionInstabilityMultiplier,
        float altitudeInfluence
) {
    public boolean matches(Holder<Biome> biome) {
        if (matchesAll) {
            return true;
        }

        ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
        if (key != null && biomeIds.contains(key.location())) {
            return true;
        }
        for (TagKey<Biome> tag : biomeTags) {
            if (biome.is(tag)) {
                return true;
            }
        }
        return false;
    }
}
