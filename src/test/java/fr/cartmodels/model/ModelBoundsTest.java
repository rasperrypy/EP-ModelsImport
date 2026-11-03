package fr.cartmodels.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class ModelBoundsTest {
    @Test
    void centersBoundsOnCubeGeometryInsteadOfTheAnchorBlock() {
        var model = JsonParser.parseString("""
                {
                  "cubes": [{
                    "from": [2, 4, 6],
                    "to": [14, 12, 10],
                    "faces": {}
                  }],
                  "meshes": []
                }
                """).getAsJsonObject();

        ModelBounds bounds = ModelBounds.fromModel(model);

        assertNotNull(bounds);
        assertEquals(2.0, bounds.minX());
        assertEquals(4.0, bounds.minY());
        assertEquals(6.0, bounds.minZ());
        assertEquals(14.0, bounds.maxX());
        assertEquals(12.0, bounds.maxY());
        assertEquals(10.0, bounds.maxZ());
    }

    @Test
    void includesRotatedCubeVerticesAndExperimentalMeshVertices() {
        var model = JsonParser.parseString("""
                {
                  "cubes": [{
                    "from": [0, 0, 0],
                    "to": [2, 1, 1],
                    "rotation": {"origin": [0, 0, 0], "euler": [0, 0, 90]},
                    "faces": {}
                  }],
                  "meshes": [{
                    "faces": [{
                      "vertices": [[-4, -2, 3], [4, -2, 3], [0, 6, 3]],
                      "uv": [[0, 0], [1, 0], [0.5, 1]]
                    }]
                  }]
                }
                """).getAsJsonObject();

        ModelBounds bounds = ModelBounds.fromModel(model);

        assertNotNull(bounds);
        assertEquals(-4.0, bounds.minX());
        assertEquals(-2.0, bounds.minY());
        assertEquals(0.0, bounds.minZ());
        assertEquals(4.0, bounds.maxX());
        assertEquals(6.0, bounds.maxY());
        assertEquals(3.0, bounds.maxZ());
    }

    @Test
    void rotatingBoundsKeepsTheHitboxCenteredOnTheModelGeometry() {
        var model = JsonParser.parseString("""
                {
                  "cubes": [{
                    "from": [1, 2, 3],
                    "to": [7, 10, 9],
                    "faces": {}
                  }],
                  "meshes": []
                }
                """).getAsJsonObject();
        ModelBounds bounds = ModelBounds.fromModel(model);

        ModelBounds north = bounds.rotatedQuarterTurns(0);
        ModelBounds east = bounds.rotatedQuarterTurns(1);

        assertEquals(4.0, (north.minX() + north.maxX()) * 0.5);
        assertEquals(6.0, (north.minY() + north.maxY()) * 0.5);
        assertEquals(6.0, (north.minZ() + north.maxZ()) * 0.5);
        assertEquals(10.0, (east.minX() + east.maxX()) * 0.5);
        assertEquals(4.0, (east.minZ() + east.maxZ()) * 0.5);
    }

    @Test
    void returnsNoBoundsForEmptyGeometry() {
        var model = JsonParser.parseString("{\"cubes\":[],\"meshes\":[]}").getAsJsonObject();

        assertNull(ModelBounds.fromModel(model));
    }
}
