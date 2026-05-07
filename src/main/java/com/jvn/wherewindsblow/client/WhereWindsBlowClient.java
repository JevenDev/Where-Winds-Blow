package com.jvn.wherewindsblow.client;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.config.ClientConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public final class WhereWindsBlowClient {
    private WhereWindsBlowClient() {
    }

    public static void register(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, screen) -> new WhereWindsBlowConfigScreen(screen));
        modEventBus.addListener(WhereWindsBlowClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        if (ClientConfig.ENABLE_GRASS_WIND.getAsBoolean()) {
            WhereWindsBlow.LOGGER.info("Grass wind config is enabled; renderer hook is intentionally deferred for compatibility.");
        }
    }
}
