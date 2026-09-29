package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.config.ClientConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

final class FoliageHeightVariation {
    private static final int HEIGHT_STEPS = 16;

    private FoliageHeightVariation() {
    }

    @Nullable
    static Range range(BlockState state, FoliageSwayProfile profile) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        boolean grass = (profile.type() == ResponsiveFoliageType.GRASS
                || profile.type() == ResponsiveFoliageType.TALL_FOLIAGE)
                && blockId.getPath().contains("grass");
        if (grass && ClientConfig.ENABLE_GRASS_HEIGHT_VARIATION.getAsBoolean()) {
            return new Range(
                    (float) ClientConfig.GRASS_HEIGHT_MIN.getAsDouble(),
                    (float) ClientConfig.GRASS_HEIGHT_MAX.getAsDouble()
            );
        }

        boolean vanillaFlower = blockId.getNamespace().equals("minecraft")
                && (profile.type() == ResponsiveFoliageType.FLOWER
                || profile.type() == ResponsiveFoliageType.TALL_FOLIAGE)
                && (state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.TALL_FLOWERS));
        if (vanillaFlower && ClientConfig.ENABLE_FLOWER_HEIGHT_VARIATION.getAsBoolean()) {
            return new Range(
                    (float) ClientConfig.FLOWER_HEIGHT_MIN.getAsDouble(),
                    (float) ClientConfig.FLOWER_HEIGHT_MAX.getAsDouble()
            );
        }

        return null;
    }

    static float sample(BlockPos root, ResourceLocation blockId, Range range) {
        long hash = root.asLong() + (long) blockId.hashCode() * 0x9E3779B97F4A7C15L;
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        float fraction = (hash >>> 40) * (1.0F / 16777216.0F);
        float step = Math.round(fraction * HEIGHT_STEPS) / (float) HEIGHT_STEPS;
        return Mth.lerp(step, range.minimum(), range.maximum());
    }

    record Range(float minimum, float maximum) {
        Range {
            float low = Math.min(minimum, maximum);
            maximum = Math.max(minimum, maximum);
            minimum = low;
        }

        boolean isOriginalHeight() {
            return minimum == 1.0F && maximum == 1.0F;
        }
    }
}
