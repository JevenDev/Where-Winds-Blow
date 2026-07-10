package com.jvn.wherewindsblow.client;

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
                        .option(worldgenOption(CommonConfig.SHORT_DRY_GRASS_PATCH_MULTIPLIER))
                        .option(worldgenOption(CommonConfig.TALL_DRY_GRASS_PATCH_MULTIPLIER))
                        .option(worldgenOption(CommonConfig.DEAD_GRASS_PATCH_MULTIPLIER))
                        .option(worldgenOption(CommonConfig.OVERGROWN_GRASS_PATCH_MULTIPLIER))
                        .option(worldgenOption(CommonConfig.WILD_WHEAT_PATCH_MULTIPLIER))
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
                        .option(booleanOption(ClientConfig.ENABLE_AUTODETECTED_FOLIAGE_MODELS, WhereWindsBlowConfigScreen::rebuildFoliage))
                        .option(booleanOption(ClientConfig.ENABLE_CUSTOM_FOLIAGE_SHADER, WhereWindsBlowConfigScreen::rebuildFoliage))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_SHEEN))
                        .option(doubleOption(
                                ClientConfig.WIND_SHEEN_STRENGTH,
                                ClientConfig.WIND_SHEEN_STRENGTH_MIN,
                                ClientConfig.WIND_SHEEN_STRENGTH_MAX,
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
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(translatable("group.weather"))
                        .description(description("group.weather.description"))
                        .option(weatherWindPowerOption(ClientConfig.CLEAR_WEATHER_WIND_POWER))
                        .option(weatherWindPowerOption(ClientConfig.RAIN_WEATHER_WIND_POWER))
                        .option(weatherWindPowerOption(ClientConfig.THUNDER_WEATHER_WIND_POWER))
                        .option(weatherSwayOption(ClientConfig.CLEAR_WEATHER_SWAY_STRENGTH))
                        .option(weatherSwayOption(ClientConfig.RAIN_WEATHER_SWAY_STRENGTH))
                        .option(weatherSwayOption(ClientConfig.THUNDER_WEATHER_SWAY_STRENGTH))
                        .option(weatherSheenOption(ClientConfig.CLEAR_WEATHER_SHEEN_STRENGTH))
                        .option(weatherSheenOption(ClientConfig.RAIN_WEATHER_SHEEN_STRENGTH))
                        .option(weatherSheenOption(ClientConfig.THUNDER_WEATHER_SHEEN_STRENGTH))
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
                        .option(doubleOption(
                                ClientConfig.WIND_LINE_DENSITY,
                                ClientConfig.WIND_LINE_DENSITY_MIN,
                                ClientConfig.WIND_LINE_DENSITY_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_LEAF_DENSITY,
                                ClientConfig.WIND_LEAF_DENSITY_MIN,
                                ClientConfig.WIND_LEAF_DENSITY_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_STREAK_OPACITY,
                                ClientConfig.WIND_STREAK_OPACITY_MIN,
                                ClientConfig.WIND_STREAK_OPACITY_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_LEAF_OPACITY,
                                ClientConfig.WIND_LEAF_OPACITY_MIN,
                                ClientConfig.WIND_LEAF_OPACITY_MAX,
                                SMALL_STEP
                        ))
                        .option(doubleOption(
                                ClientConfig.WIND_STREAK_THICKNESS,
                                ClientConfig.WIND_STREAK_THICKNESS_MIN,
                                ClientConfig.WIND_STREAK_THICKNESS_MAX,
                                SMALL_STEP
                        ))
                        .option(booleanOption(ClientConfig.ENABLE_WIND_SMOKE))
                        .option(doubleOption(
                                ClientConfig.WIND_SMOKE_STRENGTH,
                                ClientConfig.WIND_SMOKE_STRENGTH_MIN,
                                ClientConfig.WIND_SMOKE_STRENGTH_MAX,
                                SMALL_STEP
                        ))
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
