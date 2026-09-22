package com.jvn.wherewindsblow.client.particle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class ParticleWindProfilesTest {
    private static int checks;

    public static void main(String[] args) {
        parsesSelectorsAndDefaults();
        rejectsMalformedProfiles();
        resolvesOverridesDeterministically();
        reloadReplacesProfilesAndIsolatesErrors();
        System.out.println("Passed " + checks + " particle wind profile checks.");
    }

    private static void parsesSelectorsAndDefaults() {
        ParticleWindProfile single = parse("""
                {"particles": "examplemod:falling_leaf"}
                """);
        check(single.particles().equals(Set.of(id("examplemod:falling_leaf"))), "single selector");
        check(single.priority() == 0, "default priority");
        check(single.windStrengthMultiplier() == 1.0F, "default strength");

        ParticleWindProfile multiple = parse("""
                {
                  "particles": ["examplemod:falling_leaf", "othermod:pollen", "examplemod:falling_leaf"],
                  "priority": 100,
                  "wind_strength_multiplier": 0.5
                }
                """);
        check(multiple.particles().equals(Set.of(id("examplemod:falling_leaf"), id("othermod:pollen"))), "deduplicated selectors");
        check(multiple.priority() == 100, "explicit priority");
        check(multiple.windStrengthMultiplier() == 0.5F, "explicit strength");
        check(parse("{\"particles\":\"smoke\"}").particles().contains(id("minecraft:smoke")), "default namespace");
        check(parse("{\"particles\":\"mod:leaf\",\"priority\":-10000,\"wind_strength_multiplier\":0}").windStrengthMultiplier() == 0.0F, "lower bounds");
        check(parse("{\"particles\":\"mod:leaf\",\"priority\":10000,\"wind_strength_multiplier\":4}").priority() == 10000, "upper bounds");
    }

    private static void rejectsMalformedProfiles() {
        for (String json : List.of(
                "{}",
                "{\"particles\":null}",
                "{\"particles\":[]}",
                "{\"particles\":{}}",
                "{\"particles\":true}",
                "{\"particles\":42}",
                "{\"particles\":[[\"mod:leaf\"]]}",
                "{\"particles\":[\"mod:leaf\",null]}",
                "{\"particles\":\"\"}",
                "{\"particles\":\":\"}",
                "{\"particles\":\"mod:\"}",
                "{\"particles\":\"invalid id\"}",
                "{\"particles\":\"mod:Uppercase\"}",
                "{\"particles\":\"#mod:leaves\"}"
        )) {
            expectInvalid(json);
        }
        for (String value : List.of("-10001", "10001", "1.5", "4294967296", "1e100", "null", "true", "\"1\"")) {
            expectInvalid("{\"particles\":\"mod:leaf\",\"priority\":" + value + "}");
        }
        for (String value : List.of("-0.1", "4.1", "1e100", "null", "true", "\"1\"")) {
            expectInvalid("{\"particles\":\"mod:leaf\",\"wind_strength_multiplier\":" + value + "}");
        }
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            JsonObject json = JsonParser.parseString("{\"particles\":\"mod:leaf\"}").getAsJsonObject();
            json.addProperty("wind_strength_multiplier", value);
            expectInvalid(json);
        }
    }

    private static void resolvesOverridesDeterministically() {
        ResourceLocation leaf = id("examplemod:falling_leaf");
        ParticleWindProfile base = new ParticleWindProfile(id("pack:base"), Set.of(leaf), 0, 1.0F);
        ParticleWindProfile override = new ParticleWindProfile(id("pack:override"), Set.of(leaf), 100, 0.5F);
        ParticleWindProfile optOut = new ParticleWindProfile(id("pack:opt_out"), Set.of(leaf), 1000, 0.0F);
        ParticleWindProfiles.apply(List.of(base, override));
        check(ParticleWindProfiles.strengthFor(leaf) == 0.5F, "higher priority wins");
        ParticleWindProfiles.apply(List.of(override, base));
        check(ParticleWindProfiles.strengthFor(leaf) == 0.5F, "priority ignores load order");
        ParticleWindProfiles.apply(List.of(base, optOut, override));
        check(ParticleWindProfiles.strengthFor(leaf) == 0.0F, "zero strength opts out");

        ParticleWindProfile first = new ParticleWindProfile(id("a:profile"), Set.of(leaf), 100, 2.0F);
        ParticleWindProfile last = new ParticleWindProfile(id("z:profile"), Set.of(leaf), 100, 3.0F);
        ParticleWindProfiles.apply(List.of(last, first));
        check(ParticleWindProfiles.strengthFor(leaf) == 2.0F, "resource ID breaks ties");
        ParticleWindProfiles.apply(List.of(first, last));
        check(ParticleWindProfiles.strengthFor(leaf) == 2.0F, "tie ignores load order");
        check(ParticleWindProfiles.strengthFor(id("minecraft:smoke")) == 0.0F, "unlisted particles are unaffected");
        check(ParticleWindProfiles.strengthFor(null) == 0.0F, "untyped particles are unaffected");
    }

    private static void reloadReplacesProfilesAndIsolatesErrors() {
        ParticleWindProfileReloadListener listener = new ParticleWindProfileReloadListener();
        Map<ResourceLocation, JsonElement> resources = Map.of(
                id("pack:valid"), JsonParser.parseString("{\"particles\":[\"absentmod:pollen\"],\"wind_strength_multiplier\":2}"),
                id("pack:invalid"), JsonParser.parseString("{\"particles\":[\"examplemod:falling_leaf\",42]}"),
                id("pack:invalid_root"), JsonParser.parseString("[]")
        );
        listener.apply(resources, null, null);
        check(ParticleWindProfiles.strengthFor(id("absentmod:pollen")) == 2.0F, "optional mod IDs need no registry lookup");
        check(ParticleWindProfiles.strengthFor(id("examplemod:falling_leaf")) == 0.0F, "reload removes stale entries and rejects whole malformed profiles");
        listener.apply(Map.of(id("pack:valid"), JsonParser.parseString("{\"particles\":\"absentmod:pollen\",\"wind_strength_multiplier\":0.25}")), null, null);
        check(ParticleWindProfiles.strengthFor(id("absentmod:pollen")) == 0.25F, "reload updates existing entries");
        listener.apply(Map.of(), null, null);
        check(ParticleWindProfiles.strengthFor(id("absentmod:pollen")) == 0.0F, "removing all profiles clears the whitelist");
    }

    private static ParticleWindProfile parse(String json) {
        return ParticleWindProfileReloadListener.parse(id("test:profile"), JsonParser.parseString(json).getAsJsonObject());
    }

    private static void expectInvalid(String json) {
        expectInvalid(JsonParser.parseString(json).getAsJsonObject());
    }

    private static void expectInvalid(JsonObject json) {
        try {
            ParticleWindProfileReloadListener.parse(id("test:invalid"), json);
        } catch (JsonParseException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted malformed profile: " + json);
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
        checks++;
    }
}
