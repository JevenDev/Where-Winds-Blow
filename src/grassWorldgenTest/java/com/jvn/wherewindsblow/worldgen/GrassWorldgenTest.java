package com.jvn.wherewindsblow.worldgen;

import com.jvn.wherewindsblow.block.FlatGrassBlock;
import com.jvn.wherewindsblow.block.ModBlocks;
import com.jvn.wherewindsblow.block.OvergrownGrassBlock;
import com.jvn.wherewindsblow.block.OvergrownGrassPart;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntBinaryOperator;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.registries.GameData;

public final class GrassWorldgenTest {
    private static int checks;

    public static void main(String[] args) {
        checksPatchTransitions();
        bootstrapBlocks();
        followsTerrainAndFillsPatches();
        preservesExistingVegetation();
        blendsOverlappingPatches();
        fitsAvailableHeadroom();
        respectsBuildAndWriteLimits();
        restoresFailedTallPlacement();
        restoresFailedOvergrownPlacement();
        System.out.println("Passed " + checks + " grass worldgen checks.");
    }

    private static void checksPatchTransitions() {
        for (int peak : new int[]{2, 4, 5, 6}) {
            for (int seed = 0; seed < 128; seed++) {
                RandomSource random = RandomSource.create(seed);
                double radius = (peak - 1) * 1.5D;
                GrassPatchShape shape = peak == 2
                        ? GrassPatchShape.create(random, 3.25D, 5.0D, 2.25D, 3.5D)
                        : GrassPatchShape.create(random, radius + 1.0D, radius + 2.5D, radius, radius + 1.25D);
                int originX = seed * 17 - 1024;
                int originZ = 511 - seed * 31;
                boolean[] seen = new boolean[peak + 1];
                int bounds = shape.bounds();
                for (int x = -bounds - 1; x <= bounds + 1; x++) {
                    for (int z = -bounds - 1; z <= bounds + 1; z++) {
                        int height = shape.grassHeight(x, z, originX + x, originZ + z, peak);
                        check(height >= 0 && height <= peak, "patch heights stay within their limits");
                        seen[height] = true;
                        int east = shape.grassHeight(x + 1, z, originX + x + 1, originZ + z, peak);
                        int south = shape.grassHeight(x, z + 1, originX + x, originZ + z + 1, peak);
                        check(Math.abs(height - east) <= 1 && Math.abs(height - south) <= 1,
                                "neighboring columns transition one block at a time");
                        if (Math.abs(x) > bounds || Math.abs(z) > bounds) {
                            check(height == 0, "patch bounds include the whole transition");
                        }
                        if (shape.distance(x, z, originX + x, originZ + z) <= 1.0D) {
                            check(height > 0, "patch interiors have no random density holes");
                        }
                    }
                }
                for (boolean present : seen) {
                    check(present, "patches contain every height between the center and edge");
                }
            }
        }
    }

