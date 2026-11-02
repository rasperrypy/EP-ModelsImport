package fr.cartmodels.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import fr.cartmodels.model.ModelSeat;

public final class ModelPreviewRenderer {
    private static final int SIZE = 128;
    private static final int[][] VISIBLE_FACES = {{4, 5, 6, 7}, {1, 2, 6, 5}, {3, 2, 6, 7}};
    private static final Color[] FACE_COLORS = {
            new Color(0xE2E8F0), new Color(0x9BA8BA), new Color(0xBCC6D3)
    };

    private ModelPreviewRenderer() {
    }

    public static BufferedImage render(JsonObject model, int rotationStep) {
        return render(model, rotationStep, List.of(), false);
    }

    public static BufferedImage render(JsonObject model, int rotationStep, List<ModelSeat> selectedSeats,
                                       boolean showSeatVoxels) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            JsonArray cubes = model.getAsJsonArray("cubes");
            List<double[][]> vertices = new ArrayList<>();
            for (var element : cubes) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject cube = element.getAsJsonObject();
                double[] from = vector(cube.getAsJsonArray("from"));
                double[] to = vector(cube.getAsJsonArray("to"));
                if (from != null && to != null) {
                    vertices.add(transformCube(cube, from, to));
                }
            }
            if (vertices.isEmpty()) {
                return image;
            }

            double radians = Math.toRadians(45.0 + rotationStep * 45.0);
            double sin = Math.sin(radians);
            double cos = Math.cos(radians);
            List<Face> faces = new ArrayList<>();
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (double[][] cube : vertices) {
                double[][] projected = new double[8][3];
                for (int index = 0; index < cube.length; index++) {
                    double cameraX = cube[index][0] * cos - cube[index][2] * sin;
                    double cameraDepth = cube[index][0] * sin + cube[index][2] * cos;
                    projected[index] = new double[]{cameraX, cameraDepth * 0.5 - cube[index][1], cameraDepth};
                    minX = Math.min(minX, projected[index][0]);
                    maxX = Math.max(maxX, projected[index][0]);
                    minY = Math.min(minY, projected[index][1]);
                    maxY = Math.max(maxY, projected[index][1]);
                }
                for (int faceIndex = 0; faceIndex < VISIBLE_FACES.length; faceIndex++) {
                    int[] indices = VISIBLE_FACES[faceIndex];
                    double depth = 0.0;
                    double[][] points = new double[indices.length][2];
                    for (int index = 0; index < indices.length; index++) {
                        double[] vertex = projected[indices[index]];
                        points[index] = new double[]{vertex[0], vertex[1]};
                        depth += vertex[2] + vertex[1] * 0.15;
                    }
                    faces.add(new Face(points, depth / indices.length, FACE_COLORS[faceIndex]));
                }
            }
            double spanX = Math.max(1.0, maxX - minX);
            double spanY = Math.max(1.0, maxY - minY);
            double scale = Math.min((SIZE - 20.0) / spanX, (SIZE - 20.0) / spanY);
            double centerX = (minX + maxX) * 0.5;
            double centerY = (minY + maxY) * 0.5;
            faces.sort(Comparator.comparingDouble(Face::depth));
            for (Face face : faces) {
                Path2D polygon = new Path2D.Double();
                for (int index = 0; index < face.points.length; index++) {
                    double x = SIZE * 0.5 + (face.points[index][0] - centerX) * scale;
                    double y = SIZE * 0.5 + (face.points[index][1] - centerY) * scale;
                    if (index == 0) {
                        polygon.moveTo(x, y);
                    } else {
                        polygon.lineTo(x, y);
                    }
                }
                polygon.closePath();
                graphics.setColor(face.color);
                graphics.fill(polygon);
                graphics.setColor(new Color(0x263244));
                graphics.draw(polygon);
            }
            if (showSeatVoxels) {
                for (Map.Entry<ModelSeat, double[]> entry : projectVoxels(model, rotationStep).entrySet()) {
                    double[] point = entry.getValue();
                    int x = (int) Math.round(point[0]);
                    int y = (int) Math.round(point[1]);
                    boolean selected = selectedSeats.contains(entry.getKey());
                    graphics.setColor(selected ? new Color(0xFFFFC857) : new Color(0xFF31D6B0));
                    graphics.fillOval(x - 4, y - 4, 9, 9);
                    graphics.setColor(new Color(0xFF17212B));
                    graphics.drawOval(x - 4, y - 4, 9, 9);
                }
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    public static List<ModelSeat> seatVoxels(JsonObject model) {
        LinkedHashSet<ModelSeat> voxels = new LinkedHashSet<>();
        JsonArray cubes = model.getAsJsonArray("cubes");
        for (var element : cubes) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject cube = element.getAsJsonObject();
            double[] from = vector(cube.getAsJsonArray("from"));
            double[] to = vector(cube.getAsJsonArray("to"));
            if (from == null || to == null) {
                continue;
            }
            int minX = (int) Math.floor(from[0] / 16.0);
            int minY = (int) Math.floor(from[1] / 16.0);
            int minZ = (int) Math.floor(from[2] / 16.0);
            int maxX = (int) Math.ceil(to[0] / 16.0) - 1;
            int maxY = (int) Math.ceil(to[1] / 16.0) - 1;
            int maxZ = (int) Math.ceil(to[2] / 16.0) - 1;
            for (int x = minX; x <= maxX && voxels.size() < 4_096; x++) {
                for (int y = minY; y <= maxY && voxels.size() < 4_096; y++) {
                    for (int z = minZ; z <= maxZ && voxels.size() < 4_096; z++) {
                        if (x >= -128 && x <= 127 && y >= -128 && y <= 127 && z >= -128 && z <= 127) {
                            voxels.add(new ModelSeat(x, y, z));
                        }
                    }
                }
            }
            if (voxels.size() >= 4_096) {
                break;
            }
        }
        return List.copyOf(voxels);
    }

    public static ModelSeat voxelAt(JsonObject model, int rotationStep, double previewX, double previewY) {
        ModelSeat closest = null;
        double closestDistance = 11.0 * 11.0;
        for (Map.Entry<ModelSeat, double[]> entry : projectVoxels(model, rotationStep).entrySet()) {
            double dx = previewX - entry.getValue()[0];
            double dy = previewY - entry.getValue()[1];
            double distance = dx * dx + dy * dy;
            if (distance < closestDistance) {
                closest = entry.getKey();
                closestDistance = distance;
            }
        }
        return closest;
    }

    private static Map<ModelSeat, double[]> projectVoxels(JsonObject model, int rotationStep) {
        Map<ModelSeat, double[]> projected = new LinkedHashMap<>();
        JsonArray cubes = model.getAsJsonArray("cubes");
        List<double[][]> vertices = new ArrayList<>();
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double radians = Math.toRadians(45.0 + rotationStep * 45.0);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        for (var element : cubes) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject cube = element.getAsJsonObject();
            double[] from = vector(cube.getAsJsonArray("from"));
            double[] to = vector(cube.getAsJsonArray("to"));
            if (from == null || to == null) {
                continue;
            }
            double[][] cubeVertices = transformCube(cube, from, to);
            vertices.add(cubeVertices);
            for (double[] vertex : cubeVertices) {
                double cameraX = vertex[0] * cos - vertex[2] * sin;
                double cameraDepth = vertex[0] * sin + vertex[2] * cos;
                minX = Math.min(minX, cameraX);
                maxX = Math.max(maxX, cameraX);
                minY = Math.min(minY, cameraDepth * 0.5 - vertex[1]);
                maxY = Math.max(maxY, cameraDepth * 0.5 - vertex[1]);
            }
        }
        if (vertices.isEmpty()) {
            return projected;
        }
        double scale = Math.min((SIZE - 20.0) / Math.max(1.0, maxX - minX),
                (SIZE - 20.0) / Math.max(1.0, maxY - minY));
        double centerX = (minX + maxX) * 0.5;
        double centerY = (minY + maxY) * 0.5;
        for (ModelSeat voxel : seatVoxels(model)) {
            double[] center = {(voxel.x() + 0.5) * 16.0, (voxel.y() + 0.5) * 16.0,
                    (voxel.z() + 0.5) * 16.0};
            double cameraX = center[0] * cos - center[2] * sin;
            double cameraDepth = center[0] * sin + center[2] * cos;
            double screenX = SIZE * 0.5 + (cameraX - centerX) * scale;
            double screenY = SIZE * 0.5 + (cameraDepth * 0.5 - center[1] - centerY) * scale;
            projected.put(voxel, new double[]{screenX, screenY});
        }
        return projected;
    }

    private static double[][] transformCube(JsonObject cube, double[] from, double[] to) {
        double[][] vertices = {
                {from[0], from[1], from[2]}, {to[0], from[1], from[2]},
                {to[0], from[1], to[2]}, {from[0], from[1], to[2]},
                {from[0], to[1], from[2]}, {to[0], to[1], from[2]},
                {to[0], to[1], to[2]}, {from[0], to[1], to[2]}
        };
        if (!cube.has("rotation") || !cube.get("rotation").isJsonObject()) {
            return vertices;
        }
        JsonObject rotation = cube.getAsJsonObject("rotation");
        double[] origin = rotation.has("origin") ? vector(rotation.getAsJsonArray("origin")) : new double[]{8, 8, 8};
        if (origin == null) {
            return vertices;
        }
        double[] angles = rotation.has("euler")
                ? vector(rotation.getAsJsonArray("euler"))
                : axisAngles(rotation.get("axis").getAsString(), rotation.get("angle").getAsDouble());
        if (angles == null) {
            return vertices;
        }
        for (double[] vertex : vertices) {
            double x = vertex[0] - origin[0];
            double y = vertex[1] - origin[1];
            double z = vertex[2] - origin[2];
            double rx = Math.toRadians(angles[0]);
            double ry = Math.toRadians(angles[1]);
            double rz = Math.toRadians(angles[2]);
            double nextY = y * Math.cos(rx) - z * Math.sin(rx);
            double nextZ = y * Math.sin(rx) + z * Math.cos(rx);
            y = nextY;
            z = nextZ;
            double nextX = x * Math.cos(ry) + z * Math.sin(ry);
            nextZ = -x * Math.sin(ry) + z * Math.cos(ry);
            x = nextX;
            z = nextZ;
            nextX = x * Math.cos(rz) - y * Math.sin(rz);
            nextY = x * Math.sin(rz) + y * Math.cos(rz);
            vertex[0] = nextX + origin[0];
            vertex[1] = nextY + origin[1];
            vertex[2] = z + origin[2];
        }
        return vertices;
    }

    private static double[] axisAngles(String axis, double angle) {
        return switch (axis) {
            case "x" -> new double[]{angle, 0, 0};
            case "y" -> new double[]{0, angle, 0};
            case "z" -> new double[]{0, 0, angle};
            default -> null;
        };
    }

    private static double[] vector(JsonArray array) {
        if (array == null || array.size() != 3) {
            return null;
        }
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    private record Face(double[][] points, double depth, Color color) {
    }
}
