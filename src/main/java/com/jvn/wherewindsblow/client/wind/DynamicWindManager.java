package com.jvn.wherewindsblow.client.wind;

import com.jvn.toucanlib.client.ToucanMotion;
import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliagePhysics;
import com.jvn.wherewindsblow.client.banner.WindReactiveBannerRenderer;
import com.jvn.wherewindsblow.client.foliage.SodiumFoliageUniforms;
import com.jvn.wherewindsblow.client.lantern.SwingingLanternAssemblyRenderer;
import com.jvn.wherewindsblow.client.smoke.CampfireSmokePlumes;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.jvn.wherewindsblow.wind.BiomeWindProfile;
import com.jvn.wherewindsblow.wind.BiomeWindProfiles;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Authoritative client-side wind state. Rendering systems may interpolate the simulation clock,
 * but state transitions advance at most once per client tick.
 */
public final class DynamicWindManager {
    public static final int MAX_ACTIVE_GUSTS = 3;
    private static final float MAX_UPDATE_DELTA_SECONDS = 0.25F;
    private static final GlobalWindState STILL_STATE = new GlobalWindState(
            0.0F, -1.0F,
            0.0F, -1.0F,
            0.0F, -1.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F,
            1.0F,
            BiomeWindProfiles.neutral().id()
    );
    private static final GustFrontState[] ACTIVE_GUSTS = new GustFrontState[MAX_ACTIVE_GUSTS];
    private static final ThreadLocal<MutableGustContribution> GUST_SCRATCH =
            ThreadLocal.withInitial(MutableGustContribution::new);

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
    private static float gustSpawnCountdown;
    private static int activeGustCount;
    private static float ambientTurbulence;
    private static BiomeWindProfile activeProfile = BiomeWindProfiles.neutral();
    private static float effectiveBaseStrengthMultiplier = 1.0F;
    private static float effectiveGustStrengthMultiplier = 1.0F;
    private static float effectiveTurbulenceMultiplier = 1.0F;
    private static long appliedProfileRevision = -1L;
    private static boolean appliedDynamicWindMode;
    private static ClientConfig.WindDirectionMode appliedDirectionMode;
    private static boolean configModeInitialized;

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
        return sampleWind(null, null, x, y, z);
    }

    private static WindSample sampleWind(
            @Nullable BlockAndTintGetter level,
            @Nullable BlockPos pos,
            double x,
            double y,
            double z
    ) {
        GlobalWindState current = currentState();
        BiomeWindProfile profile = level != null && pos != null ? profileAt(pos) : activeProfile;
        boolean useEffectiveProfile = profile.id().equals(activeProfile.id());
        float baseStrengthMultiplier = useEffectiveProfile
                ? effectiveBaseStrengthMultiplier
                : profile.baseStrengthMultiplier();
        float gustStrengthMultiplier = useEffectiveProfile
                ? effectiveGustStrengthMultiplier
                : profile.gustStrengthMultiplier();
        float turbulenceMultiplier = useEffectiveProfile
                ? effectiveTurbulenceMultiplier
                : profile.turbulenceMultiplier();
        MutableGustContribution gust = GUST_SCRATCH.get();
        sampleGustContribution(x, z, simulationTime(), gust);
        float localAmbient = level != null
                ? ambientStrength * baseStrengthMultiplier
                : current.ambientStrength();
        float localGustStrength = gust.strength() * gustStrengthMultiplier;
        float gustScale = gust.strength() > 0.0001F ? localGustStrength / gust.strength() : 0.0F;
        float directionX = current.directionX() * localAmbient + gust.directionX() * gustScale;
        float directionZ = current.directionZ() * localAmbient + gust.directionZ() * gustScale;
        float directionLength = Mth.sqrt(directionX * directionX + directionZ * directionZ);
        if (directionLength > 0.0001F) {
            directionX /= directionLength;
            directionZ /= directionLength;
        } else {
            directionX = current.directionX();
            directionZ = current.directionZ();
        }

        float exposure = level != null && pos != null
                ? WindExposureCache.exposureAt(level, pos, directionX, directionZ)
                : 1.0F;
        float altitude = level != null ? WindExposureCache.altitudeMultiplier(level, y) : 1.0F;
        if (level != null) {
            altitude = 1.0F + (altitude - 1.0F) * profile.altitudeInfluence();
        }
        float localScale = exposure * altitude;
        float scaledAmbient = localAmbient * localScale;
        float scaledGust = localGustStrength * localScale;
        float rawStrength = Math.max(0.0F, scaledAmbient + scaledGust);
        float visualStrength = visualStrength(rawStrength);
        float visualScale = rawStrength > 0.0001F ? visualStrength / rawStrength : 0.0F;

        return new WindSample(
                directionX,
                directionZ,
                visualStrength,
                scaledAmbient * visualScale,
                scaledGust * visualScale,
                (ambientTurbulence + gust.turbulence()) * turbulenceMultiplier * exposure,
                exposure,
                current.weatherPower(),
                profile.id()
        );
    }

    /**
     * Converts physical wind power into a bounded visual response. Values through 1 retain their
     * full range; stronger weather increasingly becomes steady pressure instead of unbounded
     * displacement. The asymptotic visual maximum is 1.5.
     */
    public static float visualStrength(float rawStrength) {
        float strength = Math.max(rawStrength, 0.0F);
        float excess = Math.max(strength - 1.0F, 0.0F);
        return Math.min(strength, 1.0F) + excess / (1.0F + excess * 2.0F);
    }

    public static WindSample sampleWind(BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null
                ? sampleWind(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                : sampleWind(level, pos);
    }

    public static WindSample sampleWind(BlockAndTintGetter level, BlockPos pos) {
        return sampleWind(level, pos, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
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

    public static BiomeWindProfile activeBiomeProfile() {
        currentState();
        return activeProfile;
    }

    public static float effectiveGustStrengthMultiplier() {
        currentState();
        return effectiveGustStrengthMultiplier;
    }

    public static float effectiveTurbulenceMultiplier() {
        currentState();
        return effectiveTurbulenceMultiplier;
    }

    public static int activeGustCount() {
        currentState();
        return activeGustCount;
    }

    @Nullable
    public static GustFrontState gustFront(int index) {
        currentState();
        if (index < 0 || index >= activeGustCount) {
            return null;
        }
        int activeIndex = 0;
        for (GustFrontState gust : ACTIVE_GUSTS) {
            if (gust == null) {
                continue;
            }
            if (activeIndex == index) {
                return gust;
            }
            activeIndex++;
        }
        return null;
    }

    public static void reset() {
        reset(Minecraft.getInstance().level);
    }

    public static void reloadConfiguration() {
        WindReactiveBannerRenderer.reset();
        boolean dynamicWindMode = ClientConfig.ENABLE_DYNAMIC_WIND.getAsBoolean();
        ClientConfig.WindDirectionMode directionMode = ClientConfig.WIND_DIRECTION_MODE.get();
        boolean modeChanged = configModeInitialized
                && (dynamicWindMode != appliedDynamicWindMode || directionMode != appliedDirectionMode);
        appliedDynamicWindMode = dynamicWindMode;
        appliedDirectionMode = directionMode;
        configModeInitialized = true;
        if (modeChanged && activeLevel != null) {
            reset(activeLevel);
            return;
        }

        WindExposureCache.clear();
        float maximumHoldTime = (float) ClientConfig.MAX_DIRECTION_HOLD_TIME.getAsDouble();
        directionChangeCountdown = Math.min(directionChangeCountdown, maximumHoldTime);
        if (!ClientConfig.ENABLE_WIND_LULLS.getAsBoolean()) {
            lullElapsed = 0.0F;
            lullDuration = 0.0F;
            lullDepth = 0.0F;
        } else if (activeLevel != null && lullDuration <= 0.0F && lullCountdown <= 0.0F) {
            lullCountdown = randomLullInterval(state.weatherPower());
        }
        if (!ClientConfig.ENABLE_GUST_FRONTS.getAsBoolean()) {
            clearGusts();
        } else if (activeLevel != null && gustSpawnCountdown <= 0.0F) {
            gustSpawnCountdown = randomGustInterval(
                    state.rainLevel(),
                    state.thunderLevel(),
                    activeProfile.gustFrequencyMultiplier()
            );
        }
    }

    private static void update() {
        ensureLifecycle();
        refreshProfileRevision();
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
        float weatherPower = ToucanMotion.smoothExp(
                state.weatherPower(), targetWeatherPower, response, deltaSeconds
        );
        float rainLevel = activeLevel.getRainLevel(1.0F);
        float thunderLevel = activeLevel.getThunderLevel(1.0F);
        BiomeWindProfile profile = currentPlayerProfile();
        if (!profile.id().equals(activeProfile.id())) {
            activeProfile = profile;
            gustSpawnCountdown = Math.min(
                    gustSpawnCountdown,
                    randomGustInterval(rainLevel, thunderLevel, profile.gustFrequencyMultiplier())
            );
        } else {
            activeProfile = profile;
        }
        updateEffectiveProfile(deltaSeconds, activeProfile);
        // Wind power controls force, not the rate at which every oscillator and gust clock runs.
        // Coupling these made stronger ambient wind visibly accelerate foliage rocking.
        simulationSpeed = 1.0F;
        simulationTimeSeconds += deltaSeconds * simulationSpeed;

        boolean dynamicWind = ClientConfig.ENABLE_DYNAMIC_WIND.getAsBoolean();
        Direction direction = updateDirection(
                deltaSeconds,
                rainLevel,
                thunderLevel,
                activeProfile.directionInstabilityMultiplier(),
                dynamicWind
        );
        float lullAmount = updateLull(deltaSeconds, weatherPower, dynamicWind);
        float ambientTarget = targetAmbientStrength(weatherPower, dynamicWind, lullAmount);
        float ambientResponse = ambientTarget > ambientStrength ? 0.75F : 0.48F;
        ambientStrength = ToucanMotion.smoothExp(
                ambientStrength, ambientTarget, ambientResponse, deltaSeconds
        );
        float instability = dynamicWind
                ? Mth.clamp(
                        (rainLevel * 0.18F + thunderLevel * 0.62F) * activeProfile.directionInstabilityMultiplier(),
                        0.0F,
                        1.0F
                )
                : 0.0F;
        updateGusts(
                deltaSeconds,
                rainLevel,
                thunderLevel,
                lullAmount,
                activeProfile.gustFrequencyMultiplier(),
                dynamicWind
        );
        ambientTurbulence = dynamicWind
                ? (0.035F + rainLevel * 0.075F + thunderLevel * 0.24F)
                        * (float) ClientConfig.TURBULENCE_STRENGTH.getAsDouble()
                : 0.0F;
        double sampleX = Minecraft.getInstance().player == null ? 0.0D : Minecraft.getInstance().player.getX();
        double sampleZ = Minecraft.getInstance().player == null ? 0.0D : Minecraft.getInstance().player.getZ();
        MutableGustContribution localGust = GUST_SCRATCH.get();
        sampleGustContribution(sampleX, sampleZ, simulationTimeSeconds, localGust);
        float profiledAmbient = ambientStrength * effectiveBaseStrengthMultiplier;
        float profiledAmbientTarget = ambientTarget * effectiveBaseStrengthMultiplier;
        float profiledGust = localGust.strength() * effectiveGustStrengthMultiplier;
        float profiledTurbulence = (ambientTurbulence + localGust.turbulence())
                * effectiveTurbulenceMultiplier;
        float profiledAmbientTurbulence = ambientTurbulence * effectiveTurbulenceMultiplier;
        Direction baseDirection = directionFromDegrees(prevailingDirectionDegrees);
        Direction targetDirection = directionFromDegrees(targetDirectionDegrees);
        float transitionProgress = directionTransitionDuration <= 0.0F
                ? 1.0F
                : Mth.clamp(directionTransitionElapsed / directionTransitionDuration, 0.0F, 1.0F);
        state = new GlobalWindState(
                baseDirection.x(), baseDirection.z(),
                direction.x(), direction.z(),
                targetDirection.x(), targetDirection.z(),
                profiledAmbient + profiledGust, profiledAmbientTarget, profiledAmbient,
                profiledGust, profiledTurbulence, profiledAmbientTurbulence, instability,
                weatherPower, rainLevel, thunderLevel,
                lullAmount,
                transitionProgress,
                activeProfile.id()
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
        resetConsumers();
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
        ambientTurbulence = 0.0F;
        activeProfile = BiomeWindProfiles.neutral();
        setEffectiveProfile(activeProfile);
        appliedProfileRevision = BiomeWindProfiles.revision();
        appliedDynamicWindMode = ClientConfig.ENABLE_DYNAMIC_WIND.getAsBoolean();
        appliedDirectionMode = ClientConfig.WIND_DIRECTION_MODE.get();
        configModeInitialized = true;
        lullElapsed = 0.0F;
        lullDuration = 0.0F;
        lullDepth = 0.0F;
        gustSpawnCountdown = 0.0F;
        clearGusts();
        WindExposureCache.clear();

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
        activeProfile = currentPlayerProfile();
        setEffectiveProfile(activeProfile);
        directionChangeCountdown = randomDirectionHoldTime();
        lullCountdown = randomLullInterval(weatherPower);
        gustSpawnCountdown = randomGustInterval(
                level.getRainLevel(1.0F),
                level.getThunderLevel(1.0F),
                activeProfile.gustFrequencyMultiplier()
        );
        float profiledAmbient = ambientStrength * effectiveBaseStrengthMultiplier;
        state = new GlobalWindState(
                direction.x(), direction.z(),
                direction.x(), direction.z(),
                direction.x(), direction.z(),
                profiledAmbient, profiledAmbient, profiledAmbient,
                0.0F, 0.0F, 0.0F, 0.0F,
                weatherPower, level.getRainLevel(1.0F), level.getThunderLevel(1.0F),
                0.0F,
                1.0F,
                activeProfile.id()
        );
        // This clock is uploaded to shaders as a float. Seeding it from total world game time
        // eventually destroys sub-frame precision in older worlds and makes sine-based foliage
        // motion snap between poses. All gust records reset with the manager, so a small local
        // clock is both sufficient and stable.
        simulationTimeSeconds = 0.0F;
        WhereWindsBlow.LOGGER.debug(
                "Initialized client wind simulation for {} with seed {}.",
                level.dimension().location(),
                simulationSeed
        );
    }

    private static void resetConsumers() {
        ResponsiveFoliagePhysics.reset();
        SodiumFoliageUniforms.reset();
        SwingingLanternAssemblyRenderer.reset();
        CampfireSmokePlumes.reset();
        WindStreakRenderer.reset();
        WindReactiveBannerRenderer.reset();
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

    private static void updateEffectiveProfile(float deltaSeconds, BiomeWindProfile target) {
        // Player biome selection can alternate every tick along a boundary. Smooth only the
        // multipliers that directly drive rendered motion so that boundary crossings remain calm.
        effectiveBaseStrengthMultiplier = ToucanMotion.smoothExp(
                effectiveBaseStrengthMultiplier, target.baseStrengthMultiplier(), 0.7F, deltaSeconds
        );
        effectiveGustStrengthMultiplier = ToucanMotion.smoothExp(
                effectiveGustStrengthMultiplier, target.gustStrengthMultiplier(), 0.7F, deltaSeconds
        );
        effectiveTurbulenceMultiplier = ToucanMotion.smoothExp(
                effectiveTurbulenceMultiplier, target.turbulenceMultiplier(), 0.7F, deltaSeconds
        );
    }

    private static void setEffectiveProfile(BiomeWindProfile profile) {
        effectiveBaseStrengthMultiplier = profile.baseStrengthMultiplier();
        effectiveGustStrengthMultiplier = profile.gustStrengthMultiplier();
        effectiveTurbulenceMultiplier = profile.turbulenceMultiplier();
    }

    private static BiomeWindProfile currentPlayerProfile() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null
                ? BiomeWindProfiles.neutral()
                : profileAt(minecraft.player.blockPosition());
    }

    private static BiomeWindProfile profileAt(BlockPos pos) {
        if (!ClientConfig.ENABLE_BIOME_WIND_PROFILES.getAsBoolean()) {
            return BiomeWindProfiles.neutral();
        }
        ClientLevel clientLevel = activeLevel != null ? activeLevel : Minecraft.getInstance().level;
        if (clientLevel == null) {
            return BiomeWindProfiles.neutral();
        }
        boolean preferServerProfiles = Minecraft.getInstance().getSingleplayerServer() != null;
        return BiomeWindProfiles.resolve(clientLevel.getBiome(pos), preferServerProfiles);
    }

    private static void refreshProfileRevision() {
        long revision = BiomeWindProfiles.revision();
        if (revision == appliedProfileRevision) {
            return;
        }
        appliedProfileRevision = revision;
        WindExposureCache.clear();
        gustSpawnCountdown = Math.min(gustSpawnCountdown, 1.0F);
    }

    private static Direction updateDirection(
            float deltaSeconds,
            float rainLevel,
            float thunderLevel,
            float profileInstability,
            boolean dynamicWind
    ) {
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

        float weatherPace = 1.0F + (rainLevel * 0.22F + thunderLevel * 0.68F) * profileInstability;
        directionChangeCountdown -= deltaSeconds * weatherPace;
        if (directionChangeCountdown <= 0.0F) {
            chooseDirectionTarget(rainLevel, thunderLevel, profileInstability);
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

    private static void chooseDirectionTarget(float rainLevel, float thunderLevel, float profileInstability) {
        directionStartDegrees = currentDirectionDegrees;
        float variation = (float) ClientConfig.DYNAMIC_DIRECTION_VARIATION.getAsDouble();
        float ordinaryChange = (float) ClientConfig.MAX_ORDINARY_DIRECTION_CHANGE.getAsDouble() * variation;
        float instability = 1.0F + (rainLevel * 0.2F + thunderLevel * 0.75F) * profileInstability;
        float change = randomSigned() * ordinaryChange * instability;
        float prevailingCorrection = Mth.wrapDegrees(prevailingDirectionDegrees - currentDirectionDegrees) * 0.22F;
        if (thunderLevel > 0.75F && nextRandomFloat() < 0.045F) {
            change += randomSigned() * (70.0F + 55.0F * thunderLevel);
        }

        targetDirectionDegrees = wrapDegrees360(currentDirectionDegrees + change + prevailingCorrection);
        float holdTime = randomDirectionHoldTime();
        float weatherPace = 1.0F + (rainLevel * 0.22F + thunderLevel * 0.68F) * profileInstability;
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

    private static void updateGusts(
            float deltaSeconds,
            float rainLevel,
            float thunderLevel,
            float lullAmount,
            float profileFrequency,
            boolean dynamicWind
    ) {
        float time = simulationTimeSeconds;
        activeGustCount = 0;
        for (int index = 0; index < ACTIVE_GUSTS.length; index++) {
            GustFrontState gust = ACTIVE_GUSTS[index];
            if (gust != null && !gust.isAlive(time)) {
                ACTIVE_GUSTS[index] = null;
                gust = null;
            }
            if (gust != null) {
                activeGustCount++;
            }
        }

        boolean gustsEnabled = dynamicWind
                && ClientConfig.ENABLE_GUST_FRONTS.getAsBoolean()
                && ClientConfig.GUST_FREQUENCY.getAsDouble() > 0.0D
                && ClientConfig.GUST_STRENGTH.getAsDouble() > 0.0D;
        if (!gustsEnabled) {
            clearGusts();
            return;
        }

        gustSpawnCountdown -= deltaSeconds;
        if (gustSpawnCountdown > 0.0F || activeGustCount >= MAX_ACTIVE_GUSTS || lullAmount > 0.42F) {
            return;
        }

        spawnGust(rainLevel, thunderLevel);
        gustSpawnCountdown = randomGustInterval(rainLevel, thunderLevel, profileFrequency);
    }

    private static void spawnGust(float rainLevel, float thunderLevel) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        int slot = firstFreeGustSlot();
        if (slot < 0) {
            return;
        }

        float directionOffset = randomSigned() * (7.0F + rainLevel * 5.0F + thunderLevel * 13.0F);
        Direction direction = directionFromDegrees(currentDirectionDegrees + directionOffset);
        float crossX = -direction.z();
        float crossZ = direction.x();
        float spawnDistance = 62.0F + nextRandomFloat() * 52.0F;
        float crossOffset = randomSigned() * 28.0F;
        float originX = (float) minecraft.player.getX() - direction.x() * spawnDistance + crossX * crossOffset;
        float originZ = (float) minecraft.player.getZ() - direction.z() * spawnDistance + crossZ * crossOffset;
        float configuredSpeed = (float) ClientConfig.GUST_TRAVEL_SPEED.getAsDouble();
        float speed = configuredSpeed * (0.84F + nextRandomFloat() * 0.34F);
        float width = (float) ClientConfig.GUST_WIDTH.getAsDouble() * (0.82F + nextRandomFloat() * 0.36F);
        float weatherScale = 1.0F + rainLevel * 0.72F + thunderLevel * 1.35F;
        float peakStrength = (0.16F + nextRandomFloat() * 0.24F)
                * weatherScale
                * (float) ClientConfig.GUST_STRENGTH.getAsDouble()
                * (float) ClientConfig.OVERALL_WIND_STRENGTH.getAsDouble();
        if (thunderLevel > 0.72F && nextRandomFloat() < 0.18F) {
            peakStrength *= 1.25F + nextRandomFloat() * 0.35F;
        }
        float turbulence = (0.06F + rainLevel * 0.07F + thunderLevel * 0.21F)
                * (0.82F + nextRandomFloat() * 0.38F)
                * (float) ClientConfig.TURBULENCE_STRENGTH.getAsDouble();
        float duration = (spawnDistance + 150.0F + width * 2.0F) / Math.max(speed, 0.1F);
        ACTIVE_GUSTS[slot] = new GustFrontState(
                simulationTimeSeconds,
                Mth.clamp(duration, 24.0F, 72.0F),
                0.14F + nextRandomFloat() * 0.08F,
                0.68F + nextRandomFloat() * 0.12F,
                originX,
                originZ,
                direction.x(),
                direction.z(),
                speed,
                width,
                peakStrength,
                turbulence,
                nextRandomFloat() * Mth.TWO_PI,
                0.035F + turbulence * 0.24F
        );
        activeGustCount++;
    }

    private static void sampleGustContribution(double x, double z, float time, MutableGustContribution result) {
        float strength = 0.0F;
        float directionX = 0.0F;
        float directionZ = 0.0F;
        float turbulence = 0.0F;
        for (GustFrontState gust : ACTIVE_GUSTS) {
            if (gust == null) {
                continue;
            }

            float localStrength = gust.strengthAt(x, z, time);
            if (localStrength <= 0.0001F) {
                continue;
            }

            float crossVariation = gust.crossVariation(x, z, time);
            float localDirectionX = gust.directionX() + -gust.directionZ() * crossVariation;
            float localDirectionZ = gust.directionZ() + gust.directionX() * crossVariation;
            float length = Mth.sqrt(localDirectionX * localDirectionX + localDirectionZ * localDirectionZ);
            if (length > 0.0001F) {
                localDirectionX /= length;
                localDirectionZ /= length;
            }
            strength += localStrength;
            directionX += localDirectionX * localStrength;
            directionZ += localDirectionZ * localStrength;
            turbulence += gust.turbulence() * Mth.clamp(localStrength / Math.max(gust.peakStrength(), 0.001F), 0.0F, 1.0F);
        }

        result.set(strength, directionX, directionZ, turbulence);
    }

    private static int firstFreeGustSlot() {
        for (int index = 0; index < ACTIVE_GUSTS.length; index++) {
            if (ACTIVE_GUSTS[index] == null) {
                return index;
            }
        }
        return -1;
    }

    private static void clearGusts() {
        for (int index = 0; index < ACTIVE_GUSTS.length; index++) {
            ACTIVE_GUSTS[index] = null;
        }
        activeGustCount = 0;
    }

    private static float randomGustInterval(float rainLevel, float thunderLevel, float profileFrequency) {
        float frequency = Math.max(
                0.05F,
                (float) ClientConfig.GUST_FREQUENCY.getAsDouble() * profileFrequency
        );
        float weatherFrequency = 1.0F + rainLevel * 0.85F + thunderLevel * 2.25F;
        return Mth.lerp(nextRandomFloat(), 48.0F, 118.0F) / (frequency * weatherFrequency);
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

    private static final class MutableGustContribution {
        private float strength;
        private float directionX;
        private float directionZ;
        private float turbulence;

        private void set(float strength, float directionX, float directionZ, float turbulence) {
            this.strength = strength;
            this.directionX = directionX;
            this.directionZ = directionZ;
            this.turbulence = turbulence;
        }

        private float strength() {
            return strength;
        }

        private float directionX() {
            return directionX;
        }

        private float directionZ() {
            return directionZ;
        }

        private float turbulence() {
            return turbulence;
        }
    }
}
