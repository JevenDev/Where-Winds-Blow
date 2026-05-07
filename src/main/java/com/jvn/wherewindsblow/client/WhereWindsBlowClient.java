package com.jvn.wherewindsblow.client;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.config.ClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = WhereWindsBlow.MOD_ID, value = Dist.CLIENT)
public final class WhereWindsBlowClient {
    private WhereWindsBlowClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (ClientConfig.ENABLE_GRASS_WIND.getAsBoolean()) {
            WhereWindsBlow.LOGGER.info("Grass wind config is enabled; renderer hook is intentionally deferred for compatibility.");
        }
    }
}
