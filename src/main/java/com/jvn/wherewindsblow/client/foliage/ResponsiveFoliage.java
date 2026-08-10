package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.GlobalWindState;
import com.jvn.wherewindsblow.client.wind.WindExposureCache;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class ResponsiveFoliage {
    private static final int MAX_MODELED_COLUMN_HEIGHT = 32;

    private ResponsiveFoliage() {
    }

    public static boolean isInteractive(BlockState state) {
        FoliageSwayProfile profile = FoliageSwayProfiles.resolve(state);
        return profile != null && profile.interactive();
    }

    public static boolean isLeaf(BlockState state) {
        FoliageSwayProfile profile = FoliageSwayProfiles.resolve(state);
        return profile != null && profile.type().isLeaves();
    }

    static FoliageModelData.ColumnSegment columnSegment(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            FoliageSwayProfile profile
    ) {
        if (state.getBlock() instanceof DoublePlantBlock) {
            DoubleBlockHalf half = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    ? state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    : DoubleBlockHalf.LOWER;
            BlockPos root = half == DoubleBlockHalf.UPPER ? pos.below() : pos;
            return new FoliageModelData.ColumnSegment(
                    root,
                    half == DoubleBlockHalf.UPPER ? 1 : 0,
                    2,
                    false,
                    profile.heightScale()
            );
        }

        if (state.getBlock() instanceof PinkPetalsBlock) {
            // Vanilla petals are only three pixels tall. Normalize that height so
            // their tops reach the wind and interaction marker ranges.
            return new FoliageModelData.ColumnSegment(pos, 0, 1, false, profile.heightScale() * 16.0F / 3.0F);
        }

        if (profile.type().isColumn()) {
            return profileColumn(level, pos, profile);
        }

        return new FoliageModelData.ColumnSegment(pos, 0, 1, false, profile.heightScale());
    }

    private static FoliageModelData.ColumnSegment profileColumn(
            BlockAndTintGetter level,
            BlockPos pos,
            FoliageSwayProfile profile
    ) {
        boolean hangsFromTop = profile.type().hangsFromTop();
        BlockPos root = pos;
        int distanceToRoot = 0;
        while (distanceToRoot < MAX_MODELED_COLUMN_HEIGHT - 1
                && sameProfile(level.getBlockState(hangsFromTop ? root.above() : root.below()), profile)) {
            root = hangsFromTop ? root.above() : root.below();
            distanceToRoot++;
        }

        int height = 0;
        while (height < MAX_MODELED_COLUMN_HEIGHT
                && sameProfile(level.getBlockState(hangsFromTop ? root.below(height) : root.above(height)), profile)) {
            height++;
        }

        int offset = hangsFromTop ? root.getY() - pos.getY() : pos.getY() - root.getY();
        return new FoliageModelData.ColumnSegment(root, offset, height, hangsFromTop, profile.heightScale());
    }

    private static boolean sameProfile(BlockState state, FoliageSwayProfile profile) {
        FoliageSwayProfile other = FoliageSwayProfiles.resolve(state);
        return other != null && other.id().equals(profile.id());
    }

    public static boolean isWindExposed(BlockAndTintGetter level, BlockPos pos) {
        return windExposure(level, pos) >= 0.5F;
    }

    public static float windExposure(BlockAndTintGetter level, BlockPos pos) {
        GlobalWindState wind = DynamicWindManager.currentState();
        return WindExposureCache.exposureAt(level, pos, wind.directionX(), wind.directionZ());
    }

    public static void wrapModels(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) -> {
            if (location.variant().equals(ModelResourceLocation.INVENTORY_VARIANT)
                    || model instanceof ResponsiveFoliageModel
                    || BuiltInRegistries.BLOCK.getOptional(location.id()).isEmpty()) {
                return model;
            }
            // Profiles and server block tags can change after model baking. A lightweight
            // pass-through wrapper lets each block resolve the current profile at chunk build time.
            return new ResponsiveFoliageModel(model);
        });
    }
}
