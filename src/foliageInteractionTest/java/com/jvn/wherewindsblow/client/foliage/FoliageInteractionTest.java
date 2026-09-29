package com.jvn.wherewindsblow.client.foliage;

import com.google.gson.JsonObject;
import com.jvn.wherewindsblow.block.SnowLogging;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.registries.GameData;

public final class FoliageInteractionTest {
    private static int checks;

    public static void main(String[] args) throws ReflectiveOperationException {
        for (int groundY : new int[]{-64, 0, 64, 127}) {
            check(!hasImpulse(groundY + 1.0D, groundY + 2.8D, groundY, 1.2F, 0.0F),
                    "standing level with the foliage top does not push it");
            check(!hasImpulse(groundY + 1.25D, groundY + 3.05D, groundY, 1.2F, 0.0F),
                    "standing above foliage does not push it");
            check(!hasImpulse(groundY + 1.0D, groundY + 4.0D, groundY, 2.6F, -1.55F),
                    "large entities and moving wakes cannot reach below the feet");
            check(hasImpulse(groundY, groundY + 1.8D, groundY, 1.2F, 0.0F),
                    "standing inside foliage still pushes it");
            check(hasImpulse(groundY + 0.95D, groundY + 2.75D, groundY, 1.2F, 0.0F),
                    "partial vertical overlap still pushes foliage");
            check(hasImpulse(groundY, groundY + 1.8D, groundY + 1, 1.2F, 0.0F),
                    "the upper half of tall grass still responds to body contact");
            check(!hasImpulse(groundY + 2.0D, groundY + 3.8D, groundY + 1, 1.2F, 0.0F),
                    "standing above tall grass does not push its upper half");
            check(!hasImpulse(groundY - 1.8D, groundY, groundY, 1.2F, 0.0F),
                    "touching the bottom boundary is not vertical overlap");
            check(!hasImpulse(groundY - 2.0D, groundY - 0.2D, groundY, 1.2F, 0.0F),
                    "standing below foliage does not push it");
        }
        snowUsesAmbientOcclusion();
        loggedPlantsKeepTheirOffset();
        adjacentGrassGetsEdgeFill();
        adjacentFlowersGetDenseCoverage();
        variedPlantHeightKeepsColumnsConnected();
        System.out.println("Passed " + checks + " foliage interaction checks.");
    }

