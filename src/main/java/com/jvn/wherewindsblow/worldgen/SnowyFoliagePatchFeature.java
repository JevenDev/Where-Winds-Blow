package com.jvn.wherewindsblow.worldgen;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.block.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class SnowyFoliagePatchFeature extends Feature<NoneFeatureConfiguration> {
    private static final TagKey<Biome> BIOMES = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(WhereWindsBlow.MOD_ID, "has_snowy_foliage"));

    public SnowyFoliagePatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        RandomSource random = context.random();
        GrassPatchShape shape = GrassPatchShape.create(random, 3.0D, 5.0D, 2.5D, 4.0D);
        boolean ferns = random.nextFloat() < 0.3F;
        boolean placedAny = false;
        for (int x = -shape.bounds(); x <= shape.bounds(); x++) {
            for (int z = -shape.bounds(); z <= shape.bounds(); z++) {
                BlockPos anchor = context.origin().offset(x, 0, z);
                int height = shape.grassHeight(x, z, anchor.getX(), anchor.getZ(), 2);
                if (height > 0 && random.nextFloat() < 0.65F) {
                    Block shortPlant = ferns ? ModBlocks.SNOWY_FERN.get()
                            : random.nextFloat() < 0.35F ? ModBlocks.SNOWY_FLAT_GRASS.get() : ModBlocks.SNOWY_SHORT_GRASS.get();
                    Block tallPlant = ferns ? ModBlocks.SNOWY_LARGE_FERN.get() : ModBlocks.SNOWY_TALL_GRASS.get();
                    placedAny |= placePlant(context.level(), anchor, shortPlant, tallPlant, height > 1);
                }
            }
        }
        return placedAny;
    }

    static boolean placePlant(WorldGenLevel level, BlockPos anchor, Block shortPlant, Block tallPlant, boolean tall) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ());
        BlockPos bottom = new BlockPos(anchor.getX(), y, anchor.getZ());
        if (level.isOutsideBuildHeight(bottom) || !level.ensureCanWrite(bottom)
                || !level.getBiome(bottom).is(BIOMES) || !level.getFluidState(bottom).isEmpty()) {
            return false;
        }
        BlockState previous = level.getBlockState(bottom);
        if (!previous.isAir() && !previous.is(Blocks.SHORT_GRASS) && !previous.is(Blocks.FERN)) {
            return false;
        }
        BlockState lower = shortPlant.defaultBlockState();
        if (!lower.canSurvive(level, bottom)) {
            return false;
        }
        BlockPos top = bottom.above();
        if (tall && !level.isOutsideBuildHeight(top) && level.ensureCanWrite(top) && level.getBlockState(top).isAir()) {
            lower = tallPlant.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
            if (!level.setBlock(bottom, lower, 2)) {
                return false;
            }
            if (!level.setBlock(top, lower.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), 2)) {
                level.setBlock(bottom, previous, 2);
                return false;
            }
            return true;
        }
        return level.setBlock(bottom, lower, 2);
    }
}
