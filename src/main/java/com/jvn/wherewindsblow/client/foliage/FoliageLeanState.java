package com.jvn.wherewindsblow.client.foliage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;

public final class FoliageLeanState {
    private static final Map<BlockPos, LeanVector> ACTIVE = new ConcurrentHashMap<>();

    private FoliageLeanState() {
    }

    public static LeanVector get(BlockPos pos) {
        return ACTIVE.get(pos);
    }

    static void replaceWith(Map<BlockPos, LeanVector> next) {
        ACTIVE.clear();
        ACTIVE.putAll(next);
    }

    static void clear() {
        ACTIVE.clear();
    }

    public record LeanVector(float dirX, float dirZ, float intensity) {
    }
}
