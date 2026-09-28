package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.block.SnowLogging;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.registries.GameData;

public final class FoliageInteractionTest {
    private static int checks;

    public static void main(String[] args) throws ReflectiveOperationException {
        for (int groundY : new int[]{-64, 0, 64, 127}) {
            check(!hasImpulse(groundY + 1.0D, groundY + 2.8D, groundY, 1.2F, 0.0F),
                    "standing level with the foliage top does not push it");
            check(!hasImpulse(groundY + 1.25D, groundY + 3.05D, groundY, 1.2F, 0.0F),
                    "standing above foliage does not push it");
            check(!hasImpulse(groundY + 1.0D, groundY + 4.0D, groundY, 2.6F, -1.55F),
                    "large entities and moving wakes cannot reach below the feet");
            check(hasImpulse(groundY, groundY + 1.8D, groundY, 1.2F, 0.0F),
                    "standing inside foliage still pushes it");
            check(hasImpulse(groundY + 0.95D, groundY + 2.75D, groundY, 1.2F, 0.0F),
                    "partial vertical overlap still pushes foliage");
            check(hasImpulse(groundY, groundY + 1.8D, groundY + 1, 1.2F, 0.0F),
                    "the upper half of tall grass still responds to body contact");
            check(!hasImpulse(groundY + 2.0D, groundY + 3.8D, groundY + 1, 1.2F, 0.0F),
                    "standing above tall grass does not push its upper half");
            check(!hasImpulse(groundY - 1.8D, groundY, groundY, 1.2F, 0.0F),
                    "touching the bottom boundary is not vertical overlap");
            check(!hasImpulse(groundY - 2.0D, groundY - 0.2D, groundY, 1.2F, 0.0F),
                    "standing below foliage does not push it");
        }
        snowUsesAmbientOcclusion();
        System.out.println("Passed " + checks + " foliage interaction checks.");
    }

    private static void snowUsesAmbientOcclusion() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
        Block plant = new Block(BlockBehaviour.Properties.of()) {
            @Override
            protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
                builder.add(SnowLogging.LAYERS);
            }
        };
        Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath("where_winds_blow", "ao_test"), plant);
        BakedModel unshadedPlant = (BakedModel) Proxy.newProxyInstance(BakedModel.class.getClassLoader(),
                new Class<?>[]{BakedModel.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("useAmbientOcclusion")) {
                        return method.getReturnType() == boolean.class ? false : TriState.FALSE;
                    }
                    throw new AssertionError("unexpected model call: " + method.getName());
                });
        SnowloggedFoliageModel model = new SnowloggedFoliageModel(unshadedPlant);
        for (int layers : new int[]{0, 1, 8}) {
            BlockState state = plant.defaultBlockState().setValue(SnowLogging.LAYERS, layers);
            check(model.useAmbientOcclusion(state, ModelData.EMPTY, RenderType.solid())
                    == (layers > 0 ? TriState.TRUE : TriState.FALSE), "snow uses ambient occlusion despite an unshaded plant model");
            check(model.useAmbientOcclusion(state, ModelData.EMPTY, RenderType.cutoutMipped()) == TriState.FALSE,
                    "foliage keeps its original cutout shading");
            check(model.useAmbientOcclusion(state, ModelData.EMPTY, null)
                    == (layers > 0 ? TriState.TRUE : TriState.FALSE), "combined rendering also shades snow");
        }
    }

    private static boolean hasImpulse(double minY, double maxY, int blockY, float radius, float trailX)
            throws ReflectiveOperationException {
        Class<?> shapeType = Class.forName(ResponsiveFoliagePhysics.class.getName() + "$InteractionShape");
        Constructor<?> constructor = shapeType.getDeclaredConstructor(
                double.class, double.class, double.class, double.class,
                float.class, float.class, float.class, float.class
        );
        constructor.setAccessible(true);
        Object shape = constructor.newInstance(0.0D, minY, maxY, 0.5D, radius, trailX, 0.0F, 1.0F);
        Method addImpulse = ResponsiveFoliagePhysics.class.getDeclaredMethod(
                "addImpulseForBlock", shapeType, BlockPos.class, long.class, Map.class
        );
        addImpulse.setAccessible(true);
        Map<BlockPos, Object> impulses = new HashMap<>();
        BlockPos pos = new BlockPos(0, blockY, 0);
        addImpulse.invoke(null, shape, pos, 1000L, impulses);
        return impulses.containsKey(pos);
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
        checks++;
    }
}
