package com.jvn.wherewindsblow.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.wherewindsblow.block.FlatGrassBlock;
import com.jvn.wherewindsblow.block.SnowyGrassBlock;
import com.jvn.wherewindsblow.block.SnowyDoublePlantBlock;
import com.jvn.wherewindsblow.block.ModBlocks;
import com.jvn.wherewindsblow.block.OvergrownGrassBlock;
import com.jvn.wherewindsblow.block.OvergrownGrassPart;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.registries.GameData;

public final class GrassWorldgenTest {
    private static int checks;

    public static void main(String[] args) throws IOException {
        coversGrassyWetlandsAndShores();
        checksWetlandBiomeTags();
        checksFernBiomeTags();
        checksFlowerBiomeTags();
        checksPatchTransitions();
        bootstrapBlocks();
        checksBroadGrassCoverage();
        checksPatchPlacementResources();
        followsTerrainAndFillsPatches();
        keepsOvergrownClumpsLow();
        placesForestFerns();
        placesFlowerAccents();
        placesSnowyPatches();
        preservesFernNeighbors();
        respectsFernPlacementLimits();
        preservesExistingVegetation();
        restrictsOvergrownGrassAtBiomeBorders();
        blendsOverlappingPatches();
        fitsAvailableHeadroom();
        respectsBuildAndWriteLimits();
        restoresFailedTallPlacement();
        restoresFailedOvergrownPlacement();
        System.out.println("Passed " + checks + " grass worldgen checks.");
    }

    private static void coversGrassyWetlandsAndShores() throws IOException {
        Set<String> coveredBiomes = new HashSet<>();
        for (String density : List.of("dense", "medium", "sparse")) {
            coveredBiomes.addAll(resolveBiomeTag("where_winds_blow:has_" + density + "_short_grass", true));
        }
        for (String biome : List.of(
                "plains", "forest", "jungle", "swamp", "mangrove_swamp",
                "river", "beach", "stony_shore", "ocean", "deep_ocean", "warm_ocean")) {
            check(coveredBiomes.contains("minecraft:" + biome),
                    "base grass cover reaches grassy terrain in " + biome);
        }
        for (String biome : List.of("desert", "badlands", "snowy_plains", "mushroom_fields", "the_void")) {
            check(!coveredBiomes.contains("minecraft:" + biome),
                    "base grass cover respects excluded habitats in " + biome);
        }
    }

    private static Set<String> resolveBiomeTag(String id, boolean required) throws IOException {
        ResourceLocation tag = ResourceLocation.parse(id);
        String path = "/data/" + tag.getNamespace() + "/tags/worldgen/biome/" + tag.getPath() + ".json";
        InputStream stream = GrassWorldgenTest.class.getResourceAsStream(path);
        if (stream == null) {
            check(!required, "required biome tag exists: " + id);
            return Set.of();
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Set<String> biomes = new HashSet<>();
            for (JsonElement element : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("values")) {
                JsonObject entry = element.isJsonObject() ? element.getAsJsonObject() : null;
                String value = entry == null ? element.getAsString() : entry.get("id").getAsString();
                boolean entryRequired = entry == null || !entry.has("required") || entry.get("required").getAsBoolean();
                if (value.startsWith("#")) {
                    biomes.addAll(resolveBiomeTag(value.substring(1), entryRequired));
                } else {
                    biomes.add(value);
                }
            }
            return biomes;
        }
    }

    private static void checksWetlandBiomeTags() throws IOException {
        check(biomeTagValues("has_overgrown_grass").equals(Set.of("#c:is_swamp", "#c:is_jungle")),
                "overgrown patches default to swamp and jungle biome families");
        check(biomeTagValues("has_overgrown_grass_fields").isEmpty(),
                "overgrown fields no longer generate in open biomes by default");
    }

