package com.jvn.wherewindsblow.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public record GrassCoverConfiguration(
        GrassWorldgenDensity density,
        float coveragePerPass,
        BlockStateProvider toPlace
) implements FeatureConfiguration {
    public static final Codec<GrassCoverConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GrassWorldgenDensity.CODEC.fieldOf("density").forGetter(GrassCoverConfiguration::density),
            Codec.floatRange(0.0F, 0.25F).fieldOf("coverage_per_pass").forGetter(GrassCoverConfiguration::coveragePerPass),
            BlockStateProvider.CODEC.fieldOf("to_place").forGetter(GrassCoverConfiguration::toPlace)
    ).apply(instance, GrassCoverConfiguration::new));
}
