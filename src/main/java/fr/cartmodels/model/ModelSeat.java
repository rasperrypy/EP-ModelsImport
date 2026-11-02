package fr.cartmodels.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public record ModelSeat(int x, int y, int z) {
    public static final int MAX_SEATS = 64;
    public static final int MODEL_VOXEL_SIZE = 1;
    public static final int LEGACY_VOXEL_SIZE = 16;

    public static List<ModelSeat> read(JsonElement element) throws IOException {
        return read(element, LEGACY_VOXEL_SIZE);
    }

    public static List<ModelSeat> read(JsonElement element, int voxelSize) throws IOException {
        if (voxelSize != MODEL_VOXEL_SIZE && voxelSize != LEGACY_VOXEL_SIZE) {
            throw new IOException("Unsupported seat voxel size.");
        }
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray() || element.getAsJsonArray().size() > MAX_SEATS) {
            throw new IOException("A model can define at most 64 seats.");
        }
        int minimum = voxelSize == MODEL_VOXEL_SIZE ? -2_048 : -128;
        int maximum = voxelSize == MODEL_VOXEL_SIZE ? 2_047 : 127;
        List<ModelSeat> seats = new ArrayList<>();
        for (JsonElement seatElement : element.getAsJsonArray()) {
            if (!seatElement.isJsonArray()) {
                throw new IOException("Every seat must be a voxel coordinate.");
            }
            JsonArray coordinates = seatElement.getAsJsonArray();
            if (coordinates.size() != 3) {
                throw new IOException("Seat coordinates must contain exactly three integers.");
            }
            int[] values = new int[3];
            for (int index = 0; index < values.length; index++) {
                JsonElement coordinate = coordinates.get(index);
                if (!coordinate.isJsonPrimitive() || !coordinate.getAsJsonPrimitive().isNumber()) {
                    throw new IOException("Seat coordinates must be integers.");
                }
                double value = coordinate.getAsDouble();
                if (value != Math.rint(value) || value < minimum || value > maximum) {
                    throw new IOException("Seat voxels must be within the supported model range.");
                }
                values[index] = (int) value;
            }
            ModelSeat seat = new ModelSeat(values[0], values[1], values[2]);
            if (seats.contains(seat)) {
                throw new IOException("Seat voxels cannot be selected more than once.");
            }
            seats.add(seat);
        }
        return List.copyOf(seats);
    }

    public JsonArray toJson() {
        JsonArray coordinates = new JsonArray();
        coordinates.add(x);
        coordinates.add(y);
        coordinates.add(z);
        return coordinates;
    }
}
