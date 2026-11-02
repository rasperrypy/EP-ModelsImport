package fr.cartmodels.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
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
