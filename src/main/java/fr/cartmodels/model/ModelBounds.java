package fr.cartmodels.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.List;

public record ModelBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    private static final double SELECTION_PADDING = 1.0 / 32.0;

    public static ModelBounds fromModel(JsonObject model) {
        BoundsBuilder bounds = new BoundsBuilder();
        if (model.has("cubes") && model.get("cubes").isJsonArray()) {
            for (JsonElement element : model.getAsJsonArray("cubes")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject cube = element.getAsJsonObject();
                double[] from = vector(cube.getAsJsonArray("from"), 3);
                double[] to = vector(cube.getAsJsonArray("to"), 3);
                if (from != null && to != null) {
                    bounds.addCube(cube, from, to);
                }
            }
        }
        if (model.has("meshes") && model.get("meshes").isJsonArray()) {
            for (JsonElement meshElement : model.getAsJsonArray("meshes")) {
                if (!meshElement.isJsonObject()) {
                    continue;
                }
                JsonObject mesh = meshElement.getAsJsonObject();
                if (!mesh.has("faces") || !mesh.get("faces").isJsonArray()) {
                    continue;
                }
                for (JsonElement faceElement : mesh.getAsJsonArray("faces")) {
                    if (!faceElement.isJsonObject()) {
                        continue;
                    }
                    JsonArray vertices = faceElement.getAsJsonObject().getAsJsonArray("vertices");
                    if (vertices == null) {
                        continue;
                    }
                    for (JsonElement vertex : vertices) {
                        double[] point = vertex.isJsonArray() ? vector(vertex.getAsJsonArray(), 3) : null;
                        if (point != null) {
                            bounds.add(point);
                        }
                    }
                }
            }
        }
        return bounds.build();
    }

    public VoxelShape selectionShape(Direction facing) {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                || maxX < minX || maxY < minY || maxZ < minZ) {
            return Shapes.block();
        }
        ModelBounds oriented = rotatedQuarterTurns(switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        });
        double lowX = oriented.minX / 16.0;
        double lowY = oriented.minY / 16.0;
        double lowZ = oriented.minZ / 16.0;
        double highX = oriented.maxX / 16.0;
        double highY = oriented.maxY / 16.0;
        double highZ = oriented.maxZ / 16.0;
        lowX = Math.max(0.0, lowX);
        lowY = Math.max(0.0, lowY);
        lowZ = Math.max(0.0, lowZ);
        highX = Math.min(1.0, highX);
        highY = Math.min(1.0, highY);
        highZ = Math.min(1.0, highZ);
        if (highX <= lowX || highY <= lowY || highZ <= lowZ) {
            return Shapes.block();
        }
        double[] x = pad(lowX, highX);
        double[] y = pad(lowY, highY);
        double[] z = pad(lowZ, highZ);
        return Shapes.box(x[0], y[0], z[0], x[1], y[1], z[1]);
    }

    public ModelBounds rotatedQuarterTurns(int quarterTurns) {
        int rotations = Math.floorMod(quarterTurns, 4);
        if (rotations == 0) {
            return this;
        }
        BoundsBuilder bounds = new BoundsBuilder();
        for (double x : new double[]{minX, maxX}) {
            for (double y : new double[]{minY, maxY}) {
                for (double z : new double[]{minZ, maxZ}) {
                    double rotatedX = x;
                    double rotatedZ = z;
                    for (int turn = 0; turn < rotations; turn++) {
                        double nextX = 16.0 - rotatedZ;
                        rotatedZ = rotatedX;
                        rotatedX = nextX;
                    }
                    bounds.add(new double[]{rotatedX, y, rotatedZ});
                }
            }
        }
        return bounds.build();
    }

    private static double[] pad(double min, double max) {
        if (max - min >= SELECTION_PADDING * 2.0) {
            return new double[]{min, max};
        }
        double center = (min + max) * 0.5;
        return new double[]{Math.max(0.0, center - SELECTION_PADDING),
                Math.min(1.0, center + SELECTION_PADDING)};
    }

    private static double[] vector(JsonArray array, int size) {
        if (array == null || array.size() != size) {
            return null;
        }
        double[] result = new double[size];
        for (int index = 0; index < size; index++) {
            JsonElement value = array.get(index);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            result[index] = value.getAsDouble();
            if (!Double.isFinite(result[index])) {
                return null;
            }
        }
        return result;
    }

    private static final class BoundsBuilder {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        private void addCube(JsonObject cube, double[] from, double[] to) {
            double[] origin = new double[]{8, 8, 8};
            double[] angles = new double[3];
            if (cube.has("rotation") && cube.get("rotation").isJsonObject()) {
                JsonObject rotation = cube.getAsJsonObject("rotation");
                double[] parsedOrigin = vector(rotation.getAsJsonArray("origin"), 3);
                if (parsedOrigin != null) {
                    origin = parsedOrigin;
                }
                if (rotation.has("euler")) {
                    double[] euler = vector(rotation.getAsJsonArray("euler"), 3);
                    if (euler != null) {
                        angles = euler;
                    }
                } else if (rotation.has("axis") && rotation.get("axis").isJsonPrimitive()
                        && rotation.has("angle") && rotation.get("angle").isJsonPrimitive()) {
                    double angle = rotation.get("angle").getAsDouble();
                    switch (rotation.get("axis").getAsString()) {
                        case "x" -> angles[0] = angle;
                        case "y" -> angles[1] = angle;
                        case "z" -> angles[2] = angle;
                        default -> {
                            return;
                        }
                    }
                }
            }
            for (double x : new double[]{from[0], to[0]}) {
                for (double y : new double[]{from[1], to[1]}) {
                    for (double z : new double[]{from[2], to[2]}) {
                        add(rotateCubeVertex(new double[]{x, y, z}, origin, angles));
                    }
                }
            }
        }

        private double[] rotateCubeVertex(double[] vertex, double[] origin, double[] angles) {
            double x = vertex[0] - origin[0];
            double y = vertex[1] - origin[1];
            double z = vertex[2] - origin[2];
            double radiansX = Math.toRadians(angles[0]);
            double radiansY = Math.toRadians(angles[1]);
            double radiansZ = Math.toRadians(angles[2]);
            double nextY = y * Math.cos(radiansX) - z * Math.sin(radiansX);
            double nextZ = y * Math.sin(radiansX) + z * Math.cos(radiansX);
            y = nextY;
            z = nextZ;
            double nextX = x * Math.cos(radiansY) + z * Math.sin(radiansY);
            nextZ = -x * Math.sin(radiansY) + z * Math.cos(radiansY);
            x = nextX;
            z = nextZ;
            nextX = x * Math.cos(radiansZ) - y * Math.sin(radiansZ);
            nextY = x * Math.sin(radiansZ) + y * Math.cos(radiansZ);
            return new double[]{nextX + origin[0], nextY + origin[1], z + origin[2]};
        }

        private void add(double[] point) {
            minX = Math.min(minX, point[0]);
            minY = Math.min(minY, point[1]);
            minZ = Math.min(minZ, point[2]);
            maxX = Math.max(maxX, point[0]);
            maxY = Math.max(maxY, point[1]);
            maxZ = Math.max(maxZ, point[2]);
        }

        private ModelBounds build() {
            return Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ)
                    && Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ)
                    ? new ModelBounds(minX, minY, minZ, maxX, maxY, maxZ) : null;
        }
    }
}
