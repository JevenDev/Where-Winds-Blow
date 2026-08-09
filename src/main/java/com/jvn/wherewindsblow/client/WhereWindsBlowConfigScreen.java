package com.jvn.wherewindsblow.client;

import com.jvn.wherewindsblow.client.banner.WindReactiveBannerRenderer;
import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.jvn.wherewindsblow.config.CommonConfig;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.StateManager;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class WhereWindsBlowConfigScreen {
    private static final double WORLDGEN_MIN = 0.0D;
    private static final double WORLDGEN_MAX = 8.0D;
    private static final double WORLDGEN_STEP = 1.0D;
    private static final double SMALL_STEP = 0.05D;

    private WhereWindsBlowConfigScreen() {
    }

    public static Screen create(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(translatable("title"))
                .category(terrainCategory())
                .category(windCategory())
                .category(rainCategory())
                .category(thunderCategory())
                .category(sandstormCategory())
                .category(snowCategory())
                .category(renderingCategory())
                .save(WhereWindsBlowConfigScreen::saveAll)
                .build()
                .generateScreen(parent);
    }

    private static ConfigCategory windCategory() {
        return ConfigCategory.createBuilder()
                .name(translatable("wind"))
                .tooltip(translatable("wind.tooltip"))
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.windSimulation"))
                        .description(description("group.windSimulation.description"))
                        .option(booleanOption(ClientConfig.ENABLE_DYNAMIC_WIND, DynamicWindManager::reloadConfiguration))
                        .option(enumOption(
                                ClientConfig.WIND_DIRECTION_MODE,
                                ClientConfig.WindDirectionMode.class,
                                DynamicWindManager::reloadConfiguration
                        ))
                        .option(doubleOption(
                                ClientConfig.OVERALL_WIND_STRENGTH,
                                ClientConfig.OVERALL_WIND_STRENGTH_MIN,
                                ClientConfig.OVERALL_WIND_STRENGTH_MAX,
                                SMALL_STEP,
                                DynamicWindManager::reloadConfiguration
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_DIRECTION_DEGREES,
                                ClientConfig.WIND_DIRECTION_DEGREES_MIN,
                                ClientConfig.WIND_DIRECTION_DEGREES_MAX,
                                1.0D,
                                DynamicWindManager::reloadConfiguration
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.biomeProfiles"))
                        .description(description("group.biomeProfiles.description"))
                        .option(booleanOption(ClientConfig.ENABLE_BIOME_WIND_PROFILES, DynamicWindManager::reloadConfiguration))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.dynamicDirection"))
                        .description(description("group.dynamicDirection.description"))
                        .option(doubleOption(
                                ClientConfig.DYNAMIC_DIRECTION_VARIATION,
                                ClientConfig.DYNAMIC_DIRECTION_VARIATION_MIN,
                                ClientConfig.DYNAMIC_DIRECTION_VARIATION_MAX,
                                SMALL_STEP,
                                DynamicWindManager::reloadConfiguration
                        ))
                        .option(doubleOption(
                                ClientConfig.MIN_DIRECTION_HOLD_TIME,
                                ClientConfig.DIRECTION_HOLD_TIME_MIN,
                                ClientConfig.DIRECTION_HOLD_TIME_MAX,
                                5.0D,
                                DynamicWindManager::reloadConfiguration
                        ))
                        .option(doubleOption(
                                ClientConfig.MAX_DIRECTION_HOLD_TIME,
                                ClientConfig.DIRECTION_HOLD_TIME_MIN,
                                ClientConfig.DIRECTION_HOLD_TIME_MAX,
                                5.0D,
                                DynamicWindManager::reloadConfiguration
                        ))
                        .option(doubleOption(
                                ClientConfig.MAX_ORDINARY_DIRECTION_CHANGE,
                                ClientConfig.MAX_DIRECTION_CHANGE_MIN,
                                ClientConfig.MAX_DIRECTION_CHANGE_MAX,
                                1.0D,
                                DynamicWindManager::reloadConfiguration
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.lulls"))
                        .description(description("group.lulls.description"))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_LULLS, DynamicWindManager::reloadConfiguration))
                        .option(doubleOption(
                                ClientConfig.WIND_LULL_FREQUENCY,
                                ClientConfig.LULL_FREQUENCY_MIN,
                                ClientConfig.LULL_FREQUENCY_MAX,
                                SMALL_STEP,
                                DynamicWindManager::reloadConfiguration
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_LULL_STRENGTH,
                                ClientConfig.LULL_STRENGTH_MIN,
                                ClientConfig.LULL_STRENGTH_MAX,
                                SMALL_STEP,
                                DynamicWindManager::reloadConfiguration
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.gusts"))
                        .description(description("group.gusts.description"))
                        .option(booleanOption(ClientConfig.ENABLE_GUST_FRONTS, DynamicWindManager::reloadConfiguration))
                        .option(doubleOption(
                                ClientConfig.GUST_FREQUENCY,
                                ClientConfig.GUST_FREQUENCY_MIN,
                                ClientConfig.GUST_FREQUENCY_MAX,
                                SMALL_STEP,
                                DynamicWindManager::reloadConfiguration
                        ))
                        .option(doubleOption(
                                ClientConfig.GUST_STRENGTH,
                                ClientConfig.GUST_STRENGTH_MIN,
                                ClientConfig.GUST_STRENGTH_MAX,
                                SMALL_STEP,
                                DynamicWindManager::reloadConfiguration
                        ))
                        .option(doubleOption(
                                ClientConfig.GUST_TRAVEL_SPEED,
                                ClientConfig.GUST_TRAVEL_SPEED_MIN,
                                ClientConfig.GUST_TRAVEL_SPEED_MAX,
                                0.5D,
                                DynamicWindManager::reloadConfiguration
                        ))
                        .option(doubleOption(
                                ClientConfig.GUST_WIDTH,
                                ClientConfig.GUST_WIDTH_MIN,
                                ClientConfig.GUST_WIDTH_MAX,
                                1.0D,
                                DynamicWindManager::reloadConfiguration
                        ))
                        .option(doubleOption(
                                ClientConfig.TURBULENCE_STRENGTH,
                                ClientConfig.TURBULENCE_STRENGTH_MIN,
                                ClientConfig.TURBULENCE_STRENGTH_MAX,
                                SMALL_STEP,
                                DynamicWindManager::reloadConfiguration
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.localExposure"))
                        .description(description("group.localExposure.description"))
                        .option(booleanOption(ClientConfig.ENABLE_LOCAL_WIND_EXPOSURE, DynamicWindManager::reloadConfiguration))
                        .option(doubleOption(
                                ClientConfig.WIND_EXPOSURE_RADIUS,
                                ClientConfig.WIND_EXPOSURE_RADIUS_MIN,
                                ClientConfig.WIND_EXPOSURE_RADIUS_MAX,
                                1.0D,
                                DynamicWindManager::reloadConfiguration
                        ))
                        .option(doubleOption(
                                ClientConfig.ALTITUDE_WIND_INFLUENCE,
                                ClientConfig.ALTITUDE_WIND_INFLUENCE_MIN,
                                ClientConfig.ALTITUDE_WIND_INFLUENCE_MAX,
                                SMALL_STEP,
                                DynamicWindManager::reloadConfiguration
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.advanced"))
                        .description(description("group.advanced.description"))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_DEBUG_OVERLAY))
                        .build())
                .build();
    }

    private static ConfigCategory terrainCategory() {
        return ConfigCategory.createBuilder()
                .name(translatable("terrain"))
                .tooltip(translatable("terrain.tooltip"))
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.worldgen"))
                        .description(description("group.worldgen.description"))
                        .option(booleanOption(CommonConfig.ENABLE_DENSE_GRASS_WORLDGEN))
                        .option(worldgenOption(CommonConfig.DENSE_GRASS_DENSITY_MULTIPLIER))
                        .option(worldgenOption(CommonConfig.MEDIUM_GRASS_DENSITY_MULTIPLIER))
                        .option(worldgenOption(CommonConfig.SPARSE_GRASS_DENSITY_MULTIPLIER))
                        .option(worldgenOption(CommonConfig.TALL_GRASS_PATCH_MULTIPLIER))
                        .option(worldgenOption(CommonConfig.FERN_ACCENT_PATCH_MULTIPLIER))
                        .option(worldgenOption(CommonConfig.SHORT_DRY_GRASS_PATCH_MULTIPLIER))
                        .option(worldgenOption(CommonConfig.TALL_DRY_GRASS_PATCH_MULTIPLIER))
                        .option(worldgenOption(CommonConfig.DEAD_GRASS_PATCH_MULTIPLIER))
                        .option(worldgenOption(CommonConfig.OVERGROWN_GRASS_PATCH_MULTIPLIER))
                        .option(worldgenOption(CommonConfig.WILD_WHEAT_PATCH_MULTIPLIER))
                        .build())
                .build();
    }

    private static ConfigCategory rainCategory() {
        return ConfigCategory.createBuilder()
                .name(translatable("rain"))
                .tooltip(translatable("rain.tooltip"))
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.rainResponse"))
                        .description(description("group.rainResponse.description"))
                        .option(weatherWindPowerOption(ClientConfig.RAIN_WEATHER_WIND_POWER))
                        .option(weatherSwayOption(ClientConfig.RAIN_WEATHER_SWAY_STRENGTH))
                        .option(weatherSheenOption(ClientConfig.RAIN_WEATHER_SHEEN_STRENGTH))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.rainfall"))
                        .description(description("group.rainfall.description"))
                        .option(booleanOption(ClientConfig.ENABLE_RAIN_EFFECTS))
                        .option(booleanOption(ClientConfig.ENABLE_SLANTED_RAIN))
                        .option(doubleOption(
                                ClientConfig.RAIN_ANGLE_VARIATION,
                                ClientConfig.RAIN_ANGLE_VARIATION_MIN,
                                ClientConfig.RAIN_ANGLE_VARIATION_MAX,
                                1.0D
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.rainSqualls"))
                        .description(description("group.rainSqualls.description"))
                        .option(booleanOption(ClientConfig.ENABLE_DYNAMIC_RAIN_SQUALLS))
                        .option(doubleOption(
                                ClientConfig.RAIN_SQUALL_STRENGTH,
                                ClientConfig.RAIN_SQUALL_STRENGTH_MIN,
                                ClientConfig.RAIN_SQUALL_STRENGTH_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .build();
    }

    private static ConfigCategory thunderCategory() {
        return ConfigCategory.createBuilder()
                .name(translatable("thunder"))
                .tooltip(translatable("thunder.tooltip"))
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.thunderResponse"))
                        .description(description("group.thunderResponse.description"))
                        .option(weatherWindPowerOption(ClientConfig.THUNDER_WEATHER_WIND_POWER))
                        .option(weatherSwayOption(ClientConfig.THUNDER_WEATHER_SWAY_STRENGTH))
                        .option(weatherSheenOption(ClientConfig.THUNDER_WEATHER_SHEEN_STRENGTH))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.thunderRain"))
                        .description(description("group.thunderRain.description"))
                        .option(doubleOption(
                                ClientConfig.THUNDER_RAIN_ANGLE_VARIATION,
                                ClientConfig.RAIN_ANGLE_VARIATION_MIN,
                                ClientConfig.RAIN_ANGLE_VARIATION_MAX,
                                1.0D
                        ))
                        .build())
                .build();
    }

    private static ConfigCategory snowCategory() {
        return ConfigCategory.createBuilder()
                .name(translatable("snow"))
                .tooltip(translatable("snow.tooltip"))
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.snowfall"))
                        .description(description("group.snowfall.description"))
                        .option(booleanOption(ClientConfig.ENABLE_SNOW_EFFECTS))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_DRIVEN_SNOW))
                        .option(doubleOption(
                                ClientConfig.SNOWFLAKE_AMOUNT,
                                ClientConfig.SNOWFLAKE_AMOUNT_MIN,
                                ClientConfig.SNOWFLAKE_AMOUNT_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.SNOWFLAKE_SIZE,
                                ClientConfig.SNOWFLAKE_SIZE_MIN,
                                ClientConfig.SNOWFLAKE_SIZE_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.SNOWFLAKE_SPEED,
                                ClientConfig.SNOWFLAKE_SPEED_MIN,
                                ClientConfig.SNOWFLAKE_SPEED_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.snowFog"))
                        .description(description("group.snowFog.description"))
                        .option(enumOption(
                                ClientConfig.SNOW_FOG_MODE,
                                ClientConfig.SnowFogMode.class,
                                () -> {
                                }
                        ))
                        .option(doubleOption(
                                ClientConfig.SNOW_FOG_INTENSITY,
                                ClientConfig.SNOW_FOG_INTENSITY_MIN,
                                ClientConfig.SNOW_FOG_INTENSITY_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.blizzards"))
                        .description(description("group.blizzards.description"))
                        .option(booleanOption(ClientConfig.ENABLE_BLIZZARD_EFFECTS))
                        .option(doubleOption(
                                ClientConfig.BLIZZARD_SNOWFLAKE_AMOUNT,
                                ClientConfig.SNOWFLAKE_AMOUNT_MIN,
                                ClientConfig.SNOWFLAKE_AMOUNT_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.BLIZZARD_SNOWFLAKE_SIZE,
                                ClientConfig.SNOWFLAKE_SIZE_MIN,
                                ClientConfig.SNOWFLAKE_SIZE_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.BLIZZARD_SNOWFLAKE_SPEED,
                                ClientConfig.SNOWFLAKE_SPEED_MIN,
                                ClientConfig.SNOWFLAKE_SPEED_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.BLIZZARD_INTENSITY,
                                ClientConfig.BLIZZARD_INTENSITY_MIN,
                                ClientConfig.BLIZZARD_INTENSITY_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .build();
    }

    private static ConfigCategory sandstormCategory() {
        return ConfigCategory.createBuilder()
                .name(translatable("sandstorms"))
                .tooltip(translatable("sandstorms.tooltip"))
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.blowingSand"))
                        .description(description("group.blowingSand.description"))
                        .option(booleanOption(ClientConfig.ENABLE_DESERT_STORM_EFFECTS))
                        .option(doubleOption(
                                ClientConfig.SAND_DUST_AMOUNT,
                                ClientConfig.SAND_DUST_AMOUNT_MIN,
                                ClientConfig.SAND_DUST_AMOUNT_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.SAND_DUST_SIZE,
                                ClientConfig.SAND_DUST_SIZE_MIN,
                                ClientConfig.SAND_DUST_SIZE_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.SAND_DUST_SPEED,
                                ClientConfig.SAND_DUST_SPEED_MIN,
                                ClientConfig.SAND_DUST_SPEED_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.sandstormFog"))
                        .description(description("group.sandstormFog.description"))
                        .option(enumOption(
                                ClientConfig.SANDSTORM_FOG_MODE,
                                ClientConfig.SandstormFogMode.class,
                                () -> {
                                }
                        ))
                        .option(doubleOption(
                                ClientConfig.SANDSTORM_FOG_INTENSITY,
                                ClientConfig.SANDSTORM_FOG_INTENSITY_MIN,
                                ClientConfig.SANDSTORM_FOG_INTENSITY_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.severeSandstorms"))
                        .description(description("group.severeSandstorms.description"))
                        .option(doubleOption(
                                ClientConfig.SANDSTORM_DUST_AMOUNT,
                                ClientConfig.SAND_DUST_AMOUNT_MIN,
                                ClientConfig.SAND_DUST_AMOUNT_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.SANDSTORM_DUST_SIZE,
                                ClientConfig.SAND_DUST_SIZE_MIN,
                                ClientConfig.SAND_DUST_SIZE_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.SANDSTORM_DUST_SPEED,
                                ClientConfig.SAND_DUST_SPEED_MIN,
                                ClientConfig.SAND_DUST_SPEED_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.SANDSTORM_INTENSITY,
                                ClientConfig.SANDSTORM_INTENSITY_MIN,
                                ClientConfig.SANDSTORM_INTENSITY_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .build();
    }

    private static ConfigCategory renderingCategory() {
        return ConfigCategory.createBuilder()
                .name(translatable("rendering"))
                .tooltip(translatable("rendering.tooltip"))
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.interactivity"))
                        .description(description("group.interactivity.description"))
                        .option(booleanOption(ClientConfig.ENABLE_FOLIAGE_INTERACTIVITY))
                        .option(doubleOption(
                                ClientConfig.FOLIAGE_INTERACTIVITY_STRENGTH,
                                ClientConfig.FOLIAGE_INTERACTIVITY_STRENGTH_MIN,
                                ClientConfig.FOLIAGE_INTERACTIVITY_STRENGTH_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.foliage"))
                        .description(description("group.foliage.description"))
                        .option(booleanOption(ClientConfig.ENABLE_CUSTOM_FOLIAGE_SHADER, WhereWindsBlowConfigScreen::rebuildFoliage))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.windSheen"))
                        .description(description("group.windSheen.description"))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_SHEEN))
                        .option(doubleOption(
                                ClientConfig.WIND_SHEEN_STRENGTH,
                                ClientConfig.WIND_SHEEN_STRENGTH_MIN,
                                ClientConfig.WIND_SHEEN_STRENGTH_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.foliageColor"))
                        .description(description("group.foliageColor.description"))
                        .option(booleanOption(ClientConfig.ENABLE_FOLIAGE_COLOR_VARIATION))
                        .option(doubleOption(
                                ClientConfig.FOLIAGE_COLOR_VARIATION_STRENGTH,
                                ClientConfig.FOLIAGE_COLOR_VARIATION_STRENGTH_MIN,
                                ClientConfig.FOLIAGE_COLOR_VARIATION_STRENGTH_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.windMotion"))
                        .description(description("group.windMotion.description"))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_FOLIAGE_SWAY))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_PLANT_SWAY))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_LEAF_SWAY))
                        .option(doubleOption(
                                ClientConfig.WIND_FOLIAGE_SWAY_STRENGTH,
                                ClientConfig.WIND_FOLIAGE_SWAY_STRENGTH_MIN,
                                ClientConfig.WIND_FOLIAGE_SWAY_STRENGTH_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_PLANT_SWAY_START_HEIGHT,
                                ClientConfig.WIND_PLANT_SWAY_START_HEIGHT_MIN,
                                ClientConfig.WIND_PLANT_SWAY_START_HEIGHT_MAX,
                                SMALL_STEP,
                                WhereWindsBlowConfigScreen::rebuildFoliage
                        ))
                        .option(enumOption(
                                ClientConfig.FOLIAGE_ANIMATION_MODE,
                                ClientConfig.FoliageAnimationMode.class,
                                () -> {
                                }
                        ))
                        .option(doubleOption(
                                ClientConfig.FOLIAGE_STEPPED_RATE,
                                ClientConfig.FOLIAGE_STEPPED_RATE_MIN,
                                ClientConfig.FOLIAGE_STEPPED_RATE_MAX,
                                1.0D
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.clearWeather"))
                        .description(description("group.clearWeather.description"))
                        .option(weatherWindPowerOption(ClientConfig.CLEAR_WEATHER_WIND_POWER))
                        .option(weatherSwayOption(ClientConfig.CLEAR_WEATHER_SWAY_STRENGTH))
                        .option(weatherSheenOption(ClientConfig.CLEAR_WEATHER_SHEEN_STRENGTH))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.precipitation"))
                        .description(description("group.precipitation.description"))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_REACTIVE_PRECIPITATION))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.ambientWind"))
                        .description(description("group.ambientWind.description"))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_STREAKS))
                        .option(doubleOption(
                                ClientConfig.WIND_STREAK_VISIBILITY,
                                ClientConfig.WIND_STREAK_VISIBILITY_MIN,
                                ClientConfig.WIND_STREAK_VISIBILITY_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.windLines"))
                        .description(description("group.windLines.description"))
                        .option(doubleOption(
                                ClientConfig.WIND_LINE_DENSITY,
                                ClientConfig.WIND_LINE_DENSITY_MIN,
                                ClientConfig.WIND_LINE_DENSITY_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_STREAK_OPACITY,
                                ClientConfig.WIND_STREAK_OPACITY_MIN,
                                ClientConfig.WIND_STREAK_OPACITY_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_STREAK_THICKNESS,
                                ClientConfig.WIND_STREAK_THICKNESS_MIN,
                                ClientConfig.WIND_STREAK_THICKNESS_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.windLeaves"))
                        .description(description("group.windLeaves.description"))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_LEAVES))
                        .option(doubleOption(
                                ClientConfig.WIND_LEAF_DENSITY,
                                ClientConfig.WIND_LEAF_DENSITY_MIN,
                                ClientConfig.WIND_LEAF_DENSITY_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_LEAF_OPACITY,
                                ClientConfig.WIND_LEAF_OPACITY_MIN,
                                ClientConfig.WIND_LEAF_OPACITY_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.windFlowerPetals"))
                        .description(description("group.windFlowerPetals.description"))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_FLOWER_PETALS))
                        .option(doubleOption(
                                ClientConfig.WIND_FLOWER_PETAL_DENSITY,
                                ClientConfig.WIND_FLOWER_PETAL_DENSITY_MIN,
                                ClientConfig.WIND_FLOWER_PETAL_DENSITY_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_FLOWER_PETAL_OPACITY,
                                ClientConfig.WIND_FLOWER_PETAL_OPACITY_MIN,
                                ClientConfig.WIND_FLOWER_PETAL_OPACITY_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.tumbleweeds"))
                        .description(description("group.tumbleweeds.description"))
                        .option(booleanOption(ClientConfig.ENABLE_TUMBLEWEEDS))
                        .option(doubleOption(
                                ClientConfig.TUMBLEWEED_DENSITY,
                                ClientConfig.TUMBLEWEED_DENSITY_MIN,
                                ClientConfig.TUMBLEWEED_DENSITY_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.windSmoke"))
                        .description(description("group.windSmoke.description"))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_SMOKE))
                        .option(doubleOption(
                                ClientConfig.WIND_SMOKE_STRENGTH,
                                ClientConfig.WIND_SMOKE_STRENGTH_MIN,
                                ClientConfig.WIND_SMOKE_STRENGTH_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.hangingDecorations"))
                        .description(description("group.hangingDecorations.description"))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_LANTERN_SWAY, WhereWindsBlowConfigScreen::rebuildFoliage))
                        .option(doubleOption(
                                ClientConfig.WIND_LANTERN_SWAY_STRENGTH,
                                ClientConfig.WIND_LANTERN_SWAY_STRENGTH_MIN,
                                ClientConfig.WIND_LANTERN_SWAY_STRENGTH_MAX,
                                SMALL_STEP
                        ))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_HANGING_SIGN_SWAY))
                        .option(doubleOption(
                                ClientConfig.WIND_HANGING_SIGN_SWAY_STRENGTH,
                                ClientConfig.WIND_HANGING_SIGN_SWAY_STRENGTH_MIN,
                                ClientConfig.WIND_HANGING_SIGN_SWAY_STRENGTH_MAX,
                                SMALL_STEP
                        ))
                        .option(booleanOption(ClientConfig.ENABLE_STANDALONE_CHAIN_SWAY, WhereWindsBlowConfigScreen::rebuildFoliage))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.banners"))
                        .description(description("group.banners.description"))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_REACTIVE_BANNERS, WindReactiveBannerRenderer::reset))
                        .option(bannerResponseOption(ClientConfig.BANNER_WIND_STRENGTH))
                        .option(bannerResponseOption(ClientConfig.BANNER_GUST_RESPONSE))
                        .option(bannerResponseOption(ClientConfig.BANNER_FLUTTER_STRENGTH))
                        .option(bannerResponseOption(ClientConfig.BANNER_SAG_STRENGTH))
                        .option(bannerResponseOption(ClientConfig.BANNER_TURBULENCE_RESPONSE))
                        .option(doubleOption(
                                ClientConfig.BANNER_ANIMATION_DISTANCE,
                                ClientConfig.BANNER_ANIMATION_DISTANCE_MIN,
                                ClientConfig.BANNER_ANIMATION_DISTANCE_MAX,
                                4.0D
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.compatibility"))
                        .description(description("group.compatibility.description"))
                        .option(booleanOption(ClientConfig.ENABLE_SODIUM_SHADER_PATCH))
                        .build())
                .build();
    }

    private static Option<Boolean> booleanOption(ModConfigSpec.BooleanValue value) {
        return booleanOption(value, () -> {
        });
    }

    private static Option<Boolean> booleanOption(ModConfigSpec.BooleanValue value, Runnable afterChange) {
        String path = path(value);
        return Option.<Boolean>createBuilder()
                .name(translatable(path))
                .description(description(path + ".tooltip"))
                .stateManager(StateManager.createInstant(value.getDefault(), value::getAsBoolean, newValue -> {
                    value.set(newValue);
                    value.save();
                    afterChange.run();
                }))
                .controller(option -> BooleanControllerBuilder.create(option).onOffFormatter().coloured(true))
                .build();
    }

    private static Option<Double> worldgenOption(ModConfigSpec.DoubleValue value) {
        return doubleOption(value, WORLDGEN_MIN, WORLDGEN_MAX, WORLDGEN_STEP);
    }

    private static <T extends Enum<T>> Option<T> enumOption(
            ModConfigSpec.EnumValue<T> value,
            Class<T> enumClass,
            Runnable afterChange
    ) {
        String path = path(value);
        return Option.<T>createBuilder()
                .name(translatable(path))
                .description(description(path + ".tooltip"))
                .stateManager(StateManager.createInstant(value.getDefault(), value::get, newValue -> {
                    value.set(newValue);
                    value.save();
                    afterChange.run();
                }))
                .controller(option -> EnumControllerBuilder.create(option)
                        .enumClass(enumClass)
                        .formatValue(enumValue -> translatable(path + "." + enumValue.name().toLowerCase(Locale.ROOT))))
                .build();
    }

    private static Option<Double> weatherWindPowerOption(ModConfigSpec.DoubleValue value) {
        return doubleOption(value, ClientConfig.WEATHER_WIND_POWER_MIN, ClientConfig.WEATHER_WIND_POWER_MAX, SMALL_STEP);
    }

    private static Option<Double> weatherSwayOption(ModConfigSpec.DoubleValue value) {
        return doubleOption(value, ClientConfig.WEATHER_SWAY_STRENGTH_MIN, ClientConfig.WEATHER_SWAY_STRENGTH_MAX, SMALL_STEP);
    }

    private static Option<Double> weatherSheenOption(ModConfigSpec.DoubleValue value) {
        return doubleOption(value, ClientConfig.WEATHER_SHEEN_STRENGTH_MIN, ClientConfig.WEATHER_SHEEN_STRENGTH_MAX, SMALL_STEP);
    }

    private static Option<Double> bannerResponseOption(ModConfigSpec.DoubleValue value) {
        return doubleOption(value, ClientConfig.BANNER_RESPONSE_MIN, ClientConfig.BANNER_RESPONSE_MAX, SMALL_STEP);
    }

    private static Option<Double> doubleOption(ModConfigSpec.DoubleValue value, double min, double max, double step) {
        return doubleOption(value, min, max, step, () -> {
        });
    }

    private static Option<Double> doubleOption(ModConfigSpec.DoubleValue value, double min, double max, double step, Runnable afterChange) {
        String path = path(value);
        return Option.<Double>createBuilder()
                .name(translatable(path))
                .description(description(path + ".tooltip"))
                .stateManager(StateManager.createInstant(value.getDefault(), value::getAsDouble, newValue -> {
                    value.set(newValue);
                    value.save();
                    afterChange.run();
                }))
                .controller(option -> DoubleSliderControllerBuilder.create(option)
                        .range(min, max)
                        .step(step)
                        .formatValue(WhereWindsBlowConfigScreen::formattedValue))
                .build();
    }

    private static Component formattedValue(double value) {
        return Component.literal(value == Math.rint(value)
                ? Integer.toString((int) value)
                : String.format(Locale.ROOT, "%.2f", value));
    }

    private static OptionDescription description(String path) {
        return OptionDescription.of(translatable(path));
    }

    private static Component translatable(String path) {
        return Component.translatable("where_winds_blow.config." + path);
    }

    private static String path(ModConfigSpec.ConfigValue<?> value) {
        return value.getPath().getLast();
    }

    private static void saveAll() {
        CommonConfig.SPEC.save();
        ClientConfig.SPEC.save();
    }

    private static void rebuildFoliage() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            minecraft.levelRenderer.allChanged();
        }
    }
}
