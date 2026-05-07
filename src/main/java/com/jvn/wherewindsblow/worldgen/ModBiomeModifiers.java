package com.jvn.wherewindsblow.worldgen;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModBiomeModifiers {
    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, WhereWindsBlow.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<ConfigurableGrassBiomeModifier>> CONFIGURABLE_GRASS =
            BIOME_MODIFIER_SERIALIZERS.register("configurable_grass", () -> ConfigurableGrassBiomeModifier.CODEC);

    private ModBiomeModifiers() {
    }

    public static void register(IEventBus modEventBus) {
        BIOME_MODIFIER_SERIALIZERS.register(modEventBus);
    }
}
