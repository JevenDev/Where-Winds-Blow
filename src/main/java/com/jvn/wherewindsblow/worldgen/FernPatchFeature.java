package com.jvn.wherewindsblow.worldgen;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.block.ModBlocks;
import com.mojang.serialization.Codec;
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
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class FernPatchFeature extends Feature<NoneFeatureConfiguration> {
    private static final TagKey<Biome> FERN_BIOMES = TagKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath(WhereWindsBlow.MOD_ID, "has_fern_accents"));
    private static final TagKey<Biome> LARGE_FERN_BIOMES = TagKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath(WhereWindsBlow.MOD_ID, "has_large_ferns"));

    public FernPatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        GrassPatchShape shape = GrassPatchShape.create(random, 2.5D, 4.0D, 2.25D, 3.25D);
        boolean placedAny = false;
        int bounds = shape.bounds();

        for (int offsetX = -bounds; offsetX <= bounds; offsetX++) {
            for (int offsetZ = -bounds; offsetZ <= bounds; offsetZ++) {
                int worldX = origin.getX() + offsetX;
                int worldZ = origin.getZ() + offsetZ;
                int height = shape.grassHeight(offsetX, offsetZ, worldX, worldZ, 2);
                if (height > 0) {
                    placedAny |= placeFern(level, origin.offset(offsetX, 0, offsetZ), height);
                }
            }
        }

        return placedAny;
    }

    static boolean placeFern(WorldGenLevel level, BlockPos anchor, int height) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ());
        BlockPos bottom = new BlockPos(anchor.getX(), y, anchor.getZ());
        if (level.isOutsideBuildHeight(bottom) || !level.ensureCanWrite(bottom)
                || !level.getFluidState(bottom).isEmpty()) {
            return false;
        }

        Holder<Biome> biome = level.getBiome(bottom);
        if (!biome.is(FERN_BIOMES)) {
            return false;
        }
        boolean large = height > 1 && biome.is(LARGE_FERN_BIOMES);
        BlockState previous = level.getBlockState(bottom);
        BlockState above = level.getBlockState(bottom.above());
        boolean tallGrass = previous.is(Blocks.TALL_GRASS)
                && previous.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER
                && above.is(Blocks.TALL_GRASS)
                && above.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
        if (!previous.isAir() && !previous.is(Blocks.SHORT_GRASS) && !previous.is(ModBlocks.FLAT_GRASS.get())
                && !previous.is(Blocks.FERN) && !(large && tallGrass)) {
            return false;
        }
        if (!Blocks.FERN.defaultBlockState().canSurvive(level, bottom)) {
            return false;
        }

        if (large && !level.isOutsideBuildHeight(bottom.above()) && level.ensureCanWrite(bottom.above())
                && level.getFluidState(bottom.above()).isEmpty() && (above.isAir() || tallGrass)) {
            BlockState lower = Blocks.LARGE_FERN.defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
            if (!level.setBlock(bottom, lower, 2)) {
                return false;
            }
            if (!level.setBlock(bottom.above(), lower.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), 2)) {
                level.setBlock(bottom, previous, 2);
                return false;
            }
            return true;
        }
        if (tallGrass || previous.is(Blocks.FERN)) {
            return false;
        }
        return level.setBlock(bottom, Blocks.FERN.defaultBlockState(), 2);
    }
}
