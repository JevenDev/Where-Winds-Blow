package com.jvn.wherewindsblow.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeGenerationSettingsBuilder;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

public record ConfigurableGrassBiomeModifier(
        HolderSet<Biome> biomes,
        HolderSet<PlacedFeature> features,
        Decoration step,
        GrassWorldgenDensity density
) implements BiomeModifier {
    public static final MapCodec<ConfigurableGrassBiomeModifier> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(ConfigurableGrassBiomeModifier::biomes),
            PlacedFeature.LIST_CODEC.fieldOf("features").forGetter(ConfigurableGrassBiomeModifier::features),
            Decoration.CODEC.fieldOf("step").forGetter(ConfigurableGrassBiomeModifier::step),
            GrassWorldgenDensity.CODEC.fieldOf("density").forGetter(ConfigurableGrassBiomeModifier::density)
    ).apply(builder, ConfigurableGrassBiomeModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD || !biomes.contains(biome)) {
            return;
        }

        int passes = density.configuredPasses();
        if (passes <= 0) {
            return;
        }

        BiomeGenerationSettingsBuilder generationSettings = builder.getGenerationSettings();
        List<Holder<PlacedFeature>> availableFeatures = features.stream().toList();
        int featureCount = Math.min(passes, availableFeatures.size());
        for (int index = 0; index < featureCount; index++) {
            generationSettings.addFeature(step, availableFeatures.get(index));
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return ModBiomeModifiers.CONFIGURABLE_GRASS.get();
    }
}
