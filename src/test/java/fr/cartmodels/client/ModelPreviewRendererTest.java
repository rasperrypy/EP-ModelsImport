package fr.cartmodels.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import fr.cartmodels.model.ModelSeat;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelPreviewRendererTest {
    @Test
    void rendersVisibleIsometricPreviewAndSupportsRotation() {
        var model = JsonParser.parseString("""
                {
                  "cubes": [{
                    "from": [-8, 0, -4],
                    "to": [8, 16, 4],
                    "faces": {}
                  }],
                  "textures": {}
                }
                """).getAsJsonObject();

        BufferedImage northEast = ModelPreviewRenderer.render(model, 0);
        BufferedImage southWest = ModelPreviewRenderer.render(model, 2);

        assertEquals(128, northEast.getWidth());
        assertEquals(128, northEast.getHeight());
        assertTrue(countOpaquePixels(northEast) > 100);
        assertTrue(countDifferentPixels(northEast, southWest) > 20);
    }

    @Test
    void exposesSelectableVoxelsAndMapsPreviewClicksBackToTheirCoordinates() {
        var model = JsonParser.parseString("""
                {
                  "cubes": [{
                    "from": [0, 0, 0],
                    "to": [16, 16, 16],
                    "faces": {}
                  }],
                  "textures": {}
                }
                """).getAsJsonObject();

        assertEquals(List.of(new ModelSeat(0, 0, 0)), ModelPreviewRenderer.seatVoxels(model));
        assertEquals(new ModelSeat(0, 0, 0), ModelPreviewRenderer.voxelAt(model, 0, 64, 64));
    }

    @Test
    void rendersPerspectivePreviewAndPicksTheVoxelUnderTheCameraRay() {
        var model = JsonParser.parseString("""
                {
                  "cubes": [{
                    "from": [0, 0, 0],
                    "to": [16, 16, 16],
                    "faces": {}
                  }],
                  "textures": {}
                }
                """).getAsJsonObject();

        BufferedImage frontView = ModelPreviewRenderer.renderInteractive(model, 35, 25, List.of(), null);
        BufferedImage rotatedView = ModelPreviewRenderer.renderInteractive(model, 95, 55,
                List.of(new ModelSeat(0, 0, 0)), null);

        assertTrue(countOpaquePixels(frontView) > 100);
        assertTrue(countDifferentPixels(frontView, rotatedView) > 20);
        assertEquals(new ModelSeat(0, 0, 0), ModelPreviewRenderer.voxelAt(model, 35, 25, 64, 64));
        assertNull(ModelPreviewRenderer.voxelAt(model, 35, 25, 0, 0));
    }

    @Test
    void picksTheFrontVoxelWhenSeveralVoxelsShareTheSameScreenArea() {
        var model = JsonParser.parseString("""
                {
                  "cubes": [
                    {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {}},
                    {"from": [0, 0, 16], "to": [16, 16, 32], "faces": {}}
                  ],
                  "textures": {}
                }
                """).getAsJsonObject();

        assertEquals(new ModelSeat(0, 0, 1), ModelPreviewRenderer.voxelAt(model, 0, 0, 64, 64));
    }

    private static int countOpaquePixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countDifferentPixels(BufferedImage first, BufferedImage second) {
        int count = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }
}
