package com.jvn.wherewindsblow.client.wind;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;

public final class WindExposureCacheTest {
    private static int checks;

    public static void main(String[] args) {
        drainsTrackedSections();
        for (int light : new int[]{-1, 0, 7, 15, 16}) {
            BlockPos pos = new BlockPos(54, 72, -2);
            BlockAndTintGetter level = view((proxy, method, arguments) -> {
                check(method.getName().equals("getBrightness"), "uses the view's brightness override");
                check(arguments[0] == LightLayer.SKY, "samples sky light");
                check(arguments[1].equals(pos), "preserves sample position");
                return light;
            });
            float expected = Math.max(0.0F, Math.min(1.0F, light / 15.0F));
            check(WindExposureCache.skyAccess(level, pos) == expected, "normalizes and clamps brightness");
        }

        BlockAndTintGetter wrapped = view((proxy, method, arguments) -> {
            check(method.getName().equals("getLightEngine"), "wrapper requests the backing light engine");
            throw new UnsupportedOperationException();
        });
        BlockAndTintGetter copycatView = view((proxy, method, arguments) -> {
            if (method.getName().equals("getLightEngine")) {
                return wrapped.getLightEngine();
            }
            return InvocationHandler.invokeDefault(proxy, method, arguments);
        });
        check(WindExposureCache.skyAccess(copycatView, BlockPos.ZERO) == 1.0F,
                "unavailable light engine leaves sky exposure open");

        IllegalStateException failure = new IllegalStateException("unexpected lighting failure");
        BlockAndTintGetter brokenView = view((proxy, method, arguments) -> {
            throw failure;
        });
        try {
            WindExposureCache.skyAccess(brokenView, BlockPos.ZERO);
            throw new AssertionError("unexpected lighting failures must propagate");
        } catch (IllegalStateException exception) {
            check(exception == failure, "preserves unexpected lighting failures");
        }
        System.out.println("Passed " + checks + " wind exposure checks.");
    }

    private static void drainsTrackedSections() {
        WindExposureCache.clear();
        BlockPos first = new BlockPos(16, 64, 16);
        BlockPos second = new BlockPos(32, 64, 16);
        WindExposureCache.trackSection(first);
        WindExposureCache.trackSection(first.offset(1, 0, 0));
        WindExposureCache.trackSection(second);

        Set<Long> drained = WindExposureCache.drainTrackedSections();
        check(drained.equals(Set.of(SectionPos.asLong(first), SectionPos.asLong(second))),
                "drains each tracked section once");
        check(WindExposureCache.drainTrackedSections().isEmpty(), "clears tracked sections after invalidation");

        WindExposureCache.trackSection(second);
        check(WindExposureCache.drainTrackedSections().equals(Set.of(SectionPos.asLong(second))),
                "tracks rebuilt sections again after invalidation");
        WindExposureCache.clear();
    }

    private static BlockAndTintGetter view(InvocationHandler handler) {
        return (BlockAndTintGetter) Proxy.newProxyInstance(
                BlockAndTintGetter.class.getClassLoader(),
                new Class<?>[]{BlockAndTintGetter.class},
                handler
        );
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
        checks++;
    }
}
