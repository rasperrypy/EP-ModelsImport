package fr.cartmodels.network;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ModelChunkAssembler {
    public static final int CHUNK_SIZE = 24_000;
    private static final int MAX_CHUNKS = 512;
    private final Map<String, Assembly> transfers = new HashMap<>();

    public byte[] accept(ModelPayloads.ChunkData payload, int maximumEncodedSize) throws IOException {
        if (payload.transferId().length() != 36 || !isUuid(payload.transferId())
                || payload.modelName().length() > 32 || payload.fileName().length() > 128
                || payload.chunkCount() < 1 || payload.chunkCount() > MAX_CHUNKS
                || payload.chunkIndex() < 0 || payload.chunkIndex() >= payload.chunkCount()
                || payload.data().length() > CHUNK_SIZE) {
            throw new IOException("The model upload contains invalid chunk metadata.");
        }
        Assembly assembly = transfers.computeIfAbsent(payload.transferId(),
                ignored -> new Assembly(payload.modelName(), payload.fileName(), payload.chunkCount()));
        if (!assembly.matches(payload) || assembly.size + payload.data().length() > maximumEncodedSize) {
            transfers.remove(payload.transferId());
            throw new IOException("The model upload is inconsistent or exceeds the size limit.");
        }
        String previous = assembly.chunks[payload.chunkIndex()];
        if (previous != null) {
            if (!previous.equals(payload.data())) {
                transfers.remove(payload.transferId());
                throw new IOException("The model upload contains conflicting duplicate chunks.");
            }
            return null;
        }
        assembly.chunks[payload.chunkIndex()] = payload.data();
        assembly.size += payload.data().length();
        assembly.received++;
        if (assembly.received != assembly.chunks.length) {
            return null;
        }
        transfers.remove(payload.transferId());
        StringBuilder encoded = new StringBuilder(assembly.size);
        for (String chunk : assembly.chunks) {
            if (chunk == null) {
                throw new IOException("The model upload is missing a chunk.");
            }
            encoded.append(chunk);
        }
        try {
            return Base64.getDecoder().decode(encoded.toString());
        } catch (IllegalArgumentException exception) {
            throw new IOException("The uploaded model data is not valid base64.", exception);
        }
    }

    public void clear(String transferId) {
        transfers.remove(transferId);
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static final class Assembly {
        private final String modelName;
        private final String fileName;
        private final String[] chunks;
        private int received;
        private int size;

        private Assembly(String modelName, String fileName, int chunkCount) {
            this.modelName = modelName;
            this.fileName = fileName;
            this.chunks = new String[chunkCount];
        }

        private boolean matches(ModelPayloads.ChunkData payload) {
            return modelName.equals(payload.modelName()) && fileName.equals(payload.fileName())
                    && chunks.length == payload.chunkCount();
        }
    }
}
