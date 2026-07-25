package com.jvn.wherewindsblow.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public static final double FOLIAGE_INTERACTIVITY_STRENGTH_MIN = 0.0D;
    public static final double FOLIAGE_INTERACTIVITY_STRENGTH_MAX = 2.0D;
    public static final double WIND_SHEEN_STRENGTH_MIN = 0.0D;
    public static final double WIND_SHEEN_STRENGTH_MAX = 2.0D;
    public static final double WIND_STREAK_VISIBILITY_MIN = 0.0D;
    public static final double WIND_STREAK_VISIBILITY_MAX = 4.0D;
    public static final double WIND_LINE_DENSITY_MIN = 0.0D;
    public static final double WIND_LINE_DENSITY_MAX = 4.0D;
    public static final double WIND_LEAF_DENSITY_MIN = 0.0D;
    public static final double WIND_LEAF_DENSITY_MAX = 4.0D;
    public static final double WIND_STREAK_OPACITY_MIN = 0.0D;
    public static final double WIND_STREAK_OPACITY_MAX = 1.0D;
    public static final double WIND_LEAF_OPACITY_MIN = 0.0D;
    public static final double WIND_LEAF_OPACITY_MAX = 1.0D;
    public static final double WIND_STREAK_THICKNESS_MIN = 1.0D;
    public static final double WIND_STREAK_THICKNESS_MAX = 4.0D;
    public static final double WIND_DIRECTION_DEGREES_MIN = 0.0D;
    public static final double WIND_DIRECTION_DEGREES_MAX = 360.0D;
    public static final double OVERALL_WIND_STRENGTH_MIN = 0.0D;
    public static final double OVERALL_WIND_STRENGTH_MAX = 3.0D;
    public static final double DYNAMIC_DIRECTION_VARIATION_MIN = 0.0D;
    public static final double DYNAMIC_DIRECTION_VARIATION_MAX = 2.0D;
    public static final double DIRECTION_HOLD_TIME_MIN = 30.0D;
    public static final double DIRECTION_HOLD_TIME_MAX = 900.0D;
    public static final double MAX_DIRECTION_CHANGE_MIN = 0.0D;
    public static final double MAX_DIRECTION_CHANGE_MAX = 180.0D;
    public static final double LULL_FREQUENCY_MIN = 0.0D;
    public static final double LULL_FREQUENCY_MAX = 2.0D;
    public static final double LULL_STRENGTH_MIN = 0.0D;
    public static final double LULL_STRENGTH_MAX = 1.0D;
    public static final double GUST_FREQUENCY_MIN = 0.0D;
    public static final double GUST_FREQUENCY_MAX = 3.0D;
    public static final double GUST_STRENGTH_MIN = 0.0D;
    public static final double GUST_STRENGTH_MAX = 3.0D;
    public static final double GUST_TRAVEL_SPEED_MIN = 1.0D;
    public static final double GUST_TRAVEL_SPEED_MAX = 24.0D;
    public static final double GUST_WIDTH_MIN = 3.0D;
    public static final double GUST_WIDTH_MAX = 48.0D;
    public static final double TURBULENCE_STRENGTH_MIN = 0.0D;
    public static final double TURBULENCE_STRENGTH_MAX = 2.0D;
    public static final double WIND_EXPOSURE_RADIUS_MIN = 2.0D;
    public static final double WIND_EXPOSURE_RADIUS_MAX = 8.0D;
    public static final double ALTITUDE_WIND_INFLUENCE_MIN = 0.0D;
    public static final double ALTITUDE_WIND_INFLUENCE_MAX = 2.0D;
    public static final double WIND_SMOKE_STRENGTH_MIN = 0.0D;
    public static final double WIND_SMOKE_STRENGTH_MAX = 4.0D;
    public static final double WIND_LANTERN_SWAY_STRENGTH_MIN = 0.0D;
    public static final double WIND_LANTERN_SWAY_STRENGTH_MAX = 2.0D;
    public static final double WIND_HANGING_SIGN_SWAY_STRENGTH_MIN = 0.0D;
    public static final double WIND_HANGING_SIGN_SWAY_STRENGTH_MAX = 2.0D;
    public static final double BANNER_RESPONSE_MIN = 0.0D;
    public static final double BANNER_RESPONSE_MAX = 2.0D;
    public static final double BANNER_ANIMATION_DISTANCE_MIN = 16.0D;
    public static final double BANNER_ANIMATION_DISTANCE_MAX = 128.0D;
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
    public static final double RAIN_ANGLE_VARIATION_MIN = 0.0D;
    public static final double RAIN_ANGLE_VARIATION_MAX = 45.0D;
    public static final double RAIN_SQUALL_STRENGTH_MIN = 0.0D;
    public static final double RAIN_SQUALL_STRENGTH_MAX = 2.0D;

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
            .comment("Scales overall ambient wind streak and drifting leaf density.")
            .defineInRange("windStreakVisibility", 1.0D, WIND_STREAK_VISIBILITY_MIN, WIND_STREAK_VISIBILITY_MAX);

    public static final ModConfigSpec.DoubleValue WIND_LINE_DENSITY = BUILDER
            .comment("Scales how many wind lines spawn.")
            .defineInRange("windLineDensity", 1.0D, WIND_LINE_DENSITY_MIN, WIND_LINE_DENSITY_MAX);

    public static final ModConfigSpec.DoubleValue WIND_LEAF_DENSITY = BUILDER
            .comment("Scales how many drifting leaf particles spawn.")
            .defineInRange("windLeafDensity", 1.0D, WIND_LEAF_DENSITY_MIN, WIND_LEAF_DENSITY_MAX);

    public static final ModConfigSpec.DoubleValue WIND_STREAK_OPACITY = BUILDER
            .comment("Scales wind line opacity.")
            .defineInRange("windStreakOpacity", 0.65D, WIND_STREAK_OPACITY_MIN, WIND_STREAK_OPACITY_MAX);

    public static final ModConfigSpec.DoubleValue WIND_LEAF_OPACITY = BUILDER
            .comment("Scales drifting leaf particle opacity.")
            .defineInRange("windLeafOpacity", 1.0D, WIND_LEAF_OPACITY_MIN, WIND_LEAF_OPACITY_MAX);

    public static final ModConfigSpec.DoubleValue WIND_STREAK_THICKNESS = BUILDER
            .comment("Sets the maximum wind streak line thickness in pixels. Each streak has a soft halo and a finer bright core.")
            .defineInRange("windStreakThickness", 2.25D, WIND_STREAK_THICKNESS_MIN, WIND_STREAK_THICKNESS_MAX);

    public static final ModConfigSpec.DoubleValue WIND_DIRECTION_DEGREES = BUILDER
            .comment("Sets the fixed or prevailing wind direction in compass degrees clockwise from north. 0 is north, 90 is east, 180 is south, and 270 is west.")
            .defineInRange("windDirectionDegrees", 125.0D, WIND_DIRECTION_DEGREES_MIN, WIND_DIRECTION_DEGREES_MAX);

    public static final ModConfigSpec.BooleanValue ENABLE_DYNAMIC_WIND = BUILDER
            .comment("Enables slow ambient wind variation, direction transitions, and calm periods.")
            .define("enableDynamicWind", true);

    public static final ModConfigSpec.EnumValue<WindDirectionMode> WIND_DIRECTION_MODE = BUILDER
            .comment("Chooses whether wind direction changes dynamically or stays at the configured compass direction.")
            .defineEnum("windDirectionMode", WindDirectionMode.DYNAMIC);

    public static final ModConfigSpec.DoubleValue OVERALL_WIND_STRENGTH = BUILDER
            .comment("Scales the strength produced by the shared wind simulation.")
            .defineInRange("overallWindStrength", 1.0D, OVERALL_WIND_STRENGTH_MIN, OVERALL_WIND_STRENGTH_MAX);

    public static final ModConfigSpec.DoubleValue DYNAMIC_DIRECTION_VARIATION = BUILDER
            .comment("Scales how much dynamic wind direction can wander from its prevailing flow.")
            .defineInRange("dynamicDirectionVariation", 1.0D, DYNAMIC_DIRECTION_VARIATION_MIN, DYNAMIC_DIRECTION_VARIATION_MAX);

    public static final ModConfigSpec.DoubleValue MIN_DIRECTION_HOLD_TIME = BUILDER
            .comment("Minimum time in seconds before dynamic wind chooses a new direction target.")
            .defineInRange("minimumDirectionHoldTime", 150.0D, DIRECTION_HOLD_TIME_MIN, DIRECTION_HOLD_TIME_MAX);

    public static final ModConfigSpec.DoubleValue MAX_DIRECTION_HOLD_TIME = BUILDER
            .comment("Maximum time in seconds before dynamic wind chooses a new direction target.")
            .defineInRange("maximumDirectionHoldTime", 360.0D, DIRECTION_HOLD_TIME_MIN, DIRECTION_HOLD_TIME_MAX);

    public static final ModConfigSpec.DoubleValue MAX_ORDINARY_DIRECTION_CHANGE = BUILDER
            .comment("Largest normal direction change in degrees. Storms can rarely exceed this value.")
            .defineInRange("maximumOrdinaryDirectionChange", 38.0D, MAX_DIRECTION_CHANGE_MIN, MAX_DIRECTION_CHANGE_MAX);

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_LULLS = BUILDER
            .comment("Allows smooth calm periods that reduce the shared wind strength.")
            .define("enableWindLulls", true);

    public static final ModConfigSpec.DoubleValue WIND_LULL_FREQUENCY = BUILDER
            .comment("Scales how often calm periods occur. Zero disables them even when the toggle is on.")
            .defineInRange("windLullFrequency", 1.0D, LULL_FREQUENCY_MIN, LULL_FREQUENCY_MAX);

    public static final ModConfigSpec.DoubleValue WIND_LULL_STRENGTH = BUILDER
            .comment("Sets the fraction of normal ambient wind retained at the deepest point of a lull.")
            .defineInRange("windLullStrength", 0.3D, LULL_STRENGTH_MIN, LULL_STRENGTH_MAX);

    public static final ModConfigSpec.BooleanValue ENABLE_GUST_FRONTS = BUILDER
            .comment("Enables a bounded set of travelling gust bands shared by wind-reactive effects.")
            .define("enableGustFronts", true);

    public static final ModConfigSpec.DoubleValue GUST_FREQUENCY = BUILDER
            .comment("Scales how often travelling gust fronts form.")
            .defineInRange("gustFrequency", 1.0D, GUST_FREQUENCY_MIN, GUST_FREQUENCY_MAX);

    public static final ModConfigSpec.DoubleValue GUST_STRENGTH = BUILDER
            .comment("Scales the additional wind strength carried by travelling gust fronts.")
            .defineInRange("gustStrength", 1.0D, GUST_STRENGTH_MIN, GUST_STRENGTH_MAX);

    public static final ModConfigSpec.DoubleValue GUST_TRAVEL_SPEED = BUILDER
            .comment("Sets the average gust-front travel speed in blocks per second.")
            .defineInRange("gustTravelSpeed", 8.0D, GUST_TRAVEL_SPEED_MIN, GUST_TRAVEL_SPEED_MAX);

    public static final ModConfigSpec.DoubleValue GUST_WIDTH = BUILDER
            .comment("Sets the average width of a travelling gust front in blocks.")
            .defineInRange("gustWidth", 14.0D, GUST_WIDTH_MIN, GUST_WIDTH_MAX);

    public static final ModConfigSpec.DoubleValue TURBULENCE_STRENGTH = BUILDER
            .comment("Scales controlled crosswind, flutter, curl, and tumbling around the prevailing flow.")
            .defineInRange("turbulenceStrength", 1.0D, TURBULENCE_STRENGTH_MIN, TURBULENCE_STRENGTH_MAX);

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_DEBUG_OVERLAY = BUILDER
            .comment("Shows a compact client-side overlay with the current wind simulation state.")
            .define("enableWindDebugOverlay", false);

    public static final ModConfigSpec.BooleanValue ENABLE_LOCAL_WIND_EXPOSURE = BUILDER
            .comment("Uses cached direction-aware shelter sampling for local wind strength.")
            .define("enableLocalWindExposure", true);

    public static final ModConfigSpec.DoubleValue WIND_EXPOSURE_RADIUS = BUILDER
            .comment("Sets the radius of the small shelter sampling pattern in blocks.")
            .defineInRange("windExposureRadius", 5.0D, WIND_EXPOSURE_RADIUS_MIN, WIND_EXPOSURE_RADIUS_MAX);

    public static final ModConfigSpec.DoubleValue ALTITUDE_WIND_INFLUENCE = BUILDER
            .comment("Scales the subtle wind increase at high elevations using the current dimension's build height.")
            .defineInRange("altitudeWindInfluence", 0.5D, ALTITUDE_WIND_INFLUENCE_MIN, ALTITUDE_WIND_INFLUENCE_MAX);

    public static final ModConfigSpec.BooleanValue ENABLE_BIOME_WIND_PROFILES = BUILDER
            .comment("Applies reloadable biome-specific wind strength, gust, turbulence, and altitude multipliers.")
            .define("enableBiomeWindProfiles", true);

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_SMOKE = BUILDER
            .comment("Lets campfire smoke drift, curl, and merge into larger wind-shaped plumes.")
            .define("enableWindSmoke", true);

    public static final ModConfigSpec.DoubleValue WIND_SMOKE_STRENGTH = BUILDER
            .comment("Scales how strongly campfire smoke follows the wind and plume turbulence.")
            .defineInRange("windSmokeStrength", 3.0D, WIND_SMOKE_STRENGTH_MIN, WIND_SMOKE_STRENGTH_MAX);

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_LANTERN_SWAY = BUILDER
            .comment("Lets hanging lanterns sway subtly with the wind.")
            .define("enableWindLanternSway", true);

    public static final ModConfigSpec.DoubleValue WIND_LANTERN_SWAY_STRENGTH = BUILDER
            .comment("Scales how strongly hanging lanterns sway with the wind.")
            .defineInRange("windLanternSwayStrength", 1.0D, WIND_LANTERN_SWAY_STRENGTH_MIN, WIND_LANTERN_SWAY_STRENGTH_MAX);

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_HANGING_SIGN_SWAY = BUILDER
            .comment("Lets hanging signs sway back and forth with the wind.")
            .define("enableWindHangingSignSway", true);

    public static final ModConfigSpec.DoubleValue WIND_HANGING_SIGN_SWAY_STRENGTH = BUILDER
            .comment("Scales how strongly hanging signs sway with the wind.")
            .defineInRange("windHangingSignSwayStrength", 1.0D, WIND_HANGING_SIGN_SWAY_STRENGTH_MIN, WIND_HANGING_SIGN_SWAY_STRENGTH_MAX);

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_REACTIVE_BANNERS = BUILDER
            .comment("Lets standing and wall banners deform in response to the shared client wind simulation.")
            .define("enableWindReactiveBanners", true);

    public static final ModConfigSpec.DoubleValue BANNER_WIND_STRENGTH = BUILDER
            .comment("Scales how far wind lifts the vanilla banner cloth from its resting angle.")
            .defineInRange("bannerWindStrength", 1.0D, BANNER_RESPONSE_MIN, BANNER_RESPONSE_MAX);

    public static final ModConfigSpec.DoubleValue BANNER_GUST_RESPONSE = BUILDER
            .comment("Scales how strongly the whole banner responds to travelling gust fronts.")
            .defineInRange("bannerGustResponse", 1.0D, BANNER_RESPONSE_MIN, BANNER_RESPONSE_MAX);

    public static final ModConfigSpec.DoubleValue BANNER_FLUTTER_STRENGTH = BUILDER
            .comment("Scales the banner's small, quick whole-cloth movement.")
            .defineInRange("bannerFlutterStrength", 0.75D, BANNER_RESPONSE_MIN, BANNER_RESPONSE_MAX);

    public static final ModConfigSpec.DoubleValue BANNER_SAG_STRENGTH = BUILDER
            .comment("Scales how strongly banner cloth prefers to hang downward.")
            .defineInRange("bannerSagStrength", 1.0D, BANNER_RESPONSE_MIN, BANNER_RESPONSE_MAX);

    public static final ModConfigSpec.DoubleValue BANNER_TURBULENCE_RESPONSE = BUILDER
            .comment("Scales irregular banner sway from local turbulence.")
            .defineInRange("bannerTurbulenceResponse", 0.65D, BANNER_RESPONSE_MIN, BANNER_RESPONSE_MAX);

    public static final ModConfigSpec.DoubleValue BANNER_ANIMATION_DISTANCE = BUILDER
            .comment("Maximum camera distance in blocks for wind-reactive banner cloth.")
            .defineInRange("bannerAnimationDistance", 64.0D, BANNER_ANIMATION_DISTANCE_MIN, BANNER_ANIMATION_DISTANCE_MAX);

    public static final ModConfigSpec.BooleanValue ENABLE_STANDALONE_CHAIN_SWAY = BUILDER
            .comment("Lets vertical chain blocks sway even when their connected stack does not end in a hanging lantern.")
            .define("enableStandaloneChainSway", false);

    public static final ModConfigSpec.BooleanValue ENABLE_SODIUM_SHADER_PATCH = BUILDER
            .comment("Adds Where Winds Blow wind uniforms and source injections to Sodium's own terrain shader.")
            .define("enableSodiumShaderPatch", true);

    public static final ModConfigSpec.BooleanValue IRIS_WARNING_SHOWN = BUILDER
            .comment("Tracks whether the one-time client-side Iris compatibility warning has been shown.")
            .define("irisCompatibilityWarningShown", false);

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

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_REACTIVE_PRECIPITATION = BUILDER
            .comment("Uses Where Winds Blow's atmospheric precipitation renderer. Disable this to use vanilla rain and snow.")
            .define("enableWindReactivePrecipitation", true);

    public static final ModConfigSpec.BooleanValue ENABLE_RAIN_EFFECTS = BUILDER
            .comment("Renders atmospheric rain while Where Winds Blow's precipitation renderer is enabled.")
            .define("enableRainEffects", true);

    public static final ModConfigSpec.BooleanValue ENABLE_SLANTED_RAIN = BUILDER
            .comment("Lets rain lean with the shared wind simulation. Rain falls vertically when this is disabled.")
            .define("enableSlantedRain", true);

    public static final ModConfigSpec.DoubleValue RAIN_ANGLE_VARIATION = BUILDER
            .comment("Maximum per-stream rain angle variation in degrees during normal rain. Zero keeps every rain stream parallel.")
            .defineInRange("rainAngleVariation", 6.0D, RAIN_ANGLE_VARIATION_MIN, RAIN_ANGLE_VARIATION_MAX);

    public static final ModConfigSpec.DoubleValue THUNDER_RAIN_ANGLE_VARIATION = BUILDER
            .comment("Maximum per-stream rain angle variation in degrees at full thunder. Zero keeps every rain stream parallel.")
            .defineInRange("thunderRainAngleVariation", 14.0D, RAIN_ANGLE_VARIATION_MIN, RAIN_ANGLE_VARIATION_MAX);

    public static final ModConfigSpec.BooleanValue ENABLE_DYNAMIC_RAIN_SQUALLS = BUILDER
            .comment("Lets travelling gust fronts intensify rain while wind lulls soften it.")
            .define("enableDynamicRainSqualls", true);

    public static final ModConfigSpec.DoubleValue RAIN_SQUALL_STRENGTH = BUILDER
            .comment("Scales changes to rain density, speed, opacity, and tilt during gusts and lulls.")
            .defineInRange("rainSquallStrength", 1.0D, RAIN_SQUALL_STRENGTH_MIN, RAIN_SQUALL_STRENGTH_MAX);

    public static final ModConfigSpec.BooleanValue ENABLE_SNOW_EFFECTS = BUILDER
            .comment("Renders atmospheric snow while Where Winds Blow's precipitation renderer is enabled.")
            .define("enableSnowEffects", true);

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_DRIVEN_SNOW = BUILDER
            .comment("Lets snow drift and flutter with the shared wind simulation. Snow falls vertically when this is disabled.")
            .define("enableWindDrivenSnow", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public enum WindDirectionMode {
        DYNAMIC,
        FIXED
    }

    private ClientConfig() {
    }
}
