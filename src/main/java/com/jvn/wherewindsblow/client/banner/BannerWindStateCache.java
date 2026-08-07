package com.jvn.wherewindsblow.client.banner;

import com.jvn.toucanlib.client.ToucanMotion;
import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.WindSample;
import com.jvn.wherewindsblow.config.ClientConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Tick-owned, bounded banner response state. Rendering only reads interpolated values. */
public final class BannerWindStateCache {
    private static final int MAX_STATES_PER_TYPE = 256;
    private static final float SAMPLE_INTERVAL_SECONDS = 0.1F;
    private static final float ACTIVE_GRACE_SECONDS = 2.0F;
    private static final float EXPIRE_AFTER_SECONDS = 10.0F;
    private static final Long2ObjectLinkedOpenHashMap<State> STANDING_STATES = new Long2ObjectLinkedOpenHashMap<>();
    private static final Long2ObjectLinkedOpenHashMap<State> WALL_STATES = new Long2ObjectLinkedOpenHashMap<>();

    private BannerWindStateCache() {
    }

    public static State touch(BlockPos pos, boolean wall, float rendererYawDegrees) {
        Long2ObjectLinkedOpenHashMap<State> states = wall ? WALL_STATES : STANDING_STATES;
        long key = pos.asLong();
        State state = states.getAndMoveToLast(key);
        if (state == null) {
            if (states.size() >= MAX_STATES_PER_TYPE) {
                states.removeFirst();
            }
            state = new State(pos.immutable(), rendererYawDegrees, stablePhase(pos));
            states.putAndMoveToLast(key, state);
        }
        state.rendererYawDegrees = rendererYawDegrees;
        state.lastRenderedTime = DynamicWindManager.simulationTime();
        return state;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ClientConfig.ENABLE_WIND_REACTIVE_BANNERS.getAsBoolean()) {
            reset();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) {
            return;
        }

