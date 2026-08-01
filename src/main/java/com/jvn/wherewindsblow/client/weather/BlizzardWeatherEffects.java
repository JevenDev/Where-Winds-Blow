package com.jvn.wherewindsblow.client.weather;

import com.jvn.wherewindsblow.client.wind.DynamicWindManager;
import com.jvn.wherewindsblow.client.wind.WindSample;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.Tags;
import org.joml.Matrix4fStack;

/** Applies snow and sandstorm visibility responses that remain present, but softer, inside shelter. */
public final class BlizzardWeatherEffects {
    private static final float WHITEOUT_START_ENERGY = 0.24F;
    private static final float WHITEOUT_FULL_ENERGY = 1.05F;
    private static final float BLIZZARD_THUNDER_START = 0.08F;
    private static final float BLIZZARD_THUNDER_FULL = 0.65F;
    private static final float FULL_WHITEOUT_FOG_END = 13.0F;
    private static final float FULL_WHITEOUT_FOG_START = 1.5F;
    private static final float ENCLOSED_WHITEOUT_SCALE = 0.88F;
    private static final float PARTIAL_SHELTER_WHITEOUT_SCALE = 0.95F;
    private static final float WHITEOUT_TRANSITION_RATE = 0.16F;
    private static final int FOG_SPHERE_LATITUDE_SEGMENTS = 16;
    private static final int FOG_SPHERE_LONGITUDE_SEGMENTS = 32;

