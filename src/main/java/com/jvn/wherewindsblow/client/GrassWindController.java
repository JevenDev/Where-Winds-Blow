package com.jvn.wherewindsblow.client;

import com.jvn.wherewindsblow.config.ClientConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class GrassWindController {
    private GrassWindController() {
    }

    public static boolean isEnabled() {
        return ClientConfig.ENABLE_GRASS_WIND.getAsBoolean();
    }

    public static boolean affects(BlockState state) {
        if (!isEnabled()) {
            return false;
        }

        if (ClientConfig.AFFECT_SHORT_GRASS.getAsBoolean() && state.is(Blocks.SHORT_GRASS)) {
            return true;
        }

        if (ClientConfig.AFFECT_TALL_GRASS.getAsBoolean() && state.is(Blocks.TALL_GRASS)) {
            return true;
        }

        if (ClientConfig.AFFECT_FERNS.getAsBoolean() && (state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN))) {
            return true;
        }

        return ClientConfig.AFFECT_FLOWERS.getAsBoolean()
                && (state.is(Blocks.DANDELION)
                || state.is(Blocks.POPPY)
                || state.is(Blocks.BLUE_ORCHID)
                || state.is(Blocks.ALLIUM)
                || state.is(Blocks.AZURE_BLUET)
                || state.is(Blocks.RED_TULIP)
                || state.is(Blocks.ORANGE_TULIP)
                || state.is(Blocks.WHITE_TULIP)
                || state.is(Blocks.PINK_TULIP)
                || state.is(Blocks.OXEYE_DAISY)
                || state.is(Blocks.CORNFLOWER)
                || state.is(Blocks.LILY_OF_THE_VALLEY)
                || state.is(Blocks.TORCHFLOWER));
    }

    public static float sampleHorizontalOffset(BlockPos pos, float gameTime) {
        float strength = ClientConfig.WIND_STRENGTH.get().floatValue();
        float speed = ClientConfig.WIND_SPEED.get().floatValue();
        float phase = gameTime * 0.08F * speed + pos.getX() * 0.37F + pos.getZ() * 0.21F;
        float gust = Mth.sin(phase) * 0.7F + Mth.sin(phase * 0.47F + pos.getY()) * 0.3F;
        return gust * strength;
    }
}
