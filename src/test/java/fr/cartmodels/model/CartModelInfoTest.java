package fr.cartmodels.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CartModelInfoTest {
    @Test
    void reportsCuboidsEmbeddedTexturesAndUtf8Size() throws Exception {
        String model = """
                {
                  "cubes": [{}, {}],
                  "textures": {
                    "texture_0": {"image": "aGVsbG8="},
                    "external": {}
                  }
                }
                """;

        CartModelInfo info = CartModelInfo.inspect(model);

        assertEquals(2, info.cubeCount());
        assertEquals(1, info.embeddedTextureCount());
        assertEquals(model.getBytes(StandardCharsets.UTF_8).length, info.dataSizeBytes());
    }

    @Test
    void rejectsMalformedStoredModelData() {
        assertThrows(java.io.IOException.class, () -> CartModelInfo.inspect("{invalid"));
        assertThrows(java.io.IOException.class, () -> CartModelInfo.inspect("{\"cubes\": []}"));
    }
}
