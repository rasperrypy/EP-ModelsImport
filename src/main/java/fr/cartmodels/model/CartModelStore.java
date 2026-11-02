package fr.cartmodels.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CartModelStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("cartmodels");
    private static final Pattern VALID_NAME = Pattern.compile("[a-zA-Z0-9_-]{1,32}");
    private static final Map<MinecraftServer, CartModelStore> STORES = new ConcurrentHashMap<>();

    private final Path saveFile;
    private final Map<String, String> models = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private CartModelStore(Path saveFile) {
        this.saveFile = saveFile;
    }

    public static CartModelStore get(MinecraftServer server) {
        return STORES.computeIfAbsent(server, CartModelStore::load);
    }

    public static void unload(MinecraftServer server) {
        STORES.remove(server);
    }

    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    public synchronized void put(String name, String modelJson) throws IOException {
        if (!isValidName(name)) {
            throw new IOException("Names must be 1–32 letters, numbers, underscores, or hyphens.");
        }
        String previous = models.get(name);
        models.put(name, modelJson);
        try {
            save();
        } catch (IOException exception) {
            if (previous == null) {
                models.remove(name);
            } else {
                models.put(name, previous);
            }
            throw exception;
        }
    }

    public synchronized String getModel(String name) {
        return models.get(name);
    }

    public synchronized boolean contains(String name) {
        return models.containsKey(name);
    }

    public synchronized Map<String, String> snapshot() {
        return Collections.unmodifiableMap(new TreeMap<>(models));
    }

    private static CartModelStore load(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("cartmodels_models.json");
        CartModelStore store = new CartModelStore(file);
        if (!Files.exists(file)) {
            return store;
        }
        try {
            JsonObject saved = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : saved.entrySet()) {
                if (!isValidName(entry.getKey()) || !entry.getValue().isJsonPrimitive()
                        || !entry.getValue().getAsJsonPrimitive().isString()) {
                    throw new IOException("The saved model database contains an invalid entry: " + entry.getKey());
                }
                String json = entry.getValue().getAsString();
                if (json.getBytes(StandardCharsets.UTF_8).length > CartModelParser.MAX_FILE_BYTES) {
                    throw new IOException("Saved model '" + entry.getKey() + "' exceeds the 8 MiB limit.");
                }
                store.models.put(entry.getKey(), json);
            }
        } catch (IOException | IllegalStateException | JsonSyntaxException exception) {
            throw new IllegalStateException("Could not load Cart Models data from " + file, exception);
        }
        return store;
    }

    private synchronized void save() throws IOException {
        Files.createDirectories(saveFile.getParent());
        JsonObject saved = new JsonObject();
        models.forEach(saved::addProperty);
        Path temporaryFile = saveFile.resolveSibling(saveFile.getFileName() + ".tmp");
        Files.writeString(temporaryFile, saved.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(temporaryFile, saveFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, saveFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        LOGGER.info("Saved {} imported model(s) to {}", models.size(), saveFile);
    }
}
