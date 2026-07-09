package com.jvn.wherewindsblow.client;

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
                .category(renderingCategory())
                .save(WhereWindsBlowConfigScreen::saveAll)
                .build()
                .generateScreen(parent);
    }

    private static ConfigCategory terrainCategory() {
        return ConfigCategory.createBuilder()
                .name(translatable("terrain"))
                .tooltip(translatable("terrain.tooltip"))
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.worldgen"))
                        .description(description("group.worldgen.description"))
                        .option(booleanOption(CommonConfig.ENABLE_DENSE_GRASS_WORLDGEN, true))
                        .option(worldgenOption(CommonConfig.DENSE_GRASS_DENSITY_MULTIPLIER, 8.0D))
                        .option(worldgenOption(CommonConfig.MEDIUM_GRASS_DENSITY_MULTIPLIER, 8.0D))
                        .option(worldgenOption(CommonConfig.SPARSE_GRASS_DENSITY_MULTIPLIER, 8.0D))
                        .option(worldgenOption(CommonConfig.TALL_GRASS_PATCH_MULTIPLIER, 4.0D))
                        .option(worldgenOption(CommonConfig.SHORT_DRY_GRASS_PATCH_MULTIPLIER, 1.0D))
                        .option(worldgenOption(CommonConfig.TALL_DRY_GRASS_PATCH_MULTIPLIER, 1.0D))
                        .option(worldgenOption(CommonConfig.DEAD_GRASS_PATCH_MULTIPLIER, 1.0D))
                        .option(worldgenOption(CommonConfig.OVERGROWN_GRASS_PATCH_MULTIPLIER, 1.0D))
                        .option(worldgenOption(CommonConfig.WILD_WHEAT_PATCH_MULTIPLIER, 0.0D))
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
                        .option(booleanOption(ClientConfig.ENABLE_FOLIAGE_INTERACTIVITY, true))
                        .option(doubleOption(
                                ClientConfig.FOLIAGE_INTERACTIVITY_STRENGTH,
                                1.0D,
                                ClientConfig.FOLIAGE_INTERACTIVITY_STRENGTH_MIN,
                                ClientConfig.FOLIAGE_INTERACTIVITY_STRENGTH_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.foliage"))
                        .description(description("group.foliage.description"))
                        .option(booleanOption(ClientConfig.ENABLE_AUTODETECTED_FOLIAGE_MODELS, true, WhereWindsBlowConfigScreen::rebuildFoliage))
                        .option(booleanOption(ClientConfig.ENABLE_CUSTOM_FOLIAGE_SHADER, true, WhereWindsBlowConfigScreen::rebuildFoliage))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_SHEEN, true))
                        .option(doubleOption(
                                ClientConfig.WIND_SHEEN_STRENGTH,
                                1.0D,
                                ClientConfig.WIND_SHEEN_STRENGTH_MIN,
                                ClientConfig.WIND_SHEEN_STRENGTH_MAX,
                                SMALL_STEP
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.windMotion"))
                        .description(description("group.windMotion.description"))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_FOLIAGE_SWAY, true))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_PLANT_SWAY, true))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_LEAF_SWAY, true))
                        .option(doubleOption(
                                ClientConfig.WIND_FOLIAGE_SWAY_STRENGTH,
                                1.0D,
                                ClientConfig.WIND_FOLIAGE_SWAY_STRENGTH_MIN,
                                ClientConfig.WIND_FOLIAGE_SWAY_STRENGTH_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_PLANT_SWAY_START_HEIGHT,
                                0.6D,
                                ClientConfig.WIND_PLANT_SWAY_START_HEIGHT_MIN,
                                ClientConfig.WIND_PLANT_SWAY_START_HEIGHT_MAX,
                                SMALL_STEP,
                                WhereWindsBlowConfigScreen::rebuildFoliage
                        ))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.weather"))
                        .description(description("group.weather.description"))
                        .option(weatherWindPowerOption(ClientConfig.CLEAR_WEATHER_WIND_POWER, 0.0D))
                        .option(weatherWindPowerOption(ClientConfig.RAIN_WEATHER_WIND_POWER, 0.75D))
                        .option(weatherWindPowerOption(ClientConfig.THUNDER_WEATHER_WIND_POWER, 2.0D))
                        .option(weatherSwayOption(ClientConfig.CLEAR_WEATHER_SWAY_STRENGTH, 0.0D))
                        .option(weatherSwayOption(ClientConfig.RAIN_WEATHER_SWAY_STRENGTH, 1.5D))
                        .option(weatherSwayOption(ClientConfig.THUNDER_WEATHER_SWAY_STRENGTH, 2.0D))
                        .option(weatherSheenOption(ClientConfig.CLEAR_WEATHER_SHEEN_STRENGTH, 0.0D))
                        .option(weatherSheenOption(ClientConfig.RAIN_WEATHER_SHEEN_STRENGTH, 1.5D))
                        .option(weatherSheenOption(ClientConfig.THUNDER_WEATHER_SHEEN_STRENGTH, 2.0D))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.ambientWind"))
                        .description(description("group.ambientWind.description"))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_STREAKS, true))
                        .option(doubleOption(
                                ClientConfig.WIND_STREAK_VISIBILITY,
                                1.0D,
                                ClientConfig.WIND_STREAK_VISIBILITY_MIN,
                                ClientConfig.WIND_STREAK_VISIBILITY_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_LINE_DENSITY,
                                1.0D,
                                ClientConfig.WIND_LINE_DENSITY_MIN,
                                ClientConfig.WIND_LINE_DENSITY_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_LEAF_DENSITY,
                                1.0D,
                                ClientConfig.WIND_LEAF_DENSITY_MIN,
                                ClientConfig.WIND_LEAF_DENSITY_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_STREAK_OPACITY,
                                0.65D,
                                ClientConfig.WIND_STREAK_OPACITY_MIN,
                                ClientConfig.WIND_STREAK_OPACITY_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_LEAF_OPACITY,
                                1.0D,
                                ClientConfig.WIND_LEAF_OPACITY_MIN,
                                ClientConfig.WIND_LEAF_OPACITY_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_STREAK_THICKNESS,
                                3.0D,
                                ClientConfig.WIND_STREAK_THICKNESS_MIN,
                                ClientConfig.WIND_STREAK_THICKNESS_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_DIRECTION_DEGREES,
                                125.0D,
                                ClientConfig.WIND_DIRECTION_DEGREES_MIN,
                                ClientConfig.WIND_DIRECTION_DEGREES_MAX,
                                1.0D
                        ))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_SMOKE, true))
                        .option(doubleOption(
                                ClientConfig.WIND_SMOKE_STRENGTH,
                                3.0D,
                                ClientConfig.WIND_SMOKE_STRENGTH_MIN,
                                ClientConfig.WIND_SMOKE_STRENGTH_MAX,
                                SMALL_STEP
                        ))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_LANTERN_SWAY, true, WhereWindsBlowConfigScreen::rebuildFoliage))
                        .option(doubleOption(
                                ClientConfig.WIND_LANTERN_SWAY_STRENGTH,
                                1.0D,
                                ClientConfig.WIND_LANTERN_SWAY_STRENGTH_MIN,
                                ClientConfig.WIND_LANTERN_SWAY_STRENGTH_MAX,
                                SMALL_STEP
                        ))
                        .option(booleanOption(ClientConfig.ENABLE_STANDALONE_CHAIN_SWAY, false, WhereWindsBlowConfigScreen::rebuildFoliage))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.compatibility"))
                        .description(description("group.compatibility.description"))
                        .option(booleanOption(ClientConfig.ENABLE_SODIUM_SHADER_PATCH, true))
                        .build())
                .build();
    }

    private static Option<Boolean> booleanOption(ModConfigSpec.BooleanValue value, boolean defaultValue) {
        return booleanOption(value, defaultValue, () -> {
        });
    }

    private static Option<Boolean> booleanOption(ModConfigSpec.BooleanValue value, boolean defaultValue, Runnable afterChange) {
        String path = path(value);
        return Option.<Boolean>createBuilder()
                .name(translatable(path))
                .description(description(path + ".tooltip"))
                .stateManager(StateManager.createInstant(defaultValue, value::getAsBoolean, newValue -> {
                    value.set(newValue);
                    value.save();
                    afterChange.run();
                }))
                .controller(option -> BooleanControllerBuilder.create(option).onOffFormatter().coloured(true))
                .build();
    }

    private static Option<Double> worldgenOption(ModConfigSpec.DoubleValue value, double defaultValue) {
        return doubleOption(value, defaultValue, WORLDGEN_MIN, WORLDGEN_MAX, WORLDGEN_STEP);
    }

    private static Option<Double> weatherWindPowerOption(ModConfigSpec.DoubleValue value, double defaultValue) {
        return doubleOption(value, defaultValue, ClientConfig.WEATHER_WIND_POWER_MIN, ClientConfig.WEATHER_WIND_POWER_MAX, SMALL_STEP);
    }

    private static Option<Double> weatherSwayOption(ModConfigSpec.DoubleValue value, double defaultValue) {
        return doubleOption(value, defaultValue, ClientConfig.WEATHER_SWAY_STRENGTH_MIN, ClientConfig.WEATHER_SWAY_STRENGTH_MAX, SMALL_STEP);
    }

    private static Option<Double> weatherSheenOption(ModConfigSpec.DoubleValue value, double defaultValue) {
        return doubleOption(value, defaultValue, ClientConfig.WEATHER_SHEEN_STRENGTH_MIN, ClientConfig.WEATHER_SHEEN_STRENGTH_MAX, SMALL_STEP);
    }

    private static Option<Double> doubleOption(ModConfigSpec.DoubleValue value, double defaultValue, double min, double max, double step) {
        return doubleOption(value, defaultValue, min, max, step, () -> {
        });
    }

    private static Option<Double> doubleOption(ModConfigSpec.DoubleValue value, double defaultValue, double min, double max, double step, Runnable afterChange) {
        String path = path(value);
        return Option.<Double>createBuilder()
                .name(translatable(path))
                .description(description(path + ".tooltip"))
                .stateManager(StateManager.createInstant(defaultValue, value::getAsDouble, newValue -> {
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
