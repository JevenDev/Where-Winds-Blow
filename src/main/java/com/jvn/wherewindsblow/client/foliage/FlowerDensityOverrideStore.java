package com.jvn.wherewindsblow.client.foliage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

final class FlowerDensityOverrideStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private volatile Map<FlowerKey, ResourceLocation> singleFlowers = Map.of();

    FlowerDensityOverrideStore(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) {
            singleFlowers = read();
        }
    }

    static Path fileForWorld(Path directory, String worldIdentity) {
        UUID worldId = UUID.nameUUIDFromBytes(worldIdentity.getBytes(StandardCharsets.UTF_8));
        return directory.resolve(worldId + ".json");
    }

    boolean isSingle(ResourceLocation dimension, BlockPos pos, ResourceLocation block) {
        return block.equals(singleFlowers.get(new FlowerKey(dimension, pos.asLong())));
    }

    boolean toggle(ResourceLocation dimension, BlockPos pos, ResourceLocation block) throws IOException {
        FlowerKey key = new FlowerKey(dimension, pos.asLong());
        Map<FlowerKey, ResourceLocation> updated = new HashMap<>(singleFlowers);
        boolean single = !block.equals(updated.get(key));
        if (single) {
            updated.put(key, block);
        } else {
            updated.remove(key);
        }
        save(updated);
        singleFlowers = Map.copyOf(updated);
        return single;
    }

    private Map<FlowerKey, ResourceLocation> read() throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = GsonHelper.convertToJsonObject(JsonParser.parseReader(reader), "flower overrides");
            if (GsonHelper.getAsInt(root, "version") != 1) {
                throw new JsonParseException("Unsupported flower override version");
            }
            Map<FlowerKey, ResourceLocation> loaded = new HashMap<>();
            for (JsonElement element : GsonHelper.getAsJsonArray(root, "single_flowers")) {
                JsonObject flower = GsonHelper.convertToJsonObject(element, "single flower");
                ResourceLocation dimension = readId(flower, "dimension");
                ResourceLocation block = readId(flower, "block");
                long position = Long.parseLong(GsonHelper.getAsString(flower, "position"));
                loaded.put(new FlowerKey(dimension, position), block);
            }
            return Map.copyOf(loaded);
        } catch (JsonParseException | NumberFormatException exception) {
            throw new IOException("Invalid flower override file " + file, exception);
        }
    }

    private static ResourceLocation readId(JsonObject json, String name) {
        ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(json, name));
        if (id == null) {
            throw new JsonParseException("Invalid '" + name + "' ID");
        }
        return id;
    }

    private void save(Map<FlowerKey, ResourceLocation> updated) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray flowers = new JsonArray();
        updated.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator
                        .comparing((FlowerKey key) -> key.dimension().toString())
                        .thenComparingLong(FlowerKey::position)))
                .forEach(entry -> {
                    JsonObject flower = new JsonObject();
                    flower.addProperty("dimension", entry.getKey().dimension().toString());
                    flower.addProperty("position", entry.getKey().position());
                    flower.addProperty("block", entry.getValue().toString());
                    flowers.add(flower);
                });
        root.add("single_flowers", flowers);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "flowers-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private record FlowerKey(ResourceLocation dimension, long position) {
    }
}
