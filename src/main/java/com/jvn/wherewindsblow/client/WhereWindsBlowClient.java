package com.jvn.wherewindsblow.client;

import com.jvn.wherewindsblow.block.ModBlocks;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliage;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliagePhysics;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import com.jvn.wherewindsblow.client.lantern.LanternSway;
import com.jvn.wherewindsblow.client.wind.WindStreakRenderer;
import com.jvn.wherewindsblow.config.ClientConfig;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.GrassColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public final class WhereWindsBlowClient {
    private WhereWindsBlowClient() {
    }

    public static void register(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, screen) -> WhereWindsBlowConfigScreen.create(screen));
        modEventBus.addListener(WhereWindsBlowClient::registerBlockColors);
        modEventBus.addListener(WhereWindsBlowClient::registerItemColors);
        modEventBus.addListener(WhereWindsBlowClient::modifyBakedModels);
        modEventBus.addListener(WhereWindsBlowClient::registerShaders);
        NeoForge.EVENT_BUS.addListener(ResponsiveFoliagePhysics::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(ResponsiveFoliagePhysics::onClientTick);
        NeoForge.EVENT_BUS.addListener(WindStreakRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(WindStreakRenderer::onClientTick);
    }

    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> level != null && pos != null
                        ? BiomeColors.getAverageGrassColor(level, pos)
                        : GrassColor.get(0.5D, 1.0D),
                ModBlocks.OVERGROWN_GRASS.get(),
                ModBlocks.FLAT_GRASS.get(),
                ModBlocks.FLAT_DEAD_GRASS.get(),
                ModBlocks.SHORT_DEAD_GRASS.get()
        );
    }

    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> GrassColor.get(0.5D, 1.0D),
                ModBlocks.OVERGROWN_GRASS_ITEM.get(),
                ModBlocks.FLAT_GRASS_ITEM.get(),
                ModBlocks.FLAT_DEAD_GRASS_ITEM.get(),
                ModBlocks.SHORT_DEAD_GRASS_ITEM.get()
        );
    }

    private static void modifyBakedModels(ModelEvent.ModifyBakingResult event) {
        ResponsiveFoliage.wrapModels(event);
        LanternSway.wrapModels(event);
    }

    private static void registerShaders(RegisterShadersEvent event) {
        ResponsiveFoliageShaders.register(event);
    }
}
