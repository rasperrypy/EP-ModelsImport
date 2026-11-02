package fr.cartmodels.model;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CartModelParserTest {
    @Test
    void importsCompleteExampleBlockbenchProject() throws Exception {
        String input = Files.readString(Path.of("examples", "test_cart.bbmodel"));

        String model = CartModelParser.parse("test_cart.bbmodel", input);

        assertTrue(model.contains("\"texture\":\"texture_0\""));
        assertTrue(model.contains("\"width\":64"));
        assertTrue(model.contains("\"height\":64"));
        assertEquals(18, model.split("\"from\":", -1).length - 1);
    }

    @Test
    void parsesMinecraftBlockModelElementsAndExternalTextureFallback() throws Exception {
        String input = """
                {
                  "textures": {"all": "minecraft:block/stone"},
                  "elements": [{
                    "from": [0, 0, 0],
                    "to": [16, 16, 16],
                    "faces": {"north": {"texture": "#all", "uv": [0, 0, 16, 16]}}
                  }]
                }
                """;

        String model = CartModelParser.parse("stone.json", input);

        assertTrue(model.contains("\"cubes\":[{"));
        assertTrue(model.contains("\"texture\":\"\""));
    }

    @Test
    void importsEmbeddedBlockbenchPngTexture() throws Exception {
        String png = Base64.getEncoder().encodeToString(tinyPng());
        String input = """
                {
                  "textures": [{"id": "texture-id", "name": "paint", "path": "data:image/png;base64,%s"}],
                  "elements": [{
                    "type": "cube",
                    "from": [0, 0, 0],
                    "to": [16, 16, 16],
                    "origin": [8, 8, 8],
                    "rotation": [0, 15, 0],
                    "faces": {"north": {"texture": 0, "uv": [0, 0, 1, 1]}}
                  }]
                }
                """.formatted(png);

        String model = CartModelParser.parse("ride.bbmodel", input);

        assertTrue(model.contains("\"texture\":\"texture_0\""));
        assertTrue(model.contains("\"pixelUv\":true"));
        assertTrue(model.contains("\"width\":1"));
        assertTrue(model.contains("\"height\":1"));
        assertTrue(model.contains("\"euler\":[0.0,15.0,0.0]"));
    }

    @Test
    void rejectsUnsupportedBlockbenchMeshGeometry() {
        String input = """
                {"elements":[{"type":"mesh","from":[0,0,0],"to":[1,1,1],"faces":{}}]}
                """;

        IOException exception = assertThrows(IOException.class,
                () -> CartModelParser.parse("mesh.bbmodel", input));

        assertTrue(exception.getMessage().contains("cube elements"));
    }

    private static byte[] tinyPng() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFFFFFF);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