    private static void checksBroadGrassCoverage() {
        float minimum = 1.0F;
        float maximum = 0.0F;
        long seed = 813749L;
        for (int x = -96; x <= 96; x += 4) {
            for (int z = -96; z <= 96; z += 4) {
                float coverage = GrassCoverFeature.coverageAt(x, z, seed, 0.8F);
                minimum = Math.min(minimum, coverage);
                maximum = Math.max(maximum, coverage);
                check(coverage >= 0.0F && coverage <= 0.88F, "grass coverage stays within its limits");
                check(coverage == GrassCoverFeature.coverageAt(x, z, seed, 0.8F),
                        "grass coverage is deterministic at world coordinates");
            }
        }
        check(maximum - minimum > 0.3F, "grass cover has broad dense and thin areas");
        for (int z = -32; z <= 32; z++) {
            float west = GrassCoverFeature.coverageAt(15, z, seed, 0.8F);
            float east = GrassCoverFeature.coverageAt(16, z, seed, 0.8F);
            check(Math.abs(west - east) < 0.06F, "grass cover stays smooth across chunk borders");
        }
    }

    private static void checksFernBiomeTags() throws IOException {
        Set<String> largeFerns = resolveBiomeTag("where_winds_blow:has_large_ferns", true);
        Set<String> allFerns = resolveBiomeTag("where_winds_blow:has_fern_accents", true);
        for (String biome : List.of("taiga", "snowy_taiga", "old_growth_spruce_taiga", "old_growth_pine_taiga", "windswept_forest")) {
            check(largeFerns.contains("minecraft:" + biome), "spruce forests support large ferns in " + biome);
        }
        check(allFerns.containsAll(largeFerns), "large fern habitats also receive fern patches");
        check(!largeFerns.contains("minecraft:plains") && !largeFerns.contains("minecraft:forest"),
                "large fern accents stay in conifer habitats");
    }

    private static void checksFlowerBiomeTags() throws IOException {
        Set<String> open = resolveBiomeTag("where_winds_blow:has_open_flower_accents", true);
        Set<String> woodland = resolveBiomeTag("where_winds_blow:has_woodland_flower_accents", true);
        check(open.containsAll(Set.of("minecraft:plains", "minecraft:meadow", "minecraft:flower_forest")),
                "open flower accents reach grassy open biomes");
        check(woodland.containsAll(Set.of("minecraft:forest", "minecraft:birch_forest")),
                "woodland flower accents reach forests");
        check(!open.contains("minecraft:desert") && !woodland.contains("minecraft:snowy_plains"),
                "flower accents avoid dry and snowy habitats");
    }

