package com.jvn.wherewindsblow.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_FOLIAGE_INTERACTIVITY = BUILDER
            .comment("Enables entity-driven foliage interaction on the client.")
            .define("enableFoliageInteractivity", true);

    public static final ModConfigSpec.DoubleValue FOLIAGE_INTERACTIVITY_STRENGTH = BUILDER
            .comment("Scales how strongly foliage reacts to nearby entities.")
            .defineInRange("foliageInteractivityStrength", 1.0D, 0.0D, 2.0D);

    public static final ModConfigSpec.BooleanValue ENABLE_AUTODETECTED_FOLIAGE_MODELS = BUILDER
            .comment("Allows Where Winds Blow to animate compatible plant-like models from Minecraft and other mods.")
            .define("enableAutodetectedFoliageModels", true);

    public static final ModConfigSpec.BooleanValue ENABLE_CUSTOM_FOLIAGE_SHADER = BUILDER
            .comment("Enables Where Winds Blow's custom foliage shader for vanilla cutout foliage render types.")
            .define("enableCustomFoliageShader", true);

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_SHEEN = BUILDER
            .comment("Enables the bright wind sheen highlight on responsive foliage.")
            .define("enableWindSheen", true);

    public static final ModConfigSpec.DoubleValue WIND_SHEEN_STRENGTH = BUILDER
            .comment("Scales the visible intensity of wind sheen highlights.")
            .defineInRange("windSheenStrength", 1.0D, 0.0D, 2.0D);

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_STREAKS = BUILDER
            .comment("Enables subtle Wind Waker-inspired wind streaks in the air.")
            .define("enableWindStreaks", true);

    public static final ModConfigSpec.DoubleValue WIND_STREAK_VISIBILITY = BUILDER
            .comment("Scales wind streak density.")
            .defineInRange("windStreakVisibility", 1.0D, 0.0D, 4.0D);

    public static final ModConfigSpec.DoubleValue WIND_STREAK_OPACITY = BUILDER
            .comment("Scales wind streak opacity.")
            .defineInRange("windStreakOpacity", 0.9D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue WIND_STREAK_THICKNESS = BUILDER
            .comment("Sets wind streak line thickness in pixels.")
            .defineInRange("windStreakThickness", 2.0D, 1.0D, 4.0D);

    public static final ModConfigSpec.BooleanValue FORCE_WWB_WIND_WITH_SHADER_PACKS = BUILDER
            .comment("Keeps Where Winds Blow wind sway and sheen active even while an Iris shader pack is in use.")
            .define("forceWwbWindWithShaderPacks", false);

    public static final ModConfigSpec.BooleanValue ENABLE_SODIUM_SHADER_PATCH = BUILDER
            .comment("Adds Where Winds Blow wind uniforms and source injections to Sodium's own terrain shader.")
            .define("enableSodiumShaderPatch", true);

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_FOLIAGE_SWAY = BUILDER
            .comment("Enables wind-driven foliage sway in the responsive foliage shader.")
            .define("enableWindFoliageSway", true);

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_PLANT_SWAY = BUILDER
            .comment("Enables wind-driven sway for grass, crops, flowers, and other plant-like foliage.")
            .define("enableWindPlantSway", true);

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_LEAF_SWAY = BUILDER
            .comment("Enables wind-driven sway for leaf blocks.")
            .define("enableWindLeafSway", true);

    public static final ModConfigSpec.DoubleValue WIND_FOLIAGE_SWAY_STRENGTH = BUILDER
            .comment("Scales the strength of wind-driven foliage sway.")
            .defineInRange("windFoliageSwayStrength", 1.0D, 0.0D, 2.0D);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
