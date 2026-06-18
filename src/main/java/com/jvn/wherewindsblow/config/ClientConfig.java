package com.jvn.wherewindsblow.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public static final double FOLIAGE_INTERACTIVITY_STRENGTH_MIN = 0.0D;
    public static final double FOLIAGE_INTERACTIVITY_STRENGTH_MAX = 2.0D;
    public static final double WIND_SHEEN_STRENGTH_MIN = 0.0D;
    public static final double WIND_SHEEN_STRENGTH_MAX = 2.0D;
    public static final double WIND_STREAK_VISIBILITY_MIN = 0.0D;
    public static final double WIND_STREAK_VISIBILITY_MAX = 4.0D;
    public static final double WIND_STREAK_OPACITY_MIN = 0.0D;
    public static final double WIND_STREAK_OPACITY_MAX = 1.0D;
    public static final double WIND_STREAK_THICKNESS_MIN = 1.0D;
    public static final double WIND_STREAK_THICKNESS_MAX = 4.0D;
    public static final double WIND_SMOKE_STRENGTH_MIN = 0.0D;
    public static final double WIND_SMOKE_STRENGTH_MAX = 4.0D;
    public static final double WIND_FOLIAGE_SWAY_STRENGTH_MIN = 0.0D;
    public static final double WIND_FOLIAGE_SWAY_STRENGTH_MAX = 2.0D;
    public static final double WIND_PLANT_SWAY_START_HEIGHT_MIN = 0.0D;
    public static final double WIND_PLANT_SWAY_START_HEIGHT_MAX = 1.0D;
    public static final double WEATHER_WIND_POWER_MIN = 0.0D;
    public static final double WEATHER_WIND_POWER_MAX = 4.0D;
    public static final double WEATHER_SWAY_STRENGTH_MIN = 0.0D;
    public static final double WEATHER_SWAY_STRENGTH_MAX = 4.0D;
    public static final double WEATHER_SHEEN_STRENGTH_MIN = 0.0D;
    public static final double WEATHER_SHEEN_STRENGTH_MAX = 4.0D;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_FOLIAGE_INTERACTIVITY = BUILDER
            .comment("Enables entity-driven foliage interaction on the client.")
            .define("enableFoliageInteractivity", true);

    public static final ModConfigSpec.DoubleValue FOLIAGE_INTERACTIVITY_STRENGTH = BUILDER
            .comment("Scales how strongly foliage reacts to nearby entities.")
            .defineInRange("foliageInteractivityStrength", 1.0D, FOLIAGE_INTERACTIVITY_STRENGTH_MIN, FOLIAGE_INTERACTIVITY_STRENGTH_MAX);

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
            .defineInRange("windSheenStrength", 1.0D, WIND_SHEEN_STRENGTH_MIN, WIND_SHEEN_STRENGTH_MAX);

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_STREAKS = BUILDER
            .comment("Enables subtle Wind Waker-inspired wind streaks in the air.")
            .define("enableWindStreaks", true);

    public static final ModConfigSpec.DoubleValue WIND_STREAK_VISIBILITY = BUILDER
            .comment("Scales wind streak density.")
            .defineInRange("windStreakVisibility", 1.0D, WIND_STREAK_VISIBILITY_MIN, WIND_STREAK_VISIBILITY_MAX);

    public static final ModConfigSpec.DoubleValue WIND_STREAK_OPACITY = BUILDER
            .comment("Scales wind streak opacity.")
            .defineInRange("windStreakOpacity", 0.8D, WIND_STREAK_OPACITY_MIN, WIND_STREAK_OPACITY_MAX);

    public static final ModConfigSpec.DoubleValue WIND_STREAK_THICKNESS = BUILDER
            .comment("Sets wind streak line thickness in pixels.")
            .defineInRange("windStreakThickness", 3.0D, WIND_STREAK_THICKNESS_MIN, WIND_STREAK_THICKNESS_MAX);

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_SMOKE = BUILDER
            .comment("Lets campfire smoke naturally drift and curl with the wind.")
            .define("enableWindSmoke", true);

    public static final ModConfigSpec.DoubleValue WIND_SMOKE_STRENGTH = BUILDER
            .comment("Scales how strongly campfire smoke follows the wind.")
            .defineInRange("windSmokeStrength", 3.0D, WIND_SMOKE_STRENGTH_MIN, WIND_SMOKE_STRENGTH_MAX);

    public static final ModConfigSpec.BooleanValue FORCE_WWB_WIND_WITH_SHADER_PACKS = BUILDER
            .comment("Keeps Where Winds Blow wind sway and sheen active even while an Iris shader pack is in use.")
            .define("forceWwbWindWithShaderPacks", true);

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
            .defineInRange("windFoliageSwayStrength", 1.0D, WIND_FOLIAGE_SWAY_STRENGTH_MIN, WIND_FOLIAGE_SWAY_STRENGTH_MAX);

    public static final ModConfigSpec.DoubleValue WIND_PLANT_SWAY_START_HEIGHT = BUILDER
            .comment("Sets how far above the bottom of plant-like foliage wind sway starts, in block units.")
            .defineInRange("windPlantSwayStartHeight", 0.6D, WIND_PLANT_SWAY_START_HEIGHT_MIN, WIND_PLANT_SWAY_START_HEIGHT_MAX);

    public static final ModConfigSpec.DoubleValue CLEAR_WEATHER_WIND_POWER = BUILDER
            .comment("Weather wind power while the weather is clear. Affects foliage gust intensity, wind streak density, and wind smoke.")
            .defineInRange("clearWeatherWindPower", 0.25D, WEATHER_WIND_POWER_MIN, WEATHER_WIND_POWER_MAX);

    public static final ModConfigSpec.DoubleValue RAIN_WEATHER_WIND_POWER = BUILDER
            .comment("Weather wind power at full rain. Affects foliage gust intensity, wind streak density, and wind smoke.")
            .defineInRange("rainWeatherWindPower", 0.75D, WEATHER_WIND_POWER_MIN, WEATHER_WIND_POWER_MAX);

    public static final ModConfigSpec.DoubleValue THUNDER_WEATHER_WIND_POWER = BUILDER
            .comment("Weather wind power at full thunder. Affects foliage gust intensity, wind streak density, and wind smoke.")
            .defineInRange("thunderWeatherWindPower", 2.0D, WEATHER_WIND_POWER_MIN, WEATHER_WIND_POWER_MAX);

    public static final ModConfigSpec.DoubleValue CLEAR_WEATHER_SWAY_STRENGTH = BUILDER
            .comment("Minimum foliage sway strength while the weather is clear.")
            .defineInRange("clearWeatherSwayStrength", 0.0D, WEATHER_SWAY_STRENGTH_MIN, WEATHER_SWAY_STRENGTH_MAX);

    public static final ModConfigSpec.DoubleValue RAIN_WEATHER_SWAY_STRENGTH = BUILDER
            .comment("Minimum foliage sway strength at full rain.")
            .defineInRange("rainWeatherSwayStrength", 1.5D, WEATHER_SWAY_STRENGTH_MIN, WEATHER_SWAY_STRENGTH_MAX);

    public static final ModConfigSpec.DoubleValue THUNDER_WEATHER_SWAY_STRENGTH = BUILDER
            .comment("Minimum foliage sway strength at full thunder.")
            .defineInRange("thunderWeatherSwayStrength", 2.0D, WEATHER_SWAY_STRENGTH_MIN, WEATHER_SWAY_STRENGTH_MAX);

    public static final ModConfigSpec.DoubleValue CLEAR_WEATHER_SHEEN_STRENGTH = BUILDER
            .comment("Minimum wind sheen strength while the weather is clear.")
            .defineInRange("clearWeatherSheenStrength", 0.0D, WEATHER_SHEEN_STRENGTH_MIN, WEATHER_SHEEN_STRENGTH_MAX);

    public static final ModConfigSpec.DoubleValue RAIN_WEATHER_SHEEN_STRENGTH = BUILDER
            .comment("Minimum wind sheen strength at full rain.")
            .defineInRange("rainWeatherSheenStrength", 1.5D, WEATHER_SHEEN_STRENGTH_MIN, WEATHER_SHEEN_STRENGTH_MAX);

    public static final ModConfigSpec.DoubleValue THUNDER_WEATHER_SHEEN_STRENGTH = BUILDER
            .comment("Minimum wind sheen strength at full thunder.")
            .defineInRange("thunderWeatherSheenStrength", 2.0D, WEATHER_SHEEN_STRENGTH_MIN, WEATHER_SHEEN_STRENGTH_MAX);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
