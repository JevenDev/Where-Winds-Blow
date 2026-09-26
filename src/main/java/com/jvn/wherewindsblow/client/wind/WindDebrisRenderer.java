package com.jvn.wherewindsblow.client.wind;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliage;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.FADE_OUT_TICKS;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.LEAF_TERRAIN_CLEARANCE;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.MAX_DISTANCE_FROM_PLAYER;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.Point;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.RANDOM;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.SPAWN_ATTEMPTS;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.TerrainFlow;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.WIND_SAMPLE_INTERVAL_TICKS;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.acceptSpawnForWind;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.alphaByte;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.isLeafBlockWindExposed;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.isValidLeafSpace;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.isValidWindSpace;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.keepAboveTerrainAndCollision;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.lerp;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.randomBetween;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.sampleWind;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.smoothFade;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.terrainFlow;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.terrainHeight;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.windBoost;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.windPathBlocked;

final class WindDebrisRenderer {
    private static final int MAX_LEAVES = 18;
    private static final int MAX_SPAWNS_PER_TICK = 2;
    private static final int FADE_IN_TICKS = 16;
    private static final int MAX_FLOWER_PETALS = 18;
    private static final int LEAF_TEXTURES_PER_TYPE = 12;
    private static final String[] LEAF_TEXTURE_TYPES = {
            "oak", "spruce", "birch", "jungle", "acacia",
            "dark_oak", "mangrove", "cherry", "azalea", "flowering_azalea"
    };
    private static final int LEAF_TEXTURE_COUNT = LEAF_TEXTURES_PER_TYPE * LEAF_TEXTURE_TYPES.length;
    private static final int ROSE_PETAL_TEXTURE_COUNT = 11;
    private static final int DANDELION_PETAL_TEXTURE_COUNT = 3;
    private static final int AZURE_BLUET_PETAL_TEXTURE_COUNT = 6;
    private static final int FLOWER_PETAL_TEXTURE_COUNT = ROSE_PETAL_TEXTURE_COUNT
            + DANDELION_PETAL_TEXTURE_COUNT
            + AZURE_BLUET_PETAL_TEXTURE_COUNT;
    private static final int LEAF_BLOCK_SPAWN_ATTEMPTS = 22;
    private static final double LEAF_TERRAIN_LIFT_STRENGTH = 0.28D;
    private static final WindLeaf[] LEAVES = new WindLeaf[MAX_LEAVES];
    private static final WindLeaf[] FLOWER_PETALS = new WindLeaf[MAX_FLOWER_PETALS];
    private static final RenderType[] LEAF_RENDER_TYPES = new RenderType[LEAF_TEXTURE_COUNT];
    private static final RenderType[] FLOWER_PETAL_RENDER_TYPES = new RenderType[FLOWER_PETAL_TEXTURE_COUNT];
    private static final ByteBufferBuilder LEAF_BUFFER = new ByteBufferBuilder(65536);
    private static final RenderStateShard.ShaderStateShard PARTICLE_SHADER =
            new RenderStateShard.ShaderStateShard(GameRenderer::getParticleShader);

    static {
        for (int index = 0; index < LEAVES.length; index++) {
            LEAVES[index] = new WindLeaf();
        }

        for (int index = 0; index < FLOWER_PETALS.length; index++) {
            FLOWER_PETALS[index] = new WindLeaf();
        }

        for (int index = 0; index < LEAF_RENDER_TYPES.length; index++) {
            LEAF_RENDER_TYPES[index] = windLeafTexture(leafTexture(index));
        }

        for (int index = 0; index < FLOWER_PETAL_RENDER_TYPES.length; index++) {
            FLOWER_PETAL_RENDER_TYPES[index] = windLeafTexture(flowerPetalTexture(index));
        }
    }

    private WindDebrisRenderer() {
    }

    static void tick(ClientLevel level, LocalPlayer player, WindSample playerWind, boolean canSpawnWind) {
        int desiredLeafCount = canSpawnWind ? desiredLeafCount(playerWind) : 0;
        boolean canSpawnFlowerPetals = canSpawnWind
                && ClientConfig.ENABLE_WIND_FLOWER_PETALS.getAsBoolean()
                && isFlowerPetalBiome(level, player.blockPosition());
        int desiredFlowerPetalCount = canSpawnFlowerPetals ? desiredFlowerPetalCount(playerWind) : 0;
        int spawnBudget = MAX_SPAWNS_PER_TICK;
        for (int index = 0; index < LEAVES.length; index++) {
            WindLeaf leaf = LEAVES[index];
            if (index >= desiredLeafCount) {
                beginFadeOut(leaf);
            }

            if (leaf.active) {
                tick(leaf, player, level);
            } else if (index < desiredLeafCount && --leaf.spawnCountdown <= 0 && spawnBudget > 0) {
                spawnBudget--;
                spawn(leaf, player, level, playerWind, true);
                leaf.spawnCountdown = leaf.active ? 0 : 8 + RANDOM.nextInt(17);
            }
        }

        for (int index = 0; index < FLOWER_PETALS.length; index++) {
            WindLeaf petal = FLOWER_PETALS[index];
            if (index >= desiredFlowerPetalCount) {
                beginFadeOut(petal);
            }

            if (petal.active) {
                tick(petal, player, level);
            } else if (index < desiredFlowerPetalCount && --petal.spawnCountdown <= 0 && spawnBudget > 0) {
                spawnBudget--;
                spawnFlowerPetal(petal, player, level, playerWind, true);
                petal.spawnCountdown = petal.active ? 0 : 8 + RANDOM.nextInt(17);
            }
        }
    }

