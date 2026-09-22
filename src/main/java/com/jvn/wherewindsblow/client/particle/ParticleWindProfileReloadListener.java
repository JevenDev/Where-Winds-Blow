package com.jvn.wherewindsblow.client.particle;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.jvn.wherewindsblow.WhereWindsBlow;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

public final class ParticleWindProfileReloadListener extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "where_winds_blow/particle_profiles";
    private static final Gson GSON = new Gson();

    public ParticleWindProfileReloadListener() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<ParticleWindProfile> profiles = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            try {
                profiles.add(parse(entry.getKey(), GsonHelper.convertToJsonObject(entry.getValue(), "particle wind profile")));
            } catch (RuntimeException exception) {
                WhereWindsBlow.LOGGER.warn("Ignoring malformed particle wind profile {}: {}", entry.getKey(), exception.getMessage());
            }
        }
        ParticleWindProfiles.apply(profiles);
    }

    static ParticleWindProfile parse(ResourceLocation id, JsonObject json) {
        Set<ResourceLocation> particles = new HashSet<>();
        JsonElement selectors = json.get("particles");
        if (selectors == null) {
            throw new JsonParseException("Missing required 'particles' selector");
        }
        if (selectors.isJsonArray()) {
            for (JsonElement selector : selectors.getAsJsonArray()) {
                parseSelector(selector, particles);
            }
        } else {
            parseSelector(selectors, particles);
        }
        if (particles.isEmpty()) {
            throw new JsonParseException("Profile has no particle selectors");
        }

        return new ParticleWindProfile(
                id,
                Set.copyOf(particles),
                boundedPriority(json),
                boundedStrength(json)
        );
    }

    private static void parseSelector(JsonElement selector, Set<ResourceLocation> particles) {
        if (!selector.isJsonPrimitive() || !selector.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Particle selectors must be particle ID strings");
        }
        String value = selector.getAsString();
        ResourceLocation location = value.isBlank() ? null : ResourceLocation.tryParse(value);
        if (location == null || location.getPath().isEmpty()) {
            throw new JsonParseException("Invalid particle selector '" + value + "'");
        }
        particles.add(location);
    }

    private static int boundedPriority(JsonObject json) {
        JsonElement element = json.get("priority");
        if (element == null) {
            return 0;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            try {
                int value = element.getAsBigDecimal().intValueExact();
                if (value >= -10000 && value <= 10000) {
                    return value;
                }
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new JsonParseException("'priority' must be an integer between -10000 and 10000", exception);
            }
        }
        throw new JsonParseException("'priority' must be an integer between -10000 and 10000");
    }

    private static float boundedStrength(JsonObject json) {
        JsonElement element = json.get("wind_strength_multiplier");
        if (element == null) {
            return 1.0F;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            float value = element.getAsFloat();
            if (Float.isFinite(value) && value >= 0.0F && value <= 4.0F) {
                return value;
            }
        }
        throw new JsonParseException("'wind_strength_multiplier' must be between 0.0 and 4.0");
    }
}