    private BlizzardWeatherEffects() {
    }

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) {
            return;
        }

        StormFogSample storm = stormFogAtCamera(event.getCamera(), (float) event.getPartialTick());
        float whiteout = storm.whiteout();
        if (whiteout <= 0.001F) {
            return;
        }
        if (event.getFarPlaneDistance() <= FULL_WHITEOUT_FOG_END) {
            return;
        }

        // Geometric interpolation keeps shelter from becoming a visibility exploit. A small
        // reduction in whiteout strength moves the fog wall outward modestly instead of exposing
        // most of the configured render distance through a window.
        float originalFarDistance = event.getFarPlaneDistance();
        float farDistance = fogFarDistance(originalFarDistance, whiteout);
        float nearDistance = Mth.lerp(
                whiteout,
                Math.min(event.getNearPlaneDistance(), farDistance * 0.72F),
                FULL_WHITEOUT_FOG_START
        );
        event.setNearPlaneDistance(Math.min(nearDistance, farDistance - 0.5F));
        event.setFarPlaneDistance(farDistance);
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }

        StormFogSample storm = stormFogAtCamera(event.getCamera(), (float) event.getPartialTick());
        float whiteout = storm.whiteout();
        if (whiteout <= 0.001F) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        StormFogColor stormColor = stormFogColor(
                level, (float) event.getPartialTick(), storm
        );
        float stormBlend = stormColorBlend(whiteout);
        event.setRed(Mth.lerp(stormBlend, event.getRed(), stormColor.red()));
        event.setGreen(Mth.lerp(stormBlend, event.getGreen(), stormColor.green()));
        event.setBlue(Mth.lerp(stormBlend, event.getBlue(), stormColor.blue()));
    }

    /**
     * Closes the fog volume at its far plane. Vanilla's sky gradient and some entity render types
     * do not consistently consume terrain fog, so without this depth-tested boundary they can
     * remain visible as colored or dark silhouettes through a full whiteout.
     */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        StormFogSample storm = stormFogAtCamera(event.getCamera(), partialTick);
        BlizzardFogProfile fog = fogProfile(storm);
        if (!fog.active()) {
            return;
        }
        float blend = stormColorBlend(fog.whiteout());
        if (blend <= 0.001F) {
            return;
        }

        renderFogBoundary(
                event,
                fog.farDistance() * 0.995F,
                stormFogColor(level, partialTick, storm),
                blend
        );
    }

    public static BlizzardFogProfile fogProfile(Camera camera, float partialTick) {
        return fogProfile(stormFogAtCamera(camera, partialTick));
    }

    private static BlizzardFogProfile fogProfile(StormFogSample storm) {
        float whiteout = storm.whiteout();
        if (whiteout <= 0.001F) {
            return BlizzardFogProfile.CLEAR;
        }

        float originalFarDistance = Minecraft.getInstance().gameRenderer.getRenderDistance();
        float farDistance = fogFarDistance(originalFarDistance, whiteout);
        float originalNearDistance = originalFarDistance * 0.75F;
        float nearDistance = Mth.lerp(
                whiteout,
                Math.min(originalNearDistance, farDistance * 0.72F),
                FULL_WHITEOUT_FOG_START
        );
        return new BlizzardFogProfile(
                whiteout,
                Math.min(nearDistance, farDistance - 0.5F),
                farDistance
        );
    }

    static float blizzardEnergy(
            WindSample wind,
            float rainLevel,
            float thunder,
            float configuredIntensity
    ) {
        float stormActivity = blizzardActivity(thunder);
        if (stormActivity <= 0.0F) {
            return 0.0F;
        }
        float windStrength = Mth.clamp(wind.strength(), 0.0F, 3.0F);
        return Mth.clamp(
                rainLevel * configuredIntensity * stormActivity * (
                        0.18F + windStrength * 0.06F + thunder * 0.90F
                ),
                0.0F,
                1.35F
        );
    }

    static float snowFogEnergy(
            WindSample wind,
            float rainLevel,
            float thunder,
            float configuredIntensity,
            ClientConfig.SnowFogMode mode
    ) {
        if (mode == ClientConfig.SnowFogMode.DISABLED || configuredIntensity <= 0.0F) {
            return 0.0F;
        }

        float blizzardEnergy = blizzardEnergy(wind, rainLevel, thunder, configuredIntensity);
        if (mode == ClientConfig.SnowFogMode.BLIZZARDS_ONLY) {
            return blizzardEnergy;
        }

        float windStrength = Mth.clamp(wind.strength(), 0.0F, 3.0F);
        float snowfallEnergy = Mth.clamp(
                rainLevel * configuredIntensity * (0.42F + windStrength * 0.06F),
                0.0F,
                1.35F
        );
        return Math.max(snowfallEnergy, blizzardEnergy);
    }

    static float blizzardActivity(float thunder) {
        return smoothFade((thunder - BLIZZARD_THUNDER_START)
                / (BLIZZARD_THUNDER_FULL - BLIZZARD_THUNDER_START));
    }

    private static StormFogSample stormFogAtCamera(Camera camera, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            WhiteoutTransitionState.reset();
            return StormFogSample.CLEAR;
        }

        StormFogSample target = targetStormFogAtCamera(level, camera, partialTick);
        return WhiteoutTransitionState.sample(
                level, level.getGameTime() + (double) partialTick, target
        );
    }

    private static StormFogSample targetStormFogAtCamera(
            ClientLevel level,
            Camera camera,
            float partialTick
    ) {
        BlockPos cameraPos = camera.getBlockPosition();
        Holder<Biome> biomeHolder = level.getBiome(cameraPos);
        float rainLevel = level.getRainLevel(partialTick);
        if (rainLevel <= 0.0F) {
            return StormFogSample.CLEAR;
        }

        WindSample wind = DynamicWindManager.sampleWind(level, cameraPos);
        float thunder = level.getThunderLevel(partialTick);
        float energy;
        StormFogPalette palette;
        Biome biome = biomeHolder.value();
        if (biome.hasPrecipitation()
                && biome.getPrecipitationAt(cameraPos) == Biome.Precipitation.SNOW) {
            ClientConfig.SnowFogMode fogMode = ClientConfig.SNOW_FOG_MODE.get();
            float configuredIntensity = (float) ClientConfig.SNOW_FOG_INTENSITY.getAsDouble();
            if (!ClientConfig.ENABLE_SNOW_EFFECTS.getAsBoolean()
                    || fogMode == ClientConfig.SnowFogMode.DISABLED
                    || configuredIntensity <= 0.0F) {
                return StormFogSample.CLEAR;
            }
            energy = snowFogEnergy(
                    wind, rainLevel, thunder, configuredIntensity, fogMode
            );
            palette = StormFogPalette.SNOW;
        } else if (ClientConfig.ENABLE_DESERT_STORM_EFFECTS.getAsBoolean()
                && (biomeHolder.is(Tags.Biomes.IS_DESERT)
                        || biomeHolder.is(Tags.Biomes.IS_BADLANDS))) {
            // Desert fog is a severe-weather effect, matching the default blizzard threshold.
            // Ordinary rainy desert weather keeps its blowing sand without shortening visibility.
            energy = blizzardEnergy(wind, rainLevel, thunder, 1.0F);
            palette = usesRedSandTint(level, cameraPos, biomeHolder)
                    ? StormFogPalette.RED_SAND
                    : StormFogPalette.SAND;
        } else {
            return StormFogSample.CLEAR;
        }

        float whiteout = smoothFade((energy - WHITEOUT_START_ENERGY)
                / (WHITEOUT_FULL_ENERGY - WHITEOUT_START_ENERGY));
        if (level.canSeeSky(cameraPos)) {
            return StormFogSample.forPalette(whiteout, palette);
        }

        float shelterScale = Mth.lerp(
                Mth.clamp(wind.exposure(), 0.0F, 1.0F),
                ENCLOSED_WHITEOUT_SCALE,
                PARTIAL_SHELTER_WHITEOUT_SCALE
        );
        return StormFogSample.forPalette(whiteout * shelterScale, palette);
    }

    private static boolean usesRedSandTint(
            ClientLevel level,
            BlockPos cameraPos,
            Holder<Biome> biome
    ) {
        if (biome.is(Tags.Biomes.IS_BADLANDS)) {
            return true;
        }
        int surfaceY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING,
                cameraPos.getX(),
                cameraPos.getZ()
        );
        BlockPos surfacePos = new BlockPos(
                cameraPos.getX(),
                Math.max(level.getMinBuildHeight(), surfaceY - 1),
                cameraPos.getZ()
        );
        return level.getBlockState(surfacePos).is(Blocks.RED_SAND);
    }

    private static float fogFarDistance(float originalFarDistance, float whiteout) {
        if (originalFarDistance <= FULL_WHITEOUT_FOG_END) {
            return originalFarDistance;
        }
        return (float) (originalFarDistance * Math.pow(
                FULL_WHITEOUT_FOG_END / originalFarDistance,
                whiteout
        ));
    }

    private static StormFogColor stormFogColor(
            ClientLevel level,
            float partialTick,
            StormFogSample storm
    ) {
        // Keep each storm hue independent from sunset and vanilla weather. Only the sun cycle
        // darkens it, preventing the fog wall from changing color independently of its particles.
        float daylight = Mth.clamp(
                Mth.cos(level.getTimeOfDay(partialTick) * ((float) Math.PI * 2.0F)) * 2.0F + 0.2F,
                0.0F,
                1.0F
        );
        daylight = smoothFade(daylight);
        // Hold onto the darker dawn/dusk range longer, then settle below pure white at midday.
        daylight *= Mth.lerp(daylight, 0.78F, 1.0F);
        StormFogColor snow = new StormFogColor(
                Mth.lerp(daylight, 0.30F, 0.90F),
                Mth.lerp(daylight, 0.36F, 0.93F),
                Mth.lerp(daylight, 0.46F, 0.98F)
        );
        StormFogColor sand = new StormFogColor(
                Mth.lerp(daylight, 0.24F, 0.82F),
                Mth.lerp(daylight, 0.19F, 0.69F),
                Mth.lerp(daylight, 0.12F, 0.46F)
        );
        StormFogColor redSand = new StormFogColor(
                Mth.lerp(daylight, 0.24F, 0.73F),
                Mth.lerp(daylight, 0.10F, 0.35F),
                Mth.lerp(daylight, 0.055F, 0.17F)
        );

        float totalWeight = storm.snowWeight() + storm.sandWeight() + storm.redSandWeight();
        if (totalWeight <= 0.0001F) {
            return snow;
        }
        float inverseWeight = 1.0F / totalWeight;
        return new StormFogColor(
                (snow.red() * storm.snowWeight()
                        + sand.red() * storm.sandWeight()
                        + redSand.red() * storm.redSandWeight()) * inverseWeight,
                (snow.green() * storm.snowWeight()
                        + sand.green() * storm.sandWeight()
                        + redSand.green() * storm.redSandWeight()) * inverseWeight,
                (snow.blue() * storm.snowWeight()
                        + sand.blue() * storm.sandWeight()
                        + redSand.blue() * storm.redSandWeight()) * inverseWeight
        );
    }

    private static float stormColorBlend(float whiteout) {
        return smoothFade(Mth.clamp(whiteout * 2.0F, 0.0F, 1.0F));
    }

    private static void renderFogBoundary(
            RenderLevelStageEvent event,
            float radius,
            StormFogColor color,
            float alpha
    ) {
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            BufferBuilder buffer = Tesselator.getInstance().begin(
                    VertexFormat.Mode.TRIANGLES,
                    DefaultVertexFormat.POSITION_COLOR
            );
            for (int latitude = 0; latitude < FOG_SPHERE_LATITUDE_SEGMENTS; latitude++) {
                float latitude0 = (float) Math.PI
                        * (latitude / (float) FOG_SPHERE_LATITUDE_SEGMENTS - 0.5F);
                float latitude1 = (float) Math.PI
                        * ((latitude + 1.0F) / FOG_SPHERE_LATITUDE_SEGMENTS - 0.5F);
                for (int longitude = 0; longitude < FOG_SPHERE_LONGITUDE_SEGMENTS; longitude++) {
                    float longitude0 = (float) (Math.PI * 2.0)
                            * longitude / FOG_SPHERE_LONGITUDE_SEGMENTS;
                    float longitude1 = (float) (Math.PI * 2.0)
                            * (longitude + 1.0F) / FOG_SPHERE_LONGITUDE_SEGMENTS;
                    addFogSphereCell(
                            buffer, radius, latitude0, latitude1, longitude0, longitude1, color, alpha
                    );
                }
            }
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void addFogSphereCell(
            BufferBuilder buffer,
            float radius,
            float latitude0,
            float latitude1,
            float longitude0,
            float longitude1,
            StormFogColor color,
            float alpha
    ) {
        addFogVertex(buffer, radius, latitude0, longitude0, color, alpha);
        addFogVertex(buffer, radius, latitude1, longitude0, color, alpha);
        addFogVertex(buffer, radius, latitude1, longitude1, color, alpha);
        addFogVertex(buffer, radius, latitude0, longitude0, color, alpha);
        addFogVertex(buffer, radius, latitude1, longitude1, color, alpha);
        addFogVertex(buffer, radius, latitude0, longitude1, color, alpha);
    }

    private static void addFogVertex(
            BufferBuilder buffer,
            float radius,
            float latitude,
            float longitude,
            StormFogColor color,
            float alpha
    ) {
        float horizontalRadius = Mth.cos(latitude) * radius;
        buffer.addVertex(
                Mth.cos(longitude) * horizontalRadius,
                Mth.sin(latitude) * radius,
                Mth.sin(longitude) * horizontalRadius
        ).setColor(color.red(), color.green(), color.blue(), alpha);
    }

    private static float smoothFade(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);
        return value * value * (3.0F - 2.0F * value);
    }

    /**
     * Keeps roof, doorway, biome, and command-driven weather changes from moving the fog wall or
     * switching its palette in one frame. RenderFog and ComputeFogColor can both sample this
     * during a frame, so the level render clock ensures the transition only advances once.
     */
    private static final class WhiteoutTransitionState {
        private static ClientLevel activeLevel;
        private static float whiteout;
        private static float snowWeight;
        private static float sandWeight;
        private static float redSandWeight;
        private static double lastRenderTime = Double.NaN;

        private WhiteoutTransitionState() {
        }

        private static StormFogSample sample(
                ClientLevel level,
                double renderTime,
                StormFogSample target
        ) {
            if (activeLevel != level || !Double.isFinite(lastRenderTime)) {
                activeLevel = level;
                setTarget(target);
                lastRenderTime = renderTime;
                return currentSample();
            }

            double delta = renderTime - lastRenderTime;
            lastRenderTime = renderTime;
            if (delta < 0.0D || delta > 20.0D) {
                setTarget(target);
                return currentSample();
            }

            float blend = 1.0F - (float) Math.exp(-delta * WHITEOUT_TRANSITION_RATE);
            whiteout = approach(whiteout, target.whiteout(), blend);
            snowWeight = approach(snowWeight, target.snowWeight(), blend);
            sandWeight = approach(sandWeight, target.sandWeight(), blend);
            redSandWeight = approach(redSandWeight, target.redSandWeight(), blend);
            return currentSample();
        }

        private static void setTarget(StormFogSample target) {
            whiteout = target.whiteout();
            snowWeight = target.snowWeight();
            sandWeight = target.sandWeight();
            redSandWeight = target.redSandWeight();
        }

        private static StormFogSample currentSample() {
            return new StormFogSample(whiteout, snowWeight, sandWeight, redSandWeight);
        }

        private static float approach(float current, float target, float blend) {
            float result = Mth.lerp(blend, current, target);
            return Math.abs(result - target) < 0.0001F ? target : result;
        }

        private static void reset() {
            activeLevel = null;
            whiteout = 0.0F;
            snowWeight = 0.0F;
            sandWeight = 0.0F;
            redSandWeight = 0.0F;
            lastRenderTime = Double.NaN;
        }
    }

    private record StormFogColor(float red, float green, float blue) {
    }

    private record StormFogSample(
            float whiteout,
            float snowWeight,
            float sandWeight,
            float redSandWeight
    ) {
        private static final StormFogSample CLEAR =
                new StormFogSample(0.0F, 0.0F, 0.0F, 0.0F);

        private static StormFogSample forPalette(float whiteout, StormFogPalette palette) {
            return switch (palette) {
                case SNOW -> new StormFogSample(whiteout, 1.0F, 0.0F, 0.0F);
                case SAND -> new StormFogSample(whiteout, 0.0F, 1.0F, 0.0F);
                case RED_SAND -> new StormFogSample(whiteout, 0.0F, 0.0F, 1.0F);
                case NONE -> CLEAR;
            };
        }
    }

    private enum StormFogPalette {
        NONE,
        SNOW,
        SAND,
        RED_SAND
    }

    public record BlizzardFogProfile(
            float whiteout,
            float nearDistance,
            float farDistance
    ) {
        private static final BlizzardFogProfile CLEAR =
                new BlizzardFogProfile(0.0F, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);

        public boolean active() {
            return whiteout > 0.001F;
        }

        public float visibility(double distance) {
            if (!active() || distance <= nearDistance) {
                return 1.0F;
            }
            float fogProgress = Mth.clamp(
                    (float) ((distance - nearDistance) / Math.max(farDistance - nearDistance, 0.5F)),
                    0.0F,
                    1.0F
            );
            return 1.0F - smoothFade(fogProgress);
        }
    }

}
