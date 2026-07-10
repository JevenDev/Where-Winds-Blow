package com.jvn.wherewindsblow.client.wind;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.config.ClientConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Authoritative client-side wind state. Rendering systems may interpolate the simulation clock,
 * but state transitions advance at most once per client tick.
 */
public final class DynamicWindManager {
    private static final float WEATHER_SPEED_SCALE = 0.34F;
    private static final float MAX_UPDATE_DELTA_SECONDS = 0.25F;
    private static final GlobalWindState STILL_STATE = new GlobalWindState(
            0.0F, -1.0F,
            0.0F, -1.0F,
            0.0F, -1.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F,
            1.0F
    );

    @Nullable
    private static ClientLevel activeLevel;
    @Nullable
    private static ResourceKey<Level> activeDimension;
    private static GlobalWindState state = STILL_STATE;
    private static long lastUpdateMillis;
    private static float simulationTimeSeconds;
    private static float simulationSpeed = 1.0F;
    private static long simulationSeed;
    private static long randomState;
    private static float prevailingDirectionDegrees;
    private static float directionStartDegrees;
    private static float currentDirectionDegrees;
    private static float targetDirectionDegrees;
    private static float directionChangeCountdown;
    private static float directionTransitionElapsed;
    private static float directionTransitionDuration;
    private static float ambientStrength;
    private static float lullCountdown;
    private static float lullElapsed;
    private static float lullDuration;
    private static float lullDepth;

