package fr.cartmodels.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.BasicStroke;
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
            JsonArray cubes = model.has("cubes") && model.get("cubes").isJsonArray()
                    ? model.getAsJsonArray("cubes") : new JsonArray();
            List<double[][]> vertices = new ArrayList<>();
            List<double[][]> meshes = meshFaceVertices(model);
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
            if (vertices.isEmpty() && meshes.isEmpty()) {
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
            for (double[][] mesh : meshes) {
                double[][] points = new double[mesh.length][2];
                double depth = 0.0;
                for (int index = 0; index < mesh.length; index++) {
                    double cameraX = mesh[index][0] * cos - mesh[index][2] * sin;
                    double cameraDepth = mesh[index][0] * sin + mesh[index][2] * cos;
                    double cameraY = cameraDepth * 0.5 - mesh[index][1];
                    points[index] = new double[]{cameraX, cameraY};
                    minX = Math.min(minX, cameraX);
                    maxX = Math.max(maxX, cameraX);
                    minY = Math.min(minY, cameraY);
                    maxY = Math.max(maxY, cameraY);
                    depth += cameraDepth + cameraY * 0.15;
                }
                faces.add(new Face(points, depth / mesh.length, FACE_COLORS[0]));
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

    public static BufferedImage renderInteractive(JsonObject model, double yaw, double pitch,
                                                  List<ModelSeat> selectedSeats, ModelSeat hoveredSeat) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            List<double[][]> cubes = cubeVertices(model);
            List<double[][]> meshes = meshFaceVertices(model);
            List<double[][]> geometry = new ArrayList<>(cubes);
            geometry.addAll(meshes);
            if (geometry.isEmpty()) {
                return image;
            }

            Camera camera = camera(geometry, yaw, pitch);
            List<ProjectedFace> faces = new ArrayList<>();
            for (double[][] cube : cubes) {
                addVisibleFaces(faces, cube, camera, FACE_COLORS, null);
            }
            for (double[][] meshFace : meshes) {
                addMeshFace(faces, meshFace, camera);
            }
            for (ModelSeat seat : selectedSeats) {
                if (!seat.equals(hoveredSeat)) {
                    addSeatFaces(faces, seat, camera, new Color(0xFFFFC857));
                }
            }
            if (hoveredSeat != null) {
                addSeatFaces(faces, hoveredSeat, camera,
                        selectedSeats.contains(hoveredSeat) ? new Color(0xFFFFC857) : new Color(0xFF31D6B0));
            }

            faces.sort(Comparator.comparingDouble(ProjectedFace::depth));
            for (ProjectedFace face : faces) {
                Path2D polygon = new Path2D.Double();
                for (int index = 0; index < face.points.length; index++) {
                    if (index == 0) {
                        polygon.moveTo(face.points[index][0], face.points[index][1]);
                    } else {
                        polygon.lineTo(face.points[index][0], face.points[index][1]);
                    }
                }
                polygon.closePath();
                graphics.setColor(face.color);
                graphics.fill(polygon);
                if (face.outline != null) {
                    graphics.setColor(face.outline);
                    graphics.setStroke(new BasicStroke(1.2F));
                    graphics.draw(polygon);
                }
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    public static ModelSeat voxelAt(JsonObject model, double yaw, double pitch, double previewX, double previewY) {
        List<double[][]> cubes = cubeVertices(model);
        List<double[][]> meshes = meshFaceVertices(model);
        List<double[][]> geometry = new ArrayList<>(cubes);
        geometry.addAll(meshes);
        if (geometry.isEmpty()) {
            return null;
        }
        Camera camera = camera(geometry, yaw, pitch);
        double cameraX = (previewX - SIZE * 0.5) / camera.focal;
        double cameraY = (SIZE * 0.5 - previewY) / camera.focal;
        double[] origin = camera.toModel(0.0, 0.0, camera.distance);
        double[] direction = camera.directionToModel(cameraX, cameraY, -1.0);
        double length = Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1]
                + direction[2] * direction[2]);
        direction[0] /= length;
        direction[1] /= length;
        direction[2] /= length;

        ModelSeat closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        JsonArray modelCubes = model.has("cubes") && model.get("cubes").isJsonArray()
                ? model.getAsJsonArray("cubes") : new JsonArray();
        for (var element : modelCubes) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject cube = element.getAsJsonObject();
            double[] from = vector(cube.getAsJsonArray("from"));
            double[] to = vector(cube.getAsJsonArray("to"));
            if (from == null || to == null) {
                continue;
            }
            double distance = rayBoxDistance(origin, direction,
                    from[0], to[0], from[1], to[1], from[2], to[2]);
            if (distance >= 0.0 && distance < closestDistance) {
                double step = 1.0E-7;
                closest = new ModelSeat(
                        voxelCoordinate(origin[0] + direction[0] * (distance + step), from[0], to[0]),
                        voxelCoordinate(origin[1] + direction[1] * (distance + step), from[1], to[1]),
                        voxelCoordinate(origin[2] + direction[2] * (distance + step), from[2], to[2]));
                closestDistance = distance;
            }
        }
        for (double[][] meshFace : meshes) {
            for (int index = 1; index < meshFace.length - 1; index++) {
                double distance = rayTriangleDistance(origin, direction,
                        meshFace[0], meshFace[index], meshFace[index + 1]);
                if (distance >= 0.0 && distance < closestDistance) {
                    closestDistance = distance;
                    double step = 1.0E-7;
                    closest = new ModelSeat(
                            worldVoxelCoordinate(origin[0] + direction[0] * (distance + step)),
                            worldVoxelCoordinate(origin[1] + direction[1] * (distance + step)),
                            worldVoxelCoordinate(origin[2] + direction[2] * (distance + step)));
                }
            }
        }
        return closest;
    }

    private static int worldVoxelCoordinate(double position) {
        return Math.max(-2_048, Math.min(2_047, (int) Math.floor(position)));
    }

    private static double rayTriangleDistance(double[] origin, double[] direction,
                                              double[] first, double[] second, double[] third) {
        double[] edge1 = subtract(second, first);
        double[] edge2 = subtract(third, first);
        double[] perpendicular = cross(direction, edge2);
        double determinant = dot(edge1, perpendicular);
        if (Math.abs(determinant) < 1.0E-9) {
            return -1.0;
        }
        double inverse = 1.0 / determinant;
        double[] fromVertex = subtract(origin, first);
        double u = dot(fromVertex, perpendicular) * inverse;
        if (u < 0.0 || u > 1.0) {
            return -1.0;
        }
        double[] cross = cross(fromVertex, edge1);
        double v = dot(direction, cross) * inverse;
        if (v < 0.0 || u + v > 1.0) {
            return -1.0;
        }
        double distance = dot(edge2, cross) * inverse;
        return distance >= 0.0 ? distance : -1.0;
    }

    private static double dot(double[] first, double[] second) {
        return first[0] * second[0] + first[1] * second[1] + first[2] * second[2];
    }

    private static int voxelCoordinate(double position, double from, double to) {
        int minimum = (int) Math.floor(from);
        int maximum = (int) Math.ceil(to) - 1;
        return Math.max(minimum, Math.min(maximum, (int) Math.floor(position)));
    }

    public static List<ModelSeat> seatVoxels(JsonObject model) {
        LinkedHashSet<ModelSeat> voxels = new LinkedHashSet<>();
        JsonArray cubes = model.has("cubes") && model.get("cubes").isJsonArray()
                ? model.getAsJsonArray("cubes") : new JsonArray();
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
            int minX = (int) Math.floor(from[0]);
            int minY = (int) Math.floor(from[1]);
            int minZ = (int) Math.floor(from[2]);
            int maxX = (int) Math.ceil(to[0]) - 1;
            int maxY = (int) Math.ceil(to[1]) - 1;
            int maxZ = (int) Math.ceil(to[2]) - 1;
            for (int x = minX; x <= maxX && voxels.size() < 4_096; x++) {
                for (int y = minY; y <= maxY && voxels.size() < 4_096; y++) {
                    for (int z = minZ; z <= maxZ && voxels.size() < 4_096; z++) {
                        if (x >= -2_048 && x <= 2_047 && y >= -2_048 && y <= 2_047
                                && z >= -2_048 && z <= 2_047) {
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

    private static double rayBoxDistance(double[] origin, double[] direction,
                                         double minX, double maxX, double minY, double maxY,
                                         double minZ, double maxZ) {
        double near = 0.0;
        double far = Double.POSITIVE_INFINITY;
        double[] minimums = {minX, minY, minZ};
        double[] maximums = {maxX, maxY, maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(direction[axis]) < 1.0E-9) {
                if (origin[axis] < minimums[axis] || origin[axis] > maximums[axis]) {
                    return -1.0;
                }
                continue;
            }
            double first = (minimums[axis] - origin[axis]) / direction[axis];
            double second = (maximums[axis] - origin[axis]) / direction[axis];
            near = Math.max(near, Math.min(first, second));
            far = Math.min(far, Math.max(first, second));
            if (near > far) {
                return -1.0;
            }
        }
        return far < 0.0 ? -1.0 : near;
    }

    private static List<double[][]> cubeVertices(JsonObject model) {
        List<double[][]> cubes = new ArrayList<>();
        JsonArray elements = model.has("cubes") && model.get("cubes").isJsonArray()
                ? model.getAsJsonArray("cubes") : new JsonArray();
        for (var element : elements) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject cube = element.getAsJsonObject();
            double[] from = vector(cube.getAsJsonArray("from"));
            double[] to = vector(cube.getAsJsonArray("to"));
            if (from != null && to != null) {
                cubes.add(transformCube(cube, from, to));
            }
        }
        return cubes;
    }

    private static List<double[][]> meshFaceVertices(JsonObject model) {
        List<double[][]> faces = new ArrayList<>();
        if (!model.has("meshes") || !model.get("meshes").isJsonArray()) {
            return faces;
        }
        for (var meshElement : model.getAsJsonArray("meshes")) {
            if (!meshElement.isJsonObject()) {
                continue;
            }
            JsonObject mesh = meshElement.getAsJsonObject();
            if (!mesh.has("faces") || !mesh.get("faces").isJsonArray()) {
                continue;
            }
            for (var faceElement : mesh.getAsJsonArray("faces")) {
                if (!faceElement.isJsonObject()) {
                    continue;
                }
                JsonArray points = faceElement.getAsJsonObject().getAsJsonArray("vertices");
                if (points == null || points.size() < 3 || points.size() > 64) {
                    continue;
                }
                double[][] face = new double[points.size()][];
                for (int index = 0; index < points.size(); index++) {
                    face[index] = vector(points.get(index).getAsJsonArray());
                    if (face[index] == null) {
                        face = null;
                        break;
                    }
                }
                if (face != null) {
                    faces.add(face);
                }
            }
        }
        return faces;
    }

    private static void addMeshFace(List<ProjectedFace> faces, double[][] vertices, Camera camera) {
        double[][] points = new double[vertices.length][2];
        double depth = 0.0;
        for (int index = 0; index < vertices.length; index++) {
            double[] projected = camera.project(vertices[index]);
            if (projected == null) {
                return;
            }
            points[index] = projected;
            depth += camera.towardDepth(vertices[index]);
        }
        faces.add(new ProjectedFace(points, depth / vertices.length, FACE_COLORS[0], null));
    }

    private static Camera camera(List<double[][]> cubes, double yaw, double pitch) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double[][] cube : cubes) {
            for (double[] vertex : cube) {
                minX = Math.min(minX, vertex[0]);
                minY = Math.min(minY, vertex[1]);
                minZ = Math.min(minZ, vertex[2]);
                maxX = Math.max(maxX, vertex[0]);
                maxY = Math.max(maxY, vertex[1]);
                maxZ = Math.max(maxZ, vertex[2]);
            }
        }
        double centerX = (minX + maxX) * 0.5;
        double centerY = (minY + maxY) * 0.5;
        double centerZ = (minZ + maxZ) * 0.5;
        double radius = 1.0;
        for (double[][] cube : cubes) {
            for (double[] vertex : cube) {
                double dx = vertex[0] - centerX;
                double dy = vertex[1] - centerY;
                double dz = vertex[2] - centerZ;
                radius = Math.max(radius, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
        }
        return new Camera(centerX, centerY, centerZ, Math.toRadians(yaw),
                Math.toRadians(Math.max(-80.0, Math.min(80.0, pitch))),
                radius * 3.4, SIZE * 0.9);
    }

    private static void addSeatFaces(List<ProjectedFace> faces, ModelSeat seat, Camera camera, Color color) {
        double[][] corners = {
                {seat.x(), seat.y(), seat.z()},
                {seat.x() + 1.0, seat.y(), seat.z()},
                {seat.x() + 1.0, seat.y(), seat.z() + 1.0},
                {seat.x(), seat.y(), seat.z() + 1.0},
                {seat.x(), seat.y() + 1.0, seat.z()},
                {seat.x() + 1.0, seat.y() + 1.0, seat.z()},
                {seat.x() + 1.0, seat.y() + 1.0, seat.z() + 1.0},
                {seat.x(), seat.y() + 1.0, seat.z() + 1.0}
        };
        Color translucent = new Color(color.getRed(), color.getGreen(), color.getBlue(), 100);
        addVisibleFaces(faces, corners, camera,
                new Color[]{translucent, translucent, translucent, translucent, translucent, translucent}, color);
    }

    private static void addVisibleFaces(List<ProjectedFace> faces, double[][] vertices, Camera camera,
                                        Color[] colors, Color outline) {
        int[][] faceVertices = {
                {0, 1, 2, 3}, {4, 7, 6, 5}, {0, 3, 7, 4},
                {1, 5, 6, 2}, {0, 4, 5, 1}, {3, 2, 6, 7}
        };
        double[] cameraPosition = camera.toModel(0.0, 0.0, camera.distance);
        for (int faceIndex = 0; faceIndex < faceVertices.length; faceIndex++) {
            int[] indices = faceVertices[faceIndex];
            double[] first = vertices[indices[0]];
            double[] second = vertices[indices[1]];
            double[] third = vertices[indices[2]];
            double[] normal = cross(subtract(second, first), subtract(third, first));
            double[] faceCenter = {
                    (first[0] + second[0] + third[0] + vertices[indices[3]][0]) * 0.25,
                    (first[1] + second[1] + third[1] + vertices[indices[3]][1]) * 0.25,
                    (first[2] + second[2] + third[2] + vertices[indices[3]][2]) * 0.25
            };
            double facing = normal[0] * (cameraPosition[0] - faceCenter[0])
                    + normal[1] * (cameraPosition[1] - faceCenter[1])
                    + normal[2] * (cameraPosition[2] - faceCenter[2]);
            if (facing <= 0.0) {
                continue;
            }
            double[][] points = new double[4][2];
            double depth = 0.0;
            boolean inFrontOfCamera = true;
            for (int pointIndex = 0; pointIndex < indices.length; pointIndex++) {
                double[] projected = camera.project(vertices[indices[pointIndex]]);
                if (projected == null) {
                    inFrontOfCamera = false;
                    break;
                }
                points[pointIndex][0] = projected[0];
                points[pointIndex][1] = projected[1];
                depth += camera.towardDepth(vertices[indices[pointIndex]]) / indices.length;
            }
            if (inFrontOfCamera) {
                faces.add(new ProjectedFace(points, depth, colors[Math.min(faceIndex, colors.length - 1)], outline));
            }
        }
    }

    private static double[] subtract(double[] first, double[] second) {
        return new double[]{first[0] - second[0], first[1] - second[1], first[2] - second[2]};
    }

    private static double[] cross(double[] first, double[] second) {
        return new double[]{
                first[1] * second[2] - first[2] * second[1],
                first[2] * second[0] - first[0] * second[2],
                first[0] * second[1] - first[1] * second[0]
        };
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
        JsonArray cubes = model.has("cubes") && model.get("cubes").isJsonArray()
                ? model.getAsJsonArray("cubes") : new JsonArray();
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
            double[] center = {voxel.x() + 0.5, voxel.y() + 0.5, voxel.z() + 0.5};
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

    private record ProjectedFace(double[][] points, double depth, Color color, Color outline) {
    }

    private record Camera(double centerX, double centerY, double centerZ,
                          double yaw, double pitch, double distance, double focal) {
        private double[] project(double[] point) {
            double relativeX = point[0] - centerX;
            double relativeY = point[1] - centerY;
            double relativeZ = point[2] - centerZ;
            double horizontalX = relativeX * Math.cos(yaw) - relativeZ * Math.sin(yaw);
            double horizontalDepth = relativeX * Math.sin(yaw) + relativeZ * Math.cos(yaw);
            double cameraY = relativeY * Math.cos(pitch) - horizontalDepth * Math.sin(pitch);
            double towardDepth = relativeY * Math.sin(pitch) + horizontalDepth * Math.cos(pitch);
            double depth = distance - towardDepth;
            if (depth <= 1.0E-6) {
                return null;
            }
            return new double[]{SIZE * 0.5 + focal * horizontalX / depth,
                    SIZE * 0.5 - focal * cameraY / depth};
        }

        private double towardDepth(double[] point) {
            double relativeY = point[1] - centerY;
            double relativeZ = point[2] - centerZ;
            double horizontalDepth = (point[0] - centerX) * Math.sin(yaw) + relativeZ * Math.cos(yaw);
            return relativeY * Math.sin(pitch) + horizontalDepth * Math.cos(pitch);
        }

        private double[] toModel(double cameraX, double cameraY, double towardDepth) {
            double horizontalDepth = -cameraY * Math.sin(pitch) + towardDepth * Math.cos(pitch);
            double relativeY = cameraY * Math.cos(pitch) + towardDepth * Math.sin(pitch);
            double relativeX = cameraX * Math.cos(yaw) + horizontalDepth * Math.sin(yaw);
            double relativeZ = -cameraX * Math.sin(yaw) + horizontalDepth * Math.cos(yaw);
            return new double[]{centerX + relativeX, centerY + relativeY, centerZ + relativeZ};
        }

        private double[] directionToModel(double cameraX, double cameraY, double towardDepth) {
            double horizontalDepth = -cameraY * Math.sin(pitch) + towardDepth * Math.cos(pitch);
            double relativeY = cameraY * Math.cos(pitch) + towardDepth * Math.sin(pitch);
            double relativeX = cameraX * Math.cos(yaw) + horizontalDepth * Math.sin(yaw);
            double relativeZ = -cameraX * Math.sin(yaw) + horizontalDepth * Math.cos(yaw);
            return new double[]{relativeX, relativeY, relativeZ};
        }
    }
}
