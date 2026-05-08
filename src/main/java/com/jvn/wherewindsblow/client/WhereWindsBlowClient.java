package com.jvn.wherewindsblow.client;

import com.jvn.wherewindsblow.block.ModBlocks;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.GrassColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
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
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModBlocks.OVERGROWN_GRASS.get(), RenderType.cutoutMipped()));
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
