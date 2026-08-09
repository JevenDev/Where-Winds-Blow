package com.jvn.wherewindsblow.worldgen;

import com.jvn.wherewindsblow.config.CommonConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;
import net.neoforged.neoforge.common.ModConfigSpec;

public enum GrassWorldgenDensity {
    DENSE_SHORT_GRASS("dense_short_grass", CommonConfig.DENSE_GRASS_DENSITY_MULTIPLIER),
    MEDIUM_SHORT_GRASS("medium_short_grass", CommonConfig.MEDIUM_GRASS_DENSITY_MULTIPLIER),
    SPARSE_SHORT_GRASS("sparse_short_grass", CommonConfig.SPARSE_GRASS_DENSITY_MULTIPLIER),
    TALL_GRASS("tall_grass", CommonConfig.TALL_GRASS_PATCH_MULTIPLIER),
    FERN_ACCENTS("fern_accents", CommonConfig.FERN_ACCENT_PATCH_MULTIPLIER),
    SHORT_DRY_GRASS("short_dry_grass", CommonConfig.SHORT_DRY_GRASS_PATCH_MULTIPLIER),
    TALL_DRY_GRASS("tall_dry_grass", CommonConfig.TALL_DRY_GRASS_PATCH_MULTIPLIER),
    DEAD_GRASS("dead_grass", CommonConfig.DEAD_GRASS_PATCH_MULTIPLIER),
    OVERGROWN_GRASS("overgrown_grass", CommonConfig.OVERGROWN_GRASS_PATCH_MULTIPLIER),
    WILD_WHEAT("wild_wheat", CommonConfig.WILD_WHEAT_PATCH_MULTIPLIER);

    public static final Codec<GrassWorldgenDensity> CODEC = Codec.STRING.comapFlatMap(GrassWorldgenDensity::decode, GrassWorldgenDensity::id);

    private final String id;
    private final Supplier<ModConfigSpec.DoubleValue> multiplier;

    GrassWorldgenDensity(String id, ModConfigSpec.DoubleValue multiplier) {
        this.id = id;
        this.multiplier = () -> multiplier;
    }

    public String id() {
        return id;
    }

    public int configuredPasses() {
        if (!CommonConfig.ENABLE_DENSE_GRASS_WORLDGEN.getAsBoolean()) {
            return 0;
        }

        double value = multiplier.get().getAsDouble();
        if (value <= 0.0D) {
            return 0;
        }

        return Math.clamp(Math.round(value), 1, 8);
    }

    private static DataResult<GrassWorldgenDensity> decode(String value) {
        return Arrays.stream(values())
                .filter(density -> density.id.equals(value.toLowerCase(Locale.ROOT)))
                .findFirst()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "Unknown Where Winds Blow grass density: " + value));
    }
}
