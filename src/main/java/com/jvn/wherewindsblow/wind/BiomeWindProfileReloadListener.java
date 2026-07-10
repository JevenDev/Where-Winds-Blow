package com.jvn.wherewindsblow.wind;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.jvn.wherewindsblow.WhereWindsBlow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

public final class BiomeWindProfileReloadListener extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "where_winds_blow/wind_profiles";
    private static final Gson GSON = new Gson();
    private final BiomeWindProfiles.Source source;
    @Nullable
    private final RegistryAccess registryAccess;

    public BiomeWindProfileReloadListener(BiomeWindProfiles.Source source, @Nullable RegistryAccess registryAccess) {
        super(GSON, DIRECTORY);
        this.source = source;
        this.registryAccess = registryAccess;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<BiomeWindProfile> profiles = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            try {
                profiles.add(parse(entry.getKey(), GsonHelper.convertToJsonObject(entry.getValue(), "wind profile")));
            } catch (RuntimeException exception) {
                WhereWindsBlow.LOGGER.warn("Ignoring malformed wind profile {}: {}", entry.getKey(), exception.getMessage());
            }
        }
        BiomeWindProfiles.apply(profiles, source);
    }

    private BiomeWindProfile parse(ResourceLocation id, JsonObject json) {
        List<ResourceLocation> biomeIds = new ArrayList<>();
        List<TagKey<Biome>> biomeTags = new ArrayList<>();
        boolean matchesAll = false;
        JsonElement selectors = json.get("biomes");
        if (selectors == null) {
            throw new JsonParseException("Missing required 'biomes' selector");
        }
        if (selectors.isJsonArray()) {
            JsonArray array = selectors.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                matchesAll |= parseSelector(array.get(index).getAsString(), biomeIds, biomeTags);
            }
        } else {
            matchesAll = parseSelector(selectors.getAsString(), biomeIds, biomeTags);
        }
        if (!matchesAll && biomeIds.isEmpty() && biomeTags.isEmpty()) {
            throw new JsonParseException("Profile has no valid biome selectors");
        }

        return new BiomeWindProfile(
                id,
                List.copyOf(biomeIds),
                List.copyOf(biomeTags),
                matchesAll,
                boundedInt(json, "priority", 0, -10000, 10000),
                boundedFloat(json, "base_strength_multiplier", 1.0F, 0.0F, 4.0F),
                boundedFloat(json, "gust_strength_multiplier", 1.0F, 0.0F, 4.0F),
                boundedFloat(json, "gust_frequency_multiplier", 1.0F, 0.0F, 4.0F),
                boundedFloat(json, "turbulence_multiplier", 1.0F, 0.0F, 4.0F),
                boundedFloat(json, "direction_instability_multiplier", 1.0F, 0.0F, 4.0F),
                boundedFloat(json, "altitude_influence", 1.0F, 0.0F, 2.0F)
        );
    }

    private boolean parseSelector(
            String selector,
            List<ResourceLocation> biomeIds,
            List<TagKey<Biome>> biomeTags
    ) {
        if (selector.equals("*")) {
            return true;
        }
        boolean tag = selector.startsWith("#");
        String value = tag ? selector.substring(1) : selector;
        ResourceLocation location = ResourceLocation.tryParse(value);
        if (location == null) {
            throw new JsonParseException("Invalid biome selector '" + selector + "'");
        }
        if (tag) {
            biomeTags.add(TagKey.create(Registries.BIOME, location));
        } else {
            validateBiomeId(location);
            biomeIds.add(location);
        }
        return false;
    }

    private void validateBiomeId(ResourceLocation biomeId) {
        if (registryAccess == null) {
            return;
        }
        Registry<Biome> registry = registryAccess.registryOrThrow(Registries.BIOME);
        if (!registry.containsKey(biomeId)) {
            throw new JsonParseException("Unknown biome id '" + biomeId + "'");
        }
    }

    private static int boundedInt(JsonObject json, String name, int fallback, int minimum, int maximum) {
        int value = GsonHelper.getAsInt(json, name, fallback);
        if (value < minimum || value > maximum) {
            throw new JsonParseException("'" + name + "' must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static float boundedFloat(JsonObject json, String name, float fallback, float minimum, float maximum) {
        float value = GsonHelper.getAsFloat(json, name, fallback);
        if (!Float.isFinite(value) || value < minimum || value > maximum) {
            throw new JsonParseException("'" + name + "' must be between " + minimum + " and " + maximum);
        }
        return value;
    }
}
