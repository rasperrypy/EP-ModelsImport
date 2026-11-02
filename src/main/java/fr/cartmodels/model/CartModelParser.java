package fr.cartmodels.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

public final class CartModelParser {
    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_CUBOIDS = 4_096;
    private static final int MAX_TEXTURES = 32;
    private static final int MAX_COORDINATE = 2_048;
    private static final int MAX_TEXTURE_BYTES = 2 * 1024 * 1024;
    private static final Set<String> FACE_DIRECTIONS = Set.of("down", "up", "north", "south", "west", "east");

    private CartModelParser() {
    }

    public static String parse(String fileName, String contents) throws IOException {
        return parse(fileName, contents, false);
    }

    public static String parse(String fileName, String contents, boolean allowExperimentalMeshes) throws IOException {
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);
        if (!lowerFileName.endsWith(".bbmodel") && !lowerFileName.endsWith(".json")) {
            throw new IOException("Only .bbmodel and .json model files are supported.");
        }
        if (contents.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
            throw new IOException("Model files cannot exceed 8 MiB.");
        }

        JsonObject source;
        try {
            source = JsonParser.parseString(contents).getAsJsonObject();
        } catch (IllegalStateException | JsonSyntaxException exception) {
            throw new IOException("The model file does not contain a valid JSON object.", exception);
        }

        boolean blockbench = lowerFileName.endsWith(".bbmodel");
        JsonArray elements = source.has("elements") && source.get("elements").isJsonArray()
                ? source.getAsJsonArray("elements") : null;
        if (elements == null) {
            throw new IOException(blockbench
                    ? "This Blockbench project has no cube elements or experimental meshes."
                    : "This JSON is not a Minecraft block model with an elements array.");
        }

        JsonObject output = new JsonObject();
        JsonArray cubes = new JsonArray();
        JsonArray meshes = new JsonArray();
        JsonObject textures = new JsonObject();
        Map<String, String> textureIds = new HashMap<>();
        if (blockbench) {
            readBlockbenchTextures(source, textures, textureIds);
        } else {
            readMinecraftTextures(source, textures, textureIds);
        }

        for (JsonElement element : elements) {
            if (!element.isJsonObject()) {
                throw new IOException("Every model element must be an object.");
            }
            JsonObject modelElement = element.getAsJsonObject();
            String type = blockbench && modelElement.has("type") && modelElement.get("type").isJsonPrimitive()
                    ? modelElement.get("type").getAsString() : "cube";
            if (blockbench && !type.equals("cube") && !type.equals("mesh")) {
                throw new IOException("Only Blockbench cube and mesh elements are supported.");
            }
            if (blockbench && type.equals("mesh")) {
                if (!allowExperimentalMeshes && !readExperimentalMeshFlag(source)) {
                    throw new IOException("Blockbench mesh support is experimental. Enable it in the import screen.");
                }
                if (meshes.size() >= MAX_CUBOIDS) {
                    throw new IOException("Models cannot contain more than 4,096 mesh elements.");
                }
                meshes.add(readMesh(modelElement, textureIds));
            } else {
                if (cubes.size() >= MAX_CUBOIDS) {
                    throw new IOException("Models cannot contain more than 4,096 cuboids.");
                }
                cubes.add(readCube(modelElement, blockbench, textureIds));
            }
        }
        if (cubes.isEmpty() && meshes.isEmpty()) {
            throw new IOException("The model has no renderable elements.");
        }

