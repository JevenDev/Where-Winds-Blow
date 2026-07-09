package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.config.ClientConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

public final class ResponsiveFoliagePhysics {
    private static final float BASE_EDGE_INFLUENCE_RADIUS = 0.9F;
    private static final float SIZE_RADIUS_SCALE = 0.45F;
    private static final float HEIGHT_RADIUS_SCALE = 0.08F;
    private static final float MAX_EDGE_INFLUENCE_RADIUS = 2.6F;
    private static final float SIZE_STRENGTH_SCALE = 0.28F;
    private static final float HEIGHT_STRENGTH_SCALE = 0.08F;
    private static final float MAX_SIZE_STRENGTH = 1.8F;
    private static final float INTERACTION_OFFSET_SCALE = 0.34F;
    private static final float MAX_COMBINED_OFFSET = 0.62F;
    private static final float IMPULSE_RESPONSE = 0.68F;
    private static final float IMPULSE_RETURN_SPEED = 4.8F;
    private static final float MIN_VISIBLE_IMPULSE = 0.012F;
    private static final float MIN_IMPULSE_CHANGE_SQR = 0.000025F;
    private static final float CONTACT_SMOOTHNESS = 9.5F;
    private static final long CONTACT_LIFETIME_MILLIS = 560L;
    private static final float MIN_VISIBLE_CONTACT_DECAY = 0.035F;
    private static final float PLAYER_REFERENCE_WIDTH = 0.6F;
    private static final float PLAYER_REFERENCE_HEIGHT = 1.8F;
    private static final double ENTITY_VISIBILITY_RADIUS = 12.0D;
    private static final double FOLIAGE_SCAN_Y_PADDING = 0.55D;
    private static final double MIN_NON_PLAYER_MOVEMENT_SQR = 0.0004D;
    private static final double MIN_CONTACT_MOVEMENT_SQR = 0.0001D;
    private static final int MAX_INTERACTIVE_ENTITIES = ResponsiveFoliageShaders.MAX_FOLIAGE_INTERACTORS;
    private static final int MAX_TRACKED_FOLIAGE_IMPULSES = 192;
    private static final Map<BlockPos, FoliageImpulse> ACTIVE_IMPULSES = new ConcurrentHashMap<>();
    private static final Map<Long, FoliageContact> ACTIVE_CONTACTS = new HashMap<>();
    private static long lastInteractorUpdateGameTime = Long.MIN_VALUE;
    private static long pauseStartedMillis;

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
            clearInteractorState(minecraft.level);
        }
    }

    public static void onClientPauseChange(ClientPauseChangeEvent.Post event) {
        if (event.isPaused()) {
            pauseStartedMillis = Util.getMillis();
            return;
        }

        resumeInteractorTimers(Util.getMillis());
        lastInteractorUpdateGameTime = Long.MIN_VALUE;
    }

    @Nullable
    public static FoliageModelData.InteractionImpulse interactionAt(BlockPos pos) {
        FoliageImpulse impulse = ACTIVE_IMPULSES.get(pos);
        return impulse != null && impulse.isVisible()
                ? new FoliageModelData.InteractionImpulse(impulse.offsetX(), impulse.offsetZ())
                : null;
    }

    public static void updateShaderInteractors() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Entity player = minecraft.player;
        if (level == null || player == null || !ClientConfig.ENABLE_FOLIAGE_INTERACTIVITY.getAsBoolean()) {
            clearInteractorState(level);
            return;
        }

        if (minecraft.isPaused()) {
            if (pauseStartedMillis == 0L) {
                pauseStartedMillis = Util.getMillis();
            }
            return;
        }

        resumeInteractorTimers(Util.getMillis());

        long gameTime = level.getGameTime();
        long nowMillis = Util.getMillis();
        if (ResponsiveFoliageShaders.shouldEncodeFoliageVertexMarkers()) {
            clearBlockImpulses(level);
            if (lastInteractorUpdateGameTime != gameTime) {
                lastInteractorUpdateGameTime = gameTime;
                List<Entity> entities = interactiveEntities(level, player);
                refreshShaderContacts(entities, nowMillis);
            }

            uploadShaderContacts(player, nowMillis);
            return;
        }

        clearShaderContacts();
        if (lastInteractorUpdateGameTime == gameTime) {
            return;
        }
        lastInteractorUpdateGameTime = gameTime;

        Map<BlockPos, FoliageImpulse> touchedImpulses = new HashMap<>();
        List<Entity> entities = interactiveEntities(level, player);
        for (Entity entity : entities) {
            collectFoliageImpulses(level, entity, nowMillis, touchedImpulses);
        }

        trimImpulses(player, touchedImpulses);
        updateActiveImpulses(level, touchedImpulses, nowMillis);
    }

    private static List<Entity> interactiveEntities(ClientLevel level, Entity player) {
        AABB entityBounds = player.getBoundingBox().inflate(ENTITY_VISIBILITY_RADIUS);
        List<Entity> entities = new ArrayList<>(level.getEntitiesOfClass(Entity.class, entityBounds, entity -> isInteractiveEntity(entity, player)));
        if (!entities.contains(player)) {
            entities.add(player);
        }

        trimEntities(player, entities);
        return entities;
    }

    private static void clearInteractorState(@Nullable ClientLevel level) {
        lastInteractorUpdateGameTime = Long.MIN_VALUE;
        pauseStartedMillis = 0L;
        clearShaderContacts();
        clearBlockImpulses(level);
    }

    private static void clearShaderContacts() {
        ACTIVE_CONTACTS.clear();
        ResponsiveFoliageShaders.clearFoliageInteractors();
    }

    private static void clearBlockImpulses(@Nullable ClientLevel level) {
        if (ACTIVE_IMPULSES.isEmpty()) {
            return;
        }

        if (level != null) {
            markChanged(level, new HashSet<>(ACTIVE_IMPULSES.keySet()));
        }
        ACTIVE_IMPULSES.clear();
    }

    private static void resumeInteractorTimers(long nowMillis) {
        if (pauseStartedMillis == 0L) {
            return;
        }

        long pausedMillis = Math.max(0L, nowMillis - pauseStartedMillis);
        if (pausedMillis > 0L) {
            ACTIVE_IMPULSES.replaceAll((pos, impulse) -> impulse.shift(pausedMillis));
            ACTIVE_CONTACTS.replaceAll((key, contact) -> contact.shift(pausedMillis));
        }
        pauseStartedMillis = 0L;
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

    private static void refreshShaderContacts(List<Entity> entities, long nowMillis) {
        Set<Long> touchedKeys = new HashSet<>();
        for (Entity entity : entities) {
            touchShaderContact(entity, nowMillis, touchedKeys);
        }

        Iterator<Map.Entry<Long, FoliageContact>> iterator = ACTIVE_CONTACTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, FoliageContact> entry = iterator.next();
            FoliageContact contact = entry.getValue();
            if (!touchedKeys.contains(entry.getKey())
                    && nowMillis - contact.lastTouchedMillis() > CONTACT_LIFETIME_MILLIS) {
                iterator.remove();
            }
        }
    }

    private static void touchShaderContact(Entity entity, long nowMillis, Set<Long> touchedKeys) {
        Vec3 movement = entity.getDeltaMovement();
        if (movement.horizontalDistanceSqr() < MIN_CONTACT_MOVEMENT_SQR) {
            return;
        }

        AABB bounds = entity.getBoundingBox();
        int cellX = Mth.floor(entity.getX());
        int cellY = Mth.floor(bounds.minY);
        int cellZ = Mth.floor(entity.getZ());
        long key = contactKey(cellX, cellY, cellZ);
        float strength = entitySizeStrength(entity) * (float) ClientConfig.FOLIAGE_INTERACTIVITY_STRENGTH.getAsDouble();
        FoliageContact existingContact = ACTIVE_CONTACTS.get(key);
        FoliageContact updatedContact = existingContact != null
                ? existingContact.refresh((float) entity.getX(), (float) bounds.minY, (float) entity.getZ(), edgeInfluenceRadius(entity), strength, nowMillis)
                : FoliageContact.create(cellX, cellY, cellZ, (float) entity.getX(), (float) bounds.minY, (float) entity.getZ(), edgeInfluenceRadius(entity), strength, nowMillis);
        ACTIVE_CONTACTS.put(key, updatedContact);
        touchedKeys.add(key);
    }

    private static void uploadShaderContacts(Entity player, long nowMillis) {
        List<FoliageContact> contacts = ACTIVE_CONTACTS.values()
                .stream()
                .filter(contact -> contactDecay(contact, nowMillis) > MIN_VISIBLE_CONTACT_DECAY)
                .sorted(Comparator.comparingDouble(contact -> contactPriority(player, contact, nowMillis)))
                .limit(ResponsiveFoliageShaders.MAX_FOLIAGE_INTERACTORS)
                .toList();
        ResponsiveFoliageShaders.setFoliageInteractors(
                contacts.size(),
                (interactors, strengths, index) -> fillShaderInteractor(contacts.get(index), nowMillis, interactors, strengths, index)
        );
    }

    private static void fillShaderInteractor(FoliageContact contact, long nowMillis, float[] interactors, float[] strengths, int index) {
        ContactSample sample = contact.sample(nowMillis);
        int offset = index * 4;
        interactors[offset] = sample.x();
        interactors[offset + 1] = sample.minY();
        interactors[offset + 2] = sample.z();
        interactors[offset + 3] = sample.radius();
        strengths[index] = sample.strength() * contactDecay(contact, nowMillis);
    }

    private static double contactPriority(Entity player, FoliageContact contact, long nowMillis) {
        ContactSample sample = contact.sample(nowMillis);
        double dx = sample.x() - player.getX();
        double dy = sample.minY() - player.getY();
        double dz = sample.z() - player.getZ();
        return dx * dx + dy * dy + dz * dz - sample.strength() * 18.0D;
    }

    private static float contactDecay(FoliageContact contact, long nowMillis) {
        float age = Mth.clamp((float) (nowMillis - contact.lastTouchedMillis()) / CONTACT_LIFETIME_MILLIS, 0.0F, 1.0F);
        float remaining = 1.0F - age;
        return smoothCurve(remaining);
    }

    private static long contactKey(int cellX, int cellY, int cellZ) {
        return ((long) cellX & 0x3FFFFFFL) << 38
                | ((long) cellZ & 0x3FFFFFFL) << 12
                | ((long) cellY & 0xFFFL);
    }

    private static void collectFoliageImpulses(ClientLevel level, Entity entity, long nowMillis, Map<BlockPos, FoliageImpulse> touchedImpulses) {
        Vec3 movement = entity.getDeltaMovement();
        double horizontalMovementSqr = movement.horizontalDistanceSqr();
        if (horizontalMovementSqr < MIN_CONTACT_MOVEMENT_SQR) {
            return;
        }

        AABB bounds = entity.getBoundingBox();
        float influenceRadius = edgeInfluenceRadius(entity);
        float baseStrength = entitySizeStrength(entity) * (float) ClientConfig.FOLIAGE_INTERACTIVITY_STRENGTH.getAsDouble();
        int minX = Mth.floor(bounds.minX - influenceRadius);
        int maxX = Mth.floor(bounds.maxX + influenceRadius);
        int minY = Mth.floor(bounds.minY - FOLIAGE_SCAN_Y_PADDING);
        int maxY = Mth.floor(bounds.maxY + FOLIAGE_SCAN_Y_PADDING);
        int minZ = Mth.floor(bounds.minZ - influenceRadius);
        int maxZ = Mth.floor(bounds.maxZ + influenceRadius);
        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    samplePos.set(x, y, z);
                    BlockState state = level.getBlockState(samplePos);
                    if (!ResponsiveFoliage.isInteractive(state)) {
                        continue;
                    }

                    addImpulseForBlock(entity, bounds, movement, samplePos, influenceRadius, baseStrength, nowMillis, touchedImpulses);
                }
            }
        }
    }

    private static void addImpulseForBlock(
            Entity entity,
            AABB bounds,
            Vec3 movement,
            BlockPos pos,
            float influenceRadius,
            float baseStrength,
            long nowMillis,
            Map<BlockPos, FoliageImpulse> touchedImpulses
    ) {
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        double nearestX = Mth.clamp(centerX, bounds.minX, bounds.maxX);
        double nearestZ = Mth.clamp(centerZ, bounds.minZ, bounds.maxZ);
        double surfaceDeltaX = centerX - nearestX;
        double surfaceDeltaZ = centerZ - nearestZ;
        double surfaceDistanceSqr = surfaceDeltaX * surfaceDeltaX + surfaceDeltaZ * surfaceDeltaZ;
        float radiusSqr = influenceRadius * influenceRadius;
        if (surfaceDistanceSqr >= radiusSqr) {
            return;
        }

        float influence = smoothCurve(1.0F - (float) Math.sqrt(surfaceDistanceSqr) / influenceRadius);
        float offset = influence * baseStrength * INTERACTION_OFFSET_SCALE;
        if (offset <= MIN_VISIBLE_IMPULSE) {
            return;
        }

        double directionX = centerX - entity.getX();
        double directionZ = centerZ - entity.getZ();
        double directionLength = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (directionLength > 0.001D) {
            directionX /= directionLength;
            directionZ /= directionLength;
        } else {
            double movementLength = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
            if (movementLength > 0.001D) {
                directionX = -movement.x / movementLength;
                directionZ = -movement.z / movementLength;
            } else {
                directionX = 1.0D;
                directionZ = 0.0D;
            }
        }

        BlockPos key = pos.immutable();
        FoliageImpulse impulse = FoliageImpulse.create((float) directionX * offset, (float) directionZ * offset, nowMillis);
        touchedImpulses.merge(key, impulse, FoliageImpulse::merge);
    }

    private static void trimImpulses(Entity player, Map<BlockPos, FoliageImpulse> impulses) {
        if (impulses.size() <= MAX_TRACKED_FOLIAGE_IMPULSES) {
            return;
        }

        List<Map.Entry<BlockPos, FoliageImpulse>> entries = new ArrayList<>(impulses.entrySet());
        entries.sort(Comparator.comparingDouble(entry -> impulsePriority(player, entry.getKey(), entry.getValue())));
        impulses.clear();
        for (int index = 0; index < Math.min(MAX_TRACKED_FOLIAGE_IMPULSES, entries.size()); index++) {
            Map.Entry<BlockPos, FoliageImpulse> entry = entries.get(index);
            impulses.put(entry.getKey(), entry.getValue());
        }
    }

    private static double impulsePriority(Entity player, BlockPos pos, FoliageImpulse impulse) {
        double dx = pos.getX() + 0.5D - player.getX();
        double dy = pos.getY() + 0.5D - player.getY();
        double dz = pos.getZ() + 0.5D - player.getZ();
        return dx * dx + dy * dy + dz * dz - impulse.magnitudeSqr() * 24.0D;
    }

    private static void updateActiveImpulses(ClientLevel level, Map<BlockPos, FoliageImpulse> touchedImpulses, long nowMillis) {
        Set<BlockPos> changedPositions = new HashSet<>();
        for (Map.Entry<BlockPos, FoliageImpulse> entry : touchedImpulses.entrySet()) {
            BlockPos pos = entry.getKey();
            FoliageImpulse target = entry.getValue();
            FoliageImpulse existing = ACTIVE_IMPULSES.get(pos);
            FoliageImpulse updated = existing == null ? target : existing.approach(target, nowMillis);
            ACTIVE_IMPULSES.put(pos, updated);
            if (existing == null || existing.hasNoticeableChange(updated)) {
                changedPositions.add(pos);
            }
        }

        Iterator<Map.Entry<BlockPos, FoliageImpulse>> iterator = ACTIVE_IMPULSES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, FoliageImpulse> entry = iterator.next();
            BlockPos pos = entry.getKey();
            if (touchedImpulses.containsKey(pos)) {
                continue;
            }

            FoliageImpulse existing = entry.getValue();
            FoliageImpulse decayed = existing.decay(nowMillis);
            if (decayed == null) {
                ACTIVE_IMPULSES.remove(pos, existing);
                changedPositions.add(pos);
            } else {
                ACTIVE_IMPULSES.put(pos, decayed);
                if (existing.hasNoticeableChange(decayed)) {
                    changedPositions.add(pos);
                }
            }
        }

        if (ACTIVE_IMPULSES.size() > MAX_TRACKED_FOLIAGE_IMPULSES) {
            trimActiveImpulses(changedPositions);
        }

        markChanged(level, changedPositions);
    }

    private static void trimActiveImpulses(Set<BlockPos> changedPositions) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity player = minecraft.player;
        if (player == null || ACTIVE_IMPULSES.size() <= MAX_TRACKED_FOLIAGE_IMPULSES) {
            return;
        }

        List<Map.Entry<BlockPos, FoliageImpulse>> entries = new ArrayList<>(ACTIVE_IMPULSES.entrySet());
        entries.sort(Comparator.comparingDouble(entry -> impulsePriority(player, entry.getKey(), entry.getValue())));
        for (int index = MAX_TRACKED_FOLIAGE_IMPULSES; index < entries.size(); index++) {
            BlockPos removedPos = entries.get(index).getKey();
            ACTIVE_IMPULSES.remove(removedPos);
            changedPositions.add(removedPos);
        }
    }

    private static void markChanged(ClientLevel level, Set<BlockPos> positions) {
        if (positions.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            minecraft.levelRenderer.blockChanged(level, pos, state, state, 0);
        }
    }

    private static float smoothCurve(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);
        return value * value * (3.0F - 2.0F * value);
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

    private record FoliageContact(
            int cellX,
            int cellY,
            int cellZ,
            float previousX,
            float previousMinY,
            float previousZ,
            float previousRadius,
            float previousStrength,
            float x,
            float minY,
            float z,
            float radius,
            float strength,
            long updatedMillis,
            long lastTouchedMillis
    ) {
        private static FoliageContact create(
                int cellX,
                int cellY,
                int cellZ,
                float x,
                float minY,
                float z,
                float radius,
                float strength,
                long nowMillis
        ) {
            return new FoliageContact(
                    cellX,
                    cellY,
                    cellZ,
                    x,
                    minY,
                    z,
                    radius,
                    strength,
                    x,
                    minY,
                    z,
                    radius,
                    strength,
                    nowMillis,
                    nowMillis
            );
        }

        private FoliageContact refresh(float x, float minY, float z, float radius, float strength, long nowMillis) {
            ContactSample sample = sample(nowMillis);
            return new FoliageContact(
                    cellX,
                    cellY,
                    cellZ,
                    sample.x(),
                    sample.minY(),
                    sample.z(),
                    sample.radius(),
                    sample.strength(),
                    x,
                    minY,
                    z,
                    radius,
                    strength,
                    nowMillis,
                    nowMillis
            );
        }

        private ContactSample sample(long nowMillis) {
            float progress = Mth.clamp((nowMillis - updatedMillis) * 0.001F * CONTACT_SMOOTHNESS, 0.0F, 1.0F);
            progress = smoothCurve(progress);
            return new ContactSample(
                    Mth.lerp(progress, previousX, x),
                    Mth.lerp(progress, previousMinY, minY),
                    Mth.lerp(progress, previousZ, z),
                    Mth.lerp(progress, previousRadius, radius),
                    Mth.lerp(progress, previousStrength, strength)
            );
        }

        private FoliageContact shift(long millis) {
            return new FoliageContact(
                    cellX,
                    cellY,
                    cellZ,
                    previousX,
                    previousMinY,
                    previousZ,
                    previousRadius,
                    previousStrength,
                    x,
                    minY,
                    z,
                    radius,
                    strength,
                    updatedMillis + millis,
                    lastTouchedMillis + millis
            );
        }
    }

    private record ContactSample(float x, float minY, float z, float radius, float strength) {
    }

    private record FoliageImpulse(float offsetX, float offsetZ, long updatedMillis) {
        private static FoliageImpulse create(float offsetX, float offsetZ, long nowMillis) {
            float magnitude = (float) Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
            if (magnitude > MAX_COMBINED_OFFSET) {
                float scale = MAX_COMBINED_OFFSET / magnitude;
                offsetX *= scale;
                offsetZ *= scale;
            }

            return new FoliageImpulse(offsetX, offsetZ, nowMillis);
        }

        private FoliageImpulse merge(FoliageImpulse other) {
            return create(offsetX + other.offsetX, offsetZ + other.offsetZ, other.updatedMillis);
        }

        private FoliageImpulse approach(FoliageImpulse target, long nowMillis) {
            return create(
                    Mth.lerp(IMPULSE_RESPONSE, offsetX, target.offsetX),
                    Mth.lerp(IMPULSE_RESPONSE, offsetZ, target.offsetZ),
                    nowMillis
            );
        }

        @Nullable
        private FoliageImpulse decay(long nowMillis) {
            float deltaSeconds = Mth.clamp((nowMillis - updatedMillis) * 0.001F, 0.0F, 0.25F);
            float decay = (float) Math.exp(-deltaSeconds * IMPULSE_RETURN_SPEED);
            float decayedX = offsetX * decay;
            float decayedZ = offsetZ * decay;
            if (decayedX * decayedX + decayedZ * decayedZ <= MIN_VISIBLE_IMPULSE * MIN_VISIBLE_IMPULSE) {
                return null;
            }

            return create(decayedX, decayedZ, nowMillis);
        }

        private FoliageImpulse shift(long millis) {
            return new FoliageImpulse(offsetX, offsetZ, updatedMillis + millis);
        }

        private boolean isVisible() {
            return magnitudeSqr() > MIN_VISIBLE_IMPULSE * MIN_VISIBLE_IMPULSE;
        }

        private boolean hasNoticeableChange(FoliageImpulse other) {
            float dx = offsetX - other.offsetX;
            float dz = offsetZ - other.offsetZ;
            return dx * dx + dz * dz > MIN_IMPULSE_CHANGE_SQR;
        }

        private float magnitudeSqr() {
            return offsetX * offsetX + offsetZ * offsetZ;
        }
    }
}
