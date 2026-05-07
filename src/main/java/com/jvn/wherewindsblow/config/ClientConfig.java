package com.jvn.wherewindsblow.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_GRASS_WIND = BUILDER
            .comment("Enables client-side visual wind sway when a renderer implementation is available.")
            .define("enableGrassWind", true);

    public static final ModConfigSpec.DoubleValue WIND_STRENGTH = BUILDER
            .defineInRange("windStrength", 0.08D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue WIND_SPEED = BUILDER
            .defineInRange("windSpeed", 1.0D, 0.0D, 8.0D);

    public static final ModConfigSpec.BooleanValue AFFECT_SHORT_GRASS = BUILDER
            .define("affectShortGrass", true);

    public static final ModConfigSpec.BooleanValue AFFECT_TALL_GRASS = BUILDER
            .define("affectTallGrass", true);

    public static final ModConfigSpec.BooleanValue AFFECT_FERNS = BUILDER
            .define("affectFerns", false);

    public static final ModConfigSpec.BooleanValue AFFECT_FLOWERS = BUILDER
            .define("affectFlowers", false);

    public static final ModConfigSpec.BooleanValue DISABLE_WHEN_SHADER_PACK_DETECTED = BUILDER
            .comment("Reserved for shader compatibility checks in the client wind implementation.")
            .define("disableWhenShaderPackDetected", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
