package com.jvn.wherewindsblow;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(WhereWindsBlow.MOD_ID)
public class WhereWindsBlow {
    public static final String MOD_ID = "where_winds_blow";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WhereWindsBlow(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Where Winds Blow is ready to stir the grass.");
    }
}
