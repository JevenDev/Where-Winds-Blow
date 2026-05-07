package com.jvn.wherewindsblow;

import com.mojang.logging.LogUtils;
import com.jvn.wherewindsblow.client.WhereWindsBlowClient;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.jvn.wherewindsblow.config.CommonConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(WhereWindsBlow.MOD_ID)
public class WhereWindsBlow {
    public static final String MOD_ID = "where_winds_blow";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WhereWindsBlow(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            WhereWindsBlowClient.register(modEventBus);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Where Winds Blow is ready to stir the grass.");
    }
}
