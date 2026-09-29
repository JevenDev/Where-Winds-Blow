package com.jvn.wherewindsblow.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

public final class GrassCoverFeature extends Feature<GrassCoverConfiguration> {
    private static final long COVER_NOISE_SALT = 0x6A09E667F3BCC909L;
    private static final long PATCH_NOISE_SALT = 0xBB67AE8584CAA73BL;
    private static final float MAX_COVERAGE = 0.88F;

    public GrassCoverFeature(Codec<GrassCoverConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<GrassCoverConfiguration> context) {
        GrassCoverConfiguration configuration = context.config();
        int passes = configuration.density().configuredPasses();
        if (passes <= 0) {
            return false;
        }

        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int minX = context.origin().getX() & ~15;
        int minZ = context.origin().getZ() & ~15;
        float coverage = Math.min(MAX_COVERAGE, configuration.coveragePerPass() * passes);
        long noiseSeed = level.getSeed() ^ COVER_NOISE_SALT;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placedAny = false;

        for (int offsetZ = 0; offsetZ < 16; offsetZ++) {
            int z = minZ + offsetZ;
            for (int offsetX = 0; offsetX < 16; offsetX++) {
                int x = minX + offsetX;
                float localCoverage = coverageAt(x, z, noiseSeed, coverage);
                if (random.nextFloat() >= localCoverage) {
                    continue;
                }

                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                pos.set(x, y, z);
                if (!configuration.biomes().contains(level.getBiome(pos))) {
                    continue;
                }
                if (!level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()) {
                    continue;
                }

                BlockState grass = configuration.toPlace().getState(random, pos);
                if (!grass.canSurvive(level, pos)) {
                    continue;
                }

                level.setBlock(pos, grass, 2);
                placedAny = true;
            }
        }

        return placedAny;
    }

    static float coverageAt(int x, int z, long noiseSeed, float coverage) {
        float patch = (float) GrassPatchShape.valueNoise(x, z, noiseSeed ^ PATCH_NOISE_SALT, 28);
        float detail = (float) GrassPatchShape.valueNoise(x, z, noiseSeed) * 0.04F;
        return Mth.clamp(coverage * (0.8F + patch * 0.5F) + detail, 0.0F, MAX_COVERAGE);
    }
}