    static void clear() {
        for (WindLeaf leaf : LEAVES) {
            leaf.active = false;
            leaf.fadingOut = false;
            leaf.fadeOutAge = 0;
            leaf.spawnCountdown = 0;
        }

        for (WindLeaf petal : FLOWER_PETALS) {
            petal.active = false;
            petal.fadingOut = false;
            petal.fadeOutAge = 0;
            petal.spawnCountdown = 0;
        }
    }

    static boolean hasActiveParticles() {
        for (WindLeaf leaf : LEAVES) {
            if (leaf.active) {
                return true;
            }
        }

        for (WindLeaf petal : FLOWER_PETALS) {
            if (petal.active) {
                return true;
            }
        }

        return false;
    }

    private static int desiredLeafCount(WindSample wind) {
        if (!ClientConfig.ENABLE_WIND_LEAVES.getAsBoolean() || ClientConfig.WIND_LEAF_OPACITY.getAsDouble() <= 0.0D) {
            return 0;
        }

        float visibility = (float) ClientConfig.WIND_STREAK_VISIBILITY.getAsDouble();
        if (visibility <= 0.0F || wind.strength() <= 0.02F) {
            return 0;
        }

        float leafDensity = (float) ClientConfig.WIND_LEAF_DENSITY.getAsDouble();
        return Mth.clamp(
                Math.round((1.0F + wind.strength() * 6.0F + wind.gustStrength() * 5.0F)
                        * Math.min(visibility, 1.8F) * leafDensity),
                0,
                MAX_LEAVES
        );
    }

    private static int desiredFlowerPetalCount(WindSample wind) {
        if (ClientConfig.WIND_FLOWER_PETAL_OPACITY.getAsDouble() <= 0.0D) {
            return 0;
        }
        float visibility = (float) ClientConfig.WIND_STREAK_VISIBILITY.getAsDouble();
        if (visibility <= 0.0F || wind.strength() <= 0.02F) {
            return 0;
        }

        float petalDensity = (float) ClientConfig.WIND_FLOWER_PETAL_DENSITY.getAsDouble();
        return Mth.clamp(
                Math.round((1.0F + wind.strength() * 5.0F + wind.gustStrength() * 6.0F)
                        * Math.min(visibility, 1.8F) * petalDensity),
                0,
                MAX_FLOWER_PETALS
        );
    }

    private static boolean isFlowerPetalBiome(ClientLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        return !biome.is(Tags.Biomes.IS_SNOWY)
                && (biome.is(Tags.Biomes.IS_PLAINS) || biome.is(Tags.Biomes.IS_FLORAL));
    }

    private static ResourceLocation leafTexture(int index) {
        int typeIndex = index / LEAF_TEXTURES_PER_TYPE;
        int variant = index % LEAF_TEXTURES_PER_TYPE;
        String path = "textures/particle/leaves/" + LEAF_TEXTURE_TYPES[typeIndex] + "_" + variant + ".png";
        return WhereWindsBlow.IDS.id(path);
    }

    private static ResourceLocation flowerPetalTexture(int index) {
        String path;
        if (index < ROSE_PETAL_TEXTURE_COUNT) {
            path = "textures/particle/rose_petal_" + index + ".png";
        } else {
            int flowerIndex = index - ROSE_PETAL_TEXTURE_COUNT;
            path = flowerIndex < DANDELION_PETAL_TEXTURE_COUNT
                    ? "textures/particle/dandelion_" + flowerIndex + ".png"
                    : "textures/particle/azure_bluet_"
                            + (flowerIndex - DANDELION_PETAL_TEXTURE_COUNT) + ".png";
        }
        return WhereWindsBlow.IDS.id(path);
    }

    private static RenderType windLeafTexture(ResourceLocation texture) {
        return RenderType.create(
                "where_winds_blow_wind_leaf_" + texture.getPath().replace('/', '_').replace('.', '_'),
                DefaultVertexFormat.PARTICLE,
                VertexFormat.Mode.QUADS,
                65536,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(PARTICLE_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false)
        );
    }

