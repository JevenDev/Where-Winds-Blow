package com.jvn.wherewindsblow.worldgen;

import com.jvn.wherewindsblow.block.ModBlocks;
import com.jvn.wherewindsblow.block.WildWheatBlock;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class WildWheatPatchFeature extends Feature<NoneFeatureConfiguration> {
    private static final int MIN_FIELD_RADIUS = 9;
    private static final int MAX_FIELD_RADIUS = 14;
    private static final int MIN_BLOB_COUNT = 3;
    private static final int MAX_BLOB_COUNT = 5;
    private static final int MAX_FIELD_PLANTS = 520;
    private static final int SURFACE_SCAN_UP = 2;
    private static final int SURFACE_SCAN_DOWN = 4;

    public WildWheatPatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        WildWheatBlock block = ModBlocks.WILD_WHEAT.get();
        int fieldRadius = random.nextInt(MIN_FIELD_RADIUS, MAX_FIELD_RADIUS + 1);
        FieldBlob[] blobs = createBlobs(random, fieldRadius);
        int maxExtent = maxExtent(blobs);
        Set<BlockPos> placed = new HashSet<>();

        for (int offsetX = -maxExtent; offsetX <= maxExtent; offsetX++) {
            for (int offsetZ = -maxExtent; offsetZ <= maxExtent; offsetZ++) {
                float density = densityAt(blobs, offsetX, offsetZ);
                if (density <= 0.0F || random.nextFloat() > density) {
                    continue;
                }

                BlockPos candidate = findSurface(level, origin, offsetX, offsetZ);
                if (candidate == null || !placed.add(candidate.immutable())) {
                    continue;
                }

                int age = sampleAge(random);
                BlockState wildWheat = block.getStateForAge(age);
                if (!canPlace(level, candidate, wildWheat)) {
                    placed.remove(candidate);
                    continue;
                }

                level.setBlock(candidate, wildWheat, 2);
                if (placed.size() >= MAX_FIELD_PLANTS) {
                    return true;
                }
            }
        }

        return !placed.isEmpty();
    }

    private BlockPos findSurface(WorldGenLevel level, BlockPos origin, int offsetX, int offsetZ) {
        BlockPos.MutableBlockPos cursor = origin.mutable().move(offsetX, SURFACE_SCAN_UP, offsetZ);

        for (int scan = SURFACE_SCAN_UP; scan >= -SURFACE_SCAN_DOWN; scan--) {
            if (canPlaceAtCurrentHeight(level, cursor)) {
                return cursor.immutable();
            }

            cursor.move(0, -1, 0);
        }

        return null;
    }

    private boolean canPlaceAtCurrentHeight(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isReplaceablePlant(state) || !level.getFluidState(pos).isEmpty()) {
            return false;
        }

        return ModBlocks.WILD_WHEAT.get().defaultBlockState().canSurvive(level, pos);
    }

    private boolean canPlace(WorldGenLevel level, BlockPos pos, BlockState wildWheat) {
        return isReplaceablePlant(level.getBlockState(pos))
                && level.getFluidState(pos).isEmpty()
                && wildWheat.canSurvive(level, pos);
    }

    private boolean isReplaceablePlant(BlockState state) {
        return state.isAir()
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.FERN)
                || state.is(ModBlocks.WILD_WHEAT.get());
    }

    private FieldBlob[] createBlobs(RandomSource random, int fieldRadius) {
        int blobCount = random.nextInt(MIN_BLOB_COUNT, MAX_BLOB_COUNT + 1);
        FieldBlob[] blobs = new FieldBlob[blobCount];
        blobs[0] = new FieldBlob(0, 0, fieldRadius);

        int centerOffset = Math.max(4, fieldRadius - 3);
        int minBlobRadius = Math.max(5, fieldRadius - 5);
        for (int index = 1; index < blobCount; index++) {
            int centerX = random.nextInt(centerOffset + 1) - random.nextInt(centerOffset + 1);
            int centerZ = random.nextInt(centerOffset + 1) - random.nextInt(centerOffset + 1);
            int radius = random.nextInt(minBlobRadius, fieldRadius + 1);
            blobs[index] = new FieldBlob(centerX, centerZ, radius);
        }

        return blobs;
    }

    private int maxExtent(FieldBlob[] blobs) {
        int extent = 0;
        for (FieldBlob blob : blobs) {
            extent = Math.max(extent, Math.max(Math.abs(blob.centerX), Math.abs(blob.centerZ)) + blob.radius + 1);
        }

        return extent;
    }

    private float densityAt(FieldBlob[] blobs, int offsetX, int offsetZ) {
        float totalInfluence = 0.0F;
        float strongestInfluence = 0.0F;

        for (FieldBlob blob : blobs) {
            int deltaX = offsetX - blob.centerX;
            int deltaZ = offsetZ - blob.centerZ;
            float normalizedDistance = (deltaX * deltaX + deltaZ * deltaZ) / (float) (blob.radius * blob.radius);
            if (normalizedDistance >= 1.18F) {
                continue;
            }

            float influence = 1.18F - normalizedDistance;
            totalInfluence += influence;
            strongestInfluence = Math.max(strongestInfluence, influence);
        }

        if (strongestInfluence <= 0.0F) {
            return 0.0F;
        }

        if (strongestInfluence >= 0.9F && totalInfluence >= 1.45F) {
            return 1.0F;
        }

        return Math.min(0.97F, 0.26F + strongestInfluence * 0.42F + totalInfluence * 0.18F);
    }

    private int sampleAge(RandomSource random) {
        float roll = random.nextFloat();
        if (roll < 0.10F) {
            return 0;
        }

        if (roll < 0.30F) {
            return 1;
        }

        if (roll < 0.65F) {
            return 2;
        }

        return 3;
    }

    private record FieldBlob(int centerX, int centerZ, int radius) {
    }
}