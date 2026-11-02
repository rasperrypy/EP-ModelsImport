package fr.cartmodels.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class ModelPayloads {
    private ModelPayloads() {
    }

    public record OpenImportScreen() implements CustomPacketPayload {
        public static final Type<OpenImportScreen> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("cartmodels", "open_import_screen"));
        public static final StreamCodec<ByteBuf, OpenImportScreen> STREAM_CODEC = StreamCodec.unit(new OpenImportScreen());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ChunkData(String transferId, String modelName, String fileName, int chunkIndex, int chunkCount,
                            String data) {
    }

    public record ModelUploadChunk(String transferId, String modelName, String fileName, int chunkIndex,
                                   int chunkCount, String data) implements CustomPacketPayload {
        public static final Type<ModelUploadChunk> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("cartmodels", "model_upload_chunk"));
        public static final StreamCodec<ByteBuf, ModelUploadChunk> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ModelUploadChunk::transferId,
                ByteBufCodecs.STRING_UTF8, ModelUploadChunk::modelName,
                ByteBufCodecs.STRING_UTF8, ModelUploadChunk::fileName,
                ByteBufCodecs.VAR_INT, ModelUploadChunk::chunkIndex,
                ByteBufCodecs.VAR_INT, ModelUploadChunk::chunkCount,
                ByteBufCodecs.STRING_UTF8, ModelUploadChunk::data,
                ModelUploadChunk::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public ChunkData chunkData() {
            return new ChunkData(transferId, modelName, fileName, chunkIndex, chunkCount, data);
        }
    }

    public record ModelSyncChunk(String transferId, String modelName, String fileName, int chunkIndex,
                                 int chunkCount, String data) implements CustomPacketPayload {
        public static final Type<ModelSyncChunk> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("cartmodels", "model_sync_chunk"));
        public static final StreamCodec<ByteBuf, ModelSyncChunk> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ModelSyncChunk::transferId,
                ByteBufCodecs.STRING_UTF8, ModelSyncChunk::modelName,
                ByteBufCodecs.STRING_UTF8, ModelSyncChunk::fileName,
                ByteBufCodecs.VAR_INT, ModelSyncChunk::chunkIndex,
                ByteBufCodecs.VAR_INT, ModelSyncChunk::chunkCount,
                ByteBufCodecs.STRING_UTF8, ModelSyncChunk::data,
                ModelSyncChunk::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public ChunkData chunkData() {
            return new ChunkData(transferId, modelName, fileName, chunkIndex, chunkCount, data);
        }
    }

    public record ImportResult(boolean success, String message) implements CustomPacketPayload {
        public static final Type<ImportResult> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("cartmodels", "import_result"));
        public static final StreamCodec<ByteBuf, ImportResult> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ImportResult::success,
                ByteBufCodecs.STRING_UTF8, ImportResult::message,
                ImportResult::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
