package com.jvn.wherewindsblow.worldgen;

import com.jvn.wherewindsblow.WhereWindsBlow;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModFeatures {
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(BuiltInRegistries.FEATURE, WhereWindsBlow.MOD_ID);

    public static final DeferredHolder<Feature<?>, GrassCoverFeature> GRASS_COVER = FEATURES.register(
            "grass_cover",
            () -> new GrassCoverFeature(GrassCoverConfiguration.CODEC)
    );

    public static final DeferredHolder<Feature<?>, OvergrownGrassPatchFeature> OVERGROWN_GRASS_PATCH = FEATURES.register(
            "overgrown_grass_patch",
            () -> new OvergrownGrassPatchFeature(NoneFeatureConfiguration.CODEC)
    );

    public static final DeferredHolder<Feature<?>, TallGrassPatchFeature> TALL_GRASS_PATCH = FEATURES.register(
            "tall_grass_patch",
            () -> new TallGrassPatchFeature(NoneFeatureConfiguration.CODEC)
    );

    public static final DeferredHolder<Feature<?>, WildWheatPatchFeature> WILD_WHEAT_PATCH = FEATURES.register(
            "wild_wheat_patch",
            () -> new WildWheatPatchFeature(NoneFeatureConfiguration.CODEC)
    );

    private ModFeatures() {
    }

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}
