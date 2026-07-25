package com.jvn.wherewindsblow.client;

import com.jvn.wherewindsblow.block.ModBlocks;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliage;
import com.jvn.wherewindsblow.client.banner.BannerWindStateCache;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliagePhysics;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import com.jvn.wherewindsblow.client.lantern.LanternSway;
import com.jvn.wherewindsblow.client.lantern.SwingingLanternAssemblyRenderer;
import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.WindDebugOverlay;
import com.jvn.wherewindsblow.client.wind.WindStreakRenderer;
import com.jvn.wherewindsblow.client.wind.WindVisualShaders;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.jvn.wherewindsblow.wind.BiomeWindProfileReloadListener;
import com.jvn.wherewindsblow.wind.BiomeWindProfiles;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.GrassColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
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
        modEventBus.addListener(WhereWindsBlowClient::registerReloadListeners);
        modEventBus.addListener(WhereWindsBlowClient::onClientConfigReload);
        NeoForge.EVENT_BUS.addListener(ResponsiveFoliagePhysics::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(ResponsiveFoliagePhysics::onClientTick);
        NeoForge.EVENT_BUS.addListener(DynamicWindManager::onClientTick);
        NeoForge.EVENT_BUS.addListener(BannerWindStateCache::onClientTick);
        NeoForge.EVENT_BUS.addListener(WhereWindsBlowClient::onClientPauseChange);
        NeoForge.EVENT_BUS.addListener(WhereWindsBlowClient::onClientLogin);
        NeoForge.EVENT_BUS.addListener(SwingingLanternAssemblyRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(WindStreakRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(WindStreakRenderer::onClientTick);
        NeoForge.EVENT_BUS.addListener(WindDebugOverlay::onRenderGui);
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
        WindVisualShaders.register(event);
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new BiomeWindProfileReloadListener(BiomeWindProfiles.Source.CLIENT, null));
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> DynamicWindManager.reset());
    }

    private static void onClientConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) {
            DynamicWindManager.reloadConfiguration();
        }
    }

    private static void onClientPauseChange(ClientPauseChangeEvent.Post event) {
        DynamicWindManager.onClientPauseChange(event);
        ResponsiveFoliagePhysics.onClientPauseChange(event);
    }

    private static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!ResponsiveFoliageShaders.isIrisLoaded() || ClientConfig.IRIS_WARNING_SHOWN.getAsBoolean()) {
            return;
        }

        ClientConfig.IRIS_WARNING_SHOWN.set(true);
        ClientConfig.IRIS_WARNING_SHOWN.save();

        Component warning = Component.translatable("where_winds_blow.warning.iris.label")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal(" "))
                .append(Component.translatable("where_winds_blow.warning.iris.body")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD, ChatFormatting.ITALIC));
        event.getPlayer().displayClientMessage(warning, false);
    }
}