    private static void snowUsesAmbientOcclusion() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
        Block plant = new Block(BlockBehaviour.Properties.of()) {
            @Override
            protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
                builder.add(SnowLogging.LAYERS);
            }
        };
        Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath("where_winds_blow", "ao_test"), plant);
        BakedModel unshadedPlant = (BakedModel) Proxy.newProxyInstance(BakedModel.class.getClassLoader(),
                new Class<?>[]{BakedModel.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("useAmbientOcclusion")) {
                        return method.getReturnType() == boolean.class ? false : TriState.FALSE;
                    }
                    throw new AssertionError("unexpected model call: " + method.getName());
                });
        SnowloggedFoliageModel model = new SnowloggedFoliageModel(unshadedPlant);
        for (int layers : new int[]{0, 1, 8}) {
            BlockState state = plant.defaultBlockState().setValue(SnowLogging.LAYERS, layers);
            check(model.useAmbientOcclusion(state, ModelData.EMPTY, RenderType.solid())
                    == (layers > 0 ? TriState.TRUE : TriState.FALSE), "snow uses ambient occlusion despite an unshaded plant model");
            check(model.useAmbientOcclusion(state, ModelData.EMPTY, RenderType.cutoutMipped()) == TriState.FALSE,
                    "foliage keeps its original cutout shading");
            check(model.useAmbientOcclusion(state, ModelData.EMPTY, null)
                    == (layers > 0 ? TriState.TRUE : TriState.FALSE), "combined rendering also shades snow");
        }
    }

    private static void loggedPlantsKeepTheirOffset() throws ReflectiveOperationException {
        Block plant = new Block(BlockBehaviour.Properties.of().offsetType(BlockBehaviour.OffsetType.XZ)) {
            @Override
            protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
                builder.add(SnowLogging.LAYERS);
            }
        };
        Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath("where_winds_blow", "offset_test"), plant);
        int[] vertices = new int[32];
        for (int vertex = 0; vertex < 4; vertex++) {
            int start = vertex * 8;
            vertices[start] = Float.floatToRawIntBits(vertex % 2);
            vertices[start + 1] = Float.floatToRawIntBits(0.125F);
            vertices[start + 2] = Float.floatToRawIntBits(vertex / 2);
            vertices[start + 3] = 0x20FFFFFF;
            vertices[start + 4] = Float.floatToRawIntBits(0.75F);
            vertices[start + 5] = Float.floatToRawIntBits(0.25F);
        }
        BakedQuad quad = new BakedQuad(vertices, -1, Direction.UP, null, true, false);
        BakedModel original = (BakedModel) Proxy.newProxyInstance(BakedModel.class.getClassLoader(), new Class<?>[]{BakedModel.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getModelData" -> arguments[3];
                    case "getRenderTypes" -> ChunkRenderTypeSet.of(RenderType.cutoutMipped());
                    case "getQuads" -> List.of(quad);
                    default -> throw new AssertionError("unexpected model call: " + method.getName());
                });
        BlockAndTintGetter level = (BlockAndTintGetter) Proxy.newProxyInstance(BlockAndTintGetter.class.getClassLoader(),
                new Class<?>[]{BlockAndTintGetter.class}, (proxy, method, arguments) -> {
                    throw new AssertionError("unexpected world call: " + method.getName());
                });
        SnowloggedFoliageModel model = new SnowloggedFoliageModel(original);
        BlockState dry = plant.defaultBlockState().setValue(SnowLogging.LAYERS, 0);
        BlockState logged = dry.setValue(SnowLogging.LAYERS, 3);
        for (BlockPos pos : List.of(new BlockPos(13, 64, -9), new BlockPos(-10, 80, 17))) {
            Vec3 offset = dry.getOffset(level, pos);
            check(!offset.equals(Vec3.ZERO), "offset tests exercise displaced foliage");
            ModelData data = model.getModelData(level, pos, logged, ModelData.EMPTY);
            BakedQuad shifted = model.getQuads(logged, null, net.minecraft.util.RandomSource.create(42), data,
                    RenderType.cutoutMipped()).getFirst();
            for (int vertex = 0; vertex < 4; vertex++) {
                int start = vertex * 8;
                check(Math.abs(Float.intBitsToFloat(shifted.getVertices()[start])
                        - (Float.intBitsToFloat(vertices[start]) + offset.x)) < 0.000001D,
                        "logged foliage retains its horizontal position");
                check(Math.abs(Float.intBitsToFloat(shifted.getVertices()[start + 2])
                        - (Float.intBitsToFloat(vertices[start + 2]) + offset.z)) < 0.000001D,
                        "logged foliage retains its depth position");
                for (int component : new int[]{1, 3, 4, 5, 6, 7}) {
                    check(shifted.getVertices()[start + component] == vertices[start + component],
                            "moving foliage preserves height, wind markers, UVs, light, and normals");
                }
            }
            check(!shifted.hasAmbientOcclusion(), "moving foliage preserves its quad shading");
            check(model.getQuads(dry, null, net.minecraft.util.RandomSource.create(42), ModelData.EMPTY,
                    RenderType.cutoutMipped()).getFirst() == quad, "unlogged foliage uses its original geometry");
        }
        check(Float.intBitsToFloat(quad.getVertices()[0]) == 0.0F, "offsetting foliage does not mutate shared model geometry");
    }

    private static void adjacentGrassGetsEdgeFill() throws ReflectiveOperationException {
        BlockPos pos = new BlockPos(4, 64, 6);
        BlockState grass = Blocks.SHORT_GRASS.defaultBlockState();
        Method edges = ResponsiveFoliageModel.class.getDeclaredMethod(
                "grassFillEdges", BlockAndTintGetter.class, BlockPos.class, BlockState.class);
        edges.setAccessible(true);
        for (int neighborMask = 0; neighborMask < 4; neighborMask++) {
            int mask = neighborMask;
            BlockAndTintGetter level = (BlockAndTintGetter) Proxy.newProxyInstance(
                    BlockAndTintGetter.class.getClassLoader(), new Class<?>[]{BlockAndTintGetter.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("getBlockState")) {
                            BlockPos neighbor = (BlockPos) arguments[0];
                            boolean connected = (neighbor.equals(pos.east()) && (mask & 1) != 0)
                                    || (neighbor.equals(pos.south()) && (mask & 2) != 0);
                            return connected ? grass : Blocks.AIR.defaultBlockState();
                        }
                        throw new AssertionError("unexpected world call: " + method.getName());
                    });
            check((int) edges.invoke(null, level, pos, grass) == mask,
                    "grass fill exists exactly on shared east and south edges");
            check((int) edges.invoke(null, level, pos, Blocks.FERN.defaultBlockState()) == 0,
                    "non-grass foliage does not get grass fill");
        }

        BlockState tallGrass = Blocks.TALL_GRASS.defaultBlockState();
        BlockAndTintGetter tallLevel = (BlockAndTintGetter) Proxy.newProxyInstance(
                BlockAndTintGetter.class.getClassLoader(), new Class<?>[]{BlockAndTintGetter.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getBlockState")) {
                        BlockPos neighbor = (BlockPos) arguments[0];
                        return neighbor.equals(pos.east()) || neighbor.equals(pos.below())
                                ? tallGrass : Blocks.AIR.defaultBlockState();
                    }
                    throw new AssertionError("unexpected world call: " + method.getName());
                });
        check((int) edges.invoke(null, tallLevel, pos, tallGrass) == 0,
                "upper grass segments do not add another fill");
        BlockAndTintGetter tallRoots = (BlockAndTintGetter) Proxy.newProxyInstance(
                BlockAndTintGetter.class.getClassLoader(), new Class<?>[]{BlockAndTintGetter.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getBlockState")) {
                        return ((BlockPos) arguments[0]).equals(pos.east())
                                ? tallGrass : Blocks.AIR.defaultBlockState();
                    }
                    throw new AssertionError("unexpected world call: " + method.getName());
                });
        check((int) edges.invoke(null, tallRoots, pos, tallGrass) == 1,
                "adjacent tall grass roots share one shorter fill");

        int[] vertices = new int[32];
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * 8;
            vertices[offset] = Float.floatToRawIntBits(vertex % 2);
            vertices[offset + 1] = Float.floatToRawIntBits(vertex / 2);
            vertices[offset + 2] = Float.floatToRawIntBits(vertex / 2);
            vertices[offset + 3] = 0xFFFFFFFF;
            vertices[offset + 4] = Float.floatToRawIntBits(0.25F);
            vertices[offset + 5] = Float.floatToRawIntBits(0.75F);
        }
        BakedQuad original = new BakedQuad(vertices, 2, Direction.NORTH, null, true, false);
        Class<?> keyType = Class.forName(ResponsiveFoliageModel.class.getName() + "$GrassFillQuadKey");
        Constructor<?> keyConstructor = keyType.getDeclaredConstructor(BakedQuad.class, Direction.class);
        keyConstructor.setAccessible(true);
        Method fillQuad = ResponsiveFoliageModel.class.getDeclaredMethod("grassFillQuad", keyType);
        fillQuad.setAccessible(true);
        Map<Direction, BakedQuad> fills = new HashMap<>();
        for (Direction edge : List.of(Direction.EAST, Direction.SOUTH)) {
            BakedQuad filled = (BakedQuad) fillQuad.invoke(null, keyConstructor.newInstance(original, edge));
            fills.put(edge, filled);
            double angle = Math.toRadians(edge == Direction.EAST ? 7.0D : -7.0D);
            float cosine = (float) Math.cos(angle);
            float sine = (float) Math.sin(angle);
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * 8;
                float x = Float.intBitsToFloat(filled.getVertices()[offset]);
                float y = Float.intBitsToFloat(filled.getVertices()[offset + 1]);
                float z = Float.intBitsToFloat(filled.getVertices()[offset + 2]);
                float originalX = vertex % 2 - 0.5F;
                float originalZ = vertex / 2 - 0.5F;
                float expectedX = 0.5F + originalX * cosine - originalZ * sine
                        + (edge == Direction.EAST ? 0.5F : 0.0F);
                float expectedZ = 0.5F + originalX * sine + originalZ * cosine
                        + (edge == Direction.SOUTH ? 0.5F : 0.0F);
                check(Math.abs(x - expectedX) < 0.0001F,
                        "fill keeps its width while rotating at the shared edge");
                check(Math.abs(y - (vertex / 2) * 0.7F) < 0.000001F,
                        "fill is shorter than the source grass");
                check(Math.abs(z - expectedZ) < 0.0001F,
                        "fill keeps its depth while rotating at the shared edge");
                for (int component = 3; component < 8; component++) {
                    check(filled.getVertices()[offset + component] == vertices[offset + component],
                            "fill keeps the source texture and vertex data");
                }
            }
        }
        float eastDx = Float.intBitsToFloat(fills.get(Direction.EAST).getVertices()[24])
                - Float.intBitsToFloat(fills.get(Direction.EAST).getVertices()[0]);
        float eastDz = Float.intBitsToFloat(fills.get(Direction.EAST).getVertices()[26])
                - Float.intBitsToFloat(fills.get(Direction.EAST).getVertices()[2]);
        float southDx = Float.intBitsToFloat(fills.get(Direction.SOUTH).getVertices()[24])
                - Float.intBitsToFloat(fills.get(Direction.SOUTH).getVertices()[0]);
        float southDz = Float.intBitsToFloat(fills.get(Direction.SOUTH).getVertices()[26])
                - Float.intBitsToFloat(fills.get(Direction.SOUTH).getVertices()[2]);
        check(Math.abs(eastDx * southDz - eastDz * southDx) > 0.1F,
                "neighboring edge copies no longer share a diagonal plane");
        check(Float.intBitsToFloat(original.getVertices()[0]) == 0.0F,
                "fill does not mutate the original grass model");
    }

    private static void adjacentFlowersGetDenseCoverage() throws ReflectiveOperationException {
        Method parse = FoliageSwayProfileReloadListener.class.getDeclaredMethod(
                "parse", ResourceLocation.class, JsonObject.class);
        parse.setAccessible(true);
        JsonObject json = new JsonObject();
        json.addProperty("type", "flower");
        json.addProperty("blocks", "minecraft:dandelion");
        json.addProperty("dense_when_adjacent", true);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("where_winds_blow", "dense_test");
        FoliageSwayProfile enabled = (FoliageSwayProfile) parse.invoke(null, id, json);
        check(enabled.denseWhenAdjacent(), "flower profiles can opt into dense coverage");
        json.addProperty("blocks", "minecraft:poppy");
        FoliageSwayProfile poppy = (FoliageSwayProfile) parse.invoke(null, id, json);
        json.remove("dense_when_adjacent");
        json.addProperty("blocks", "minecraft:blue_orchid");
        FoliageSwayProfile defaultProfile = (FoliageSwayProfile) parse.invoke(null, id, json);
        check(!defaultProfile.denseWhenAdjacent(), "dense coverage is opt in for other profiles");
        FoliageSwayProfiles.apply(List.of(enabled, poppy, defaultProfile));

        BlockPos pos = new BlockPos(4, 64, 6);
        BlockState flower = Blocks.DANDELION.defaultBlockState();
        Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        Method neighbors = ResponsiveFoliageModel.class.getDeclaredMethod(
                "flowerNeighbors", BlockAndTintGetter.class, BlockPos.class, BlockState.class);
        neighbors.setAccessible(true);
        for (int neighborMask = 0; neighborMask < 16; neighborMask++) {
            int mask = neighborMask;
            BlockAndTintGetter level = (BlockAndTintGetter) Proxy.newProxyInstance(
                    BlockAndTintGetter.class.getClassLoader(), new Class<?>[]{BlockAndTintGetter.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("getBlockState")) {
                            BlockPos adjacent = (BlockPos) arguments[0];
                            for (int index = 0; index < directions.length; index++) {
                                if (adjacent.equals(pos.relative(directions[index]))) {
                                    return (mask & (1 << index)) != 0
                                            ? (index == 1 ? Blocks.POPPY.defaultBlockState() : flower)
                                            : Blocks.BLUE_ORCHID.defaultBlockState();
                                }
                            }
                            return Blocks.AIR.defaultBlockState();
                        }
                        throw new AssertionError("unexpected world call: " + method.getName());
                    });
            check((int) neighbors.invoke(null, level, pos, flower) == mask,
                    "dense coverage detects each eligible flower neighbor");
        }
        BlockAndTintGetter stacked = (BlockAndTintGetter) Proxy.newProxyInstance(
                BlockAndTintGetter.class.getClassLoader(), new Class<?>[]{BlockAndTintGetter.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getBlockState")) {
                        return flower;
                    }
                    throw new AssertionError("unexpected world call: " + method.getName());
                });
        check((int) neighbors.invoke(null, stacked, pos, flower) == 0,
                "stacked flower parts do not receive dense copies");

        FoliageSwayProfiles.apply(List.of());
        scatteredFlowersVaryByPosition();
    }

    private static void scatteredFlowersVaryByPosition() throws ReflectiveOperationException {
        Method variant = ResponsiveFoliageModel.class.getDeclaredMethod("flowerVariant", BlockPos.class);
        variant.setAccessible(true);
        Set<Integer> layouts = new HashSet<>();
        for (int x = -8; x < 8; x++) {
            for (int z = -8; z < 8; z++) {
                BlockPos pos = new BlockPos(x, 64, z);
                int layout = (int) variant.invoke(null, pos);
                check(layout == (int) variant.invoke(null, pos),
                        "flower placement stays stable when the same block rebuilds");
                layouts.add(layout);
            }
        }
        check(layouts.size() >= 24, "nearby flower blocks use a wide range of layouts");

        int[] vertices = new int[32];
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * 8;
            float diagonal = vertex == 0 || vertex == 3 ? 0.2F : 0.8F;
            vertices[offset] = Float.floatToRawIntBits(diagonal);
            vertices[offset + 1] = Float.floatToRawIntBits(vertex < 2 ? 0.0F : 1.0F);
            vertices[offset + 2] = Float.floatToRawIntBits(diagonal);
            vertices[offset + 3] = 0xFFFFFFFF;
            vertices[offset + 4] = Float.floatToRawIntBits(0.25F);
            vertices[offset + 5] = Float.floatToRawIntBits(0.75F);
        }
        BakedQuad original = new BakedQuad(vertices, 2, Direction.NORTH, null, true, false);
        Class<?> copyType = Class.forName(ResponsiveFoliageModel.class.getName() + "$FlowerCopy");
        Object[] copies = copyType.getEnumConstants();
        Class<?> keyType = Class.forName(ResponsiveFoliageModel.class.getName() + "$FlowerFillQuadKey");
        Constructor<?> keyConstructor = keyType.getDeclaredConstructor(BakedQuad.class, copyType, int.class);
        keyConstructor.setAccessible(true);
        Method fillQuad = ResponsiveFoliageModel.class.getDeclaredMethod("flowerFillQuad", keyType);
        fillQuad.setAccessible(true);
        double minimumGapOffset = Double.POSITIVE_INFINITY;
        double maximumGapOffset = Double.NEGATIVE_INFINITY;
        double minimumHeight = Double.POSITIVE_INFINITY;
        double maximumHeight = Double.NEGATIVE_INFINITY;
        for (int layout = 0; layout < 32; layout++) {
            Vec3[] centers = new Vec3[copies.length];
            for (int index = 0; index < copies.length; index++) {
                Object key = keyConstructor.newInstance(original, copies[index], layout);
                BakedQuad copy = (BakedQuad) fillQuad.invoke(null, key);
                check(Arrays.equals(copy.getVertices(), ((BakedQuad) fillQuad.invoke(null, key)).getVertices()),
                        "flower geometry is deterministic for each layout");
                Vec3 center = quadCenter(copy).subtract(0.5D, 0.0D, 0.5D);
                centers[index] = center;
                if (index < 2) {
                    double radius = Math.sqrt(center.x * center.x + center.z * center.z);
                    check(radius >= 0.239D && radius <= 0.371D,
                            "extra flowers scatter within their block");
                } else {
                    double along = index == 2 ? center.x : center.z;
                    double across = index == 2 ? center.z : center.x;
                    check(along >= 0.419D && along <= 0.581D && Math.abs(across) <= 0.231D,
                            "gap flowers stay near their shared edge with sideways variation");
                    minimumGapOffset = Math.min(minimumGapOffset, across);
                    maximumGapOffset = Math.max(maximumGapOffset, across);
                }
                double height = center.y * 2.0D;
                check(height >= 0.739D && height <= 0.971D, "extra flower heights stay within their bounds");
                minimumHeight = Math.min(minimumHeight, height);
                maximumHeight = Math.max(maximumHeight, height);
                double dx = Float.intBitsToFloat(copy.getVertices()[8]) - Float.intBitsToFloat(copy.getVertices()[0]);
                double dz = Float.intBitsToFloat(copy.getVertices()[10]) - Float.intBitsToFloat(copy.getVertices()[2]);
                double widthScale = Math.sqrt(dx * dx + dz * dz) / Math.sqrt(0.72D);
                check(widthScale >= 0.679D && widthScale <= 0.861D,
                        "extra flowers use smaller widths to limit overlap");
                for (int vertex = 0; vertex < 4; vertex++) {
                    for (int component = 3; component < 8; component++) {
                        int offset = vertex * 8 + component;
                        check(copy.getVertices()[offset] == vertices[offset],
                                "flower copies preserve texture and vertex data");
                    }
                }
            }
            check(Math.abs(centers[0].x * centers[1].z - centers[0].z * centers[1].x) > 0.03D,
                    "flowers within each block form a cluster instead of a straight line");
        }
        check(maximumGapOffset - minimumGapOffset > 0.35D,
                "gap flowers spread across both sides of the connecting axis");
        check(maximumHeight - minimumHeight > 0.18D,
                "individual flower copies have visible height variation");
        check(Float.intBitsToFloat(original.getVertices()[0]) == 0.2F,
                "dense flowers do not mutate the original model");
    }

    private static Vec3 quadCenter(BakedQuad quad) {
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        Vec3 center = Vec3.ZERO;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            center = center.add(Float.intBitsToFloat(vertices[offset]),
                    Float.intBitsToFloat(vertices[offset + 1]), Float.intBitsToFloat(vertices[offset + 2]));
        }
        return center.scale(0.25D);
    }

    private static void variedPlantHeightKeepsColumnsConnected() throws ReflectiveOperationException {
        FoliageHeightVariation.Range range = new FoliageHeightVariation.Range(1.1F, 0.9F);
        check(range.minimum() == 0.9F && range.maximum() == 1.1F,
                "height limits can be entered in either order");
        check(new FoliageHeightVariation.Range(1.0F, 1.0F).isOriginalHeight(),
                "an original-height range disables geometry changes");
        ResourceLocation grassId = ResourceLocation.withDefaultNamespace("short_grass");
        BlockPos root = new BlockPos(11, 64, -7);
        float chosen = FoliageHeightVariation.sample(root, grassId, range);
        check(chosen >= range.minimum() && chosen <= range.maximum(),
                "sampled grass height remains inside the configured range");
        check(chosen == FoliageHeightVariation.sample(root, grassId, range),
                "the same plant keeps its height between rebuilds");
        boolean foundOtherHeight = false;
        for (int x = 12; x < 44; x++) {
            if (chosen != FoliageHeightVariation.sample(new BlockPos(x, 64, -7), grassId, range)) {
                foundOtherHeight = true;
                break;
            }
        }
        check(foundOtherHeight, "nearby plants can have different heights");

        int[] vertices = new int[32];
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * 8;
            vertices[offset] = Float.floatToRawIntBits(vertex % 2);
            vertices[offset + 1] = Float.floatToRawIntBits(vertex / 2);
            vertices[offset + 2] = Float.floatToRawIntBits(vertex / 2);
            vertices[offset + 3] = 0xFFFFFFFF;
            vertices[offset + 4] = Float.floatToRawIntBits(0.25F);
        }
        BakedQuad original = new BakedQuad(vertices, 1, Direction.NORTH, null, true, false);
        Class<?> keyType = Class.forName(ResponsiveFoliageModel.class.getName() + "$HeightQuadKey");
        Constructor<?> constructor = keyType.getDeclaredConstructor(BakedQuad.class, int.class, int.class);
        constructor.setAccessible(true);
        Method heightQuad = ResponsiveFoliageModel.class.getDeclaredMethod("heightQuad", keyType);
        heightQuad.setAccessible(true);
        for (int segment = 0; segment < 2; segment++) {
            BakedQuad varied = (BakedQuad) heightQuad.invoke(null,
                    constructor.newInstance(original, segment, Float.floatToIntBits(0.9F)));
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * 8;
                float expectedY = (segment + vertex / 2) * 0.9F - segment;
                check(Math.abs(Float.intBitsToFloat(varied.getVertices()[offset + 1]) - expectedY) < 0.000001F,
                        "each column segment uses the same root height scale");
                for (int component = 0; component < 8; component++) {
                    if (component != 1) {
                        check(varied.getVertices()[offset + component] == vertices[offset + component],
                                "height variation preserves width, depth, texture, and vertex data");
                    }
                }
            }
        }
        check(Float.intBitsToFloat(original.getVertices()[1]) == 0.0F,
                "height variation does not mutate the original model");
    }

    private static boolean hasImpulse(double minY, double maxY, int blockY, float radius, float trailX)
            throws ReflectiveOperationException {
        Class<?> shapeType = Class.forName(ResponsiveFoliagePhysics.class.getName() + "$InteractionShape");
        Constructor<?> constructor = shapeType.getDeclaredConstructor(
                double.class, double.class, double.class, double.class,
                float.class, float.class, float.class, float.class
        );
        constructor.setAccessible(true);
        Object shape = constructor.newInstance(0.0D, minY, maxY, 0.5D, radius, trailX, 0.0F, 1.0F);
        Method addImpulse = ResponsiveFoliagePhysics.class.getDeclaredMethod(
                "addImpulseForBlock", shapeType, BlockPos.class, long.class, Map.class
        );
        addImpulse.setAccessible(true);
        Map<BlockPos, Object> impulses = new HashMap<>();
        BlockPos pos = new BlockPos(0, blockY, 0);
        addImpulse.invoke(null, shape, pos, 1000L, impulses);
        return impulses.containsKey(pos);
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
        checks++;
    }
}
