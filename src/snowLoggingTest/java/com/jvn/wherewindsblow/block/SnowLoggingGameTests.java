package com.jvn.wherewindsblow.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@GameTestHolder("where_winds_blow")
@PrefixGameTestTemplate(false)
public final class SnowLoggingGameTests {
    @GameTest(template = "empty")
    public static void preservesPlantsAndSnow(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        for (Block plant : new Block[]{Blocks.SHORT_GRASS, Blocks.DANDELION, Blocks.FERN, ModBlocks.FLAT_GRASS.get(),
                ModBlocks.SNOWY_FLAT_GRASS.get(), ModBlocks.SNOWY_SHORT_GRASS.get(), ModBlocks.SNOWY_FERN.get()}) {
            level.setBlockAndUpdate(pos, plant.defaultBlockState());
            level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3));
            BlockState state = level.getBlockState(pos);
            helper.assertTrue(state.is(plant) && SnowLogging.layers(state) == 3, "snow preserves the foliage");
            helper.assertTrue(level.getBlockState(pos.below()).getValue(SnowyDirtBlock.SNOWY), "soil remains snowy under foliage");
            helper.assertTrue(!state.getCollisionShape(level, pos).isEmpty(), "logged snow retains its collision");
            helper.assertTrue(state.isRandomlyTicking(), "logged snow receives melting ticks");
            var saved = BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
            helper.assertTrue(BlockState.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow().equals(state), "snow layers survive serialization");
            level.destroyBlock(pos, false);
            helper.assertTrue(level.getBlockState(pos).is(Blocks.SNOW)
                    && level.getBlockState(pos).getValue(SnowLayerBlock.LAYERS) == 3, "breaking foliage preserves its snow");
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        helper.assertTrue(!Blocks.WHEAT.defaultBlockState().hasProperty(SnowLogging.LAYERS), "crops retain their state schema");
        helper.assertTrue(!Blocks.SEAGRASS.defaultBlockState().hasProperty(SnowLogging.LAYERS), "aquatic plants retain their state schema");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snowItemsAndShovels(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.DANDELION.defaultBlockState());
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        BlockPlaceContext context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, new ItemStack(Items.SNOW), hit);
        helper.assertTrue(context.getClickedPos().equals(pos), "snow items target the flower's block");
        BlockState placed = Blocks.SNOW.getStateForPlacement(context);
        helper.assertTrue(placed.is(Blocks.DANDELION) && SnowLogging.layers(placed) == 1, "snow items preserve flowers");
        level.setBlockAndUpdate(pos, placed);
        for (int layers = 2; layers <= 8; layers++) {
            context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, new ItemStack(Items.SNOW), hit);
            level.setBlockAndUpdate(pos, Blocks.SNOW.getStateForPlacement(context));
            helper.assertTrue(SnowLogging.layers(level.getBlockState(pos)) == layers, "snow stacks to eight layers");
        }
        context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, new ItemStack(Items.SNOW), hit);
        helper.assertTrue(!context.getClickedPos().equals(pos), "full snow redirects further placement above");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
        var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        SnowLogging.onShovel(event);
        helper.assertTrue(event.isCanceled(), "shoveling consumes the interaction");
        helper.assertTrue(level.getBlockState(pos).is(Blocks.DANDELION) && SnowLogging.layers(level.getBlockState(pos)) == 0,
                "shovels clear snow without breaking the flower");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void keepsDoublePlantsPaired(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        for (Block plant : new Block[]{Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.ROSE_BUSH,
                ModBlocks.SNOWY_TALL_GRASS.get(), ModBlocks.SNOWY_LARGE_FERN.get()}) {
            DoublePlantBlock.placeAt(level, plant.defaultBlockState(), pos, 3);
            level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2));
            helper.assertTrue(level.getBlockState(pos).is(plant) && level.getBlockState(pos.above()).is(plant), "snow preserves both plant halves");
            helper.assertTrue(!SnowLogging.canContainSnow(level.getBlockState(pos.above())), "upper halves cannot contain ground snow");
            level.destroyBlock(pos.above(), false);
            helper.assertTrue(level.getBlockState(pos).is(Blocks.SNOW), "breaking the upper half preserves ground snow");
            helper.assertTrue(level.getBlockState(pos.above()).isAir(), "breaking leaves no orphaned plant half");
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void preservesSnowThroughGrassGrowth(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pos, ModBlocks.SNOWY_FERN.get().defaultBlockState().setValue(SnowLogging.LAYERS, 3));
        ModBlocks.SNOWY_FERN.get().performBonemeal(level, level.random, pos, level.getBlockState(pos));
        helper.assertTrue(level.getBlockState(pos).is(ModBlocks.SNOWY_LARGE_FERN.get())
                && SnowLogging.layers(level.getBlockState(pos)) == 3, "bonemeal preserves snow while growing large ferns");
        helper.assertTrue(SnowLogging.layers(level.getBlockState(pos.above())) == 0, "bonemeal keeps snow at ground level");
        SnowLogging.restorePlacement(level, pos.above(), Blocks.AIR.defaultBlockState(), 2);
        SnowLogging.restorePlacement(level, pos, Blocks.AIR.defaultBlockState(), 2);
        var grass = ModBlocks.OVERGROWN_GRASS.get();
        helper.assertTrue(grass.placeColumn(level, pos, 3), "overgrown grass places a complete column");
        level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(SnowLogging.LAYERS, 4));
        helper.assertTrue(grass.placeColumn(level, pos, 4), "overgrown columns can grow taller");
        helper.assertTrue(SnowLogging.layers(level.getBlockState(pos)) == 4, "column growth and refresh preserve ground snow");
        for (int i = 1; i < 4; i++) {
            helper.assertTrue(SnowLogging.layers(level.getBlockState(pos.above(i))) == 0, "overgrown snow remains on the root");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void placesPatchesThroughSnow(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        var snowyBiome = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.SNOWY_PLAINS);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                BlockPos pos = helper.absolutePos(new BlockPos(x, 1, z));
                level.getChunkAt(pos).fillBiomesFromNoise((bx, by, bz, sampler) -> snowyBiome, level.getChunkSource().randomState().sampler());
                level.setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 6));
                for (int y = 1; y <= 10; y++) {
                    level.setBlockAndUpdate(pos.above(y), Blocks.AIR.defaultBlockState());
                }
            }
        }
        var feature = new com.jvn.wherewindsblow.worldgen.SnowyFoliagePatchFeature(
                net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC);
        helper.assertTrue(feature.place(new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(java.util.Optional.empty(),
                level, null, net.minecraft.util.RandomSource.create(42), origin,
                net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE)),
                "snowy patches grow through existing snow cover");
        int plants = 0;
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                BlockPos pos = helper.absolutePos(new BlockPos(x, 1, z));
                BlockState state = level.getBlockState(pos);
                if (state.hasProperty(SnowLogging.LAYERS)) {
                    plants++;
                    helper.assertTrue(SnowLogging.layers(state) == 6, "patches preserve all existing snow layers");
                    if (state.getBlock() instanceof DoublePlantBlock) {
                        helper.assertTrue(level.getBlockState(pos.above()).is(state.getBlock())
                                && SnowLogging.layers(level.getBlockState(pos.above())) == 0, "snowy tall patches retain paired dry upper halves");
                    }
                }
            }
        }
        helper.assertTrue(plants > 0 && plants < 16, "snowy patches blend plants with uncovered snow");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snowfallAndMelting(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var snowyBiome = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.SNOWY_PLAINS);
        level.getChunkAt(pos).fillBiomesFromNoise((x, y, z, sampler) -> snowyBiome, level.getChunkSource().randomState().sampler());
        level.setWeatherParameters(0, 6000, true, false);
        level.setRainLevel(1.0F);
        level.getGameRules().getRule(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT).set(3, level.getServer());
        level.setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.FERN.defaultBlockState());
        for (int y = 1; y <= 10; y++) {
            level.setBlockAndUpdate(pos.above(y), Blocks.AIR.defaultBlockState());
        }
        helper.assertTrue(snowyBiome.value().shouldSnow(level, pos), "snow generation accepts foliage");
        for (int i = 0; i < 6; i++) {
            level.tickPrecipitation(pos);
        }
        helper.assertTrue(level.getBlockState(pos).is(Blocks.FERN) && SnowLogging.layers(level.getBlockState(pos)) == 3,
                "snowfall accumulates to the gamerule limit: " + level.getBlockState(pos)
                        + " at " + level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos)
                        + " rain=" + level.isRaining() + " loaded=" + level.isAreaLoaded(pos, 1));
        level.setBlockAndUpdate(pos.relative(Direction.EAST), Blocks.GLOWSTONE.defaultBlockState());
        helper.runAfterDelay(5, () -> {
            level.getBlockState(pos).randomTick(level, pos, level.random);
            helper.assertTrue(level.getBlockState(pos).is(Blocks.FERN) && SnowLogging.layers(level.getBlockState(pos)) == 0,
                    "block light melts snow while preserving foliage");
            helper.succeed();
        });
    }
}
