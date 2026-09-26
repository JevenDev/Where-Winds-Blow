package com.jvn.wherewindsblow.worldgen;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.block.ModBlocks;
import com.jvn.wherewindsblow.block.OvergrownGrassBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

final class GrassPatchPlacement {
    private static final float FLAT_GRASS_CHANCE = 0.28F;
    private static final TagKey<Biome> OVERGROWN_PATCH_BIOMES = TagKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(WhereWindsBlow.MOD_ID, "has_overgrown_grass")
    );
    private static final TagKey<Biome> OVERGROWN_FIELD_BIOMES = TagKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(WhereWindsBlow.MOD_ID, "has_overgrown_grass_fields")
    );

    private GrassPatchPlacement() {
    }

    static boolean placeGrass(WorldGenLevel level, RandomSource random, BlockPos anchor, int height) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ());
        BlockPos bottom = new BlockPos(anchor.getX(), y, anchor.getZ());
        if (level.isOutsideBuildHeight(bottom) || !level.ensureCanWrite(bottom)) {
            return false;
        }

        if (height > 2) {
            Holder<Biome> biome = level.getBiome(bottom);
            if (!biome.is(OVERGROWN_PATCH_BIOMES) && !biome.is(OVERGROWN_FIELD_BIOMES)) {
                height = 2;
            }
        }

        BlockState previous = level.getBlockState(bottom);
        int existingHeight;
        if (previous.isAir()) {
            existingHeight = 0;
        } else if (previous.is(Blocks.SHORT_GRASS) || previous.is(ModBlocks.FLAT_GRASS.get())) {
            existingHeight = 1;
        } else if (previous.is(Blocks.TALL_GRASS)
                && previous.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER
                && level.getBlockState(bottom.above()).is(Blocks.TALL_GRASS)
                && level.getBlockState(bottom.above()).getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
            existingHeight = 2;
        } else if (previous.is(ModBlocks.OVERGROWN_GRASS.get())) {
            existingHeight = 1;
            while (existingHeight < OvergrownGrassBlock.MAX_HEIGHT
                    && level.getBlockState(bottom.above(existingHeight)).is(ModBlocks.OVERGROWN_GRASS.get())) {
                existingHeight++;
            }
        } else {
            return false;
        }

        if (height <= existingHeight || !level.getFluidState(bottom).isEmpty()
                || !Blocks.SHORT_GRASS.defaultBlockState().canSurvive(level, bottom)) {
            return false;
        }

        int availableHeight = 1;
        while (availableHeight < height) {
            BlockPos next = bottom.above(availableHeight);
            if (level.isOutsideBuildHeight(next) || !level.ensureCanWrite(next)
                    || !level.getFluidState(next).isEmpty()) {
                break;
            }
            BlockState state = level.getBlockState(next);
            if (!state.isAir() && availableHeight >= existingHeight) {
                break;
            }
            availableHeight++;
        }

        if (availableHeight <= existingHeight) {
            return false;
        }
        if (availableHeight >= 3) {
            return ModBlocks.OVERGROWN_GRASS.get().placeColumn(level, bottom, availableHeight);
        }
        if (availableHeight == 2) {
            BlockState lower = Blocks.TALL_GRASS.defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
            BlockState upper = lower.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
            if (!level.setBlock(bottom, lower, 2)) {
                return false;
            }
            if (!level.setBlock(bottom.above(), upper, 2)) {
                level.setBlock(bottom, previous, 2);
                return false;
            }
            return true;
        }

        BlockState grass = random.nextFloat() < FLAT_GRASS_CHANCE
                ? ModBlocks.FLAT_GRASS.get().defaultBlockState()
                : Blocks.SHORT_GRASS.defaultBlockState();
        return level.setBlock(bottom, grass, 2);
    }
}
