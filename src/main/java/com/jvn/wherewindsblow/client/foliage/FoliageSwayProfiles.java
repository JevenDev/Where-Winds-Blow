package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.WhereWindsBlow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class FoliageSwayProfiles {
    private static final FoliageSwayProfile NO_PROFILE = new FoliageSwayProfile(
            WhereWindsBlow.IDS.id("none"),
            Set.of(),
            List.of(),
            ResponsiveFoliageType.NONE,
            Integer.MIN_VALUE,
            false,
            0.0F,
            1.0F,
            1.0F,
            0.0F,
            0.0F
    );
    // Block tags are block-wide, so resolving once per block keeps the render hot path constant-time.
    private static volatile Map<Block, FoliageSwayProfile> cache = new ConcurrentHashMap<>();
    private static volatile List<FoliageSwayProfile> profiles = List.of();

    private FoliageSwayProfiles() {
    }

    @Nullable
    static FoliageSwayProfile resolve(BlockState state) {
        FoliageSwayProfile cached = cache.computeIfAbsent(state.getBlock(), block -> {
            FoliageSwayProfile resolved = find(state);
            return resolved != null ? resolved : NO_PROFILE;
        });
        return cached.type() != ResponsiveFoliageType.NONE ? cached : null;
    }

    @Nullable
    private static FoliageSwayProfile find(BlockState state) {
        for (FoliageSwayProfile profile : profiles) {
            if (profile.matches(state)) {
                return profile;
            }
        }
        return null;
    }

    static void apply(List<FoliageSwayProfile> updatedProfiles) {
        List<FoliageSwayProfile> ordered = new ArrayList<>(updatedProfiles);
        ordered.sort((left, right) -> {
            int priority = Integer.compare(right.priority(), left.priority());
            return priority != 0 ? priority : left.id().toString().compareTo(right.id().toString());
        });
        profiles = List.copyOf(ordered);
        cache = new ConcurrentHashMap<>();
        WhereWindsBlow.LOGGER.info("Loaded {} foliage sway profiles.", ordered.size());
    }

    public static void clearCache() {
        cache = new ConcurrentHashMap<>();
    }
}