    private static Set<String> biomeTagValues(String name) throws IOException {
        String resource = "/data/where_winds_blow/tags/worldgen/biome/" + name + ".json";
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(GrassWorldgenTest.class.getResourceAsStream(resource)), StandardCharsets.UTF_8)) {
            Set<String> values = new HashSet<>();
            JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("values")
                    .forEach(value -> values.add(value.getAsString()));
            return values;
        }
    }

    private static void checksPatchTransitions() {
        int offCenterPeaks = 0;
        for (int peak : new int[]{2, 3, 4, 5, 6}) {
            boolean[] allHeightsSeen = new boolean[peak + 1];
            for (int seed = 0; seed < 128; seed++) {
                RandomSource random = RandomSource.create(seed);
                double radius = (peak - 1) * 1.5D;
                GrassPatchShape shape = peak == 2
                        ? GrassPatchShape.create(random, 3.0D, 4.5D, 2.75D, 3.75D)
                        : GrassPatchShape.create(random, radius + 1.0D, radius + 2.5D, radius, radius + 1.25D);
                int originX = seed * 17 - 1024;
                int originZ = 511 - seed * 31;
                boolean[] seen = new boolean[peak + 1];
                int bounds = shape.bounds();
                if (shape.grassHeight(0, 0, originX, originZ, peak) < peak) {
                    offCenterPeaks++;
                }
                for (int x = -bounds - 1; x <= bounds + 1; x++) {
                    for (int z = -bounds - 1; z <= bounds + 1; z++) {
                        int height = shape.grassHeight(x, z, originX + x, originZ + z, peak);
                        check(height >= 0 && height <= peak, "patch heights stay within their limits");
                        seen[height] = true;
                        allHeightsSeen[height] = true;
                        if (height > 0) {
                            check(Math.abs(x) < 16 && Math.abs(z) < 16,
                                    "clumps stay within the neighboring chunks at every chunk-edge origin");
                        }
                        int east = shape.grassHeight(x + 1, z, originX + x + 1, originZ + z, peak);
                        int south = shape.grassHeight(x, z + 1, originX + x, originZ + z + 1, peak);
                        check(Math.abs(height - east) <= 1 && Math.abs(height - south) <= 1,
                                "neighboring columns transition one block at a time");
                        if (Math.abs(x) > bounds || Math.abs(z) > bounds) {
                            check(height == 0, "patch bounds include the whole transition");
                        }
                        if (height > 1) {
                            check(east > 0 && south > 0, "tall clumps have shorter cover between them and openings");
                        }
                    }
                }
                int highest = peak;
                while (!seen[highest]) {
                    highest--;
                }
                for (int height = 0; height <= highest; height++) {
                    check(seen[height], "patches contain every step between their local peaks and edges");
                }
            }
            for (boolean present : allHeightsSeen) {
                check(present, "the full grass height range appears across varied clumps");
            }
        }
        check(offCenterPeaks > 64, "patches break up the repeated central mound profile");
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
        for (var block : List.of(ModBlocks.SNOWY_FLAT_GRASS, ModBlocks.SNOWY_SHORT_GRASS, ModBlocks.SNOWY_FERN)) {
            Registry.register(BuiltInRegistries.BLOCK, block.getId(),
                    new SnowyGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS)));
        }
        for (var block : List.of(ModBlocks.SNOWY_TALL_GRASS, ModBlocks.SNOWY_LARGE_FERN)) {
            Registry.register(BuiltInRegistries.BLOCK, block.getId(),
                    new SnowyDoublePlantBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.TALL_GRASS)));
        }
        BuiltInRegistries.BLOCK.freeze();
        Registry.register(BuiltInRegistries.FEATURE, ModFeatures.FERN_PATCH.getId(),
                new FernPatchFeature(NoneFeatureConfiguration.CODEC));
        Registry.register(BuiltInRegistries.FEATURE, ModFeatures.FLOWER_PATCH.getId(),
                new FlowerPatchFeature(FlowerPatchConfiguration.CODEC));
        Registry.register(BuiltInRegistries.FEATURE, ModFeatures.TALL_GRASS_PATCH.getId(),
                new TallGrassPatchFeature(NoneFeatureConfiguration.CODEC));
        Registry.register(BuiltInRegistries.FEATURE, ModFeatures.OVERGROWN_GRASS_PATCH.getId(),
                new OvergrownGrassPatchFeature(NoneFeatureConfiguration.CODEC));
    }

    private static JsonObject worldgenResource(String folder, String name) throws IOException {
        String path = "/data/where_winds_blow/worldgen/" + folder + "/" + name + ".json";
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(GrassWorldgenTest.class.getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void checksPatchPlacementResources() throws IOException {
        for (String family : List.of("fern_accent_patch", "tall_grass_patch", "tall_grass_field_patch",
                "overgrown_grass_patch", "overgrown_grass_field_patch")) {
            for (int pass = 1; pass <= 8; pass++) {
                String name = family + (pass == 1 ? "" : "_" + pass);
                JsonObject resource = worldgenResource("placed_feature", name);
                String configuredName = ResourceLocation.parse(resource.get("feature").getAsString()).getPath();
                ConfiguredFeature.DIRECT_CODEC.parse(JsonOps.INSTANCE,
                        worldgenResource("configured_feature", configuredName)).getOrThrow();
                List<PlacementModifier> frequency = new ArrayList<>();
                boolean reachedHeightmap = false;
                for (JsonElement element : resource.getAsJsonArray("placement")) {
                    JsonObject placement = element.getAsJsonObject();
                    PlacementModifier modifier = PlacementModifier.CODEC.parse(JsonOps.INSTANCE, placement).getOrThrow();
                    if (placement.get("type").getAsString().equals("minecraft:heightmap")) {
                        check(placement.get("heightmap").getAsString().equals("MOTION_BLOCKING_NO_LEAVES"),
                                "patch origins use the forest floor in " + name);
                        reachedHeightmap = true;
                    } else if (!reachedHeightmap) {
                        frequency.add(modifier);
                    }
                }
                check(reachedHeightmap, "patches resolve a terrain height in " + name);

                RandomSource random = RandomSource.create(817263L + pass);
                int starts = 0;
                for (int region = -4; region < 4; region++) {
                    int regionalStarts = 0;
                    for (int sample = 0; sample < 64; sample++) {
                        List<BlockPos> positions = List.of(new BlockPos(region * 4096 + sample * 16, 64, region * -8192));
                        for (PlacementModifier modifier : frequency) {
                            positions = positions.stream().flatMap(pos -> modifier.getPositions(null, random, pos)).toList();
                        }
                        regionalStarts += positions.size();
                    }
                    check(regionalStarts > 0 && regionalStarts < 64,
                            "every region has patch starts and clearings in " + name);
                    starts += regionalStarts;
                }
                double rate = starts / 512.0D;
                double expectedRate = family.equals("fern_accent_patch") ? 0.5D
                        : family.equals("tall_grass_field_patch") ? 0.25D
                        : family.equals("overgrown_grass_field_patch") ? 0.125D : 1.0D / 3.0D;
                check(Math.abs(rate - expectedRate) < 0.08D, "patch frequency remains dispersed in " + name);
            }
        }
    }

    private static void keepsOvergrownClumpsLow() {
        OvergrownGrassPatchFeature feature = new OvergrownGrassPatchFeature(NoneFeatureConfiguration.CODEC);
        RandomSource seeds = RandomSource.create(7935612L);
        int columns = 0;
        int lowColumns = 0;
        int patchesWithOvergrowth = 0;
        int veryTallPatches = 0;
        for (int sample = 0; sample < 256; sample++) {
            TestWorld world = new TestWorld((x, z) -> 64);
            feature.place(context(world, new BlockPos(sample * 31 - 4096, 64, 2048 - sample * 17), seeds.nextLong()));
            int peak = 0;
            for (BlockPos pos : world.blocks.keySet()) {
                if (pos.getY() == 64) {
                    int height = world.columnHeight(pos.getX(), pos.getZ());
                    peak = Math.max(peak, height);
                    columns++;
                    if (height <= 3) {
                        lowColumns++;
                    }
                }
            }
            if (peak >= 3) {
                patchesWithOvergrowth++;
            }
            if (peak >= 5) {
                veryTallPatches++;
            }
            world.checkPlants();
        }
        check(columns > 256 * 40 && columns < 256 * 180, "overgrown patches form compact stands with substantial ground cover");
        check(lowColumns > columns * 0.9D, "most overgrown patch cover stays at three blocks or below");
        check(patchesWithOvergrowth > 64, "lower patches still contain substantial overgrown grass");
        check(veryTallPatches < 26, "five- and six-block stands remain occasional accents");
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

    private static void placesForestFerns() {
        FernPatchFeature feature = new FernPatchFeature(NoneFeatureConfiguration.CODEC);
        BlockPos origin = new BlockPos(-16, 140, 15);
        TestWorld spruce = new TestWorld((x, z) -> 80 + x - z);
        spruce.biome = biome("has_fern_accents", "has_large_ferns");
        check(feature.place(context(spruce, origin, 42)), "ferns follow the forest floor below a canopy-height origin");
        check(spruce.blocks.values().stream().anyMatch(state -> state.is(Blocks.FERN)), "fern patches include small ferns");
        check(spruce.blocks.values().stream().anyMatch(state -> state.is(Blocks.LARGE_FERN)), "spruce patches include large ferns");
        long fernCount = spruce.blocks.values().stream().filter(state -> state.is(Blocks.FERN)
                || state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER).count();
        check(fernCount <= 12, "forest ferns remain scattered accents");
        check(spruce.blocks.keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow()
                - spruce.blocks.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow() >= 8,
                "forest fern accents spread across the surrounding floor");
        spruce.checkPlants();

        TestWorld repeated = new TestWorld((x, z) -> 80 + x - z);
        repeated.biome = spruce.biome;
        feature.place(context(repeated, origin, 42));
        check(repeated.blocks.equals(spruce.blocks), "seeded fern patches are reproducible");

        TestWorld forest = new TestWorld((x, z) -> 64);
        forest.biome = biome("has_fern_accents");
        check(feature.place(context(forest, origin, 42)), "other forests retain their fern accents");
        check(forest.blocks.values().stream().allMatch(state -> state.is(Blocks.FERN)),
                "large ferns only appear in their tagged habitats");

        TestWorld border = new TestWorld((x, z) -> 64);
        border.biome = spruce.biome;
        border.includedBiomes = pos -> pos.getX() < 0;
        check(feature.place(context(border, new BlockPos(-1, 64, 0), 42)), "fern patches reach habitat boundaries");
        check(border.blocks.keySet().stream().allMatch(pos -> pos.getX() < 0), "ferns respect each column's biome");
        border.checkPlants();
    }

    private static void placesFlowerAccents() throws IOException {
        for (String family : List.of("open", "woodland")) {
            String name = family + "_flower_patch";
            JsonObject placed = worldgenResource("placed_feature", name);
            check(placed.get("feature").getAsString().equals("where_winds_blow:" + name),
                    "flower patches reference their own configured feature");
            JsonObject configured = worldgenResource("configured_feature", name);
            JsonObject settings = configured.getAsJsonObject("config");
            check(settings.get("biomes").getAsString().equals("#where_winds_blow:has_" + family + "_flower_accents"),
                    "flower patches use their matching biome tags");
            settings.add("biomes", new com.google.gson.JsonArray());
            ConfiguredFeature.DIRECT_CODEC.parse(JsonOps.INSTANCE, configured).getOrThrow();
            for (JsonElement element : placed.getAsJsonArray("placement")) {
                PlacementModifier.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow();
            }
            JsonObject modifier = biomeModifierResource("where_winds_blow_" + family + "_flower_accents");
            check(modifier.get("density").getAsString().equals("flower_accents"),
                    "flower accents use their config control");
        }

        FlowerPatchFeature feature = new FlowerPatchFeature(FlowerPatchConfiguration.CODEC);
        TestWorld field = new TestWorld((x, z) -> 64);
        field.biome = biome("has_open_flower_accents");
        FlowerPatchConfiguration config = new FlowerPatchConfiguration(
                HolderSet.direct(field.biome), BlockStateProvider.simple(Blocks.CORNFLOWER.defaultBlockState()));
        BlockPos origin = new BlockPos(0, 64, 0);
        check(feature.place(new FeaturePlaceContext<>(Optional.empty(), field.level, null,
                RandomSource.create(42), origin, config)), "flowers form an accent patch");
        check(field.blocks.size() >= 5 && field.blocks.size() <= 80,
                "flower patches stay compact");
        check(field.blocks.values().stream().allMatch(state -> state.is(Blocks.CORNFLOWER)),
                "a patch keeps one flower species");
        field.checkPlants();

        TestWorld repeated = new TestWorld((x, z) -> 64);
        repeated.biome = field.biome;
        feature.place(new FeaturePlaceContext<>(Optional.empty(), repeated.level, null,
                RandomSource.create(42), origin, config));
        check(repeated.blocks.equals(field.blocks), "flower patches reproduce from the same seed");

        TestWorld border = new TestWorld((x, z) -> 64);
        border.biome = field.biome;
        border.includedBiomes = pos -> pos.getX() < 0;
        feature.place(new FeaturePlaceContext<>(Optional.empty(), border.level, null,
                RandomSource.create(42), origin, config));
        check(border.blocks.keySet().stream().allMatch(pos -> pos.getX() < 0),
                "flower patches stop at biome borders");

        TestWorld occupied = new TestWorld((x, z) -> 64);
        occupied.biome = field.biome;
        occupied.blocks.put(occupied.bottom(), Blocks.DANDELION.defaultBlockState());
        check(!FlowerPatchFeature.placeFlower(occupied.level, 0, 0, Blocks.CORNFLOWER.defaultBlockState(), config),
                "flower patches preserve existing plants");
        check(occupied.state(occupied.bottom()).is(Blocks.DANDELION),
                "flower patches leave established flowers intact");
        occupied.blocks.put(occupied.bottom(), Blocks.SHORT_GRASS.defaultBlockState());
        check(FlowerPatchFeature.placeFlower(occupied.level, 0, 0, Blocks.CORNFLOWER.defaultBlockState(), config),
                "flower accents can replace short grass");
    }

    private static JsonObject biomeModifierResource(String name) throws IOException {
        String path = "/data/where_winds_blow/neoforge/biome_modifier/" + name + ".json";
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(GrassWorldgenTest.class.getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void preservesFernNeighbors() {
        for (BlockState plant : new BlockState[]{
                Blocks.DANDELION.defaultBlockState(), Blocks.LARGE_FERN.defaultBlockState(),
                Blocks.DEAD_BUSH.defaultBlockState(), Blocks.WHEAT.defaultBlockState(),
                Blocks.WATER.defaultBlockState(), Blocks.SPRUCE_LEAVES.defaultBlockState(),
                ModBlocks.OVERGROWN_GRASS.get().defaultBlockState(), Blocks.STONE.defaultBlockState()
        }) {
            TestWorld world = new TestWorld((x, z) -> 64);
            world.biome = biome("has_fern_accents", "has_large_ferns");
            world.blocks.put(world.bottom(), plant);
            Map<BlockPos, BlockState> before = Map.copyOf(world.blocks);
            check(!FernPatchFeature.placeFern(world.level, world.bottom(), 2), "ferns preserve established plants and solids");
            check(world.blocks.equals(before), "fern placement leaves neighboring vegetation untouched");
        }

        for (BlockState cover : new BlockState[]{
                Blocks.AIR.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState(),
                ModBlocks.FLAT_GRASS.get().defaultBlockState(), Blocks.FERN.defaultBlockState()
        }) {
            TestWorld world = new TestWorld((x, z) -> 64);
            world.biome = biome("has_fern_accents", "has_large_ferns");
            if (!cover.isAir()) {
                world.blocks.put(world.bottom(), cover);
            }
            check(FernPatchFeature.placeFern(world.level, world.bottom(), 2), "large ferns grow through short ground cover");
            world.checkPlants();
            Map<BlockPos, BlockState> before = Map.copyOf(world.blocks);
            check(!world.place(2), "grass patches preserve established large ferns");
            check(world.blocks.equals(before), "grass leaves both fern halves intact");
        }

        TestWorld grass = new TestWorld((x, z) -> 64);
        grass.biome = biome("has_fern_accents", "has_large_ferns");
        check(grass.place(2), "creates tall grass before fern placement");
        check(!FernPatchFeature.placeFern(grass.level, grass.bottom(), 1), "small ferns never leave orphaned tall grass");
        check(FernPatchFeature.placeFern(grass.level, grass.bottom(), 2), "large ferns can replace complete tall grass plants");
        grass.checkPlants();
    }

    private static void respectsFernPlacementLimits() {
        for (BlockState obstruction : new BlockState[]{
                Blocks.SPRUCE_LEAVES.defaultBlockState(), Blocks.WATER.defaultBlockState(), Blocks.STONE.defaultBlockState()
        }) {
            TestWorld world = new TestWorld((x, z) -> 64);
            world.biome = biome("has_fern_accents", "has_large_ferns");
            world.blocks.put(world.bottom().above(), obstruction);
            check(FernPatchFeature.placeFern(world.level, world.bottom(), 2), "ferns fall back to small plants under obstructions");
            check(world.state(world.bottom()).is(Blocks.FERN), "low headroom produces a small fern");
            check(world.state(world.bottom().above()).equals(obstruction), "fern headroom checks preserve obstructions");
        }
        for (int ground : new int[]{319, 320}) {
            TestWorld world = new TestWorld((x, z) -> ground);
            world.biome = biome("has_fern_accents", "has_large_ferns");
            check(FernPatchFeature.placeFern(world.level, world.bottom(), 2) == (ground < 320),
                    "ferns respect the build ceiling");
            check(world.blocks.size() == (ground < 320 ? 1 : 0), "fern halves never exceed the build ceiling");
        }
        for (int writableHeight : new int[]{64, 65}) {
            TestWorld world = new TestWorld((x, z) -> 64);
            world.biome = biome("has_fern_accents", "has_large_ferns");
            world.writableHeight = writableHeight;
            check(FernPatchFeature.placeFern(world.level, world.bottom(), 2) == (writableHeight > 64),
                    "ferns respect generation write limits");
            check(world.blocks.size() == writableHeight - 64, "fern fallback stays within writable space");
        }
        for (int existingHeight : new int[]{0, 1, 2}) {
            TestWorld world = new TestWorld((x, z) -> 64);
            world.biome = biome("has_fern_accents", "has_large_ferns");
            if (existingHeight > 0) {
                check(world.place(existingHeight), "creates grass before a failed fern write");
            }
            Map<BlockPos, BlockState> before = Map.copyOf(world.blocks);
            world.failedWrite = world.bottom().above();
            check(!FernPatchFeature.placeFern(world.level, world.bottom(), 2), "failed fern writes report failure");
            check(world.blocks.equals(before), "failed fern placement restores the original ground cover");
            world.checkPlants();
        }
    }

    private static void placesSnowyPatches() throws IOException {
        check(biomeTagValues("has_snowy_foliage").equals(Set.of("#c:is_snowy")), "snowy foliage follows snowy biome tags");
        check(worldgenResource("placed_feature", "snowy_foliage_patch").getAsJsonArray("placement")
                .get(0).getAsJsonObject().get("chance").getAsInt() == 3, "snowy patches remain sparse");
        SnowyFoliagePatchFeature feature = new SnowyFoliagePatchFeature(NoneFeatureConfiguration.CODEC);
        int grasses = 0;
        int ferns = 0;
        int mixed = 0;
        boolean smallFern = false;
        boolean largeFern = false;
        for (int seed = 0; seed < 64; seed++) {
            TestWorld world = new TestWorld((x, z) -> 64);
            world.biome = biome("has_snowy_foliage");
            feature.place(context(world, world.bottom(), seed));
            int patchGrasses = 0;
            int patchFerns = 0;
            for (BlockState state : world.blocks.values()) {
                if (state.hasProperty(DoublePlantBlock.HALF) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
                    continue;
                }
                if (state.is(ModBlocks.SNOWY_FERN.get()) || state.is(ModBlocks.SNOWY_LARGE_FERN.get())) {
                    patchFerns++;
                    smallFern |= state.is(ModBlocks.SNOWY_FERN.get());
                    largeFern |= state.is(ModBlocks.SNOWY_LARGE_FERN.get());
                } else {
                    patchGrasses++;
                }
            }
            check(patchGrasses > 0, "snowy generation keeps grass throughout each patch");
            grasses += patchGrasses;
            ferns += patchFerns;
            if (patchGrasses > 0 && patchFerns > 0) {
                mixed++;
            }
            world.checkPlants();
        }
        check(ferns > 0 && ferns * 4 < grasses, "snowy ferns remain occasional accents among grass");
        check(mixed > 32 && smallFern && largeFern, "both fern sizes are sprinkled through mixed snowy patches");
        TestWorld border = new TestWorld((x, z) -> 64 + Math.floorDiv(x, 4));
        border.biome = biome("has_snowy_foliage");
        border.includedBiomes = pos -> pos.getX() < 0;
        check(feature.place(context(border, new BlockPos(-1, 90, 0), 42)), "snowy patches follow sloping terrain");
        for (BlockPos pos : border.blocks.keySet()) {
            check(pos.getX() < 0, "snowy plants stay within snowy biome boundaries");
        }
        border.checkPlants();
        for (BlockState obstruction : new BlockState[]{Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                Blocks.DANDELION.defaultBlockState(), Blocks.LARGE_FERN.defaultBlockState()}) {
            TestWorld world = new TestWorld((x, z) -> 64);
            world.biome = biome("has_snowy_foliage");
            world.blocks.put(world.bottom(), obstruction);
            check(!SnowyFoliagePatchFeature.placePlant(world.level, world.bottom(), ModBlocks.SNOWY_FERN.get(),
                    ModBlocks.SNOWY_LARGE_FERN.get(), true), "snowy patches preserve existing vegetation and obstacles");
            check(world.state(world.bottom()).equals(obstruction), "snowy placement leaves obstructions intact");
        }
        for (var tallPlant : List.of(ModBlocks.SNOWY_TALL_GRASS, ModBlocks.SNOWY_LARGE_FERN)) {
            TestWorld world = new TestWorld((x, z) -> 64);
            world.biome = biome("has_snowy_foliage");
            world.failedWrite = world.bottom().above();
            check(!SnowyFoliagePatchFeature.placePlant(world.level, world.bottom(), ModBlocks.SNOWY_SHORT_GRASS.get(),
                    tallPlant.get(), true), "failed snowy upper-half writes report failure");
            check(world.blocks.isEmpty(), "failed snowy placement restores the original cover");
            world.failedWrite = null;
            world.blocks.put(world.bottom().above(), Blocks.STONE.defaultBlockState());
            check(SnowyFoliagePatchFeature.placePlant(world.level, world.bottom(), ModBlocks.SNOWY_SHORT_GRASS.get(),
                    tallPlant.get(), true), "snowy tall plants fall back to short cover under obstacles");
            check(world.state(world.bottom()).is(ModBlocks.SNOWY_SHORT_GRASS.get()), "snowy fallback uses a short plant");
        }
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

    private static void restrictsOvergrownGrassAtBiomeBorders() {
        TestWorld dry = new TestWorld((x, z) -> 64);
        dry.includedBiomes = pos -> false;
        check(dry.place(6), "excluded biomes still receive shorter ground cover");
        check(dry.columnHeight(0, 0) == 2, "overgrown columns fall back to tall grass outside their habitat");

        TestWorld border = new TestWorld((x, z) -> 64);
        border.includedBiomes = pos -> pos.getX() < 0;
        OvergrownGrassPatchFeature feature = new OvergrownGrassPatchFeature(NoneFeatureConfiguration.CODEC);
        check(feature.place(context(border, new BlockPos(-1, 120, 0), 42)), "patches generate across a biome boundary");
        boolean hasOvergrown = false;
        boolean hasBorderCover = false;
        for (Map.Entry<BlockPos, BlockState> entry : border.blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            if (entry.getValue().is(ModBlocks.OVERGROWN_GRASS.get())) {
                check(pos.getX() < 0, "overgrown grass never spills into excluded neighboring biomes");
                hasOvergrown = true;
            } else if (pos.getX() >= 0) {
                hasBorderCover = true;
            }
        }
        check(hasOvergrown && hasBorderCover, "biome borders retain both wetland growth and shorter transition cover");
        border.checkPlants();

        TestWorld customField = new TestWorld((x, z) -> 64);
        customField.biome = biome("has_overgrown_grass_fields");
        check(customField.place(4) && customField.columnHeight(0, 0) == 4,
                "data packs can still opt biomes into overgrown fields");
    }

    @SuppressWarnings("unchecked")
    private static Holder<Biome> biome(String... tagNames) {
        Set<TagKey<Biome>> allowedTags = new HashSet<>();
        for (String tagName : tagNames) {
            allowedTags.add(TagKey.create(Registries.BIOME,
                    ResourceLocation.fromNamespaceAndPath("where_winds_blow", tagName)));
        }
        return (Holder<Biome>) Proxy.newProxyInstance(
                Holder.class.getClassLoader(), new Class<?>[]{Holder.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "is" -> allowedTags.contains(arguments[0]);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new AssertionError("placement checks biome tag membership");
                }
        );
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
        private Holder<Biome> biome = biome("has_overgrown_grass");
        private final Holder<Biome> otherBiome = biome();
        private Predicate<BlockPos> includedBiomes = pos -> true;

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
                            case "getBiome" -> includedBiomes.test((BlockPos) arguments[0]) ? biome : otherBiome;
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
                if (state.getBlock() instanceof DoublePlantBlock) {
                    boolean lower = state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER;
                    BlockState partner = state(lower ? pos.above() : pos.below());
                    check(partner.is(state.getBlock())
                                    && partner.getValue(DoublePlantBlock.HALF) != state.getValue(DoublePlantBlock.HALF),
                            "double plant halves stay paired");
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