        output.add("cubes", cubes);
        output.add("meshes", meshes);
        if (!meshes.isEmpty()) {
            output.addProperty("experimentalMeshes", true);
        }
        output.add("textures", textures);
        int seatVoxelSize = readSeatVoxelSize(source);
        output.addProperty("seatVoxelSize", seatVoxelSize);
        output.add("seats", readSeats(source, elements, seatVoxelSize, allowExperimentalMeshes
                || readExperimentalMeshFlag(source)));
        String normalized = output.toString();
        if (normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
            throw new IOException("The normalized model exceeds the 8 MiB synchronization limit.");
        }
        return normalized;
    }

    private static boolean readExperimentalMeshFlag(JsonObject source) throws IOException {
        JsonElement element = source.get("cartmodels_experimental_meshes");
        if (element == null) {
            return false;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IOException("The experimental mesh option must be a boolean.");
        }
        return element.getAsBoolean();
    }

    private static int readSeatVoxelSize(JsonObject source) throws IOException {
        JsonElement element = source.get("cartmodels_seat_voxel_size");
        if (element == null) {
            return ModelSeat.LEGACY_VOXEL_SIZE;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IOException("Seat voxel size must be a supported integer.");
        }
        double value = element.getAsDouble();
        if (value != ModelSeat.MODEL_VOXEL_SIZE && value != ModelSeat.LEGACY_VOXEL_SIZE) {
            throw new IOException("Seat voxel size must be 1 or 16 model pixels.");
        }
        return (int) value;
    }

    private static JsonArray readSeats(JsonObject source, JsonArray elements, int voxelSize,
                                       boolean allowMeshes) throws IOException {
        List<ModelSeat> seats = ModelSeat.read(source.get("cartmodels_seats"), voxelSize);
        JsonArray result = new JsonArray();
        for (ModelSeat seat : seats) {
            boolean overlapsModel = false;
            for (JsonElement element : elements) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject cube = element.getAsJsonObject();
                if (cube.has("from") && cube.has("to")
                        && cube.get("from").isJsonArray() && cube.get("to").isJsonArray()) {
                    JsonArray from = cube.getAsJsonArray("from");
                    JsonArray to = cube.getAsJsonArray("to");
                    if (from.size() == 3 && to.size() == 3) {
                        double x0 = from.get(0).getAsDouble();
                        double y0 = from.get(1).getAsDouble();
                        double z0 = from.get(2).getAsDouble();
                        double x1 = to.get(0).getAsDouble();
                        double y1 = to.get(1).getAsDouble();
                        double z1 = to.get(2).getAsDouble();
                        overlapsModel = x1 > seat.x() * (double) voxelSize
                                && x0 < (seat.x() + 1) * (double) voxelSize
                                && y1 > seat.y() * (double) voxelSize
                                && y0 < (seat.y() + 1) * (double) voxelSize
                                && z1 > seat.z() * (double) voxelSize
                                && z0 < (seat.z() + 1) * (double) voxelSize;
                    }
                }
                if (!overlapsModel && allowMeshes && cube.has("type") && cube.get("type").isJsonPrimitive()
                        && "mesh".equals(cube.get("type").getAsString())
                        && meshOverlapsSeat(cube, seat, voxelSize)) {
                    overlapsModel = true;
                }
                if (overlapsModel) break;
            }
            if (!overlapsModel) {
                throw new IOException("Seat voxel " + seat + " does not overlap a model cuboid.");
            }
            result.add(seat.toJson());
        }
        return result;
    }

    private static boolean meshOverlapsSeat(JsonObject mesh, ModelSeat seat, int voxelSize) {
        if (!mesh.has("vertices") || !mesh.get("vertices").isJsonObject()
                || !mesh.has("faces") || !mesh.get("faces").isJsonObject()) {
            return false;
        }
        JsonObject vertices = mesh.getAsJsonObject("vertices");
        for (Map.Entry<String, JsonElement> faceEntry : mesh.getAsJsonObject("faces").entrySet()) {
            if (!faceEntry.getValue().isJsonObject()) {
                continue;
            }
            JsonElement faceVertices = faceEntry.getValue().getAsJsonObject().get("vertices");
            if (faceVertices == null || !faceVertices.isJsonArray()) {
                continue;
            }
            double[] minimum = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
            double[] maximum = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (JsonElement reference : faceVertices.getAsJsonArray()) {
                if (!reference.isJsonPrimitive()) {
                    continue;
                }
                JsonElement vertex = vertices.get(reference.getAsString());
                if (vertex == null || !vertex.isJsonArray() || vertex.getAsJsonArray().size() != 3) {
                    continue;
                }
                for (int axis = 0; axis < 3; axis++) {
                    double coordinate = vertex.getAsJsonArray().get(axis).getAsDouble();
                    minimum[axis] = Math.min(minimum[axis], coordinate);
                    maximum[axis] = Math.max(maximum[axis], coordinate);
                }
            }
            if (maximum[0] >= minimum[0] && maximum[1] >= minimum[1] && maximum[2] >= minimum[2]
                    && maximum[0] >= seat.x() * (double) voxelSize
                    && minimum[0] <= (seat.x() + 1) * (double) voxelSize
                    && maximum[1] >= seat.y() * (double) voxelSize
                    && minimum[1] <= (seat.y() + 1) * (double) voxelSize
                    && maximum[2] >= seat.z() * (double) voxelSize
                    && minimum[2] <= (seat.z() + 1) * (double) voxelSize) {
                return true;
            }
        }
        return false;
    }

    private static void readBlockbenchTextures(JsonObject source, JsonObject textures, Map<String, String> textureIds)
            throws IOException {
        JsonArray sourceTextures = source.has("textures") && source.get("textures").isJsonArray()
                ? source.getAsJsonArray("textures") : null;
        if (sourceTextures == null) {
            return;
        }
        for (int index = 0; index < sourceTextures.size(); index++) {
            JsonElement textureElement = sourceTextures.get(index);
            if (!textureElement.isJsonObject()) {
                continue;
            }
            JsonObject texture = textureElement.getAsJsonObject();
            String image = texture.has("source") && texture.get("source").isJsonPrimitive()
                    ? texture.get("source").getAsString()
                    : texture.has("data") && texture.get("data").isJsonPrimitive()
                            ? texture.get("data").getAsString()
                            : texture.has("path") && texture.get("path").isJsonPrimitive()
                                    ? texture.get("path").getAsString() : "";
            String canonicalId = addEmbeddedTexture(image, textures);
            if (canonicalId == null) {
                continue;
            }
            textureIds.put(Integer.toString(index), canonicalId);
            if (texture.has("id") && texture.get("id").isJsonPrimitive()) {
                textureIds.put(texture.get("id").getAsString(), canonicalId);
            }
            if (texture.has("name") && texture.get("name").isJsonPrimitive()) {
                textureIds.put(texture.get("name").getAsString(), canonicalId);
            }
        }
    }

    private static void readMinecraftTextures(JsonObject source, JsonObject textures, Map<String, String> textureIds)
            throws IOException {
        JsonObject sourceTextures = source.has("textures") && source.get("textures").isJsonObject()
                ? source.getAsJsonObject("textures") : null;
        if (sourceTextures == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : sourceTextures.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                continue;
            }
            String value = entry.getValue().getAsString();
            String canonicalId = addEmbeddedTexture(value, textures);
            if (canonicalId != null) {
                textureIds.put(entry.getKey(), canonicalId);
            }
        }
    }

    private static String addEmbeddedTexture(String value, JsonObject textures) throws IOException {
        if (!value.startsWith("data:image/png;base64,")) {
            return null;
        }
        String encoded = value.substring("data:image/png;base64,".length());
        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IOException("An embedded texture contains invalid base64 data.", exception);
        }
        if (imageBytes.length == 0 || imageBytes.length > MAX_TEXTURE_BYTES) {
            throw new IOException("Embedded PNG textures must be between 1 byte and 2 MiB.");
        }
        byte[] pngSignature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        if (imageBytes.length < 24 || !java.util.Arrays.equals(
                java.util.Arrays.copyOf(imageBytes, pngSignature.length), pngSignature)) {
            throw new IOException("Embedded textures must be PNG images.");
        }
        ByteBuffer header = ByteBuffer.wrap(imageBytes);
        int width = header.getInt(16);
        int height = header.getInt(20);
        if (width < 1 || height < 1 || width > 2_048 || height > 2_048) {
            throw new IOException("Embedded textures must be no larger than 2,048×2,048.");
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null || image.getWidth() != width || image.getHeight() != height) {
            throw new IOException("Embedded textures must be valid PNG images no larger than 2,048×2,048.");
        }
        if (textures.size() >= MAX_TEXTURES) {
            throw new IOException("Models cannot contain more than 32 embedded textures.");
        }
        String id = "texture_" + textures.size();
        JsonObject textureData = new JsonObject();
        textureData.addProperty("image", Base64.getEncoder().encodeToString(imageBytes));
        textureData.addProperty("width", width);
        textureData.addProperty("height", height);
        textures.add(id, textureData);
        return id;
    }

    private static JsonObject readMesh(JsonObject source, Map<String, String> textureIds) throws IOException {
        JsonObject sourceVertices = source.has("vertices") && source.get("vertices").isJsonObject()
                ? source.getAsJsonObject("vertices") : null;
        JsonObject sourceFaces = source.has("faces") && source.get("faces").isJsonObject()
                ? source.getAsJsonObject("faces") : null;
        if (sourceVertices == null || sourceFaces == null || sourceVertices.size() < 3
                || sourceVertices.size() > 16_384 || sourceFaces.isEmpty() || sourceFaces.size() > 4_096) {
            throw new IOException("A Blockbench mesh must contain vertices and faces.");
        }
        JsonObject vertices = new JsonObject();
        for (String id : sourceVertices.keySet()) {
            vertices.add(id, readVector(sourceVertices, id, 3));
        }
        JsonArray faces = new JsonArray();
        for (Map.Entry<String, JsonElement> entry : sourceFaces.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                throw new IOException("Blockbench mesh faces must be objects.");
            }
            JsonObject sourceFace = entry.getValue().getAsJsonObject();
            JsonArray references = sourceFace.has("vertices") && sourceFace.get("vertices").isJsonArray()
                    ? sourceFace.getAsJsonArray("vertices") : null;
            if (references == null || references.size() < 3 || references.size() > 64) {
                throw new IOException("A Blockbench mesh face must reference 3 to 64 vertices.");
            }
            JsonArray points = new JsonArray();
            JsonArray uvs = new JsonArray();
            JsonObject sourceUvs = sourceFace.has("uv") && sourceFace.get("uv").isJsonObject()
                    ? sourceFace.getAsJsonObject("uv") : null;
            for (JsonElement reference : references) {
                if (!reference.isJsonPrimitive()) {
                    throw new IOException("Blockbench mesh vertex references must be identifiers.");
                }
                String id = reference.getAsString();
                if (!vertices.has(id)) {
                    throw new IOException("A Blockbench mesh face references a missing vertex.");
                }
                points.add(vertices.get(id).deepCopy());
                JsonArray uv = sourceUvs != null && sourceUvs.has(id)
                        ? readVector(sourceUvs, id, 2) : new JsonArray();
                if (uv.isEmpty()) {
                    uv.add(0);
                    uv.add(0);
                }
                uvs.add(uv);
            }
            JsonObject face = new JsonObject();
            face.add("vertices", points);
            face.add("uv", uvs);
            face.addProperty("pixelUv", true);
            String textureReference = sourceFace.has("texture") && sourceFace.get("texture").isJsonPrimitive()
                    ? sourceFace.get("texture").getAsString() : "";
            face.addProperty("texture", textureIds.getOrDefault(textureReference, ""));
            faces.add(face);
        }
        JsonObject mesh = new JsonObject();
        mesh.add("vertices", vertices);
        mesh.add("faces", faces);
        return mesh;
    }

    private static JsonObject readCube(JsonObject source, boolean blockbench, Map<String, String> textureIds)
            throws IOException {
        JsonObject cube = new JsonObject();
        cube.add("from", readVector(source, "from", 3));
        cube.add("to", readVector(source, "to", 3));
        if (source.has("rotation")) {
            JsonElement sourceRotation = source.get("rotation");
            if (sourceRotation.isJsonObject()) {
                JsonObject rotation = sourceRotation.getAsJsonObject();
                JsonObject canonicalRotation = new JsonObject();
                String axis = rotation.has("axis") && rotation.get("axis").isJsonPrimitive()
                        ? rotation.get("axis").getAsString() : "y";
                if (!axis.equals("x") && !axis.equals("y") && !axis.equals("z")) {
                    throw new IOException("A cuboid rotation axis must be x, y, or z.");
                }
                canonicalRotation.addProperty("axis", axis);
                canonicalRotation.add("origin", rotation.has("origin")
                        ? readVector(rotation, "origin", 3) : defaultOrigin());
                canonicalRotation.addProperty("angle", finiteNumber(rotation, "angle", 0));
                cube.add("rotation", canonicalRotation);
            } else if (blockbench && sourceRotation.isJsonArray()) {
                JsonObject canonicalRotation = new JsonObject();
                canonicalRotation.add("origin", source.has("origin")
                        ? readVector(source, "origin", 3) : zeroVector());
                canonicalRotation.add("euler", readVector(source, "rotation", 3));
                cube.add("rotation", canonicalRotation);
            } else {
                throw new IOException("A model element has an unsupported rotation value.");
            }
        }

        JsonObject faces = new JsonObject();
        JsonObject sourceFaces = source.has("faces") && source.get("faces").isJsonObject()
                ? source.getAsJsonObject("faces") : null;
        if (sourceFaces != null) {
            for (Map.Entry<String, JsonElement> entry : sourceFaces.entrySet()) {
                if (!FACE_DIRECTIONS.contains(entry.getKey())) {
                    throw new IOException("Unsupported model face direction: " + entry.getKey());
                }
                if (!entry.getValue().isJsonObject()) {
                    throw new IOException("Model face definitions must be objects.");
                }
                JsonObject sourceFace = entry.getValue().getAsJsonObject();
                JsonObject face = new JsonObject();
                if (sourceFace.has("uv") && sourceFace.get("uv").isJsonArray()) {
                    face.add("uv", readVector(sourceFace, "uv", 4));
                }
                face.addProperty("pixelUv", blockbench);
                if (sourceFace.has("rotation")) {
                    double rotation = finiteNumber(sourceFace, "rotation", 0);
                    if (rotation % 90 != 0) {
                        throw new IOException("Face UV rotation must be a multiple of 90 degrees.");
                    }
                    face.addProperty("rotation", rotation);
                }
                if (sourceFace.has("texture") && sourceFace.get("texture").isJsonPrimitive()) {
                    String reference = sourceFace.get("texture").getAsString();
                    if (blockbench && sourceFace.get("texture").isJsonPrimitive()
                            && sourceFace.get("texture").getAsJsonPrimitive().isNumber()) {
                        reference = sourceFace.get("texture").getAsString();
                    } else if (reference.startsWith("#")) {
                        reference = reference.substring(1);
                    }
                    face.addProperty("texture", textureIds.getOrDefault(reference, ""));
                } else {
                    face.addProperty("texture", "");
                }
                faces.add(entry.getKey(), face);
            }
        }
        if (faces.isEmpty()) {
            throw new IOException("Every cuboid must define at least one renderable face.");
        }
        cube.add("faces", faces);
        return cube;
    }

    private static JsonArray readVector(JsonObject source, String key, int expectedSize) throws IOException {
        JsonArray vector = source.has(key) && source.get(key).isJsonArray() ? source.getAsJsonArray(key) : null;
        if (vector == null || vector.size() != expectedSize) {
            throw new IOException("Model element field '" + key + "' must contain " + expectedSize + " numbers.");
        }
        JsonArray copy = new JsonArray();
        for (JsonElement element : vector) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IOException("Model coordinates must be numbers.");
            }
            double number = element.getAsDouble();
            if (!Double.isFinite(number) || Math.abs(number) > MAX_COORDINATE) {
                throw new IOException("Model coordinates must be finite and within ±2,048 model pixels.");
            }
            copy.add(number);
        }
        return copy;
    }

    private static double finiteNumber(JsonObject source, String key, double defaultValue) throws IOException {
        JsonElement element = source.get(key);
        if (element == null) {
            return defaultValue;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IOException("Model rotation angles must be numeric.");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        double value = primitive.getAsDouble();
        if (!Double.isFinite(value) || Math.abs(value) > 360) {
            throw new IOException("Model rotation angles must be finite and within ±360 degrees.");
        }
        return value;
    }

    private static JsonArray defaultOrigin() {
        JsonArray origin = new JsonArray();
        origin.add(8);
        origin.add(8);
        origin.add(8);
        return origin;
    }

    private static JsonArray zeroVector() {
        JsonArray origin = new JsonArray();
        origin.add(0);
        origin.add(0);
        origin.add(0);
        return origin;
    }
}
