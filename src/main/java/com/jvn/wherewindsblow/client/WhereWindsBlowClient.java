package com.jvn.wherewindsblow.client;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.block.ModBlocks;
import com.jvn.wherewindsblow.config.ClientConfig;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.GrassColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public final class WhereWindsBlowClient {
    private WhereWindsBlowClient() {
    }

    public static void register(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, screen) -> new WhereWindsBlowConfigScreen(screen));
        modEventBus.addListener(WhereWindsBlowClient::onClientSetup);
        modEventBus.addListener(WhereWindsBlowClient::registerBlockColors);
        modEventBus.addListener(WhereWindsBlowClient::registerItemColors);
        modEventBus.addListener(GrassWindShaders::wrapPlantModels);
        NeoForge.EVENT_BUS.addListener(GrassWindShaders::updateUniforms);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModBlocks.OVERGROWN_GRASS.get(), RenderType.cutout()));

        if (ClientConfig.ENABLE_GRASS_WIND.getAsBoolean()) {
            WhereWindsBlow.LOGGER.info("Grass wind shader is enabled for marked vanilla plant models.");
        }
    }

    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> level != null && pos != null
                        ? BiomeColors.getAverageGrassColor(level, pos)
                        : GrassColor.get(0.5D, 1.0D),
                ModBlocks.OVERGROWN_GRASS.get()
        );
    }

    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> GrassColor.get(0.5D, 1.0D), ModBlocks.OVERGROWN_GRASS_ITEM.get());
    }
}
