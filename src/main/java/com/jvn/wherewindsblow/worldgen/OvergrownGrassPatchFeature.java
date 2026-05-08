package com.jvn.wherewindsblow.worldgen;

import com.jvn.wherewindsblow.block.ModBlocks;
import com.jvn.wherewindsblow.block.OvergrownGrassBlock;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class OvergrownGrassPatchFeature extends Feature<NoneFeatureConfiguration> {
    private static final int TRIES = 48;
    private static final int XZ_SPREAD = 7;
    private static final int Y_SPREAD = 2;
    private static final int EDGE_SCAN_UP = 2;
    private static final int EDGE_SCAN_DOWN = 3;

    public OvergrownGrassPatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        OvergrownGrassBlock block = ModBlocks.OVERGROWN_GRASS.get();
        int placedColumns = 0;
        Set<BlockPos> placedBottoms = new HashSet<>();

        for (int attempt = 0; attempt < TRIES; attempt++) {
            BlockPos candidate = origin.offset(
                    random.nextInt(XZ_SPREAD + 1) - random.nextInt(XZ_SPREAD + 1),
                    random.nextInt(Y_SPREAD + 1) - random.nextInt(Y_SPREAD + 1),
                    random.nextInt(XZ_SPREAD + 1) - random.nextInt(XZ_SPREAD + 1)
            );

            int maxHeight = maxPlaceableHeight(level, candidate, block);
            if (maxHeight < OvergrownGrassBlock.MIN_WORLDGEN_HEIGHT) {
                continue;
            }

            int height = OvergrownGrassBlock.MIN_WORLDGEN_HEIGHT
                    + random.nextInt(Math.min(OvergrownGrassBlock.MAX_HEIGHT, maxHeight) - OvergrownGrassBlock.MIN_WORLDGEN_HEIGHT + 1);
            if (block.placeColumn(level, candidate, height)) {
                placedColumns++;
                placedBottoms.add(candidate.immutable());
            }
        }

        if (placedColumns > 0) {
            sprinkleEdgeTallGrass(level, random, placedBottoms);
        }

        return placedColumns > 0;
    }

    private int maxPlaceableHeight(WorldGenLevel level, BlockPos bottomPos, OvergrownGrassBlock block) {
        int maxHeight = 0;
        while (maxHeight < OvergrownGrassBlock.MAX_HEIGHT && block.canPlaceColumn(level, bottomPos, maxHeight + 1)) {
            maxHeight++;
        }

        return maxHeight;
    }

    private void sprinkleEdgeTallGrass(WorldGenLevel level, RandomSource random, Set<BlockPos> placedBottoms) {
        Set<Long> occupiedColumns = new HashSet<>();
        Set<Long> processedEdges = new HashSet<>();

        for (BlockPos placedBottom : placedBottoms) {
            occupiedColumns.add(columnKey(placedBottom.getX(), placedBottom.getZ()));
        }

        for (BlockPos placedBottom : placedBottoms) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    if (offsetX == 0 && offsetZ == 0) {
                        continue;
                    }

                    int edgeX = placedBottom.getX() + offsetX;
                    int edgeZ = placedBottom.getZ() + offsetZ;
                    long edgeKey = columnKey(edgeX, edgeZ);
                    if (occupiedColumns.contains(edgeKey) || !processedEdges.add(edgeKey)) {
                        continue;
                    }

                    int neighboringColumns = countNeighboringColumns(occupiedColumns, edgeX, edgeZ);
                    if (neighboringColumns == 0 || neighboringColumns >= 5) {
                        continue;
                    }

                    float chance = neighboringColumns >= 3 ? 0.45F : 0.25F;
                    if (random.nextFloat() <= chance) {
                        tryPlaceTallGrass(level, placedBottom, offsetX, offsetZ);
                    }
                }
            }
        }
    }

    private int countNeighboringColumns(Set<Long> occupiedColumns, int x, int z) {
        int count = 0;
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                if (offsetX == 0 && offsetZ == 0) {
                    continue;
                }

                if (occupiedColumns.contains(columnKey(x + offsetX, z + offsetZ))) {
                    count++;
                }
            }
        }

        return count;
    }

    private boolean tryPlaceTallGrass(WorldGenLevel level, BlockPos sourceBottom, int offsetX, int offsetZ) {
        BlockState lowerTallGrass = Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upperTallGrass = Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);

        for (int offsetY = EDGE_SCAN_UP; offsetY >= -EDGE_SCAN_DOWN; offsetY--) {
            BlockPos candidate = sourceBottom.offset(offsetX, offsetY, offsetZ);
            if (!canPlaceTallGrass(level, candidate, lowerTallGrass)) {
                continue;
            }

            level.setBlock(candidate, lowerTallGrass, 3);
            level.setBlock(candidate.above(), upperTallGrass, 3);
            return true;
        }

        return false;
    }

    private boolean canPlaceTallGrass(WorldGenLevel level, BlockPos pos, BlockState lowerTallGrass) {
        BlockState lowerState = level.getBlockState(pos);
        BlockState upperState = level.getBlockState(pos.above());
        return level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.above()).isEmpty()
                && !lowerState.is(ModBlocks.OVERGROWN_GRASS.get())
                && !upperState.is(ModBlocks.OVERGROWN_GRASS.get())
                && (lowerState.isAir() || lowerState.canBeReplaced())
                && (upperState.isAir() || upperState.canBeReplaced())
                && lowerTallGrass.canSurvive(level, pos);
    }

    private long columnKey(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }
}