    private DynamicWindManager() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        update();
    }

    public static void onClientPauseChange(ClientPauseChangeEvent.Post event) {
        lastUpdateMillis = Util.getMillis();
    }

    public static GlobalWindState currentState() {
        ensureLifecycle();
        return state;
    }

    public static WindSample sampleWind(double x, double y, double z) {
        GlobalWindState current = currentState();
        return new WindSample(
                current.directionX(),
                current.directionZ(),
                current.strength(),
                current.ambientStrength(),
                current.gustStrength(),
                current.turbulence(),
                1.0F,
                current.weatherPower()
        );
    }

    public static WindSample sampleWind(BlockPos pos) {
        return sampleWind(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    /**
     * Returns the tick-owned simulation clock plus a bounded render interpolation remainder.
     */
    public static float simulationTime() {
        ensureLifecycle();
        Minecraft minecraft = Minecraft.getInstance();
        if (activeLevel == null || minecraft.isPaused() || lastUpdateMillis == 0L) {
            return simulationTimeSeconds;
        }

        float remainder = Mth.clamp(
                (Util.getMillis() - lastUpdateMillis) * 0.001F,
                0.0F,
                0.05F
        );
        return simulationTimeSeconds + remainder * simulationSpeed;
    }

    public static long simulationSeed() {
        ensureLifecycle();
        return simulationSeed;
    }

    public static void reset() {
        reset(null);
    }

    public static void reloadConfiguration() {
        float maximumHoldTime = (float) ClientConfig.MAX_DIRECTION_HOLD_TIME.getAsDouble();
        directionChangeCountdown = Math.min(directionChangeCountdown, maximumHoldTime);
        if (!ClientConfig.ENABLE_WIND_LULLS.getAsBoolean()) {
            lullElapsed = 0.0F;
            lullDuration = 0.0F;
            lullDepth = 0.0F;
        } else if (activeLevel != null && lullDuration <= 0.0F && lullCountdown <= 0.0F) {
            lullCountdown = randomLullInterval(state.weatherPower());
        }
    }

    private static void update() {
        ensureLifecycle();
        Minecraft minecraft = Minecraft.getInstance();
        if (activeLevel == null || minecraft.isPaused()) {
            lastUpdateMillis = Util.getMillis();
            return;
        }

        long now = Util.getMillis();
        if (lastUpdateMillis == 0L) {
            lastUpdateMillis = now;
            return;
        }

        float deltaSeconds = Mth.clamp(
                (now - lastUpdateMillis) * 0.001F,
                0.0F,
                MAX_UPDATE_DELTA_SECONDS
        );
        lastUpdateMillis = now;
        if (deltaSeconds <= 0.0F) {
            return;
        }

        float targetWeatherPower = targetWeatherWindPower(activeLevel);
        float response = targetWeatherPower > state.weatherPower() ? 1.7F : 1.05F;
        float blend = 1.0F - (float) Math.exp(-deltaSeconds * response);
        float weatherPower = Mth.lerp(blend, state.weatherPower(), targetWeatherPower);
        float rainLevel = activeLevel.getRainLevel(1.0F);
        float thunderLevel = activeLevel.getThunderLevel(1.0F);
        simulationSpeed = 1.0F + weatherPower * WEATHER_SPEED_SCALE;
        simulationTimeSeconds += deltaSeconds * simulationSpeed;

        boolean dynamicWind = ClientConfig.ENABLE_DYNAMIC_WIND.getAsBoolean();
        Direction direction = updateDirection(deltaSeconds, rainLevel, thunderLevel, dynamicWind);
        float lullAmount = updateLull(deltaSeconds, weatherPower, dynamicWind);
        float ambientTarget = targetAmbientStrength(weatherPower, dynamicWind, lullAmount);
        float ambientResponse = ambientTarget > ambientStrength ? 0.75F : 0.48F;
        float ambientBlend = 1.0F - (float) Math.exp(-deltaSeconds * ambientResponse);
        ambientStrength = Mth.lerp(ambientBlend, ambientStrength, ambientTarget);
        float instability = dynamicWind ? Mth.clamp(rainLevel * 0.18F + thunderLevel * 0.62F, 0.0F, 1.0F) : 0.0F;
        Direction baseDirection = directionFromDegrees(prevailingDirectionDegrees);
        Direction targetDirection = directionFromDegrees(targetDirectionDegrees);
        float transitionProgress = directionTransitionDuration <= 0.0F
                ? 1.0F
                : Mth.clamp(directionTransitionElapsed / directionTransitionDuration, 0.0F, 1.0F);
        state = new GlobalWindState(
                baseDirection.x(), baseDirection.z(),
                direction.x(), direction.z(),
                targetDirection.x(), targetDirection.z(),
                ambientStrength, ambientTarget, ambientStrength,
                0.0F, 0.0F, instability,
                weatherPower, rainLevel, thunderLevel,
                lullAmount,
                transitionProgress
        );
    }

    private static void ensureLifecycle() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            if (activeLevel != null) {
                reset(null);
            }
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        if (activeLevel != level || activeDimension != dimension) {
            reset(level);
        }
    }

    private static void reset(@Nullable ClientLevel level) {
        boolean hadLevel = activeLevel != null;
        activeLevel = level;
        activeDimension = level == null ? null : level.dimension();
        lastUpdateMillis = Util.getMillis();
        simulationSpeed = 1.0F;
        prevailingDirectionDegrees = configuredDirectionDegrees();
        directionStartDegrees = prevailingDirectionDegrees;
        currentDirectionDegrees = prevailingDirectionDegrees;
        targetDirectionDegrees = prevailingDirectionDegrees;
        directionTransitionElapsed = 0.0F;
        directionTransitionDuration = 0.0F;
        ambientStrength = 0.0F;
        lullElapsed = 0.0F;
        lullDuration = 0.0F;
        lullDepth = 0.0F;

        if (level == null) {
            state = STILL_STATE;
            simulationTimeSeconds = 0.0F;
            simulationSeed = 0L;
            if (hadLevel) {
                WhereWindsBlow.LOGGER.debug("Reset client wind simulation after leaving the world.");
            }
            return;
        }

        Direction direction = directionFromDegrees(prevailingDirectionDegrees);
        float weatherPower = targetWeatherWindPower(level);
        ambientStrength = weatherPower * (float) ClientConfig.OVERALL_WIND_STRENGTH.getAsDouble();
        simulationSeed = mix64(level.dimension().location().hashCode());
        randomState = simulationSeed == 0L ? 0x9e3779b97f4a7c15L : simulationSeed;
        directionChangeCountdown = randomDirectionHoldTime();
        lullCountdown = randomLullInterval(weatherPower);
        state = new GlobalWindState(
                direction.x(), direction.z(),
                direction.x(), direction.z(),
                direction.x(), direction.z(),
                ambientStrength, ambientStrength, ambientStrength,
                0.0F, 0.0F, 0.0F,
                weatherPower, level.getRainLevel(1.0F), level.getThunderLevel(1.0F),
                0.0F,
                1.0F
        );
        simulationTimeSeconds = level.getGameTime() * 0.05F;
        WhereWindsBlow.LOGGER.debug(
                "Initialized client wind simulation for {} with seed {}.",
                level.dimension().location(),
                simulationSeed
        );
    }

    private static float targetWeatherWindPower(ClientLevel level) {
        float rain = level.getRainLevel(1.0F);
        float thunder = level.getThunderLevel(1.0F);
        float rainyValue = Mth.lerp(
                rain,
                (float) ClientConfig.CLEAR_WEATHER_WIND_POWER.getAsDouble(),
                (float) ClientConfig.RAIN_WEATHER_WIND_POWER.getAsDouble()
        );
        return Mth.lerp(
                thunder,
                rainyValue,
                (float) ClientConfig.THUNDER_WEATHER_WIND_POWER.getAsDouble()
        );
    }

    private static Direction updateDirection(float deltaSeconds, float rainLevel, float thunderLevel, boolean dynamicWind) {
        prevailingDirectionDegrees = configuredDirectionDegrees();
        boolean dynamicDirection = dynamicWind
                && ClientConfig.WIND_DIRECTION_MODE.get() == ClientConfig.WindDirectionMode.DYNAMIC;
        if (!dynamicDirection) {
            if (Math.abs(Mth.wrapDegrees(targetDirectionDegrees - prevailingDirectionDegrees)) > 0.01F) {
                directionStartDegrees = currentDirectionDegrees;
                targetDirectionDegrees = prevailingDirectionDegrees;
                directionTransitionElapsed = 0.0F;
                directionTransitionDuration = 4.0F;
            }
            advanceDirectionTransition(deltaSeconds);
            return directionFromDegrees(currentDirectionDegrees);
        }

        float weatherPace = 1.0F + rainLevel * 0.22F + thunderLevel * 0.68F;
        directionChangeCountdown -= deltaSeconds * weatherPace;
        if (directionChangeCountdown <= 0.0F) {
            chooseDirectionTarget(rainLevel, thunderLevel);
        }

        advanceDirectionTransition(deltaSeconds);

        return directionFromDegrees(currentDirectionDegrees);
    }

    private static void advanceDirectionTransition(float deltaSeconds) {
        if (directionTransitionElapsed < directionTransitionDuration) {
            directionTransitionElapsed = Math.min(directionTransitionElapsed + deltaSeconds, directionTransitionDuration);
            float progress = directionTransitionElapsed / directionTransitionDuration;
            float eased = progress * progress * (3.0F - 2.0F * progress);
            currentDirectionDegrees = lerpDegrees(directionStartDegrees, targetDirectionDegrees, eased);
        } else {
            currentDirectionDegrees = targetDirectionDegrees;
        }
    }

    private static void chooseDirectionTarget(float rainLevel, float thunderLevel) {
        directionStartDegrees = currentDirectionDegrees;
        float variation = (float) ClientConfig.DYNAMIC_DIRECTION_VARIATION.getAsDouble();
        float ordinaryChange = (float) ClientConfig.MAX_ORDINARY_DIRECTION_CHANGE.getAsDouble() * variation;
        float instability = 1.0F + rainLevel * 0.2F + thunderLevel * 0.75F;
        float change = randomSigned() * ordinaryChange * instability;
        float prevailingCorrection = Mth.wrapDegrees(prevailingDirectionDegrees - currentDirectionDegrees) * 0.22F;
        if (thunderLevel > 0.75F && nextRandomFloat() < 0.045F) {
            change += randomSigned() * (70.0F + 55.0F * thunderLevel);
        }

        targetDirectionDegrees = wrapDegrees360(currentDirectionDegrees + change + prevailingCorrection);
        float holdTime = randomDirectionHoldTime();
        float weatherPace = 1.0F + rainLevel * 0.22F + thunderLevel * 0.68F;
        directionTransitionDuration = Mth.clamp(
                holdTime * (0.16F + nextRandomFloat() * 0.12F) / weatherPace,
                14.0F,
                80.0F
        );
        directionTransitionElapsed = 0.0F;
        directionChangeCountdown = holdTime;
    }

    private static float updateLull(float deltaSeconds, float weatherPower, boolean dynamicWind) {
        if (!dynamicWind
                || !ClientConfig.ENABLE_WIND_LULLS.getAsBoolean()
                || ClientConfig.WIND_LULL_FREQUENCY.getAsDouble() <= 0.0D) {
            lullElapsed = 0.0F;
            lullDuration = 0.0F;
            lullDepth = 0.0F;
            return 0.0F;
        }

        if (lullDuration <= 0.0F) {
            lullCountdown -= deltaSeconds;
            if (lullCountdown <= 0.0F) {
                lullDuration = 24.0F + nextRandomFloat() * 48.0F;
                lullElapsed = 0.0F;
                lullDepth = 0.72F + nextRandomFloat() * 0.28F;
            }
            return 0.0F;
        }

        lullElapsed += deltaSeconds;
        float progress = Mth.clamp(lullElapsed / lullDuration, 0.0F, 1.0F);
        float envelope = smoothEnvelope(progress, 0.22F, 0.66F);
        if (progress >= 1.0F) {
            lullDuration = 0.0F;
            lullElapsed = 0.0F;
            lullCountdown = randomLullInterval(weatherPower);
            return 0.0F;
        }

        return envelope * lullDepth;
    }

    private static float targetAmbientStrength(float weatherPower, boolean dynamicWind, float lullAmount) {
        float overallStrength = (float) ClientConfig.OVERALL_WIND_STRENGTH.getAsDouble();
        if (!dynamicWind) {
            return weatherPower * overallStrength;
        }

        float seedPhase = (simulationSeed & 0xffffL) * (Mth.TWO_PI / 65535.0F);
        float slowRise = Mth.sin(simulationTimeSeconds * 0.014F + seedPhase) * 0.12F;
        float broadDrift = Mth.sin(simulationTimeSeconds * 0.0063F + seedPhase * 1.73F) * 0.08F;
        float baseline = weatherPower * (1.0F + slowRise + broadDrift);
        float lullFloor = (float) ClientConfig.WIND_LULL_STRENGTH.getAsDouble();
        float lullMultiplier = Mth.lerp(lullAmount, 1.0F, lullFloor);
        return Math.max(0.0F, baseline * overallStrength * lullMultiplier);
    }

    private static float randomDirectionHoldTime() {
        float minimum = (float) ClientConfig.MIN_DIRECTION_HOLD_TIME.getAsDouble();
        float maximum = Math.max(minimum, (float) ClientConfig.MAX_DIRECTION_HOLD_TIME.getAsDouble());
        return Mth.lerp(nextRandomFloat(), minimum, maximum);
    }

    private static float randomLullInterval(float weatherPower) {
        float frequency = Math.max(0.05F, (float) ClientConfig.WIND_LULL_FREQUENCY.getAsDouble());
        float weatherDelay = 1.0F + Mth.clamp(weatherPower, 0.0F, 2.5F) * 0.55F;
        return (Mth.lerp(nextRandomFloat(), 150.0F, 390.0F) * weatherDelay) / frequency;
    }

    private static float smoothEnvelope(float progress, float attackEnd, float releaseStart) {
        if (progress < attackEnd) {
            float value = progress / attackEnd;
            return value * value * (3.0F - 2.0F * value);
        }
        if (progress <= releaseStart) {
            return 1.0F;
        }

        float value = 1.0F - (progress - releaseStart) / (1.0F - releaseStart);
        value = Mth.clamp(value, 0.0F, 1.0F);
        return value * value * (3.0F - 2.0F * value);
    }

    private static float lerpDegrees(float from, float to, float progress) {
        return wrapDegrees360(from + Mth.wrapDegrees(to - from) * progress);
    }

    private static float wrapDegrees360(float degrees) {
        return (degrees % 360.0F + 360.0F) % 360.0F;
    }

    private static float configuredDirectionDegrees() {
        return wrapDegrees360((float) ClientConfig.WIND_DIRECTION_DEGREES.getAsDouble());
    }

    private static Direction directionFromDegrees(float degrees) {
        double radians = Math.toRadians(degrees);
        return new Direction((float) Math.sin(radians), (float) -Math.cos(radians));
    }

    private static float randomSigned() {
        return nextRandomFloat() * 2.0F - 1.0F;
    }

    private static float nextRandomFloat() {
        long value = randomState;
        value ^= value << 13;
        value ^= value >>> 7;
        value ^= value << 17;
        randomState = value;
        return (value >>> 40 & 0xffffffL) / 16777216.0F;
    }

    private static long mix64(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }

    private record Direction(float x, float z) {
    }
}
