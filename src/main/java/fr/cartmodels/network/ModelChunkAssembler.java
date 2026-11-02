package fr.cartmodels.network;

import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ModelChunkAssembler {
    public static final int CHUNK_SIZE = 24_000;
    private static final int MAX_CHUNKS = 512;
    private static final int MAX_ACTIVE_TRANSFERS = 16;
    private static final int MAX_BUFFERED_BYTES = 16 * 1024 * 1024;
    private static final long TRANSFER_TIMEOUT_NANOS = 2L * 60L * 1_000_000_000L;
    private final Map<String, Assembly> transfers = new HashMap<>();
    private final int maxActiveTransfers;
    private final long transferTimeoutNanos;
    private int bufferedBytes;

    public ModelChunkAssembler() {
        this(MAX_ACTIVE_TRANSFERS, TRANSFER_TIMEOUT_NANOS);
    }

    ModelChunkAssembler(int maxActiveTransfers, long transferTimeoutNanos) {
        this.maxActiveTransfers = maxActiveTransfers;
        this.transferTimeoutNanos = transferTimeoutNanos;
    }

    public synchronized byte[] accept(ModelPayloads.ChunkData payload, int maximumEncodedSize) throws IOException {
        long now = System.nanoTime();
        removeExpired(now);
        if (payload == null || payload.transferId() == null || payload.modelName() == null
                || payload.fileName() == null || payload.data() == null
                || payload.transferId().length() != 36 || !isUuid(payload.transferId())
                || payload.modelName().length() > 32 || payload.fileName().length() > 128
                || payload.chunkCount() < 1 || payload.chunkCount() > MAX_CHUNKS
                || payload.chunkIndex() < 0 || payload.chunkIndex() >= payload.chunkCount()
                || payload.data().isEmpty() || payload.data().length() > CHUNK_SIZE
                || payload.chunkCount() > (maximumEncodedSize + CHUNK_SIZE - 1) / CHUNK_SIZE) {
            throw new IOException("The model upload contains invalid chunk metadata.");
        }
        if (!isBase64Chunk(payload.data())) {
            throw new IOException("The model upload contains invalid base64 characters.");
        }
        Assembly assembly = transfers.get(payload.transferId());
        if (assembly == null) {
            if (transfers.size() >= maxActiveTransfers) {
                throw new IOException("Too many model transfers are currently in progress.");
            }
            assembly = new Assembly(payload.modelName(), payload.fileName(), payload.chunkCount(), now);
            transfers.put(payload.transferId(), assembly);
        }
        if (!assembly.matches(payload) || assembly.size + payload.data().length() > maximumEncodedSize) {
            remove(payload.transferId());
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
        if (bufferedBytes + payload.data().length() > MAX_BUFFERED_BYTES) {
            remove(payload.transferId());
            throw new IOException("Too much model data is currently being transferred.");
        }
        assembly.chunks[payload.chunkIndex()] = payload.data();
        assembly.size += payload.data().length();
        assembly.lastUpdatedNanos = now;
        bufferedBytes += payload.data().length();
        assembly.received++;
        if (assembly.received != assembly.chunks.length) {
            return null;
        }
        remove(payload.transferId());
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

    public synchronized void clear(String transferId) {
        remove(transferId);
    }

    public synchronized void cleanupExpired() {
        removeExpired(System.nanoTime());
    }

    private void removeExpired(long now) {
        Iterator<Map.Entry<String, Assembly>> iterator = transfers.entrySet().iterator();
        while (iterator.hasNext()) {
            Assembly assembly = iterator.next().getValue();
            if (now - assembly.lastUpdatedNanos >= transferTimeoutNanos) {
                bufferedBytes -= assembly.size;
                iterator.remove();
            }
        }
    }

    private void remove(String transferId) {
        Assembly removed = transfers.remove(transferId);
        if (removed != null) {
            bufferedBytes -= removed.size;
        }
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isBase64Chunk(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'A' && character <= 'Z')
                    && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '+' && character != '/' && character != '=') {
                return false;
            }
        }
        return true;
    }

    private static final class Assembly {
        private final String modelName;
        private final String fileName;
        private final String[] chunks;
        private int received;
        private int size;
        private long lastUpdatedNanos;

        private Assembly(String modelName, String fileName, int chunkCount, long now) {
            this.modelName = modelName;
            this.fileName = fileName;
            this.chunks = new String[chunkCount];
            this.lastUpdatedNanos = now;
        }

        private boolean matches(ModelPayloads.ChunkData payload) {
            return modelName.equals(payload.modelName()) && fileName.equals(payload.fileName())
                    && chunks.length == payload.chunkCount();
        }
    }
}
