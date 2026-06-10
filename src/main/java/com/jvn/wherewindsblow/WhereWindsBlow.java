package com.jvn.wherewindsblow;

import com.jvn.wherewindsblow.block.ModBlocks;
import com.mojang.logging.LogUtils;
import com.jvn.wherewindsblow.config.CommonConfig;
import com.jvn.wherewindsblow.worldgen.ModBiomeModifiers;
import com.jvn.wherewindsblow.worldgen.ModFeatures;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(WhereWindsBlow.MOD_ID)
public class WhereWindsBlow {
    public static final String MOD_ID = "where_winds_blow";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WhereWindsBlow(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        ModBlocks.register(modEventBus);
        ModFeatures.register(modEventBus);
        ModBiomeModifiers.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Where Winds Blow is ready.");
    }
}
