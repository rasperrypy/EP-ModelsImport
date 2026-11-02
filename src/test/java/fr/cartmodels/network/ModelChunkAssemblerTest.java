package fr.cartmodels.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelChunkAssemblerTest {
    @Test
    void assemblesChunksAndReleasesCapacityAfterCompletion() throws Exception {
        ModelChunkAssembler assembler = new ModelChunkAssembler(1, Long.MAX_VALUE);
        String firstId = UUID.randomUUID().toString();
        String secondId = UUID.randomUUID().toString();

        assertNull(assembler.accept(chunk(firstId, 0, 2, "aGVs"), 48_000));
        assertThrows(IOException.class, () -> assembler.accept(chunk(secondId, 0, 2, "d29y"), 48_000));
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8),
                assembler.accept(chunk(firstId, 1, 2, "bG8="), 48_000));

        assertNull(assembler.accept(chunk(secondId, 0, 2, "d29y"), 48_000));
    }

    @Test
    void expiresAbandonedTransfersBeforeAcceptingNewOnes() throws Exception {
        ModelChunkAssembler assembler = new ModelChunkAssembler(1, 0);
        String abandonedId = UUID.randomUUID().toString();
        String nextId = UUID.randomUUID().toString();

        assertNull(assembler.accept(chunk(abandonedId, 0, 2, "aGVs"), 48_000));
        assertNull(assembler.accept(chunk(nextId, 0, 2, "d29y"), 48_000));
    }

    @Test
    void rejectsImpossibleChunkCountsAndEmptyChunks() {
        ModelChunkAssembler assembler = new ModelChunkAssembler();
        String id = UUID.randomUUID().toString();

        assertThrows(IOException.class, () -> assembler.accept(chunk(id, 0, 2, "a"), 24_000));
        assertThrows(IOException.class, () -> assembler.accept(chunk(id, 0, 1, ""), 100));
    }

    @Test
    void rejectsNonBase64ChunkCharactersBeforeBuffering() {
        ModelChunkAssembler assembler = new ModelChunkAssembler();

        assertThrows(IOException.class,
                () -> assembler.accept(chunk(UUID.randomUUID().toString(), 0, 1, "é==="), 100));
    }

    private static ModelPayloads.ChunkData chunk(String id, int index, int count, String data) {
        return new ModelPayloads.ChunkData(id, "cart", "cart.bbmodel", index, count, data);
    }
}
