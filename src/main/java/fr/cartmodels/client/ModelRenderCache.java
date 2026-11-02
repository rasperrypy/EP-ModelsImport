package fr.cartmodels.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
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
        try {
            JsonObject model = JsonParser.parseString(json).getAsJsonObject();
            MODELS.put(name, model);
            JsonObject textures = model.has("textures") ? model.getAsJsonObject("textures") : new JsonObject();
            Map<String, ResourceLocation> locations = new ConcurrentHashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : textures.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject texture = entry.getValue().getAsJsonObject();
                if (texture.has("image")) {
                    locations.put(entry.getKey(),
                            DynamicTextures.register(name, entry.getKey(), texture.get("image").getAsString()));
                }
            }
            MODEL_TEXTURES.put(name, locations);
        } catch (IllegalStateException | JsonSyntaxException exception) {
            LOGGER.error("Received invalid model data for '{}'", name, exception);
        }
    }

    public static JsonObject get(String name) {
        return MODELS.get(name);
    }

    public static ResourceLocation textureLocation(String modelName, String textureId) {
        return MODEL_TEXTURES.getOrDefault(modelName, Map.of()).getOrDefault(textureId,
                ResourceLocation.withDefaultNamespace("block/white_wool"));
    }
}
