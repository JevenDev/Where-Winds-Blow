package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.config.ClientConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.jetbrains.annotations.Nullable;

public final class FlowerDensityOverrides {
    @Nullable
    private static volatile FlowerDensityOverrideStore store;
    private static boolean useHandled;

    private FlowerDensityOverrides() {
    }

    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft minecraft = Minecraft.getInstance();
        store = null;
        useHandled = false;
        String worldIdentity;
        if (minecraft.getSingleplayerServer() != null) {
            Path world = minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            worldIdentity = "singleplayer:" + world;
        } else if (minecraft.getCurrentServer() != null) {
            worldIdentity = "server:" + minecraft.getCurrentServer().ip.trim().toLowerCase(Locale.ROOT);
        } else {
            worldIdentity = "connection:" + event.getConnection().getRemoteAddress();
        }
        Path directory = FMLPaths.CONFIGDIR.get().resolve(WhereWindsBlow.MOD_ID).resolve("flower_density");
        Path file = FlowerDensityOverrideStore.fileForWorld(directory, worldIdentity);
        try {
            store = new FlowerDensityOverrideStore(file);
        } catch (IOException exception) {
            WhereWindsBlow.LOGGER.error("Could not load flower density overrides from {}", file, exception);
            event.getPlayer().displayClientMessage(Component.translatable("where_winds_blow.flowerDensity.loadFailed"), false);
        }
    }

    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        store = null;
        useHandled = false;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.keyUse.isDown()) {
            useHandled = false;
        }
    }

    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        FlowerDensityOverrideStore activeStore = store;
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND
                || !ClientConfig.ENABLE_DENSE_FLOWERS.getAsBoolean() || activeStore == null
                || minecraft.screen != null || minecraft.level == null || minecraft.player == null
                || !minecraft.options.keyShift.isDown() || !minecraft.player.getMainHandItem().isEmpty()
                || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = minecraft.level.getBlockState(pos);
        FoliageSwayProfile profile = FoliageSwayProfiles.resolve(state);
        if (profile == null || profile.type() != ResponsiveFoliageType.FLOWER || !profile.denseWhenAdjacent()
                || minecraft.level.getBlockState(pos.below()).is(state.getBlock())) {
            return;
        }
        if (!isSingleFlower(pos, state) && !hasFlowerNeighbor(minecraft, pos)) {
            return;
        }

        // cancel before vanilla sends a use-item packet
        event.setCanceled(true);
        event.setSwingHand(false);
        if (useHandled) {
            return;
        }
        useHandled = true;
        try {
            boolean single = activeStore.toggle(minecraft.level.dimension().location(), pos,
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            minecraft.levelRenderer.setBlocksDirty(pos.getX() - 1, pos.getY(), pos.getZ() - 1,
                    pos.getX() + 1, pos.getY(), pos.getZ() + 1);
            minecraft.player.displayClientMessage(Component.translatable(single
                    ? "where_winds_blow.flowerDensity.single" : "where_winds_blow.flowerDensity.cluster"), true);
        } catch (IOException exception) {
            WhereWindsBlow.LOGGER.error("Could not save the flower density override at {}", pos, exception);
            minecraft.player.displayClientMessage(Component.translatable("where_winds_blow.flowerDensity.saveFailed"), true);
        }
    }

    static boolean isSingleFlower(BlockPos pos, BlockState state) {
        FlowerDensityOverrideStore activeStore = store;
        Minecraft minecraft = Minecraft.getInstance();
        return activeStore != null && minecraft.level != null
                && activeStore.isSingle(minecraft.level.dimension().location(), pos,
                BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private static boolean hasFlowerNeighbor(Minecraft minecraft, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(direction);
            BlockState state = minecraft.level.getBlockState(adjacent);
            FoliageSwayProfile profile = FoliageSwayProfiles.resolve(state);
            if (profile != null && profile.type() == ResponsiveFoliageType.FLOWER && profile.denseWhenAdjacent()
                    && !minecraft.level.getBlockState(adjacent.below()).is(state.getBlock())) {
                return true;
            }
        }
        return false;
    }
}
