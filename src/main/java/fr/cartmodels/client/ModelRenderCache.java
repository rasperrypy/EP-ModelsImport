package fr.cartmodels.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModelRenderCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("cartmodels");
    private static final Map<String, JsonObject> MODELS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, ResourceLocation>> MODEL_TEXTURES = new ConcurrentHashMap<>();

    private ModelRenderCache() {
    }

    public static void put(String name, String json) {
        String cacheKey = canonicalName(name);
        try {
            JsonObject model = JsonParser.parseString(json).getAsJsonObject();
            MODELS.put(cacheKey, model);
            JsonObject textures = model.has("textures") ? model.getAsJsonObject("textures") : new JsonObject();
            Map<String, ResourceLocation> locations = new ConcurrentHashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : textures.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject texture = entry.getValue().getAsJsonObject();
                if (texture.has("image")) {
                    locations.put(entry.getKey(),
                            DynamicTextures.register(cacheKey, entry.getKey(), texture.get("image").getAsString()));
                }
            }
            MODEL_TEXTURES.put(cacheKey, locations);
        } catch (IllegalStateException | JsonSyntaxException exception) {
            LOGGER.error("Received invalid model data for '{}'", name, exception);
        }
    }

    public static JsonObject get(String name) {
        return MODELS.get(canonicalName(name));
    }

    public static boolean contains(String name) {
        return MODELS.containsKey(canonicalName(name));
    }

    public static ResourceLocation textureLocation(String modelName, String textureId) {
        return MODEL_TEXTURES.getOrDefault(canonicalName(modelName), Map.of()).getOrDefault(textureId,
                ResourceLocation.withDefaultNamespace("block/white_wool"));
    }

    private static String canonicalName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
