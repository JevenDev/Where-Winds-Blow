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

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_SHEEN = BUILDER
            .comment("Enables the bright wind sheen highlight on responsive foliage.")
            .define("enableWindSheen", true);

    public static final ModConfigSpec.DoubleValue WIND_SHEEN_STRENGTH = BUILDER
            .comment("Scales the visible intensity of wind sheen highlights.")
            .defineInRange("windSheenStrength", 1.0D, 0.0D, 2.0D);

    public static final ModConfigSpec.BooleanValue ENABLE_WIND_FOLIAGE_SWAY = BUILDER
            .comment("Enables wind-driven foliage sway in the responsive foliage shader.")
            .define("enableWindFoliageSway", true);

    public static final ModConfigSpec.DoubleValue WIND_FOLIAGE_SWAY_STRENGTH = BUILDER
            .comment("Scales the strength of wind-driven foliage sway.")
            .defineInRange("windFoliageSwayStrength", 1.0D, 0.0D, 2.0D);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
