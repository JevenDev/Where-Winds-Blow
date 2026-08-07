package com.jvn.wherewindsblow.client;

import com.jvn.toucanlib.neoforge.config.ToucanConfigScreens;
import com.jvn.toucanlib.neoforge.event.ToucanEventBuses;
import com.jvn.wherewindsblow.block.ModBlocks;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliage;
import com.jvn.wherewindsblow.client.banner.BannerWindStateCache;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliagePhysics;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliageShaders;
import com.jvn.wherewindsblow.client.foliage.FoliageSwayProfileReloadListener;
import com.jvn.wherewindsblow.client.foliage.FoliageSwayProfiles;
import com.jvn.wherewindsblow.client.lantern.LanternSway;
import com.jvn.wherewindsblow.client.lantern.SwingingLanternAssemblyRenderer;
import com.jvn.wherewindsblow.client.weather.BlizzardWeatherEffects;
import com.jvn.wherewindsblow.client.weather.DesertStormShaders;
import com.jvn.wherewindsblow.client.weather.SnowfallShaders;
import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.WindDebugOverlay;
import com.jvn.wherewindsblow.client.wind.WindStreakRenderer;
import com.jvn.wherewindsblow.client.wind.TumbleweedRenderer;
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
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

public final class WhereWindsBlowClient {
    private WhereWindsBlowClient() {
    }

    public static void register(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        ToucanConfigScreens.register(modContainer, WhereWindsBlowConfigScreen::create);
        ToucanEventBuses.on(modEventBus)
                .listener(WhereWindsBlowClient::registerBlockColors)
                .listener(WhereWindsBlowClient::registerItemColors)
                .listener(WhereWindsBlowClient::modifyBakedModels)
                .listener(WhereWindsBlowClient::registerShaders)
                .listener(WhereWindsBlowClient::registerReloadListeners)
                .listener(WhereWindsBlowClient::onClientConfigReload);
        ToucanEventBuses.game()
                .listener(ResponsiveFoliagePhysics::onRenderLevelStage)
                .listener(ResponsiveFoliagePhysics::onClientTick)
                .listener(DynamicWindManager::onClientTick)
                .listener(BannerWindStateCache::onClientTick)
                .listener(WhereWindsBlowClient::onClientPauseChange)
                .listener(WhereWindsBlowClient::onClientLogin)
                .listener(WhereWindsBlowClient::onTagsUpdated)
                .listener(SwingingLanternAssemblyRenderer::onRenderLevelStage)
                .listener(WindStreakRenderer::onRenderLevelStage)
                .listener(WindStreakRenderer::onClientTick)
                .listener(TumbleweedRenderer::onRenderLevelStage)
                .listener(TumbleweedRenderer::onClientTick)
                .listener(WindDebugOverlay::onRenderGui)
                .listener(BlizzardWeatherEffects::onRenderFog)
                .listener(BlizzardWeatherEffects::onComputeFogColor)
                .listener(BlizzardWeatherEffects::onRenderLevelStage);
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
        SnowfallShaders.register(event);
        DesertStormShaders.register(event);
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new FoliageSwayProfileReloadListener());
        event.registerReloadListener(new BiomeWindProfileReloadListener(BiomeWindProfiles.Source.CLIENT, null));
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> DynamicWindManager.reset());
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        FoliageSwayProfiles.clearCache();
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
