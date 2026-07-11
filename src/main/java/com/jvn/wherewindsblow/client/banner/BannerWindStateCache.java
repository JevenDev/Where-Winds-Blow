package com.jvn.wherewindsblow.client.banner;

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

            this.extension = approach(this.extension, this.targetExtension, deltaSeconds,
                    this.targetExtension > this.extension ? 7.5F : 3.2F);
            this.trailingExtension = approach(this.trailingExtension, this.targetExtension, deltaSeconds,
                    this.targetExtension > this.trailingExtension ? 4.2F : 2.1F);
            this.crosswind = approach(this.crosswind, this.targetCrosswind, deltaSeconds, 5.0F);
            this.outwardWind = approach(this.outwardWind, this.targetOutwardWind, deltaSeconds, 5.0F);
            this.gust = approach(this.gust, this.targetGust, deltaSeconds,
                    this.targetGust > this.gust ? 9.0F : 3.0F);
            this.turbulence = approach(this.turbulence, this.targetTurbulence, deltaSeconds, 8.0F);
            this.exposure = approach(this.exposure, this.targetExposure, deltaSeconds, 4.0F);
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
            float wallPressure = wall ? Mth.clamp(0.35F + localOutward * 0.65F, 0.1F, 1.0F) : 1.0F;

            this.targetExtension = (1.0F - (float) Math.exp(-effectiveStrength * 0.85F)) * wallPressure;
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
        }

        private static float approach(float current, float target, float deltaSeconds, float response) {
            return Mth.lerp(1.0F - (float) Math.exp(-deltaSeconds * response), current, target);
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
    }
}
