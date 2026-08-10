package com.jvn.wherewindsblow.client.foliage;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.jvn.wherewindsblow.WhereWindsBlow;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;

public final class FoliageSwayProfileReloadListener extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "where_winds_blow/foliage_profiles";
    private static final Gson GSON = new Gson();

    public FoliageSwayProfileReloadListener() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<FoliageSwayProfile> profiles = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            try {
                profiles.add(parse(entry.getKey(), GsonHelper.convertToJsonObject(entry.getValue(), "foliage sway profile")));
            } catch (RuntimeException exception) {
                WhereWindsBlow.LOGGER.warn("Ignoring malformed foliage sway profile {}: {}", entry.getKey(), exception.getMessage());
            }
        }
        FoliageSwayProfiles.apply(profiles);
    }

    private static FoliageSwayProfile parse(ResourceLocation id, JsonObject json) {
        Set<ResourceLocation> blocks = new HashSet<>();
        List<TagKey<Block>> tags = new ArrayList<>();
        JsonElement selectors = json.get("blocks");
        if (selectors == null) {
            throw new JsonParseException("Missing required 'blocks' selector");
        }
        if (selectors.isJsonArray()) {
            JsonArray array = selectors.getAsJsonArray();
            for (JsonElement selector : array) {
                parseSelector(selector.getAsString(), blocks, tags);
            }
        } else {
            parseSelector(selectors.getAsString(), blocks, tags);
        }
        if (blocks.isEmpty() && tags.isEmpty()) {
            throw new JsonParseException("Profile has no valid block selectors");
        }

        String typeName = GsonHelper.getAsString(json, "type").toUpperCase(Locale.ROOT);
        ResponsiveFoliageType type;
        try {
            type = ResponsiveFoliageType.valueOf(typeName);
        } catch (IllegalArgumentException exception) {
            throw new JsonParseException("Unknown foliage type '" + typeName.toLowerCase(Locale.ROOT) + "'");
        }

        return new FoliageSwayProfile(
                id,
                Set.copyOf(blocks),
                List.copyOf(tags),
                type,
                boundedInt(json, "priority", 0, -10000, 10000),
                GsonHelper.getAsBoolean(json, "interactive", type.isPlant()),
                boundedFloat(json, "sway_start_height_multiplier", 1.0F, 0.0F, 4.0F),
                boundedFloat(json, "height_scale", 1.0F, 0.0625F, 16.0F),
                boundedFloat(json, "interaction_height_scale_multiplier", 1.0F, 0.0625F, 16.0F),
                boundedFloat(json, "sway_strength_multiplier", 1.0F, 0.0F, 4.0F),
                boundedFloat(json, "interaction_strength_multiplier", 1.0F, 0.0F, 4.0F)
        );
    }

    private static void parseSelector(String selector, Set<ResourceLocation> blocks, List<TagKey<Block>> tags) {
        boolean tag = selector.startsWith("#");
        String value = tag ? selector.substring(1) : selector;
        ResourceLocation location = ResourceLocation.tryParse(value);
        if (location == null) {
            throw new JsonParseException("Invalid block selector '" + selector + "'");
        }
        if (tag) {
            tags.add(TagKey.create(Registries.BLOCK, location));
        } else {
            blocks.add(location);
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
