package com.jvn.wherewindsblow.wind;

import com.jvn.toucanlib.util.ToucanBoundedCache;
import com.jvn.wherewindsblow.WhereWindsBlow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public final class BiomeWindProfiles {
    private static final int MAX_CACHED_BIOMES = 512;
    private static final BiomeWindProfile FALLBACK = new BiomeWindProfile(
            WhereWindsBlow.IDS.id("default"),
            List.of(),
            List.of(),
            true,
            Integer.MIN_VALUE,
            1.0F,
            1.0F,
            1.0F,
            1.0F,
            1.0F,
            1.0F
    );
    private static final ProfileSet CLIENT = new ProfileSet();
    private static final ProfileSet SERVER = new ProfileSet();
    private static final AtomicLong REVISION = new AtomicLong();

    private BiomeWindProfiles() {
    }

    public static BiomeWindProfile resolve(Holder<Biome> biome, boolean preferServerProfiles) {
        ProfileSet profiles = preferServerProfiles && SERVER.hasLoadedProfiles() ? SERVER : CLIENT;
        return profiles.resolve(biome);
    }

    public static BiomeWindProfile neutral() {
        return FALLBACK;
    }

    public static void apply(List<BiomeWindProfile> profiles, Source source) {
        List<BiomeWindProfile> ordered = new ArrayList<>(profiles);
        ordered.sort((left, right) -> {
            int priority = Integer.compare(right.priority(), left.priority());
            return priority != 0 ? priority : left.id().toString().compareTo(right.id().toString());
        });
        (source == Source.SERVER ? SERVER : CLIENT).replace(List.copyOf(ordered));
        REVISION.incrementAndGet();
        WhereWindsBlow.LOGGER.info("Loaded {} {} biome wind profiles.", ordered.size(), source.logName());
    }

    public static long revision() {
        return REVISION.get();
    }

    public enum Source {
        CLIENT("client"),
        SERVER("server datapack");

        private final String logName;

        Source(String logName) {
            this.logName = logName;
        }

        private String logName() {
            return logName;
        }
    }

    private static final class ProfileSet {
        private List<BiomeWindProfile> profiles = List.of();
        private final Map<ResourceKey<Biome>, BiomeWindProfile> cache = new ToucanBoundedCache<>(64, MAX_CACHED_BIOMES);

        private synchronized BiomeWindProfile resolve(Holder<Biome> biome) {
            ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
            if (key != null) {
                BiomeWindProfile cached = cache.get(key);
                if (cached != null) {
                    return cached;
                }
            }

            BiomeWindProfile resolved = FALLBACK;
            for (BiomeWindProfile profile : profiles) {
                if (profile.matches(biome)) {
                    resolved = profile;
                    break;
                }
            }
            if (key != null) {
                cache.put(key, resolved);
            }
            return resolved;
        }

        private synchronized void replace(List<BiomeWindProfile> updatedProfiles) {
            profiles = updatedProfiles;
            cache.clear();
        }

        private synchronized boolean hasLoadedProfiles() {
            return !profiles.isEmpty();
        }
    }
}
