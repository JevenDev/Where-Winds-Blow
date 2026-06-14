package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.config.ClientConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
    private static final double ENTITY_VISIBILITY_RADIUS = 12.0D;
    private static final double MIN_NON_PLAYER_MOVEMENT_SQR = 0.0004D;
    private static final double MIN_CONTACT_MOVEMENT_SQR = 0.0001D;
    private static final long CONTACT_LIFETIME_MILLIS = 420L;
    private static final float MIN_VISIBLE_CONTACT_DECAY = 0.08F;
    private static final int MAX_INTERACTIVE_ENTITIES = ResponsiveFoliageShaders.MAX_FOLIAGE_INTERACTORS;
    private static final Map<Long, FoliageContact> ACTIVE_CONTACTS = new HashMap<>();

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
            ACTIVE_CONTACTS.clear();
        }
    }

    public static void updateShaderInteractors() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Entity player = minecraft.player;
        if (level == null || player == null || !ClientConfig.ENABLE_FOLIAGE_INTERACTIVITY.getAsBoolean()) {
            ResponsiveFoliageShaders.clearFoliageInteractors();
            ACTIVE_CONTACTS.clear();
            return;
        }

        long nowMillis = Util.getMillis();
        expireContacts(nowMillis);

        AABB entityBounds = player.getBoundingBox().inflate(ENTITY_VISIBILITY_RADIUS);
        List<Entity> entities = new ArrayList<>(level.getEntitiesOfClass(Entity.class, entityBounds, entity -> isInteractiveEntity(entity, player)));
        if (!entities.contains(player)) {
            entities.add(player);
        }
        trimEntities(player, entities);
        for (Entity entity : entities) {
            touchContact(entity, nowMillis);
        }

        List<FoliageContact> contacts = ACTIVE_CONTACTS.values()
                .stream()
                .filter(contact -> contactDecay(contact, nowMillis) > MIN_VISIBLE_CONTACT_DECAY)
                .sorted(Comparator.comparingLong(FoliageContact::lastTouchedMillis).reversed())
                .limit(ResponsiveFoliageShaders.MAX_FOLIAGE_INTERACTORS)
                .toList();
        ResponsiveFoliageShaders.setFoliageInteractors(contacts.size(), (interactors, strengths, index) -> fillInteractor(contacts.get(index), nowMillis, interactors, strengths, index));
    }

    private static void trimEntities(Entity player, List<Entity> entities) {
        if (entities.size() <= MAX_INTERACTIVE_ENTITIES) {
            return;
        }

        entities.sort(Comparator.comparingDouble(entity -> entity == player ? -1.0D : entity.distanceToSqr(player)));
        entities.subList(MAX_INTERACTIVE_ENTITIES, entities.size()).clear();
    }

    private static boolean isInteractiveEntity(Entity entity, Entity player) {
        if (entity.isSpectator() || entity.isRemoved()) {
            return false;
        }
        if (entity == player) {
            return true;
        }

        AABB bounds = entity.getBoundingBox();
        if (bounds.getXsize() < 0.1D || bounds.getZsize() < 0.1D) {
            return false;
        }

        return entity.getDeltaMovement().horizontalDistanceSqr() >= MIN_NON_PLAYER_MOVEMENT_SQR;
    }

    private static void expireContacts(long nowMillis) {
        Iterator<FoliageContact> iterator = ACTIVE_CONTACTS.values().iterator();
        while (iterator.hasNext()) {
            FoliageContact contact = iterator.next();
            if (nowMillis - contact.lastTouchedMillis() > CONTACT_LIFETIME_MILLIS) {
                iterator.remove();
            }
        }
    }

    private static void touchContact(Entity entity, long nowMillis) {
        Vec3 movement = entity.getDeltaMovement();
        double horizontalMovementSqr = movement.horizontalDistanceSqr();
        if (horizontalMovementSqr < MIN_CONTACT_MOVEMENT_SQR) {
            return;
        }

        AABB bounds = entity.getBoundingBox();
        int cellY = Mth.floor(bounds.minY);
        int cellX = Mth.floor(entity.getX());
        int cellZ = Mth.floor(entity.getZ());
        long key = contactKey(cellX, cellY, cellZ);
        float strength = entitySizeStrength(entity) * (float) ClientConfig.FOLIAGE_INTERACTIVITY_STRENGTH.getAsDouble();
        FoliageContact existingContact = ACTIVE_CONTACTS.get(key);
        if (existingContact != null) {
            ACTIVE_CONTACTS.put(key, existingContact.refresh((float) entity.getX(), (float) bounds.minY, (float) entity.getZ(), edgeInfluenceRadius(entity), strength, nowMillis));
            return;
        }

        ACTIVE_CONTACTS.put(key, new FoliageContact(
                cellX,
                cellY,
                cellZ,
                (float) entity.getX(),
                (float) bounds.minY,
                (float) entity.getZ(),
                edgeInfluenceRadius(entity),
                strength,
                nowMillis
        ));
    }

    private static void fillInteractor(FoliageContact contact, long nowMillis, float[] interactors, float[] strengths, int index) {
        float decay = contactDecay(contact, nowMillis);
        int offset = index * 4;
        interactors[offset] = contact.x();
        interactors[offset + 1] = contact.minY();
        interactors[offset + 2] = contact.z();
        interactors[offset + 3] = contact.radius();
        strengths[index] = contact.strength() * decay;
    }

    private static float contactDecay(FoliageContact contact, long nowMillis) {
        float age = Mth.clamp((float) (nowMillis - contact.lastTouchedMillis()) / CONTACT_LIFETIME_MILLIS, 0.0F, 1.0F);
        float remaining = 1.0F - age;
        return remaining * remaining * (3.0F - 2.0F * remaining);
    }

    private static long contactKey(int cellX, int cellY, int cellZ) {
        return ((long) cellX & 0x3FFFFFFL) << 38
                | ((long) cellZ & 0x3FFFFFFL) << 12
                | ((long) cellY & 0xFFFL);
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

    private record FoliageContact(int cellX, int cellY, int cellZ, float x, float minY, float z, float radius, float strength, long lastTouchedMillis) {
        private FoliageContact refresh(float x, float minY, float z, float radius, float strength, long nowMillis) {
            return new FoliageContact(cellX, cellY, cellZ, x, minY, z, radius, strength, nowMillis);
        }
    }
}
