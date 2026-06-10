package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.config.ClientConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class ResponsiveFoliagePhysics {
    private static final float BASE_EDGE_INFLUENCE_RADIUS = 0.9F;
    private static final float SIZE_RADIUS_SCALE = 0.45F;
    private static final float HEIGHT_RADIUS_SCALE = 0.08F;
    private static final float MAX_EDGE_INFLUENCE_RADIUS = 2.6F;
    private static final float SIZE_STRENGTH_SCALE = 0.28F;
    private static final float HEIGHT_STRENGTH_SCALE = 0.08F;
    private static final float MAX_SIZE_STRENGTH = 1.8F;
    private static final float PLAYER_REFERENCE_WIDTH = 0.6F;
    private static final float PLAYER_REFERENCE_HEIGHT = 1.8F;
    private static final float LEAN_IN_SMOOTHING = 0.38F;
    private static final float LEAN_OUT_SMOOTHING = 0.18F;
    private static final float CHANGE_THRESHOLD = 0.012F;
    private static final float MIN_ACTIVE_INTENSITY = 0.01F;
    private static final double ENTITY_VISIBILITY_RADIUS = 24.0D;

    private static final Map<BlockPos, FoliageLeanState.LeanVector> CURRENT_LEAN = new HashMap<>();

    private ResponsiveFoliagePhysics() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        tick(event.getFrustum(), event.getLevelRenderer());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            reset();
        }
    }

    private static void tick(Frustum frustum, LevelRenderer levelRenderer) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Entity player = minecraft.player;
        if (level == null || player == null) {
            reset();
            return;
        }

        if (!ClientConfig.ENABLE_FOLIAGE_INTERACTIVITY.getAsBoolean()) {
            clearLean(levelRenderer, level);
            return;
        }

        Map<BlockPos, FoliageLeanState.LeanVector> targetLean = new HashMap<>();
        AABB entityBounds = player.getBoundingBox().inflate(ENTITY_VISIBILITY_RADIUS);
        List<Entity> entities = new ArrayList<>(level.getEntitiesOfClass(Entity.class, entityBounds, entity -> !entity.isSpectator() && !entity.isRemoved()));
        if (!entities.contains(player)) {
            entities.add(player);
        }

        Set<BlockPos> entityRoots = new HashSet<>();
        for (Entity entity : entities) {
            collectEntityInfluence(level, frustum, entities, entity, entityRoots, targetLean);
        }

        Map<BlockPos, FoliageLeanState.LeanVector> nextLean = smoothLean(targetLean);
        for (Map.Entry<BlockPos, FoliageLeanState.LeanVector> entry : nextLean.entrySet()) {
            FoliageLeanState.LeanVector previous = CURRENT_LEAN.get(entry.getKey());
            if (previous == null || changed(previous, entry.getValue())) {
                markColumnDirty(levelRenderer, level, entry.getKey());
            }
        }

        for (BlockPos previousPos : CURRENT_LEAN.keySet()) {
            if (!nextLean.containsKey(previousPos)) {
                markColumnDirty(levelRenderer, level, previousPos);
            }
        }

        FoliageLeanState.replaceWith(nextLean);
        CURRENT_LEAN.clear();
        CURRENT_LEAN.putAll(nextLean);
    }

    private static void collectEntityInfluence(ClientLevel level, Frustum frustum, List<Entity> entities, Entity source, Set<BlockPos> entityRoots, Map<BlockPos, FoliageLeanState.LeanVector> nextLean) {
        double radius = edgeInfluenceRadius(source);
        AABB searchBounds = source.getBoundingBox().inflate(radius, 1.0D, radius);

        for (int x = Mth.floor(searchBounds.minX); x <= Mth.floor(searchBounds.maxX); x++) {
            for (int z = Mth.floor(searchBounds.minZ); z <= Mth.floor(searchBounds.maxZ); z++) {
                for (int y = Mth.floor(searchBounds.minY); y <= Mth.floor(searchBounds.maxY); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!isVisible(frustum, pos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (!ResponsiveFoliage.isInteractive(state)) {
                        continue;
                    }

                    FoliageModelData.ColumnSegment segment = ResponsiveFoliage.columnSegment(level, pos, state);
                    if (!entityRoots.add(segment.rootPos())) {
                        continue;
                    }

                    FoliageLeanState.LeanVector lean = computeLean(segment.rootPos(), ResponsiveFoliage.columnSwayMultiplier(level, segment), entities);
                    if (lean != null) {
                        combineLean(nextLean, segment.rootPos(), lean);
                    }
                }
            }
        }
    }

    private static FoliageLeanState.LeanVector computeLean(BlockPos pos, float swayMultiplier, List<Entity> entities) {
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        Entity strongest = null;
        double strongestDirX = 0.0D;
        double strongestDirZ = 0.0D;
        float strongestIntensity = 0.0F;

        for (Entity entity : entities) {
            AABB bounds = entity.getBoundingBox();
            double closestX = Mth.clamp(centerX, bounds.minX, bounds.maxX);
            double closestZ = Mth.clamp(centerZ, bounds.minZ, bounds.maxZ);
            double dirX = centerX - closestX;
            double dirZ = centerZ - closestZ;
            double edgeDistance = Math.sqrt(dirX * dirX + dirZ * dirZ);
            double radius = edgeInfluenceRadius(entity);
            if (edgeDistance >= radius) {
                continue;
            }

            float intensity = (float) (1.0D - edgeDistance / radius)
                    * entitySizeStrength(entity)
                    * swayMultiplier
                    * (float) ClientConfig.FOLIAGE_INTERACTIVITY_STRENGTH.getAsDouble();
            if (intensity > strongestIntensity) {
                strongest = entity;
                strongestDirX = dirX;
                strongestDirZ = dirZ;
                strongestIntensity = intensity;
            }
        }

        if (strongest == null) {
            return null;
        }

        double length = Math.sqrt(strongestDirX * strongestDirX + strongestDirZ * strongestDirZ);
        if (length < 0.001D) {
            strongestDirX = -strongest.getDeltaMovement().x;
            strongestDirZ = -strongest.getDeltaMovement().z;
            length = Math.sqrt(strongestDirX * strongestDirX + strongestDirZ * strongestDirZ);
            if (length < 0.001D) {
                strongestDirX = centerX - strongest.getX();
                strongestDirZ = centerZ - strongest.getZ();
                length = Math.sqrt(strongestDirX * strongestDirX + strongestDirZ * strongestDirZ);
            }

            if (length < 0.001D) {
                strongestDirX = 1.0D;
                strongestDirZ = 0.0D;
                length = 1.0D;
            }
        }

        return new FoliageLeanState.LeanVector(
                (float) (strongestDirX / length),
                (float) (strongestDirZ / length),
                Math.min(strongestIntensity, 1.0F)
        );
    }

    private static void combineLean(Map<BlockPos, FoliageLeanState.LeanVector> leanMap, BlockPos pos, FoliageLeanState.LeanVector addition) {
        FoliageLeanState.LeanVector existing = leanMap.get(pos);
        if (existing == null) {
            leanMap.put(pos, addition);
            return;
        }

        float x = existing.dirX() * existing.intensity() + addition.dirX() * addition.intensity();
        float z = existing.dirZ() * existing.intensity() + addition.dirZ() * addition.intensity();
        float intensity = Math.min((float) Math.sqrt(x * x + z * z), 1.0F);
        if (intensity < 0.001F) {
            leanMap.remove(pos);
            return;
        }

        leanMap.put(pos, new FoliageLeanState.LeanVector(x / intensity, z / intensity, intensity));
    }

    private static Map<BlockPos, FoliageLeanState.LeanVector> smoothLean(Map<BlockPos, FoliageLeanState.LeanVector> targetLean) {
        Map<BlockPos, FoliageLeanState.LeanVector> smoothed = new HashMap<>();
        Set<BlockPos> positions = new HashSet<>(CURRENT_LEAN.keySet());
        positions.addAll(targetLean.keySet());

        for (BlockPos pos : positions) {
            FoliageLeanState.LeanVector current = CURRENT_LEAN.get(pos);
            FoliageLeanState.LeanVector target = targetLean.get(pos);
            FoliageLeanState.LeanVector next = target == null
                    ? easeOut(current)
                    : easeToward(current, target);
            if (next != null && next.intensity() >= MIN_ACTIVE_INTENSITY) {
                smoothed.put(pos, next);
            }
        }

        return smoothed;
    }

    private static FoliageLeanState.LeanVector easeToward(FoliageLeanState.LeanVector current, FoliageLeanState.LeanVector target) {
        if (current == null) {
            return new FoliageLeanState.LeanVector(target.dirX(), target.dirZ(), target.intensity() * LEAN_IN_SMOOTHING);
        }

        float dirX = Mth.lerp(LEAN_IN_SMOOTHING, current.dirX(), target.dirX());
        float dirZ = Mth.lerp(LEAN_IN_SMOOTHING, current.dirZ(), target.dirZ());
        float length = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 0.001F) {
            dirX = target.dirX();
            dirZ = target.dirZ();
        } else {
            dirX /= length;
            dirZ /= length;
        }

        float intensity = Mth.lerp(LEAN_IN_SMOOTHING, current.intensity(), target.intensity());
        return new FoliageLeanState.LeanVector(dirX, dirZ, intensity);
    }

    private static FoliageLeanState.LeanVector easeOut(FoliageLeanState.LeanVector current) {
        if (current == null) {
            return null;
        }

        return new FoliageLeanState.LeanVector(
                current.dirX(),
                current.dirZ(),
                Mth.lerp(LEAN_OUT_SMOOTHING, current.intensity(), 0.0F)
        );
    }

    private static float edgeInfluenceRadius(Entity entity) {
        AABB bounds = entity.getBoundingBox();
        float horizontalSize = (float) Math.max(bounds.getXsize(), bounds.getZsize());
        float extraWidth = Math.max(0.0F, horizontalSize - PLAYER_REFERENCE_WIDTH);
        float extraHeight = Math.max(0.0F, (float) bounds.getYsize() - PLAYER_REFERENCE_HEIGHT);
        return Mth.clamp(
                BASE_EDGE_INFLUENCE_RADIUS + extraWidth * SIZE_RADIUS_SCALE + extraHeight * HEIGHT_RADIUS_SCALE,
                BASE_EDGE_INFLUENCE_RADIUS,
                MAX_EDGE_INFLUENCE_RADIUS
        );
    }

    private static float entitySizeStrength(Entity entity) {
        AABB bounds = entity.getBoundingBox();
        float horizontalSize = (float) Math.max(bounds.getXsize(), bounds.getZsize());
        float extraWidth = Math.max(0.0F, horizontalSize - PLAYER_REFERENCE_WIDTH);
        float extraHeight = Math.max(0.0F, (float) bounds.getYsize() - PLAYER_REFERENCE_HEIGHT);
        return Mth.clamp(
                1.0F + extraWidth * SIZE_STRENGTH_SCALE + extraHeight * HEIGHT_STRENGTH_SCALE,
                1.0F,
                MAX_SIZE_STRENGTH
        );
    }

    private static boolean isVisible(Frustum frustum, BlockPos pos) {
        AABB bounds = new AABB(pos).inflate(0.4D, 0.15D, 0.4D);
        return frustum.isVisible(bounds);
    }

    private static boolean changed(FoliageLeanState.LeanVector previous, FoliageLeanState.LeanVector current) {
        return Math.abs(previous.intensity() - current.intensity()) > CHANGE_THRESHOLD
                || Math.abs(previous.dirX() - current.dirX()) > CHANGE_THRESHOLD
                || Math.abs(previous.dirZ() - current.dirZ()) > CHANGE_THRESHOLD;
    }

    private static void markColumnDirty(LevelRenderer levelRenderer, ClientLevel level, BlockPos pos) {
        markDirty(levelRenderer, level, pos);

        BlockState rootState = level.getBlockState(pos);
        FoliageModelData.ColumnSegment segment = ResponsiveFoliage.columnSegment(level, pos, rootState);
        for (int offset = 1; offset < segment.height(); offset++) {
            markDirty(levelRenderer, level, pos.above(offset));
        }
    }

    private static void markDirty(LevelRenderer levelRenderer, ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        levelRenderer.blockChanged(level, pos, state, state, 0);
    }

    private static void reset() {
        FoliageLeanState.clear();
        CURRENT_LEAN.clear();
    }

    private static void clearLean(LevelRenderer levelRenderer, ClientLevel level) {
        if (CURRENT_LEAN.isEmpty()) {
            FoliageLeanState.clear();
            return;
        }

        for (BlockPos pos : CURRENT_LEAN.keySet()) {
            markColumnDirty(levelRenderer, level, pos);
        }

        reset();
    }
}
