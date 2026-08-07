package com.jvn.wherewindsblow;

import com.jvn.toucanlib.neoforge.event.ToucanEventBuses;
import com.jvn.toucanlib.util.ToucanIds;
import com.jvn.wherewindsblow.block.ModBlocks;
import com.mojang.logging.LogUtils;
import com.jvn.wherewindsblow.config.CommonConfig;
import com.jvn.wherewindsblow.worldgen.ModBiomeModifiers;
import com.jvn.wherewindsblow.worldgen.ModFeatures;
import com.jvn.wherewindsblow.wind.BiomeWindProfileReloadListener;
import com.jvn.wherewindsblow.wind.BiomeWindProfiles;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

@Mod(WhereWindsBlow.MOD_ID)
public class WhereWindsBlow {
    public static final String MOD_ID = "where_winds_blow";
    public static final ToucanIds IDS = ToucanIds.create(MOD_ID);
    public static final Logger LOGGER = LogUtils.getLogger();

    public WhereWindsBlow(IEventBus modEventBus, ModContainer modContainer) {
        ToucanEventBuses.on(modEventBus).listener(this::commonSetup);
        ToucanEventBuses.game().listener(this::addServerReloadListeners);

        ModBlocks.register(modEventBus);
        ModFeatures.register(modEventBus);
        ModBiomeModifiers.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Where Winds Blow is ready.");
    }

    private void addServerReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new BiomeWindProfileReloadListener(BiomeWindProfiles.Source.SERVER, event.getRegistryAccess()));
    }
}
