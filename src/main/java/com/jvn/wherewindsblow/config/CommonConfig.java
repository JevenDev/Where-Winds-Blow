package com.jvn.wherewindsblow.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CommonConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_DENSE_GRASS_WORLDGEN = BUILDER
            .comment("Enables Where Winds Blow's added grass worldgen. Changes require a world reload/restart and only affect newly generated chunks.")
            .define("enableDenseGrassWorldgen", true);

    public static final ModConfigSpec.DoubleValue DENSE_GRASS_DENSITY_MULTIPLIER = BUILDER
            .comment("Multiplies dense short grass feature passes. Rounded to whole passes when biome modifiers load; changes require a world reload/restart.")
            .defineInRange("denseGrassDensityMultiplier", 8.0D, 0.0D, 8.0D);

    public static final ModConfigSpec.DoubleValue MEDIUM_GRASS_DENSITY_MULTIPLIER = BUILDER
            .comment("Multiplies medium short grass feature passes. Rounded to whole passes when biome modifiers load; changes require a world reload/restart.")
            .defineInRange("mediumGrassDensityMultiplier", 8.0D, 0.0D, 8.0D);

    public static final ModConfigSpec.DoubleValue SPARSE_GRASS_DENSITY_MULTIPLIER = BUILDER
            .comment("Multiplies sparse short grass feature passes. Rounded to whole passes when biome modifiers load; changes require a world reload/restart.")
            .defineInRange("sparseGrassDensityMultiplier", 8.0D, 0.0D, 8.0D);

    public static final ModConfigSpec.DoubleValue TALL_GRASS_PATCH_MULTIPLIER = BUILDER
            .comment("Multiplies tall grass patch feature passes. Rounded to whole passes when biome modifiers load; changes require a world reload/restart.")
            .defineInRange("tallGrassPatchMultiplier", 4.0D, 0.0D, 8.0D);

    public static final ModConfigSpec.DoubleValue FERN_ACCENT_PATCH_MULTIPLIER = BUILDER
            .comment("Multiplies coherent vanilla fern accent passes. Rounded to whole passes when biome modifiers load; changes require a world reload/restart.")
            .defineInRange("fernAccentPatchMultiplier", 2.0D, 0.0D, 8.0D);

    public static final ModConfigSpec.DoubleValue SHORT_DRY_GRASS_PATCH_MULTIPLIER = BUILDER
            .comment("Multiplies short dry grass patch feature passes. Rounded to whole passes when biome modifiers load; changes require a world reload/restart.")
            .defineInRange("shortDryGrassPatchMultiplier", 1.0D, 0.0D, 8.0D);

    public static final ModConfigSpec.DoubleValue TALL_DRY_GRASS_PATCH_MULTIPLIER = BUILDER
            .comment("Multiplies tall dry grass patch feature passes. Rounded to whole passes when biome modifiers load; changes require a world reload/restart.")
            .defineInRange("tallDryGrassPatchMultiplier", 1.0D, 0.0D, 8.0D);

    public static final ModConfigSpec.DoubleValue DEAD_GRASS_PATCH_MULTIPLIER = BUILDER
            .comment("Multiplies dead grass patch feature passes. Rounded to whole passes when biome modifiers load; changes require a world reload/restart.")
            .defineInRange("deadGrassPatchMultiplier", 1.0D, 0.0D, 8.0D);

    public static final ModConfigSpec.DoubleValue OVERGROWN_GRASS_PATCH_MULTIPLIER = BUILDER
            .comment("Multiplies overgrown grass patch feature passes. Rounded to whole passes when biome modifiers load; changes require a world reload/restart.")
            .defineInRange("overgrownGrassPatchMultiplier", 1.0D, 0.0D, 8.0D);

    public static final ModConfigSpec.DoubleValue WILD_WHEAT_PATCH_MULTIPLIER = BUILDER
            .comment("Multiplies wild wheat patch feature passes. Rounded to whole passes when biome modifiers load; changes require a world reload/restart.")
            .defineInRange("wildWheatPatchMultiplier", 0.0D, 0.0D, 8.0D);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CommonConfig() {
    }
}
