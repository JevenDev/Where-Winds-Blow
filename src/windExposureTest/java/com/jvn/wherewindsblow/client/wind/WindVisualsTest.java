package com.jvn.wherewindsblow.client.wind;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.Point;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.TerrainFlow;

public final class WindVisualsTest {
    private static int checks;

    public static void main(String[] args) throws ReflectiveOperationException {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        rejectsShelteredSpawns();
        checksWholeLineAndClearance();
        respectsLoadedChunksAndBuildHeight();
        checksSweptPathsAndInterpolation();
        rechecksFadingPathsAfterWorldChanges();
        checksDebrisCollisionsAndOutdoorAccess();
        usesClientHeightmaps();
        keepsTerrainFlowFinite();
        refreshesColumnCaches();
        checksPixelLockedWaving();
        System.out.println("Passed " + checks + " wind visual checks.");
    }

    private static void checksPixelLockedWaving() throws ReflectiveOperationException {
        CommentedConfig config = CommentedConfig.inMemory();
        ClientConfig.SPEC.correct(config);
        Constructor<?> loadedConfig = Class.forName("net.neoforged.fml.config.LoadedConfig")
                .getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
        loadedConfig.setAccessible(true);
        ClientConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) loadedConfig.newInstance(config, null, null));
        float[] vertex = new float[3];
        Map<String, Object[]> attributes = new HashMap<>();
        VertexConsumer consumer = (VertexConsumer) Proxy.newProxyInstance(
                WindVisualsTest.class.getClassLoader(), new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("addVertex")) {
                        for (int axis = 0; axis < 3; axis++) {
                            vertex[axis] = (float) args[axis];
                        }
                    } else {
                        attributes.put(method.getName(), args);
                    }
                    return proxy;
                }
        );
        MultiBufferSource buffers = type -> consumer;
        check(!PixelLockedWaving.enabled(), "pixel locked waving defaults to off");
        check(PixelLockedWaving.snap(0.047D) == 0.047D, "disabled motion keeps subtexel precision");
        check(PixelLockedWaving.buffers(buffers, new Matrix4f(), new Matrix4f()) == buffers,
                "disabled models keep the original buffer source");
        check(PixelLockedWaving.particleConsumer(consumer, new Vec3(0.047D, 0.0D, 0.0D)) == consumer,
                "disabled particles keep the original consumer");
        ClientConfig.PIXEL_LOCKED_WAVING.set(true);
        try {
            for (double coordinate : new double[]{-30000000.047D, -1.047D, -0.03D, 0.03D, 1.047D, 30000000.047D}) {
                double snapped = PixelLockedWaving.snap(coordinate);
                check(snapped * 16.0D == Math.rint(snapped * 16.0D), "world motion lands on the texel grid");
                check(Math.abs(snapped - coordinate) <= 1.0D / 32.0D, "snapping moves at most half a texel");
            }
            check(PixelLockedWaving.snap(-0.0625D) == -0.0625D, "negative texel boundaries stay fixed");
            check(PixelLockedWaving.snap(0.03125D) == 0.0625D, "half-texel ties match GLSL rounding");
            for (Vec3 camera : List.of(Vec3.ZERO, new Vec3(0.173D, -0.329D, 0.411D))) {
                Matrix4f rest = new Matrix4f().translation((float) -camera.x, (float) -camera.y, (float) -camera.z);
                Matrix4f animated = new Matrix4f(rest).translate(0.047F, -0.047F, 0.016F);
                Vector3f point = animated.transformPosition(0.001F, 0.25F, -0.013F, new Vector3f());
                VertexConsumer locked = PixelLockedWaving.buffers(buffers, rest, animated).getBuffer(null);
                locked.addVertex(point.x, point.y, point.z).setColor(10, 20, 30, 40)
                        .setUv(0.2F, 0.8F).setUv1(3, 7).setUv2(11, 15).setNormal(0.0F, 1.0F, 0.0F);
                check(Math.abs(vertex[0] + camera.x - 0.0635D) < 0.00001D
                                && Math.abs(vertex[1] + camera.y - 0.1875D) < 0.00001D
                                && Math.abs(vertex[2] + camera.z + 0.013D) < 0.00001D,
                        "model displacement stays texel locked as the camera moves and preserves thin geometry");
                check(attributes.size() == 5 && (int) attributes.get("setColor")[3] == 40
                                && (float) attributes.get("setUv")[1] == 0.8F
                                && (int) attributes.get("setUv1")[1] == 7
                                && (int) attributes.get("setUv2")[1] == 15
                                && (float) attributes.get("setNormal")[1] == 1.0F,
                        "locked model vertices preserve color, texture, overlay, light, and normals");
                Vec3 position = new Vec3(2.047D, -0.047D, 4.016D);
                locked = PixelLockedWaving.particleConsumer(consumer, position);
                locked.addVertex((float) (position.x - camera.x + 0.01D),
                        (float) (position.y - camera.y), (float) (position.z - camera.z));
                check(Math.abs(vertex[0] + camera.x - 2.0725D) < 0.00001D
                                && Math.abs(vertex[1] + camera.y + 0.0625D) < 0.00001D
                                && Math.abs(vertex[2] + camera.z - 4.0D) < 0.00001D,
                        "particle centers snap in world space while billboard dimensions remain intact");
            }
            Matrix4f rest = new Matrix4f().translation(0.173F, -0.329F, 0.411F)
                    .rotateY(0.63F).scale(0.6666667F, -0.6666667F, -0.6666667F);
            Matrix4f animated = new Matrix4f(rest).rotateX(0.21F).rotateZ(-0.07F);
            VertexConsumer locked = PixelLockedWaving.buffers(buffers, rest, animated).getBuffer(null);
            for (Vector3f local : List.of(new Vector3f(), new Vector3f(0.01F, 2.0F, -0.003F))) {
                Vector3f base = rest.transformPosition(local, new Vector3f());
                Vector3f point = animated.transformPosition(local, new Vector3f());
                locked.addVertex(point.x, point.y, point.z);
                for (int axis = 0; axis < 3; axis++) {
                    float delta = vertex[axis] - base.get(axis);
                    check(Math.abs(delta * 16.0F - Math.round(delta * 16.0F)) < 0.00001F,
                            "scaled and rotated decorations retain their rest position plus whole texel offsets");
                }
            }
            float angle = PixelLockedWaving.angle(0.37F, 0.5F);
            check(Math.abs(angle * 0.5F * 16.0F - Math.round(angle * 0.5F * 16.0F)) < 0.00001F,
                    "tumbling uses texel-sized arc steps");
        } finally {
            ClientConfig.PIXEL_LOCKED_WAVING.set(false);
        }
        check(PixelLockedWaving.snap(0.047D) == 0.047D, "turning the mode off restores smooth motion immediately");
    }

    private static void rejectsShelteredSpawns() {
        TestWorld world = new TestWorld();
        check(WindParticleEnvironment.isValidWindSpace(world.level, 0.5D, 66.0D, 0.5D, 1.45D),
                "open outdoor air permits wind");
        world.blocks.put(new BlockPos(0, 68, 0), Blocks.TALL_GRASS.defaultBlockState());
        check(WindParticleEnvironment.isValidWindSpace(world.level, 0.5D, 66.0D, 0.5D, 1.45D),
                "vegetation without collision does not count as a roof");
        check(WindParticleEnvironment.isWindLineSpace(world.level,
                        new AABB(0.2D, 66.0D, 0.2D, 0.8D, 66.2D, 0.8D), 1.45D),
                "outdoor lines remain available amongst tall vegetation");
        world.blocks.clear();
        world.blocks.put(new BlockPos(0, 60, 0), Blocks.AIR.defaultBlockState());
        check(!WindParticleEnvironment.isValidWindSpace(world.level, 0.5D, 60.5D, 0.5D, 0.0D),
                "underground air is rejected even with stale bright skylight");
        for (BlockState roof : List.of(Blocks.STONE.defaultBlockState(), Blocks.GLASS.defaultBlockState(),
                Blocks.OAK_SLAB.defaultBlockState(), Blocks.OAK_TRAPDOOR.defaultBlockState(),
                Blocks.OAK_LEAVES.defaultBlockState())) {
            world.blocks.put(new BlockPos(0, 70, 0), roof);
            check(!WindParticleEnvironment.isValidWindSpace(world.level, 0.5D, 66.0D, 0.5D, 0.0D),
                    "covered air rejects wind beneath " + roof);
            check(!WindParticleEnvironment.isWindLineSpace(world.level,
                            new AABB(0.2D, 66.0D, 0.2D, 0.8D, 66.2D, 0.8D), 1.45D),
                    "line bounds reject cover beneath " + roof);
            check(WindParticleEnvironment.isValidWindSpace(world.level, 0.5D, 73.0D, 0.5D, 1.45D),
                    "air above the roof remains available");
        }
        world.blocks.put(new BlockPos(0, 70, 0), Blocks.WATER.defaultBlockState());
        check(!WindParticleEnvironment.isValidWindSpace(world.level, 0.5D, 70.5D, 0.5D, 0.0D),
                "wind cannot spawn in fluids");
    }

    private static void checksWholeLineAndClearance() {
        TestWorld world = new TestWorld();
        AABB line = new AABB(0.5D, 66.0D, 0.5D, 4.5D, 66.2D, 0.5D);
        check(WindParticleEnvironment.isWindLineSpace(world.level, line, 1.45D), "clear lines are accepted");
        check(!WindParticleEnvironment.isWindLineSpace(world.level,
                        new AABB(0.5D, 65.5D, 0.5D, 4.5D, 65.5D, 0.5D), 1.45D),
                "the full line keeps a margin above terrain");
        world.blocks.put(new BlockPos(2, 69, 0), Blocks.GLASS.defaultBlockState());
        check(WindParticleEnvironment.isValidWindSpace(world.level, 0.5D, 66.0D, 0.5D, 1.45D)
                        && WindParticleEnvironment.isValidWindSpace(world.level, 4.5D, 66.0D, 0.5D, 1.45D),
                "both endpoints can be open beside a roof");
        check(!WindParticleEnvironment.isWindLineSpace(world.level, line, 1.45D),
                "cover between the endpoints blocks the line");
        world.blocks.clear();
        world.blocks.put(new BlockPos(-1, 69, 0), Blocks.STONE.defaultBlockState());
        check(!WindParticleEnvironment.isWindLineSpace(world.level,
                        new AABB(0.02D, 66.0D, 0.5D, 0.5D, 66.0D, 0.5D), 1.45D),
                "clearance includes adjacent columns at negative coordinates");
    }

    private static void respectsLoadedChunksAndBuildHeight() {
        TestWorld world = new TestWorld();
        world.loaded = (x, z) -> x == 0 && z == 0;
        check(!WindParticleEnvironment.isValidWindSpace(world.level, 16.5D, 66.0D, 0.5D, 0.0D),
                "unloaded wind positions are rejected before block access");
        check(!WindParticleEnvironment.isValidLeafSpace(world.level, -0.5D, 66.0D, 0.5D, 0.0D),
                "unloaded debris positions are rejected before block access");
        check(!WindParticleEnvironment.isWindLineSpace(world.level,
                        new AABB(15.5D, 66.0D, 0.5D, 16.5D, 66.0D, 0.5D), 1.45D),
                "a line crossing a chunk boundary needs all chunks loaded");
        check(Double.isInfinite(WindParticleEnvironment.terrainHeight(world.level, -0.5D, 0.5D)),
                "missing terrain cannot be mistaken for the world floor");
        Point point = WindParticleEnvironment.keepAboveTerrainAndCollision(world.level, -0.5D, 66.0D, 0.5D, 1.45D);
        check(Double.isFinite(point.y()), "missing terrain does not create infinite spawn coordinates");
        check(!WindParticleEnvironment.isValidWindSpace(world.level, 0.5D, 320.0D, 0.5D, 0.0D),
                "spawns above build height are rejected");
        check(!WindParticleEnvironment.isValidLeafSpace(world.level, 0.5D, -65.0D, 0.5D, 0.0D),
                "debris below build height is rejected");
        check(!WindParticleEnvironment.isWindLineSpace(world.level,
                        new AABB(0.5D, 319.95D, 0.5D, 1.5D, 319.95D, 0.5D), 0.0D),
                "line clearance stays within build height");
    }

    private static void checksSweptPathsAndInterpolation() {
        TestWorld world = new TestWorld();
        WindStreakPath path = new WindStreakPath(1);
        Point first = new Point(0.5D, 66.0D, 0.5D);
        path.setCandidate(0, first);
        path.setCandidate(1, new Point(4.5D, 66.0D, 0.5D));
        check(path.advance(world.level, 1.45D), "a valid path initializes");
        check(path.interpolate(0, 0.0F).equals(first), "a new path has no stale interpolation origin");
        path.setCandidate(0, new Point(0.5D, 66.0D, 2.5D));
        path.setCandidate(1, new Point(4.5D, 66.0D, 2.5D));
        world.blocks.put(new BlockPos(2, 69, 1), Blocks.GLASS.defaultBlockState());
        check(!path.advance(world.level, 1.45D), "movement rejects cover inside the swept area");
        check(path.interpolate(0, 1.0F).equals(first), "rejected motion preserves the last safe path");
        world.blocks.clear();
        check(path.advance(world.level, 1.45D), "motion resumes when the swept area is clear");
        check(path.interpolate(0, 0.5F).equals(new Point(0.5D, 66.0D, 1.5D)),
                "rendering interpolates between validated tick positions");
        int queries = world.queries;
        for (int frame = 0; frame < 144; frame++) {
            path.interpolate(0, frame / 144.0F);
            path.interpolate(1, frame / 144.0F);
        }
        check(world.queries == queries, "render interpolation performs no world queries");
        path.hold();
        check(path.interpolate(0, 0.0F).equals(path.interpolate(0, 1.0F)),
                "a held path does not replay its last movement");
        path.reset();
        path.setCandidate(0, new Point(8.5D, 66.0D, 0.5D));
        path.setCandidate(1, new Point(12.5D, 66.0D, 0.5D));
        world.blocks.put(new BlockPos(6, 69, 1), Blocks.STONE.defaultBlockState());
        check(path.advance(world.level, 1.45D), "reused slots do not sweep from an expired path");
    }

    private static void rechecksFadingPathsAfterWorldChanges() {
        TestWorld world = new TestWorld();
        WindStreakPath path = new WindStreakPath(1);
        path.setCandidate(0, new Point(0.5D, 66.0D, 0.5D));
        path.setCandidate(1, new Point(4.5D, 66.0D, 0.5D));
        check(path.advance(world.level, 1.45D), "fade regression starts with a clear path");
        path.hold();
        world.blocks.put(new BlockPos(2, 69, 0), Blocks.STONE.defaultBlockState());
        check(!path.advance(world.level, 1.45D), "a newly placed roof blocks a held path");
        check(!path.isValid(world.level, 1.45D), "fading paths cannot retain invalid geometry");
        world.blocks.clear();
        check(path.isValid(world.level, 1.45D), "removed cover is observed without stale shelter data");
        world.loaded = (x, z) -> false;
        check(!path.isValid(world.level, 1.45D), "chunk unloads invalidate existing paths");
    }

    private static void checksDebrisCollisionsAndOutdoorAccess() {
        TestWorld world = new TestWorld();
        check(WindParticleEnvironment.isValidLeafSpace(world.level, 0.5D, 66.0D, 0.5D, 0.78D),
                "outdoor debris can spawn");
        world.blocks.put(new BlockPos(0, 69, 0), Blocks.OAK_LEAVES.defaultBlockState());
        check(WindParticleEnvironment.isValidLeafSpace(world.level, 0.5D, 66.0D, 0.5D, 0.78D),
                "debris can drift under a canopy with open side access");
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.blocks.put(new BlockPos(x, 69, z), Blocks.GLASS.defaultBlockState());
                if (x != 0 || z != 0) {
                    world.blocks.put(new BlockPos(x, 66, z), Blocks.GLASS.defaultBlockState());
                }
            }
        }
        check(!WindParticleEnvironment.isValidLeafSpace(world.level, 0.5D, 66.0D, 0.5D, 0.0D),
                "bright enclosed rooms do not admit debris");
        world.blocks.clear();
        world.blocks.put(new BlockPos(1, 66, 0), Blocks.GLASS_PANE.defaultBlockState());
        Point destination = new Point(2.5D, 66.5D, 0.5D);
        check(WindParticleEnvironment.windPathBlocked(world.level, 0.5D, 66.5D, 0.5D, destination),
                "debris motion cannot tunnel through a thin collision shape");
        world.blocks.put(new BlockPos(1, 66, 0), Blocks.WATER.defaultBlockState());
        check(WindParticleEnvironment.lineHitsCollision(world.level,
                        new Vec3(0.5D, 66.5D, 0.5D), new Vec3(2.5D, 66.5D, 0.5D)),
                "debris paths collide with fluids between their endpoints");
        world.blocks.clear();
        world.loaded = (x, z) -> x != 1;
        check(WindParticleEnvironment.windPathBlocked(world.level, 32.5D, 66.5D, 0.5D,
                        new Point(0.5D, 66.5D, 0.5D)),
                "negative motion checks intermediate chunks before collision queries");
    }

    private static void usesClientHeightmaps() {
        TestWorld world = new TestWorld();
        check(WindParticleEnvironment.terrainHeight(world.level, 0.5D, 0.5D) == 64.0D,
                "fresh client chunks use the synchronized ground height");
        world.blocks.put(new BlockPos(0, 74, 0), Blocks.OAK_LEAVES.defaultBlockState());
        world.blocks.put(new BlockPos(0, 75, 0), Blocks.OAK_LEAVES.defaultBlockState());
        check(WindParticleEnvironment.terrainHeight(world.level, 0.5D, 0.5D) == 64.0D,
                "terrain sampling descends through leaf canopies and air gaps");
        world.blocks.put(new BlockPos(0, 65, 0), Blocks.WATER.defaultBlockState());
        check(WindParticleEnvironment.terrainHeight(world.level, 0.5D, 0.5D) == 66.0D,
                "fluid surfaces remain part of the terrain below leaves");
        world.blocks.put(new BlockPos(0, 69, 0), Blocks.GLASS.defaultBlockState());
        check(WindParticleEnvironment.terrainHeight(world.level, 0.5D, 0.5D) == 70.0D,
                "transparent solid roofs remain part of the terrain below leaves");
    }

    private static void keepsTerrainFlowFinite() {
        TestWorld world = new TestWorld();
        world.loaded = (x, z) -> x == 0 && z == 0;
        TerrainFlow edge = WindParticleEnvironment.terrainFlow(world.level, 15.5D, 66.0D, 15.5D,
                1.45D, 0.18D, 0.0D, 0.0D, 1.0D, 0.0D);
        check(Double.isFinite(edge.lift()) && Double.isFinite(edge.side()),
                "unloaded lookahead columns keep terrain motion finite");
        TerrainFlow missing = WindParticleEnvironment.terrainFlow(world.level, 16.5D, 66.0D, 0.5D,
                1.45D, 0.18D, 0.0D, 0.0D, 1.0D, 0.0D);
        check(missing.equals(new TerrainFlow(0.0D, 0.0D)), "unloaded origins cannot produce invalid velocities");
    }

    private static void refreshesColumnCaches() {
        TestWorld world = new TestWorld();
        AABB bounds = new AABB(0.2D, 66.0D, 0.2D, 0.8D, 66.2D, 0.8D);
        WindParticleEnvironment.beginTick(world.level);
        check(WindParticleEnvironment.isWindLineSpace(world.level, bounds, 1.45D), "initial outdoor column is open");
        int queries = world.heightQueries;
        for (int segment = 0; segment < 24; segment++) {
            WindParticleEnvironment.isWindLineSpace(world.level, bounds, 1.45D);
        }
        check(world.heightQueries == queries, "adjacent segments reuse column heights within a tick");
        world.blocks.put(new BlockPos(0, 70, 0), Blocks.GLASS.defaultBlockState());
        WindParticleEnvironment.beginTick(world.level);
        check(!WindParticleEnvironment.isWindLineSpace(world.level, bounds, 1.45D),
                "the next tick observes a newly placed roof");
        TestWorld other = new TestWorld();
        check(WindParticleEnvironment.isWindLineSpace(other.level, bounds, 1.45D),
                "column caches never cross worlds");
        world.blocks.clear();
        WindParticleEnvironment.beginTick(world.level);
        check(WindParticleEnvironment.isWindLineSpace(world.level, bounds, 1.45D),
                "removing a roof reopens the column on the next tick");
        world.loaded = (x, z) -> false;
        check(!WindParticleEnvironment.isWindLineSpace(world.level, bounds, 1.45D),
                "cached heights never bypass chunk residency");
        WindParticleEnvironment.clear();
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
        checks++;
    }

    private static final class TestWorld {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private BiPredicate<Integer, Integer> loaded = (x, z) -> true;
        private int queries;
        private int heightQueries;
        private final LevelReader level = (LevelReader) Proxy.newProxyInstance(
                LevelReader.class.getClassLoader(), new Class<?>[]{LevelReader.class},
                (proxy, method, arguments) -> {
                    queries++;
                    return switch (method.getName()) {
                        case "getMinBuildHeight" -> -64;
                        case "getHeight" -> arguments == null || arguments.length == 0 ? 384
                                : height((Heightmap.Types) arguments[0], (int) arguments[1], (int) arguments[2]);
                        case "hasChunk" -> loaded.test((int) arguments[0], (int) arguments[1]);
                        case "getBlockState" -> state((BlockPos) arguments[0]);
                        case "getFluidState" -> state((BlockPos) arguments[0]).getFluidState();
                        case "getBrightness" -> 15;
                        default -> InvocationHandler.invokeDefault(proxy, method, arguments);
                    };
                }
        );

        private BlockState state(BlockPos pos) {
            requireLoaded(pos.getX(), pos.getZ());
            return blocks.getOrDefault(pos, pos.getY() < 64 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }

        private int height(Heightmap.Types type, int x, int z) {
            heightQueries++;
            if (!type.sendToClient()) {
                throw new AssertionError("queried a heightmap that is not synchronized to clients: " + type);
            }
            requireLoaded(x, z);
            int height = 64;
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                if (pos.getX() == x && pos.getZ() == z && type.isOpaque().test(entry.getValue())) {
                    height = Math.max(height, pos.getY() + 1);
                }
            }
            return height;
        }

        private void requireLoaded(int x, int z) {
            if (!loaded.test(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                throw new AssertionError("world data queried in an unloaded chunk");
            }
        }
    }
}
