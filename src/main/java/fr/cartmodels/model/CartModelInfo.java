package fr.cartmodels.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public record CartModelInfo(int cubeCount, int meshCount, int embeddedTextureCount, int dataSizeBytes) {
    public static CartModelInfo inspect(String normalizedJson) throws IOException {
        try {
            JsonObject model = JsonParser.parseString(normalizedJson).getAsJsonObject();
            if (!model.has("cubes") || !model.get("cubes").isJsonArray()
                    || (model.has("meshes") && !model.get("meshes").isJsonArray())
                    || !model.has("textures") || !model.get("textures").isJsonObject()) {
                throw new IOException("The stored model data has an invalid structure.");
            }
            int textureCount = (int) model.getAsJsonObject("textures").entrySet().stream()
                    .filter(entry -> entry.getValue().isJsonObject()
                            && entry.getValue().getAsJsonObject().has("image"))
                    .count();
            int meshCount = model.has("meshes") ? model.getAsJsonArray("meshes").size() : 0;
            return new CartModelInfo(model.getAsJsonArray("cubes").size(), meshCount,
                    textureCount,
                    normalizedJson.getBytes(StandardCharsets.UTF_8).length);
        } catch (IllegalStateException | JsonSyntaxException exception) {
            throw new IOException("The stored model data is not valid JSON.", exception);
        }
    }
}
