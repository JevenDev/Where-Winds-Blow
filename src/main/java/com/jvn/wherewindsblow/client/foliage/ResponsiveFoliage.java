package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.GlobalWindState;
import com.jvn.wherewindsblow.client.wind.WindExposureCache;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class ResponsiveFoliage {
    private static final int MAX_MODELED_COLUMN_HEIGHT = 32;
    private static final String CONTINUITY_MODEL_PACKAGE = "me.pepperbell.continuity.";
    private static boolean continuityWrappingWarningLogged;
    private static boolean snowRealMagicWrappingWarningLogged;

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
                && sameProfile(level, hangsFromTop ? root.above() : root.below(), profile)) {
            root = hangsFromTop ? root.above() : root.below();
            distanceToRoot++;
        }

        int height = 0;
        while (height < MAX_MODELED_COLUMN_HEIGHT
                && sameProfile(level, hangsFromTop ? root.below(height) : root.above(height), profile)) {
            height++;
        }

        int offset = hangsFromTop ? root.getY() - pos.getY() : pos.getY() - root.getY();
        return new FoliageModelData.ColumnSegment(root, offset, height, hangsFromTop, profile.heightScale());
    }

    private static boolean sameProfile(BlockAndTintGetter level, BlockPos pos, FoliageSwayProfile profile) {
        FoliageSwayProfile other = FoliageSwayProfiles.resolve(SnowRealMagicCompat.effectiveState(level, pos));
        return other != null && other.id().equals(profile.id());
    }

    static LeafSnowSupport leafSnowSupport(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState renderedState
    ) {
        if (!(renderedState.getBlock() instanceof SnowLayerBlock)) {
            return null;
        }

        BlockState actualState = level.getBlockState(pos);
        BlockState containedState = SnowRealMagicCompat.effectiveState(level, pos);
        if (containedState != actualState) {
            FoliageSwayProfile containedProfile = FoliageSwayProfiles.resolve(containedState);
            if (containedProfile != null && containedProfile.type().isLeaves()) {
                return new LeafSnowSupport(pos, containedProfile);
            }
        }

        BlockPos supportPos = pos.below();
        FoliageSwayProfile supportProfile = FoliageSwayProfiles.resolve(
                SnowRealMagicCompat.effectiveState(level, supportPos)
        );
        return supportProfile != null && supportProfile.type().isLeaves()
                ? new LeafSnowSupport(supportPos, supportProfile)
                : null;
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
            // Profiles and server block tags can change after model baking
            return wrapModel(model);
        });
    }

    public static BakedModel wrapSnowRealMagicVariant(BakedModel model) {
        return model instanceof ResponsiveFoliageModel ? model : wrapModel(model);
    }

    private static BakedModel wrapModel(BakedModel model) {
        if (SnowRealMagicCompat.isModel(model)) {
            return wrapSnowRealMagicModel(model);
        }

        if (!isContinuityModel(model)) {
            return new ResponsiveFoliageModel(model);
        }

        // Continuity emits connected and emissive quads from its own model wrappers.
        BakedModel continuityModel = model;
        try {
            while (true) {
                Field wrappedModelField = findWrappedModelField(continuityModel.getClass());
                if (wrappedModelField == null) {
                    warnContinuityWrappingFailure(null);
                    return model;
                }

                wrappedModelField.setAccessible(true);
                Object wrapped = wrappedModelField.get(continuityModel);
                if (!(wrapped instanceof BakedModel wrappedModel)) {
                    warnContinuityWrappingFailure(null);
                    return model;
                }
                if (isContinuityModel(wrappedModel)) {
                    continuityModel = wrappedModel;
                    continue;
                }
                if (!(wrappedModel instanceof ResponsiveFoliageModel)) {
                    wrappedModelField.set(continuityModel, new ResponsiveFoliageModel(wrappedModel));
                }
                return model;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnContinuityWrappingFailure(exception);
            return model;
        }
    }

    private static BakedModel wrapSnowRealMagicModel(BakedModel model) {
        boolean foundWrappedModel = false;
        try {
            for (Class<?> type = model.getClass(); type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())
                            || !BakedModel.class.isAssignableFrom(field.getType())) {
                        continue;
                    }

                    field.setAccessible(true);
                    Object value = field.get(model);
                    if (!(value instanceof BakedModel wrappedModel)) {
                        continue;
                    }
                    foundWrappedModel = true;
                    if (!(wrappedModel instanceof ResponsiveFoliageModel)) {
                        field.set(model, wrapModel(wrappedModel));
                    }
                }
            }

            if (!foundWrappedModel) {
                warnSnowRealMagicWrappingFailure(null);
            }
            return model;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnSnowRealMagicWrappingFailure(exception);
            return model;
        }
    }

    private static Field findWrappedModelField(Class<?> modelClass) {
        for (Class<?> type = modelClass; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("wrapped");
                if (BakedModel.class.isAssignableFrom(field.getType())) {
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
                // Continue through Continuity's forwarding-model hierarchy.
            }
        }
        return null;
    }

    private static boolean isContinuityModel(Object model) {
        for (Class<?> type = model.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().startsWith(CONTINUITY_MODEL_PACKAGE)) {
                return true;
            }
        }
        return false;
    }

    private static void warnContinuityWrappingFailure(Exception exception) {
        if (continuityWrappingWarningLogged) {
            return;
        }
        continuityWrappingWarningLogged = true;
        String message = "Could not compose responsive foliage with Continuity's model wrapper; preserving Continuity rendering.";
        if (exception == null) {
            WhereWindsBlow.LOGGER.warn(message);
        } else {
            WhereWindsBlow.LOGGER.warn(message, exception);
        }
    }

    private static void warnSnowRealMagicWrappingFailure(Exception exception) {
        if (snowRealMagicWrappingWarningLogged) {
            return;
        }
        snowRealMagicWrappingWarningLogged = true;
        String message = "Could not compose responsive foliage with Snow Real Magic's model wrapper; "
                + "preserving Snow Real Magic rendering.";
        if (exception == null) {
            WhereWindsBlow.LOGGER.warn(message);
        } else {
            WhereWindsBlow.LOGGER.warn(message, exception);
        }
    }

    record LeafSnowSupport(BlockPos pos, FoliageSwayProfile profile) {
        LeafSnowSupport {
            pos = pos.immutable();
        }
    }
}