        float simulationTime = DynamicWindManager.simulationTime();
        tickStates(STANDING_STATES, level, simulationTime, false);
        tickStates(WALL_STATES, level, simulationTime, true);
    }

    public static void reset() {
        STANDING_STATES.clear();
        WALL_STATES.clear();
    }

    public static State debugState(BlockPos pos, boolean wall) {
        return (wall ? WALL_STATES : STANDING_STATES).get(pos.asLong());
    }

    public static int stateCount() {
        return STANDING_STATES.size() + WALL_STATES.size();
    }

    private static void tickStates(
            Long2ObjectLinkedOpenHashMap<State> states,
            ClientLevel level,
            float simulationTime,
            boolean wall
    ) {
        var iterator = states.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            State state = iterator.next().getValue();
            float unseenTime = simulationTime - state.lastRenderedTime;
            if (unseenTime > EXPIRE_AFTER_SECONDS) {
                iterator.remove();
                continue;
            }
            if (unseenTime <= ACTIVE_GRACE_SECONDS) {
                state.tick(level, simulationTime, wall);
            }
        }
    }

    private static float stablePhase(BlockPos pos) {
        long value = pos.asLong() ^ DynamicWindManager.simulationSeed();
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        return (value & 0xffffL) * ((float) (Math.PI * 2.0) / 65536.0F);
    }

    public static final class State {
        private final BlockPos pos;
        private final float phase;
        private float rendererYawDegrees;
        private float lastRenderedTime;
        private float lastSampleTime = Float.NEGATIVE_INFINITY;
        private float lastTickTime = Float.NaN;
        private float previousExtension;
        private float extension;
        private float previousTrailingExtension;
        private float trailingExtension;
        private float previousCrosswind;
        private float crosswind;
        private float previousOutwardWind;
        private float outwardWind;
        private float previousGust;
        private float gust;
        private float previousTurbulence;
        private float turbulence;
        private float previousExposure = 1.0F;
        private float exposure = 1.0F;
        private float previousHingeResponse;
        private float hingeResponse;
        private float hingeVelocity;
        private float previousSideResponse;
        private float sideResponse;
        private float sideVelocity;
        private float targetExtension;
        private float targetCrosswind;
        private float targetOutwardWind;
        private float targetGust;
        private float targetTurbulence;
        private float targetExposure = 1.0F;

        private State(BlockPos pos, float rendererYawDegrees, float phase) {
            this.pos = pos;
            this.rendererYawDegrees = rendererYawDegrees;
            this.phase = phase;
        }

        private void tick(ClientLevel level, float simulationTime, boolean wall) {
            float deltaSeconds = Float.isNaN(this.lastTickTime)
                    ? 0.05F
                    : Mth.clamp(simulationTime - this.lastTickTime, 0.0F, 0.1F);
            this.lastTickTime = simulationTime;
            copyPrevious();

            if (simulationTime - this.lastSampleTime >= SAMPLE_INTERVAL_SECONDS) {
                sample(level, wall);
                this.lastSampleTime = simulationTime;
            }

            this.extension = ToucanMotion.smoothExp(this.extension, this.targetExtension,
                    this.targetExtension > this.extension ? 7.5F : 3.2F, deltaSeconds);
            this.trailingExtension = ToucanMotion.smoothExp(this.trailingExtension, this.targetExtension,
                    this.targetExtension > this.trailingExtension ? 4.2F : 2.1F, deltaSeconds);
            this.crosswind = ToucanMotion.smoothExp(this.crosswind, this.targetCrosswind, 5.0F, deltaSeconds);
            this.outwardWind = ToucanMotion.smoothExp(this.outwardWind, this.targetOutwardWind, 5.0F, deltaSeconds);
            this.gust = ToucanMotion.smoothExp(this.gust, this.targetGust,
                    this.targetGust > this.gust ? 9.0F : 3.0F, deltaSeconds);
            this.turbulence = ToucanMotion.smoothExp(this.turbulence, this.targetTurbulence, 8.0F, deltaSeconds);
            this.exposure = ToucanMotion.smoothExp(this.exposure, this.targetExposure, 4.0F, deltaSeconds);

            // Model the vanilla flag as a light rigid panel hanging from its top edge. The spring
            // gives gusts a little overshoot and follow-through without introducing cloth geometry.
            float hingeTarget = Mth.clamp(this.targetExtension + this.targetGust * 0.045F, 0.0F, 1.1F);
            this.hingeVelocity += ((hingeTarget - this.hingeResponse) * 19.0F
                    - this.hingeVelocity * 6.2F) * deltaSeconds;
            this.hingeResponse = Mth.clamp(
                    this.hingeResponse + this.hingeVelocity * deltaSeconds,
                    -0.06F,
                    1.14F
            );

            this.sideVelocity += ((this.targetCrosswind - this.sideResponse) * 15.0F
                    - this.sideVelocity * 6.8F) * deltaSeconds;
            this.sideResponse = Mth.clamp(
                    this.sideResponse + this.sideVelocity * deltaSeconds,
                    -1.1F,
                    1.1F
            );
        }

        private void sample(ClientLevel level, boolean wall) {
            WindSample sample = DynamicWindManager.sampleWind(level, this.pos);
            float yawRadians = this.rendererYawDegrees * Mth.DEG_TO_RAD;
            float sinYaw = Mth.sin(yawRadians);
            float cosYaw = Mth.cos(yawRadians);
            float localSide = sample.directionX() * cosYaw - sample.directionZ() * sinYaw;
            float localOutward = sample.directionX() * sinYaw + sample.directionZ() * cosYaw;
            float ambient = sample.ambientStrength() * (float) ClientConfig.BANNER_WIND_STRENGTH.getAsDouble();
            float gustStrength = sample.gustStrength() * (float) ClientConfig.BANNER_GUST_RESPONSE.getAsDouble();
            float effectiveStrength = Math.max(0.0F, ambient + gustStrength);
            // The vanilla banner cuboid is mounted against one side of its pole and cannot fold
            // along its width. Only wind directed away from that mounting side should lift it;
            // back-facing or edge-on wind leaves it pressed close to the pole instead of rotating
            // the cloth through the support. Wall banners retain a small sheltered response.
            float directionalPressure = wall
                    ? Mth.clamp(0.35F + localOutward * 0.65F, 0.1F, 1.0F)
                    : Mth.lerp(Math.max(0.0F, localOutward), 0.12F, 1.0F);

            this.targetExtension = (1.0F - (float) Math.exp(-effectiveStrength * 0.85F)) * directionalPressure;
            this.targetCrosswind = localSide * this.targetExtension;
            this.targetOutwardWind = localOutward * this.targetExtension;
            this.targetGust = Mth.clamp(gustStrength, 0.0F, 2.0F);
            this.targetTurbulence = Mth.clamp(
                    sample.turbulence() * (float) ClientConfig.BANNER_TURBULENCE_RESPONSE.getAsDouble(),
                    0.0F,
                    2.0F
            );
            this.targetExposure = sample.exposure();
        }

        private void copyPrevious() {
            this.previousExtension = this.extension;
            this.previousTrailingExtension = this.trailingExtension;
            this.previousCrosswind = this.crosswind;
            this.previousOutwardWind = this.outwardWind;
            this.previousGust = this.gust;
            this.previousTurbulence = this.turbulence;
            this.previousExposure = this.exposure;
            this.previousHingeResponse = this.hingeResponse;
            this.previousSideResponse = this.sideResponse;
        }

        public float phase() {
            return this.phase;
        }

        public float extension(float partialTick) {
            return Mth.lerp(partialTick, this.previousExtension, this.extension);
        }

        public float trailingExtension(float partialTick) {
            return Mth.lerp(partialTick, this.previousTrailingExtension, this.trailingExtension);
        }

        public float crosswind(float partialTick) {
            return Mth.lerp(partialTick, this.previousCrosswind, this.crosswind);
        }

        public float outwardWind(float partialTick) {
            return Mth.lerp(partialTick, this.previousOutwardWind, this.outwardWind);
        }

        public float gust(float partialTick) {
            return Mth.lerp(partialTick, this.previousGust, this.gust);
        }

        public float turbulence(float partialTick) {
            return Mth.lerp(partialTick, this.previousTurbulence, this.turbulence);
        }

        public float exposure(float partialTick) {
            return Mth.lerp(partialTick, this.previousExposure, this.exposure);
        }

        public float hingeResponse(float partialTick) {
            return Mth.lerp(partialTick, this.previousHingeResponse, this.hingeResponse);
        }

        public float sideResponse(float partialTick) {
            return Mth.lerp(partialTick, this.previousSideResponse, this.sideResponse);
        }
    }
}
