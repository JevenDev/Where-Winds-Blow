package com.jvn.wherewindsblow.client.foliage;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

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
        System.out.println("Passed " + checks + " foliage interaction checks.");
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