    private static void tick(WindLeaf leaf, LocalPlayer player, ClientLevel level) {
        leaf.xOld = leaf.x;
        leaf.yOld = leaf.y;
        leaf.zOld = leaf.z;
        leaf.age++;
        if (leaf.age >= leaf.lifetime || !isValidLeafSpace(level, leaf.x, leaf.y, leaf.z, 0.0D)) {
            leaf.active = false;
            return;
        }
        double dx = leaf.x - player.getX();
        double dy = leaf.y - player.getEyeY();
        double dz = leaf.z - player.getZ();
        if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_FROM_PLAYER * MAX_DISTANCE_FROM_PLAYER) {
            beginFadeOut(leaf);
        }
        if (leaf.fadingOut) {
            if (++leaf.fadeOutAge >= FADE_OUT_TICKS) {
                leaf.active = false;
                return;
            }
            leaf.velocityX *= 0.86D;
            leaf.velocityY *= 0.86D;
            leaf.velocityZ *= 0.86D;
        } else {
            leaf.fadeInAge = Math.min(leaf.fadeInAge + 1, FADE_IN_TICKS);
            refreshWind(leaf, level);
            updateVelocity(leaf, level);
        }
        Point next = new Point(leaf.x + leaf.velocityX, leaf.y + leaf.velocityY, leaf.z + leaf.velocityZ);
        if (windPathBlocked(level, leaf.x, leaf.y, leaf.z, next)) {
            beginFadeOut(leaf);
            leaf.velocityX = 0.0D;
            leaf.velocityY = 0.0D;
            leaf.velocityZ = 0.0D;
        } else {
            leaf.x = next.x();
            leaf.y = next.y();
            leaf.z = next.z();
        }
        leaf.light = LevelRenderer.getLightColor(level, BlockPos.containing(leaf.x, leaf.y, leaf.z));
    }

    private static void updateVelocity(WindLeaf leaf, ClientLevel level) {
        double motionScale = Mth.clamp(0.42D + leaf.windStrength * 1.18D + leaf.gustStrength * 0.78D, 0.2D, 3.25D);
        double speed = leaf.speed * motionScale;
        double leafTime = (double) leaf.age + leaf.seed;
        double turbulenceMotion = 0.72D + Mth.clamp(leaf.turbulence * 3.2D, 0.0D, 2.1D);
        double weave = (Math.sin(leafTime * 0.09D)
                + Math.sin(leaf.swirlPhase + leafTime * 0.027D) * 0.46D) * leaf.crossDrift * turbulenceMotion;
        double swirlAngle = leaf.swirlPhase + leafTime * leaf.swirlSpeed;
        double loopAngle = leaf.loopPhase + leafTime * leaf.loopSpeed;
        double swirl = Math.sin(swirlAngle) * leaf.swirlStrength * turbulenceMotion;
        double loop = Math.sin(loopAngle) * leaf.loopStrength * turbulenceMotion;
        double loopLift = Math.cos(loopAngle) * leaf.loopStrength * 0.72D * turbulenceMotion;
        TerrainFlow flow = terrainFlow(
                level, leaf.x, leaf.y, leaf.z, LEAF_TERRAIN_CLEARANCE,
                LEAF_TERRAIN_LIFT_STRENGTH, leaf.terrainLift, leaf.terrainSideFlow,
                leaf.windX, leaf.windZ
        );
        leaf.terrainLift = flow.lift();
        leaf.terrainSideFlow = flow.side();
        double flutter = (Math.sin(leafTime * 0.084D)
                + Math.sin(leaf.flipPhase + leafTime * 0.17D) * 0.54D) * leaf.verticalDrift * turbulenceMotion;
        double bobSide = (Math.sin(leafTime * 0.16D) - Math.sin((leafTime - 1.0D) * 0.16D)) * leaf.bob;
        double bobLift = (Math.cos(leafTime * 0.13D) - Math.cos((leafTime - 1.0D) * 0.13D)) * leaf.bob;
        double forwardPulse = Math.cos(swirlAngle) * leaf.swirlStrength * 0.16D;
        double sideDrift = weave + swirl + loop + leaf.terrainSideFlow + bobSide * turbulenceMotion;
        double lullDescent = Mth.lerp(Mth.clamp(leaf.windStrength / 0.32D, 0.0D, 1.0D), 1.72D, 0.82D);
        double vertical = leaf.terrainLift + flutter + Math.cos(swirlAngle) * leaf.swirlStrength * 0.42D
                + loopLift + bobLift - leaf.fallSpeed * lullDescent;
        leaf.velocityX = Mth.lerp(0.24D, leaf.velocityX, leaf.windX * (speed + forwardPulse) + leaf.crossX * sideDrift);
        leaf.velocityY = Mth.lerp(0.24D, leaf.velocityY, vertical);
        leaf.velocityZ = Mth.lerp(0.24D, leaf.velocityZ, leaf.windZ * (speed + forwardPulse) + leaf.crossZ * sideDrift);
    }

    private static void spawn(WindLeaf leaf, LocalPlayer player, ClientLevel level, WindSample playerWind, boolean scatterAge) {
        LeafBlockSpawn leafBlockSpawn = RANDOM.nextDouble() < 0.58D
                ? randomLeafBlockSpawn(player, level, playerWind)
                : null;
        Point spawn = leafBlockSpawn != null ? leafBlockSpawn.position() : randomOpenAirPosition(
                player,
                level,
                playerWind,
                -26.0D,
                18.0D,
                -28.0D,
                28.0D,
                -0.8D,
                6.2D,
                0.3D,
                1.8D,
                LEAF_TERRAIN_CLEARANCE,
                0.05D,
                1.15D
        );
        if (spawn == null) {
            leaf.active = false;
            return;
        }

        WindSample localWind = sampleWind(level, spawn.x(), spawn.y(), spawn.z());
        if (!acceptSpawnForWind(localWind)) {
            leaf.active = false;
            return;
        }
        applyWind(leaf, localWind, true);
        double windBoost = windBoost(localWind);

        leaf.x = spawn.x();
        leaf.y = spawn.y();
        leaf.z = spawn.z();
        leaf.xOld = leaf.x;
        leaf.yOld = leaf.y;
        leaf.zOld = leaf.z;
        BlockPos leafColorPos = leafBlockSpawn != null
                ? leafBlockSpawn.source()
                : BlockPos.containing(leaf.x, player.getY(), leaf.z);
        BlockState sourceLeafState = leafBlockSpawn != null
                ? level.getBlockState(leafBlockSpawn.source())
                : level.getBiome(leafColorPos).is(Tags.Biomes.IS_SAVANNA)
                        ? Blocks.ACACIA_LEAVES.defaultBlockState()
                        : Blocks.OAK_LEAVES.defaultBlockState();
        int leafType = leafTextureType(sourceLeafState);
        leaf.textureIndex = leafType * LEAF_TEXTURES_PER_TYPE + RANDOM.nextInt(LEAF_TEXTURES_PER_TYPE);
        boolean fromLeafBlock = leafBlockSpawn != null;
        leaf.size = randomBetween(fromLeafBlock ? 0.08D : 0.09D, fromLeafBlock ? 0.19D : 0.23D) * (1.0D + windBoost * 0.12D);
        leaf.speed = randomBetween(fromLeafBlock ? 0.095D : 0.16D, fromLeafBlock ? 0.235D : 0.31D);
        leaf.crossDrift = randomBetween(fromLeafBlock ? -0.028D : -0.018D, fromLeafBlock ? 0.028D : 0.018D) * (1.0D + localWind.turbulence() * 2.1D);
        leaf.verticalDrift = randomBetween(fromLeafBlock ? 0.006D : 0.003D, fromLeafBlock ? 0.017D : 0.012D) * (1.0D + localWind.turbulence() * 1.2D);
        leaf.fallSpeed = randomBetween(fromLeafBlock ? 0.012D : 0.002D, fromLeafBlock ? 0.032D : 0.009D);
        leaf.bob = randomBetween(fromLeafBlock ? 0.032D : 0.025D, fromLeafBlock ? 0.088D : 0.075D) * (1.0D + localWind.turbulence());
        leaf.swirlStrength = randomBetween(fromLeafBlock ? 0.012D : 0.006D, fromLeafBlock ? 0.048D : 0.035D) * (1.0D + localWind.turbulence() * 2.4D);
        leaf.swirlSpeed = randomBetween(0.105D, 0.225D) * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        leaf.swirlPhase = RANDOM.nextDouble() * Math.PI * 2.0D;
        leaf.loopStrength = (RANDOM.nextDouble() < 0.44D ? randomBetween(0.018D, 0.065D) : randomBetween(0.0D, 0.018D))
                * (1.0D + localWind.turbulence() * 2.7D);
        leaf.loopSpeed = randomBetween(0.072D, 0.152D) * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        leaf.loopPhase = RANDOM.nextDouble() * Math.PI * 2.0D;
        leaf.terrainLift = 0.0D;
        leaf.terrainSideFlow = 0.0D;
        leaf.tilt = randomBetween(-Math.PI, Math.PI);
        leaf.tiltSpeed = randomBetween(0.045D, 0.145D) * (1.0D + localWind.turbulence() * 1.8D)
                * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        leaf.flipPhase = RANDOM.nextDouble() * Math.PI * 2.0D;
        leaf.flipSpeed = randomBetween(0.115D, 0.265D) * (1.0D + localWind.turbulence() * 1.6D)
                * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        leaf.tumbleStrength = randomBetween(0.12D, 0.42D) * (1.0D + localWind.turbulence() * 2.0D);
        leaf.seed = RANDOM.nextDouble() * Math.PI * 2.0D;
        leaf.lifetime = Math.max(64, Math.round((94 + RANDOM.nextInt(70)) / (float) (1.0D + windBoost * 0.28D)));
        leaf.age = scatterAge ? RANDOM.nextInt(Math.max(1, leaf.lifetime / 2)) : 0;
        leaf.fadingOut = false;
        leaf.fadeOutAge = 0;
        leaf.fadeInAge = 0;
        leaf.velocityX = leaf.windX * leaf.speed;
        leaf.velocityY = -leaf.fallSpeed;
        leaf.velocityZ = leaf.windZ * leaf.speed;
        leaf.light = LevelRenderer.getLightColor(level, BlockPos.containing(leaf.x, leaf.y, leaf.z));
        int color = sampleLeafColor(level, sourceLeafState, leafColorPos);
        leaf.red = color >> 16 & 255;
        leaf.green = color >> 8 & 255;
        leaf.blue = color & 255;
        leaf.active = true;
    }

    private static void spawnFlowerPetal(
            WindLeaf petal,
            LocalPlayer player,
            ClientLevel level,
            WindSample playerWind,
            boolean scatterAge
    ) {
        Point spawn = randomOpenAirPosition(
                player, level, playerWind,
                -26.0D, 18.0D, -28.0D, 28.0D, -0.4D, 6.4D,
                0.35D, 1.9D, LEAF_TERRAIN_CLEARANCE, 0.05D, 1.2D
        );
        if (spawn == null) {
            petal.active = false;
            return;
        }

        BlockPos spawnPos = BlockPos.containing(spawn.x(), spawn.y(), spawn.z());
        if (!isFlowerPetalBiome(level, spawnPos)) {
            petal.active = false;
            return;
        }

        WindSample localWind = sampleWind(level, spawn.x(), spawn.y(), spawn.z());
        if (!acceptSpawnForWind(localWind)) {
            petal.active = false;
            return;
        }
        applyWind(petal, localWind, true);
        double windBoost = windBoost(localWind);

        petal.x = spawn.x();
        petal.y = spawn.y();
        petal.z = spawn.z();
        petal.xOld = petal.x;
        petal.yOld = petal.y;
        petal.zOld = petal.z;
        var biome = level.getBiome(spawnPos);
        double roseChance = biome.is(Tags.Biomes.IS_FLOWER_FOREST)
                ? 0.62D
                : biome.is(Tags.Biomes.IS_FLORAL) ? 0.44D : 0.28D;
        double azureBluetChance = biome.is(Tags.Biomes.IS_FLOWER_FOREST)
                ? 0.24D
                : biome.is(Tags.Biomes.IS_FLORAL) ? 0.34D : 0.30D;
        double textureRoll = RANDOM.nextDouble();
        if (textureRoll < roseChance) {
            petal.textureIndex = RANDOM.nextInt(ROSE_PETAL_TEXTURE_COUNT);
        } else if (textureRoll < roseChance + azureBluetChance) {
            petal.textureIndex = ROSE_PETAL_TEXTURE_COUNT
                    + DANDELION_PETAL_TEXTURE_COUNT
                    + RANDOM.nextInt(AZURE_BLUET_PETAL_TEXTURE_COUNT);
        } else {
            petal.textureIndex = ROSE_PETAL_TEXTURE_COUNT
                    + RANDOM.nextInt(DANDELION_PETAL_TEXTURE_COUNT);
        }
        petal.size = randomBetween(0.12D, 0.23D) * (1.0D + windBoost * 0.08D);
        petal.speed = randomBetween(0.14D, 0.29D);
        petal.crossDrift = randomBetween(-0.026D, 0.026D) * (1.0D + localWind.turbulence() * 2.5D);
        petal.verticalDrift = randomBetween(0.006D, 0.018D) * (1.0D + localWind.turbulence() * 1.6D);
        petal.fallSpeed = randomBetween(0.003D, 0.012D);
        petal.bob = randomBetween(0.035D, 0.095D) * (1.0D + localWind.turbulence() * 1.2D);
        petal.swirlStrength = randomBetween(0.012D, 0.052D) * (1.0D + localWind.turbulence() * 2.8D);
        petal.swirlSpeed = randomBetween(0.12D, 0.26D) * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        petal.swirlPhase = RANDOM.nextDouble() * Math.PI * 2.0D;
        petal.loopStrength = randomBetween(0.012D, 0.07D) * (1.0D + localWind.turbulence() * 2.9D);
        petal.loopSpeed = randomBetween(0.085D, 0.18D) * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        petal.loopPhase = RANDOM.nextDouble() * Math.PI * 2.0D;
        petal.terrainLift = 0.0D;
        petal.terrainSideFlow = 0.0D;
        petal.tilt = randomBetween(-Math.PI, Math.PI);
        petal.tiltSpeed = randomBetween(0.075D, 0.19D) * (1.0D + localWind.turbulence() * 2.0D)
                * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        petal.flipPhase = RANDOM.nextDouble() * Math.PI * 2.0D;
        petal.flipSpeed = randomBetween(0.17D, 0.34D) * (1.0D + localWind.turbulence() * 1.8D)
                * (RANDOM.nextBoolean() ? 1.0D : -1.0D);
        petal.tumbleStrength = randomBetween(0.2D, 0.56D) * (1.0D + localWind.turbulence() * 2.2D);
        petal.seed = RANDOM.nextDouble() * Math.PI * 2.0D;
        petal.lifetime = Math.max(58, Math.round((82 + RANDOM.nextInt(66)) / (float) (1.0D + windBoost * 0.25D)));
        petal.age = scatterAge ? RANDOM.nextInt(Math.max(1, petal.lifetime / 2)) : 0;
        petal.fadingOut = false;
        petal.fadeOutAge = 0;
        petal.fadeInAge = 0;
        petal.velocityX = petal.windX * petal.speed;
        petal.velocityY = -petal.fallSpeed;
        petal.velocityZ = petal.windZ * petal.speed;
        petal.light = LevelRenderer.getLightColor(level, BlockPos.containing(petal.x, petal.y, petal.z));
        petal.red = 255;
        petal.green = 255;
        petal.blue = 255;
        petal.active = true;
    }

    private static LeafBlockSpawn randomLeafBlockSpawn(LocalPlayer player, ClientLevel level, WindSample playerWind) {
        BlockPos.MutableBlockPos leafPos = new BlockPos.MutableBlockPos();
        int baseY = Mth.floor(player.getY());
        for (int attempt = 0; attempt < LEAF_BLOCK_SPAWN_ATTEMPTS; attempt++) {
            double along = randomBetween(-22.0D, 18.0D);
            double across = randomBetween(-24.0D, 24.0D);
            int x = Mth.floor(player.getX() + playerWind.directionX() * along + playerWind.crossX() * across);
            int z = Mth.floor(player.getZ() + playerWind.directionZ() * along + playerWind.crossZ() * across);
            if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                continue;
            }
            int startY = Math.min(level.getMaxBuildHeight() - 1, baseY + 11 - RANDOM.nextInt(4));
            int endY = Math.max(level.getMinBuildHeight(), baseY - 2);

            for (int y = startY; y >= endY; y--) {
                leafPos.set(x, y, z);
                BlockState state = level.getBlockState(leafPos);
                if (!ResponsiveFoliage.isLeaf(state) || !isLeafBlockWindExposed(level, leafPos)) {
                    continue;
                }

                LeafBlockSpawn spawn = spawnNearLeafBlock(level, leafPos);
                if (spawn != null) {
                    return spawn;
                }
            }
        }

        return null;
    }

    private static LeafBlockSpawn spawnNearLeafBlock(ClientLevel level, BlockPos source) {
        WindSample wind = DynamicWindManager.sampleWind(level, source);
        for (int attempt = 0; attempt < 4; attempt++) {
            double side = randomBetween(-0.34D, 0.34D);
            double outward = randomBetween(0.34D, 0.72D);
            double downward = RANDOM.nextDouble() < 0.72D ? randomBetween(0.1D, 0.86D) : randomBetween(-0.08D, 0.22D);
            double x = source.getX() + 0.5D + wind.directionX() * outward + wind.crossX() * side;
            double y = source.getY() + 0.92D - downward;
            double z = source.getZ() + 0.5D + wind.directionZ() * outward + wind.crossZ() * side;
            Point spawn = keepAboveTerrainAndCollision(level, x, y, z, LEAF_TERRAIN_CLEARANCE);
            if (isValidLeafSpace(level, spawn.x(), spawn.y(), spawn.z(), LEAF_TERRAIN_CLEARANCE)) {
                return new LeafBlockSpawn(spawn, source.immutable());
            }
        }

        return null;
    }

    private static void beginFadeOut(WindLeaf leaf) {
        if (!leaf.active || leaf.fadingOut) {
            return;
        }

        leaf.fadingOut = true;
        leaf.fadeOutAge = 0;
    }

    private static float fadeOutMultiplier(WindLeaf leaf, float partialTick) {
        if (!leaf.fadingOut) {
            return 1.0F;
        }

        float remaining = 1.0F - Mth.clamp(((float) leaf.fadeOutAge + partialTick) / (float) FADE_OUT_TICKS, 0.0F, 1.0F);
        return smoothFade(remaining);
    }

    private static Point randomOpenAirPosition(
            LocalPlayer player,
            ClientLevel level,
            WindSample wind,
            double minAlong,
            double maxAlong,
            double minAcross,
            double maxAcross,
            double minEyeOffset,
            double maxEyeOffset,
            double minPlayerClearance,
            double maxPlayerClearance,
            double terrainClearance,
            double minTerrainExtraClearance,
            double maxTerrainExtraClearance
    ) {
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double along = randomBetween(minAlong, maxAlong);
            double across = randomBetween(minAcross, maxAcross);
            double x = player.getX() + wind.directionX() * along + wind.crossX() * across;
            double y = player.getEyeY() + randomBetween(minEyeOffset, maxEyeOffset);
            double z = player.getZ() + wind.directionZ() * along + wind.crossZ() * across;
            if (y < player.getY() + minPlayerClearance) {
                y = player.getY() + randomBetween(minPlayerClearance, maxPlayerClearance);
            }

            double terrainY = terrainHeight(level, x, z);
            if (!Double.isFinite(terrainY)) {
                continue;
            }
            y = Math.max(y, terrainY + terrainClearance
                    + randomBetween(minTerrainExtraClearance, maxTerrainExtraClearance));
            Point spawn = keepAboveTerrainAndCollision(level, x, y, z, terrainClearance);
            if (isValidWindSpace(level, spawn.x(), spawn.y(), spawn.z(), terrainClearance)) {
                return spawn;
            }
        }

        return null;
    }

    static void renderLeaves(
            Camera camera,
            Matrix4f pose,
            Vec3 cameraPos,
            float partialTick,
            double windTime,
            float opacity
    ) {
        renderWindParticles(
                camera, pose, cameraPos, partialTick, windTime, opacity,
                LEAVES, LEAF_RENDER_TYPES
        );
    }

    static void renderFlowerPetals(
            Camera camera,
            Matrix4f pose,
            Vec3 cameraPos,
            float partialTick,
            double windTime,
            float opacity
    ) {
        renderWindParticles(
                camera, pose, cameraPos, partialTick, windTime, opacity,
                FLOWER_PETALS, FLOWER_PETAL_RENDER_TYPES
        );
    }

    private static void renderWindParticles(
            Camera camera,
            Matrix4f pose,
            Vec3 cameraPos,
            float partialTick,
            double windTime,
            float opacity,
            WindLeaf[] particles,
            RenderType[] renderTypes
    ) {
        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(LEAF_BUFFER);
        for (int textureIndex = 0; textureIndex < renderTypes.length; textureIndex++) {
            VertexConsumer consumer = null;
            for (WindLeaf particle : particles) {
                if (particle.active && particle.textureIndex == textureIndex) {
                    if (consumer == null) {
                        consumer = bufferSource.getBuffer(renderTypes[textureIndex]);
                    }
                    renderLeaf(
                            consumer, pose, particle, camera, cameraPos,
                            partialTick, windTime, opacity
                    );
                }
            }
        }
        bufferSource.endBatch();
    }

    private static void renderLeaf(
            VertexConsumer consumer,
            Matrix4f pose,
            WindLeaf leaf,
            Camera camera,
            Vec3 cameraPos,
            float partialTick,
            double windTime,
            float opacity
    ) {
        float life = ((float) leaf.age + partialTick) / (float) leaf.lifetime;
        float fade = smoothFade(Mth.clamp((leaf.fadeInAge + (leaf.fadingOut ? 0.0F : partialTick)) / FADE_IN_TICKS, 0.0F, 1.0F))
                * smoothFade(Mth.clamp((1.0F - life) / 0.36F, 0.0F, 1.0F));
        if (fade <= 0.01F) {
            return;
        }

        double centerX = lerp(partialTick, leaf.xOld, leaf.x);
        double centerY = lerp(partialTick, leaf.yOld, leaf.y);
        double centerZ = lerp(partialTick, leaf.zOld, leaf.z);
        double leafTime = (double) leaf.age + partialTick + leaf.seed;

        double dx = centerX - cameraPos.x();
        double dy = centerY - cameraPos.y();
        double dz = centerZ - cameraPos.z();
        double cameraDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float distanceFade = smoothFade(Mth.clamp((float) ((MAX_DISTANCE_FROM_PLAYER - cameraDistance) / 18.0D), 0.0F, 1.0F));
        float alpha = 0.95F * opacity * fade * distanceFade
                * fadeOutMultiplier(leaf, partialTick);
        if (alpha <= 0.006F) {
            return;
        }

        double liveTumble = 0.72D + Mth.clamp(leaf.turbulence * 3.0D, 0.0D, 1.9D);
        double flip = Math.cos(
                leaf.flipPhase + leafTime * leaf.flipSpeed
                        + Math.sin(leaf.swirlPhase + leafTime * 0.083D) * leaf.tumbleStrength * liveTumble
        );
        float tilt = (float) (leaf.tilt + leafTime * leaf.tiltSpeed
                + Math.sin(leaf.seed + windTime * 0.9D) * 0.18D
                + Math.sin(leaf.loopPhase + leafTime * leaf.loopSpeed) * leaf.tumbleStrength * liveTumble);
        float halfSize = (float) (leaf.size * (0.5D + Math.sin(leaf.seed * 1.3D + leafTime * 0.11D) * 0.035D));
        float flipScale = (float) Mth.clamp(0.18D + Math.abs(flip) * 0.82D, 0.18D, 1.0D);
        float stretchScale = (float) Mth.clamp(
                1.0D + Math.sin(leaf.swirlPhase + leafTime * leaf.swirlSpeed) * 0.13D,
                0.86D,
                1.14D
        );
        int alphaByte = alphaByte(alpha);
        int light = leaf.light;
        emitLeafQuad(
                consumer,
                pose,
                camera,
                cameraPos,
                centerX,
                centerY,
                centerZ,
                halfSize,
                tilt,
                flipScale,
                stretchScale,
                leaf.red,
                leaf.green,
                leaf.blue,
                alphaByte,
                light
        );
    }

    private static void emitLeafQuad(
            VertexConsumer consumer,
            Matrix4f pose,
            Camera camera,
            Vec3 cameraPos,
            double centerX,
            double centerY,
            double centerZ,
            float halfSize,
            float tilt,
            float flipScale,
            float stretchScale,
            int red,
            int green,
            int blue,
            int alpha,
            int light
    ) {
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        float tiltCos = Mth.cos(tilt);
        float tiltSin = Mth.sin(tilt);
        double axisXx = left.x() * tiltCos + up.x() * tiltSin;
        double axisXy = left.y() * tiltCos + up.y() * tiltSin;
        double axisXz = left.z() * tiltCos + up.z() * tiltSin;
        double axisYx = up.x() * tiltCos - left.x() * tiltSin;
        double axisYy = up.y() * tiltCos - left.y() * tiltSin;
        double axisYz = up.z() * tiltCos - left.z() * tiltSin;
        double halfWidth = halfSize * flipScale;
        double halfHeight = halfSize * stretchScale;

        emitLeafVertex(
                consumer,
                pose,
                cameraPos,
                centerX - axisXx * halfWidth - axisYx * halfHeight,
                centerY - axisXy * halfWidth - axisYy * halfHeight,
                centerZ - axisXz * halfWidth - axisYz * halfHeight,
                0.0F,
                1.0F,
                red,
                green,
                blue,
                alpha,
                light
        );
        emitLeafVertex(
                consumer,
                pose,
                cameraPos,
                centerX + axisXx * halfWidth - axisYx * halfHeight,
                centerY + axisXy * halfWidth - axisYy * halfHeight,
                centerZ + axisXz * halfWidth - axisYz * halfHeight,
                1.0F,
                1.0F,
                red,
                green,
                blue,
                alpha,
                light
        );
        emitLeafVertex(
                consumer,
                pose,
                cameraPos,
                centerX + axisXx * halfWidth + axisYx * halfHeight,
                centerY + axisXy * halfWidth + axisYy * halfHeight,
                centerZ + axisXz * halfWidth + axisYz * halfHeight,
                1.0F,
                0.0F,
                red,
                green,
                blue,
                alpha,
                light
        );
        emitLeafVertex(
                consumer,
                pose,
                cameraPos,
                centerX - axisXx * halfWidth + axisYx * halfHeight,
                centerY - axisXy * halfWidth + axisYy * halfHeight,
                centerZ - axisXz * halfWidth + axisYz * halfHeight,
                0.0F,
                0.0F,
                red,
                green,
                blue,
                alpha,
                light
        );
    }

    private static void emitLeafVertex(
            VertexConsumer consumer,
            Matrix4f pose,
            Vec3 cameraPos,
            double x,
            double y,
            double z,
            float u,
            float v,
            int red,
            int green,
            int blue,
            int alpha,
            int light
    ) {
        consumer.addVertex(pose, (float) (x - cameraPos.x()), (float) (y - cameraPos.y()), (float) (z - cameraPos.z()))
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setLight(light);
    }

    private static int leafTextureType(BlockState state) {
        if (state.is(Blocks.SPRUCE_LEAVES)) {
            return 1;
        }
        if (state.is(Blocks.BIRCH_LEAVES)) {
            return 2;
        }
        if (state.is(Blocks.JUNGLE_LEAVES)) {
            return 3;
        }
        if (state.is(Blocks.ACACIA_LEAVES)) {
            return 4;
        }
        if (state.is(Blocks.DARK_OAK_LEAVES)) {
            return 5;
        }
        if (state.is(Blocks.MANGROVE_LEAVES)) {
            return 6;
        }
        if (state.is(Blocks.CHERRY_LEAVES)) {
            return 7;
        }
        if (state.is(Blocks.AZALEA_LEAVES)) {
            return 8;
        }
        if (state.is(Blocks.FLOWERING_AZALEA_LEAVES)) {
            return 9;
        }
        return 0;
    }

    private static int sampleLeafColor(ClientLevel level, BlockState state, BlockPos pos) {
        int color = Minecraft.getInstance().getBlockColors().getColor(
                state,
                level,
                pos,
                0
        );
        return color == -1 ? 0xFFFFFF : color;
    }

    private static void refreshWind(WindLeaf leaf, ClientLevel level) {
        if (--leaf.windSampleCountdown > 0) {
            return;
        }

        applyWind(leaf, sampleWind(level, leaf.x, leaf.y, leaf.z), false);
    }

    private static void applyWind(WindLeaf leaf, WindSample wind, boolean immediate) {
        double blend = immediate ? 1.0D : 0.42D;
        double x = Mth.lerp(blend, leaf.windX, wind.directionX());
        double z = Mth.lerp(blend, leaf.windZ, wind.directionZ());
        double length = Math.sqrt(x * x + z * z);
        if (length > 0.0001D) {
            x /= length;
            z /= length;
        }
        leaf.windX = x;
        leaf.windZ = z;
        leaf.crossX = -z;
        leaf.crossZ = x;
        leaf.windStrength = Mth.lerp(blend, leaf.windStrength, wind.strength());
        leaf.gustStrength = Mth.lerp(blend, leaf.gustStrength, wind.gustStrength());
        leaf.turbulence = Mth.lerp(blend, leaf.turbulence, wind.turbulence());
        leaf.windSampleCountdown = WIND_SAMPLE_INTERVAL_TICKS;
    }

    private static final class WindLeaf {
        private int spawnCountdown;
        private int fadeInAge;
        private int light;
        private double velocityX;
        private double velocityY;
        private double velocityZ;
        private boolean active;
        private boolean fadingOut;
        private int age;
        private int lifetime;
        private int fadeOutAge;
        private int textureIndex;
        private int red;
        private int green;
        private int blue;
        private double x;
        private double y;
        private double z;
        private double xOld;
        private double yOld;
        private double zOld;
        private double size;
        private double speed;
        private double crossDrift;
        private double verticalDrift;
        private double fallSpeed;
        private double terrainLift;
        private double terrainSideFlow;
        private double bob;
        private double swirlStrength;
        private double swirlSpeed;
        private double swirlPhase;
        private double loopStrength;
        private double loopSpeed;
        private double loopPhase;
        private double tilt;
        private double tiltSpeed;
        private double flipPhase;
        private double flipSpeed;
        private double tumbleStrength;
        private double seed;
        private int windSampleCountdown;
        private double windX;
        private double windZ;
        private double crossX;
        private double crossZ;
        private double windStrength;
        private double gustStrength;
        private double turbulence;
    }

    private record LeafBlockSpawn(Point position, BlockPos source) {
    }
}
