package com.jvn.wherewindsblow.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CommonConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_DENSE_GRASS_WORLDGEN = BUILDER
            .comment("Reserved for future code-driven or generated worldgen tuning. The bundled v1 JSON worldgen remains static.")
            .define("enableDenseGrassWorldgen", true);

    public static final ModConfigSpec.DoubleValue DENSE_GRASS_DENSITY_MULTIPLIER = BUILDER
            .comment("Reserved for future code-driven or generated worldgen tuning. Static JSON features do not read this at runtime.")
            .defineInRange("denseGrassDensityMultiplier", 1.0D, 0.0D, 8.0D);

    public static final ModConfigSpec.DoubleValue MEDIUM_GRASS_DENSITY_MULTIPLIER = BUILDER
            .comment("Reserved for future code-driven or generated worldgen tuning. Static JSON features do not read this at runtime.")
            .defineInRange("mediumGrassDensityMultiplier", 1.0D, 0.0D, 8.0D);

    public static final ModConfigSpec.DoubleValue SPARSE_GRASS_DENSITY_MULTIPLIER = BUILDER
            .comment("Reserved for future code-driven or generated worldgen tuning. Static JSON features do not read this at runtime.")
            .defineInRange("sparseGrassDensityMultiplier", 1.0D, 0.0D, 8.0D);

    public static final ModConfigSpec.DoubleValue TALL_GRASS_PATCH_MULTIPLIER = BUILDER
            .comment("Reserved for future code-driven or generated worldgen tuning. Static JSON features do not read this at runtime.")
            .defineInRange("tallGrassPatchMultiplier", 1.0D, 0.0D, 8.0D);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CommonConfig() {
    }
}