    private static void bootstrapBlocks() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
        Registry.register(BuiltInRegistries.BLOCK, ModBlocks.FLAT_GRASS.getId(),
                new FlatGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS)));
        Registry.register(BuiltInRegistries.BLOCK, ModBlocks.OVERGROWN_GRASS.getId(),
                new OvergrownGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.TALL_GRASS)));
        for (BlockState state : ModBlocks.FLAT_GRASS.get().getStateDefinition().getPossibleStates()) {
            state.initCache();
        }
        for (BlockState state : ModBlocks.OVERGROWN_GRASS.get().getStateDefinition().getPossibleStates()) {
            state.initCache();
        }
        BuiltInRegistries.BLOCK.freeze();
    }

    private static void followsTerrainAndFillsPatches() {
        TestWorld sloped = new TestWorld((x, z) -> 80 + x - z);
        BlockPos origin = new BlockPos(-16, 140, 15);
        check(new TallGrassPatchFeature(NoneFeatureConfiguration.CODEC).place(context(sloped, origin, 42)),
                "tall patches place across slopes despite a distant origin height");
        check(sloped.blocks.size() > 30, "tall patches retain dense cover");
        sloped.checkPlants();

        TestWorld overgrown = new TestWorld((x, z) -> 64);
        check(new OvergrownGrassPatchFeature(NoneFeatureConfiguration.CODEC).place(context(overgrown, origin, 42)),
                "overgrown patches generate");
        check(overgrown.blocks.size() > 100, "overgrown patches retain dense cover");
        overgrown.checkPlants();
        Map<BlockPos, BlockState> before = Map.copyOf(overgrown.blocks);
        new TallGrassPatchFeature(NoneFeatureConfiguration.CODEC).place(context(overgrown, origin, 42));
        for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
            if (entry.getValue().is(ModBlocks.OVERGROWN_GRASS.get())) {
                check(overgrown.state(entry.getKey()).equals(entry.getValue()), "tall patches preserve overgrown columns");
            }
        }
        overgrown.checkPlants();

        TestWorld repeated = new TestWorld((x, z) -> 64);
        new OvergrownGrassPatchFeature(NoneFeatureConfiguration.CODEC).place(context(repeated, origin, 42));
        check(repeated.blocks.equals(before), "seeded patches are reproducible");
    }

    private static void preservesExistingVegetation() {
        for (BlockState plant : new BlockState[]{
                Blocks.DANDELION.defaultBlockState(), Blocks.FERN.defaultBlockState(),
                Blocks.LARGE_FERN.defaultBlockState(), Blocks.DEAD_BUSH.defaultBlockState(),
                Blocks.WHEAT.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                Blocks.OAK_LEAVES.defaultBlockState(), Blocks.STONE.defaultBlockState()
        }) {
            TestWorld world = new TestWorld((x, z) -> 64);
            world.blocks.put(world.bottom(), plant);
            for (int height = 1; height <= 6; height++) {
                check(!world.place(height), "grass does not replace other vegetation, fluids, or solids");
                check(world.state(world.bottom()).equals(plant), "existing block survives unchanged");
            }
        }

        TestWorld world = new TestWorld((x, z) -> 64);
        check(world.place(2), "creates a complete tall grass plant");
        Map<BlockPos, BlockState> tall = Map.copyOf(world.blocks);
        check(!world.place(1), "short grass does not downgrade tall grass");
        check(world.blocks.equals(tall), "both tall grass halves survive an overlap");
        check(world.place(4), "overgrown grass upgrades both tall grass halves");
        world.checkPlants();
        Map<BlockPos, BlockState> overgrown = Map.copyOf(world.blocks);
        check(!world.place(2), "tall grass does not downgrade overgrown grass");
        check(world.blocks.equals(overgrown), "all overgrown parts survive an overlap");
    }

    private static void blendsOverlappingPatches() {
        BlockPos first = new BlockPos(-2, 64, 1);
        BlockPos second = new BlockPos(2, 64, -1);
        OvergrownGrassPatchFeature feature = new OvergrownGrassPatchFeature(NoneFeatureConfiguration.CODEC);
        TestWorld forward = new TestWorld((x, z) -> 64);
        feature.place(context(forward, first, 11));
        feature.place(context(forward, second, 29));
        TestWorld reverse = new TestWorld((x, z) -> 64);
        feature.place(context(reverse, second, 29));
        feature.place(context(reverse, first, 11));
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                int height = forward.columnHeight(x, z);
                check(height == reverse.columnHeight(x, z), "overlapping heights are independent of placement order");
                check(Math.abs(height - forward.columnHeight(x + 1, z)) <= 1
                                && Math.abs(height - forward.columnHeight(x, z + 1)) <= 1,
                        "overlapping overgrown patches keep gradual transitions");
            }
        }
        forward.checkPlants();
        reverse.checkPlants();
    }

    private static void fitsAvailableHeadroom() {
        for (int available = 1; available <= 5; available++) {
            TestWorld world = new TestWorld((x, z) -> 64);
            BlockPos obstruction = world.bottom().above(available);
            world.blocks.put(obstruction, Blocks.OAK_LEAVES.defaultBlockState());
            check(world.place(6), "blocked columns fall back to shorter grass");
            check(world.state(obstruction).is(Blocks.OAK_LEAVES), "headroom checks preserve tree leaves");
            check(world.blocks.size() == available + 1, "fallback uses exactly the available headroom");
            world.checkPlants();
        }
    }

    private static void respectsBuildAndWriteLimits() {
        for (int available = 1; available <= 6; available++) {
            int ground = 320 - available;
            TestWorld world = new TestWorld((x, z) -> ground);
            check(world.place(6), "grass fits below the build ceiling");
            check(world.blocks.size() == available, "ceiling limits every grass column");
            world.checkPlants();
        }
        TestWorld outside = new TestWorld((x, z) -> 320);
        check(!outside.place(6) && outside.blocks.isEmpty(), "no grass outside build height");

        TestWorld unwritable = new TestWorld((x, z) -> 64);
        unwritable.writableHeight = 64;
        check(!unwritable.place(6) && unwritable.blocks.isEmpty(), "unwritable ground stays untouched");

        TestWorld limited = new TestWorld((x, z) -> 64);
        limited.writableHeight = 66;
        check(limited.place(6), "generation height restrictions still allow shorter cover");
        check(limited.blocks.size() == 2, "all written parts stay in the generation region");
        limited.checkPlants();
    }

    private static void restoresFailedTallPlacement() {
        TestWorld world = new TestWorld((x, z) -> 64);
        world.blocks.put(world.bottom(), Blocks.SHORT_GRASS.defaultBlockState());
        world.failedWrite = world.bottom().above();
        check(!world.place(2), "failed upper-half writes report failure");
        check(world.state(world.bottom()).is(Blocks.SHORT_GRASS), "failed tall placement restores the original cover");
        check(world.blocks.size() == 1, "failed placement leaves no orphaned parts");
    }

    private static void restoresFailedOvergrownPlacement() {
        for (int existingHeight : new int[]{0, 1, 2, 3}) {
            TestWorld world = new TestWorld((x, z) -> 64);
            if (existingHeight > 0) {
                check(world.place(existingHeight), "creates existing cover before failed growth");
            }
            Map<BlockPos, BlockState> before = Map.copyOf(world.blocks);
            world.failedWrite = world.bottom().above(4);
            check(!world.place(6), "failed overgrown writes report failure");
            check(world.blocks.equals(before), "failed overgrown placement restores all existing vegetation");
            world.checkPlants();
        }
    }

    private static FeaturePlaceContext<NoneFeatureConfiguration> context(TestWorld world, BlockPos origin, long seed) {
        return new FeaturePlaceContext<>(Optional.empty(), world.level, null,
                RandomSource.create(seed), origin, NoneFeatureConfiguration.INSTANCE);
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
        checks++;
    }

    private static final class TestWorld {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final IntBinaryOperator surface;
        private final WorldGenLevel level;
        private int writableHeight = 320;
        private BlockPos failedWrite;

        private TestWorld(IntBinaryOperator surface) {
            this.surface = surface;
            level = (WorldGenLevel) Proxy.newProxyInstance(
                    WorldGenLevel.class.getClassLoader(),
                    new Class<?>[]{WorldGenLevel.class},
                    (proxy, method, arguments) -> {
                        return switch (method.getName()) {
                            case "getHeight" -> {
                                if (arguments == null || arguments.length == 0) {
                                    yield 384;
                                }
                                check(arguments[0] == Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                        "surface height ignores existing grass and leaf canopies");
                                yield surface.applyAsInt((int) arguments[1], (int) arguments[2]);
                            }
                            case "getMinBuildHeight" -> -64;
                            case "getBlockState" -> state((BlockPos) arguments[0]);
                            case "getFluidState" -> state((BlockPos) arguments[0]).getFluidState();
                            case "ensureCanWrite" -> ((BlockPos) arguments[0]).getY() < writableHeight;
                            case "setBlock" -> {
                                BlockPos pos = ((BlockPos) arguments[0]).immutable();
                                check(pos.getY() >= -64 && pos.getY() < 320 && pos.getY() < writableHeight,
                                        "writes stay within build and generation limits");
                                if (pos.equals(failedWrite)) {
                                    yield false;
                                }
                                BlockState state = (BlockState) arguments[1];
                                if (state.isAir()) {
                                    blocks.remove(pos);
                                } else {
                                    blocks.put(pos, state);
                                }
                                yield true;
                            }
                            default -> InvocationHandler.invokeDefault(proxy, method, arguments);
                        };
                    }
            );
        }

        private BlockPos bottom() {
            return new BlockPos(0, surface.applyAsInt(0, 0), 0);
        }

        private BlockState state(BlockPos pos) {
            BlockState state = blocks.get(pos);
            if (state != null) {
                return state;
            }
            return pos.getY() < surface.applyAsInt(pos.getX(), pos.getZ())
                    ? Blocks.FARMLAND.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }

        private boolean place(int height) {
            return GrassPatchPlacement.placeGrass(level, RandomSource.create(1), bottom(), height);
        }

        private int columnHeight(int x, int z) {
            BlockPos bottom = new BlockPos(x, surface.applyAsInt(x, z), z);
            int height = 0;
            while (!state(bottom.above(height)).isAir()) {
                height++;
            }
            return height;
        }

        private void checkPlants() {
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState state = entry.getValue();
                if (state.is(Blocks.TALL_GRASS)) {
                    boolean lower = state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER;
                    BlockState partner = state(lower ? pos.above() : pos.below());
                    check(partner.is(Blocks.TALL_GRASS)
                                    && partner.getValue(DoublePlantBlock.HALF) != state.getValue(DoublePlantBlock.HALF),
                            "tall grass halves stay paired");
                } else if (state.is(ModBlocks.OVERGROWN_GRASS.get())) {
                    boolean above = state(pos.above()).is(ModBlocks.OVERGROWN_GRASS.get());
                    boolean below = state(pos.below()).is(ModBlocks.OVERGROWN_GRASS.get());
                    OvergrownGrassPart expected = !below ? OvergrownGrassPart.LOWER
                            : above ? OvergrownGrassPart.MIDDLE : OvergrownGrassPart.UPPER;
                    check(state.getValue(OvergrownGrassBlock.PART) == expected, "overgrown column parts stay consistent");
                }
                if (!state.is(Blocks.OAK_LEAVES)) {
                    check(state.canSurvive(level, pos), "generated plants have valid support");
                }
            }
        }
    }
}
