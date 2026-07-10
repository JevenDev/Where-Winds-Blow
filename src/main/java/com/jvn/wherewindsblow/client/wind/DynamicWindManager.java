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

        Direction direction = configuredDirection();
        state = new GlobalWindState(
                direction.x(), direction.z(),
                direction.x(), direction.z(),
                direction.x(), direction.z(),
                weatherPower, targetWeatherPower, weatherPower,
                0.0F, 0.0F, 0.0F,
                weatherPower, rainLevel, thunderLevel,
                1.0F
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

        if (level == null) {
            state = STILL_STATE;
            simulationTimeSeconds = 0.0F;
            simulationSeed = 0L;
            if (hadLevel) {
                WhereWindsBlow.LOGGER.debug("Reset client wind simulation after leaving the world.");
            }
            return;
        }

        Direction direction = configuredDirection();
        float weatherPower = targetWeatherWindPower(level);
        state = new GlobalWindState(
                direction.x(), direction.z(),
                direction.x(), direction.z(),
                direction.x(), direction.z(),
                weatherPower, weatherPower, weatherPower,
                0.0F, 0.0F, 0.0F,
                weatherPower, level.getRainLevel(1.0F), level.getThunderLevel(1.0F),
                1.0F
        );
        simulationTimeSeconds = level.getGameTime() * 0.05F;
        simulationSeed = mix64(level.dimension().location().hashCode());
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

    private static Direction configuredDirection() {
        double radians = Math.toRadians(ClientConfig.WIND_DIRECTION_DEGREES.getAsDouble());
        return new Direction((float) Math.sin(radians), (float) -Math.cos(radians));
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
