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
    void preparesCompactUploadWithoutEditorMetadataButKeepsRenderableData() throws Exception {
        String input = """
                {
                  "name": "Decorated project",
                  "outliner": [{"name": "Editor-only group", "children": ["cube-1"]}],
                  "animations": [{"name": "unused animation", "animators": {}}],
                  "textures": [{
                    "id": "texture-id",
                    "name": "paint",
                    "source": "data:image/png;base64,%s",
                    "relative_path": "textures/paint.png",
                    "saved": true
                  }],
                  "elements": [{
                    "type": "cube",
                    "uuid": "cube-1",
                    "name": "Visible cube",
                    "from": [0, 0, 0],
                    "to": [16, 16, 16],
                    "origin": [8, 8, 8],
                    "rotation": [0, 0, 0],
                    "faces": {"north": {
                      "uv": [0, 0, 16, 16],
                      "texture": "texture-id",
                      "tint": 0,
                      "enabled": true
                    }}
                  }]
                }
                """.formatted(Base64.getEncoder().encodeToString(tinyPng()));

        byte[] prepared = CartModelParser.prepareUpload("compact.bbmodel", input);
        String compact = new String(prepared, java.nio.charset.StandardCharsets.UTF_8);
        String normalized = CartModelParser.parse("compact.bbmodel", compact);

        assertTrue(prepared.length < input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertTrue(!compact.contains("animations"));
        assertTrue(!compact.contains("outliner"));
        assertTrue(!compact.contains("relative_path"));
        assertTrue(compact.contains("\"texture\":\"texture-id\""));
        assertTrue(normalized.contains("\"texture\":\"texture_0\""));
        assertTrue(normalized.contains("\"euler\":[0.0,0.0,0.0]"));
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
        assertTrue(model.contains("\"seats\":[]"));
    }

    @Test
    void storesSeatLockDurationAndRejectsUnsupportedValues() throws Exception {
        String input = """
                {
                  "cartmodels_seat_lock_seconds": 45,
                  "elements": [{
                    "from": [0, 0, 0],
                    "to": [16, 16, 16],
                    "faces": {"north": {"texture": "missing"}}
                  }]
                }
                """;

        assertTrue(CartModelParser.parse("locked.json", input).contains("\"seatLockSeconds\":45"));
        assertTrue(CartModelParser.parse("unlocked.json", input.replace(
                "\"cartmodels_seat_lock_seconds\": 45,", "")).contains("\"seatLockSeconds\":0"));
        assertThrows(IOException.class, () -> CartModelParser.parse("fraction.json",
                input.replace("45", "1.5")));
        assertThrows(IOException.class, () -> CartModelParser.parse("too-long.json",
                input.replace("45", "3601")));
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
    void rejectsBlockbenchMeshesUnlessTheExperimentalOptionIsEnabled() {
        String input = """
                {"elements":[{"type":"mesh","vertices":{"a":[0,0,0],"b":[1,1,0],"c":[0,1,1]},
                  "faces":{"face":{"vertices":["a","b","c"]}}}]}
                """;

        IOException exception = assertThrows(IOException.class,
                () -> CartModelParser.parse("mesh.bbmodel", input));

        assertTrue(exception.getMessage().contains("experimental"));
    }

    @Test
    void importsExperimentalBlockbenchMeshesAndValidatesTheirSeatVoxels() throws Exception {
        String input = """
                {
                  "cartmodels_experimental_meshes": true,
                  "cartmodels_seat_voxel_size": 1,
                  "cartmodels_seats": [[0, 0, 0]],
                  "elements": [{
                    "type": "mesh",
                    "vertices": {"a": [0, 0, 0], "b": [1, 1, 0], "c": [0, 1, 1]},
                    "faces": {"face": {
                      "vertices": ["a", "b", "c"],
                      "uv": {"a": [0, 0], "b": [16, 0], "c": [0, 16]},
                      "texture": 0
                    }}
                  }]
                }
                """;

        String parsed = CartModelParser.parse("mesh.bbmodel", input);

        assertTrue(parsed.contains("\"experimentalMeshes\":true"));
        assertTrue(parsed.contains("\"meshes\":["));
        assertTrue(parsed.contains("\"vertices\":[[0.0,0.0,0.0],[1.0,1.0,0.0],[0.0,1.0,1.0]]"));
        assertTrue(parsed.contains("\"seats\":[[0,0,0]]"));
    }

    @Test
    void acceptsSeatVoxelsThatOverlapTheModelAndRejectsSeatsOutsideIt() throws Exception {
        String input = """
                {
                  "cartmodels_seats": [[0, 0, 0]],
                  "elements": [{
                    "from": [0, 0, 0],
                    "to": [16, 16, 16],
                    "faces": {"north": {"texture": "missing"}}
                  }]
                }
                """;

        String parsed = CartModelParser.parse("seat.json", input);

        assertTrue(parsed.contains("\"seats\":[[0,0,0]]"));
        assertTrue(parsed.contains("\"seatVoxelSize\":16"));
        IOException exception = assertThrows(IOException.class, () -> CartModelParser.parse("seat.json",
                input.replace("[[0, 0, 0]]", "[[1, 0, 0]]")));
        assertTrue(exception.getMessage().contains("does not overlap"));
    }

    @Test
    void acceptsModelPixelSeatVoxelsAndRejectsAnAdjacentVoxel() throws Exception {
        String input = """
                {
                  "cartmodels_seat_voxel_size": 1,
                  "cartmodels_seats": [[4, 5, 6]],
                  "elements": [{
                    "from": [4, 5, 6],
                    "to": [5, 6, 7],
                    "faces": {"north": {"texture": "missing"}}
                  }]
                }
                """;

        String parsed = CartModelParser.parse("seat.json", input);

        assertTrue(parsed.contains("\"seatVoxelSize\":1"));
        assertTrue(parsed.contains("\"seats\":[[4,5,6]]"));
        IOException exception = assertThrows(IOException.class, () -> CartModelParser.parse("seat.json",
                input.replace("[[4, 5, 6]]", "[[5, 5, 6]]")));
        assertTrue(exception.getMessage().contains("does not overlap"));
    }

    private static byte[] tinyPng() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFFFFFF);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
