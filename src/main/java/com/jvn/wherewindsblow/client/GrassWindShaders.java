package com.jvn.wherewindsblow.client;

import com.jvn.wherewindsblow.WhereWindsBlow;
import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.shaders.Uniform;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

public final class GrassWindShaders {
    private static final int COLOR_OFFSET = 3;
    private static final int POSITION_Y_OFFSET = 1;
    private static final int HEIGHT_LEVELS = 31;
    private static final int SHORT_GRASS_MARKER = 1;
    private static final int TALL_GRASS_MARKER = 2;
    private static final int FERN_MARKER = 3;
    private static final int FLOWER_MARKER = 4;

    private static final Set<String> SHORT_GRASS_MODELS = Set.of("short_grass");
    private static final Set<String> TALL_GRASS_MODELS = Set.of("tall_grass", "overgrown_grass");
    private static final Set<String> FERN_MODELS = Set.of("fern", "large_fern");
    private static final Set<String> FLOWER_MODELS = Set.of(
            "dandelion",
            "poppy",
            "blue_orchid",
            "allium",
            "azure_bluet",
            "red_tulip",
            "orange_tulip",
            "white_tulip",
            "pink_tulip",
            "oxeye_daisy",
            "cornflower",
            "lily_of_the_valley",
            "torchflower"
    );

    private GrassWindShaders() {
    }

    public static void wrapPlantModels(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) -> {
            int marker = markerFor(location);
            return marker == 0 ? model : new WindMarkedModel(model, marker);
        });
    }

    public static void updateUniforms(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage != RenderLevelStageEvent.Stage.AFTER_SKY && stage != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }

        float time = event.getRenderTick() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float strength = shouldRenderWind() ? ClientConfig.WIND_STRENGTH.get().floatValue() : 0.0F;
        float speed = ClientConfig.WIND_SPEED.get().floatValue();
        Vec3 cameraPosition = event.getCamera().getPosition();

        setUniforms(GameRenderer.getRendertypeCutoutShader(), time, strength, speed, cameraPosition);
        setUniforms(GameRenderer.getRendertypeCutoutMippedShader(), time, strength, speed, cameraPosition);
    }

    private static boolean shouldRenderWind() {
        if (!ClientConfig.ENABLE_GRASS_WIND.getAsBoolean()) {
            return false;
        }

        return !ClientConfig.DISABLE_WHEN_SHADER_PACK_DETECTED.getAsBoolean() || (!ModList.get().isLoaded("oculus") && !ModList.get().isLoaded("iris"));
    }

    private static void setUniforms(@Nullable ShaderInstance shader, float time, float strength, float speed, Vec3 cameraPosition) {
        if (shader == null) {
            return;
        }

        Uniform windTime = shader.getUniform("WindTime");
        if (windTime != null) {
            windTime.set(time);
        }

        Uniform windStrength = shader.getUniform("WindStrength");
        if (windStrength != null) {
            windStrength.set(strength);
        }

        Uniform windSpeed = shader.getUniform("WindSpeed");
        if (windSpeed != null) {
            windSpeed.set(speed);
        }

        Uniform windOptions = shader.getUniform("WindOptions");
        if (windOptions != null) {
            windOptions.set(
                    ClientConfig.AFFECT_SHORT_GRASS.getAsBoolean() ? 1.0F : 0.0F,
                    ClientConfig.AFFECT_TALL_GRASS.getAsBoolean() ? 1.0F : 0.0F,
                    ClientConfig.AFFECT_FERNS.getAsBoolean() ? 1.0F : 0.0F,
                    ClientConfig.AFFECT_FLOWERS.getAsBoolean() ? 1.0F : 0.0F
            );
        }

        Uniform cameraUniform = shader.getUniform("CameraPosition");
        if (cameraUniform != null) {
            cameraUniform.set((float) cameraPosition.x, (float) cameraPosition.y, (float) cameraPosition.z);
        }
    }

    private static int markerFor(ModelResourceLocation location) {
        String namespace = location.id().getNamespace();
        if (!"minecraft".equals(namespace) && !WhereWindsBlow.MOD_ID.equals(namespace)) {
            return 0;
        }

        String path = location.id().getPath();
        if (SHORT_GRASS_MODELS.contains(path)) {
            return SHORT_GRASS_MARKER;
        }
        if (TALL_GRASS_MODELS.contains(path)) {
            return TALL_GRASS_MARKER;
        }
        if (FERN_MODELS.contains(path)) {
            return FERN_MARKER;
        }
        if (FLOWER_MODELS.contains(path)) {
            return FLOWER_MARKER;
        }

        return 0;
    }

    private static BakedQuad markQuad(BakedQuad quad, int alphaMarker) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        for (int vertex = 0; vertex < 4; vertex++) {
            float y = Float.intBitsToFloat(vertices[vertex * stride + POSITION_Y_OFFSET]);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        float heightRange = maxY - minY;

        for (int vertex = 0; vertex < 4; vertex++) {
            int colorIndex = vertex * stride + COLOR_OFFSET;
            float y = Float.intBitsToFloat(vertices[vertex * stride + POSITION_Y_OFFSET]);
            float normalizedHeight = heightRange < 1.0E-4F ? 1.0F : (y - minY) / heightRange;
            int heightMarker = Math.round(normalizedHeight * HEIGHT_LEVELS);
            int packedMarker = (alphaMarker << 5) | Math.min(heightMarker, HEIGHT_LEVELS);
            int encodedAlpha = 255 - packedMarker;
            vertices[colorIndex] = (encodedAlpha << 24) | (vertices[colorIndex] & 0x00FFFFFF);
        }

        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
    }

    private static final class WindMarkedModel implements BakedModel {
        private final BakedModel delegate;
        private final int alphaMarker;
        private final Map<BakedQuad, BakedQuad> markedQuads = new Object2ObjectOpenHashMap<>();

        private WindMarkedModel(BakedModel delegate, int alphaMarker) {
            this.delegate = delegate;
            this.alphaMarker = alphaMarker;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
            return mark(delegate.getQuads(state, direction, random));
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType renderType) {
            return mark(delegate.getQuads(state, side, rand, data, renderType));
        }

        @Override
        public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
            return delegate.useAmbientOcclusion(state, data, renderType);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return delegate.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return delegate.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return delegate.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return delegate.isCustomRenderer();
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return delegate.getParticleIcon();
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            return delegate.getParticleIcon(data);
        }

        @Override
        public ItemTransforms getTransforms() {
            return delegate.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return delegate.getOverrides();
        }

        private List<BakedQuad> mark(List<BakedQuad> quads) {
            return quads.stream()
                    .map(quad -> markedQuads.computeIfAbsent(quad, q -> markQuad(q, alphaMarker)))
                    .toList();
        }
    }
}
