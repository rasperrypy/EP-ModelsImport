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
                  "meshes": [],
                  "textures": {
                    "texture_0": {"image": "aGVsbG8="},
                    "external": {}
                  }
                }
                """;

        CartModelInfo info = CartModelInfo.inspect(model);

        assertEquals(2, info.cubeCount());
        assertEquals(0, info.meshCount());
        assertEquals(1, info.embeddedTextureCount());
        assertEquals(model.getBytes(StandardCharsets.UTF_8).length, info.dataSizeBytes());
    }

    @Test
    void reportsExperimentalMeshCount() throws Exception {
        CartModelInfo info = CartModelInfo.inspect("""
                {"cubes":[],"meshes":[{},{}],"textures":{}}
                """);

        assertEquals(0, info.cubeCount());
        assertEquals(2, info.meshCount());
    }

    @Test
    void rejectsMalformedStoredModelData() {
        assertThrows(java.io.IOException.class, () -> CartModelInfo.inspect("{invalid"));
        assertThrows(java.io.IOException.class, () -> CartModelInfo.inspect("{\"cubes\": []}"));
    }
}
