package com.jvn.wherewindsblow.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public record FlowerPatchConfiguration(
        HolderSet<Biome> biomes,
        BlockStateProvider toPlace
) implements FeatureConfiguration {
    public static final Codec<FlowerPatchConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(FlowerPatchConfiguration::biomes),
            BlockStateProvider.CODEC.fieldOf("to_place").forGetter(FlowerPatchConfiguration::toPlace)
    ).apply(instance, FlowerPatchConfiguration::new));
}
