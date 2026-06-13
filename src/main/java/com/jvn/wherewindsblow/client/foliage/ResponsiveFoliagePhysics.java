package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.config.ClientConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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
    private static final double ENTITY_VISIBILITY_RADIUS = 24.0D;
    private static final int MAX_INTERACTIVE_ENTITIES = ResponsiveFoliageShaders.MAX_FOLIAGE_INTERACTORS;

    private ResponsiveFoliagePhysics() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }

        updateShaderInteractors();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            ResponsiveFoliageShaders.clearFoliageInteractors();
        }
    }

    public static void updateShaderInteractors() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Entity player = minecraft.player;
        if (level == null || player == null || !ClientConfig.ENABLE_FOLIAGE_INTERACTIVITY.getAsBoolean()) {
            ResponsiveFoliageShaders.clearFoliageInteractors();
            return;
        }

        AABB entityBounds = player.getBoundingBox().inflate(ENTITY_VISIBILITY_RADIUS);
        List<Entity> entities = new ArrayList<>(level.getEntitiesOfClass(Entity.class, entityBounds, entity -> !entity.isSpectator() && !entity.isRemoved()));
        if (!entities.contains(player)) {
            entities.add(player);
        }
        trimEntities(player, entities);

        ResponsiveFoliageShaders.setFoliageInteractors(entities, ResponsiveFoliagePhysics::fillInteractor);
    }

    private static void trimEntities(Entity player, List<Entity> entities) {
        if (entities.size() <= MAX_INTERACTIVE_ENTITIES) {
            return;
        }

        entities.sort(Comparator.comparingDouble(entity -> entity == player ? -1.0D : entity.distanceToSqr(player)));
        entities.subList(MAX_INTERACTIVE_ENTITIES, entities.size()).clear();
    }

    private static void fillInteractor(Entity entity, float[] interactors, float[] strengths, int index) {
        AABB bounds = entity.getBoundingBox();
        int offset = index * 4;
        interactors[offset] = (float) entity.getX();
        interactors[offset + 1] = (float) bounds.minY;
        interactors[offset + 2] = (float) entity.getZ();
        interactors[offset + 3] = edgeInfluenceRadius(entity);
        strengths[index] = entitySizeStrength(entity) * (float) ClientConfig.FOLIAGE_INTERACTIVITY_STRENGTH.getAsDouble();
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
}
