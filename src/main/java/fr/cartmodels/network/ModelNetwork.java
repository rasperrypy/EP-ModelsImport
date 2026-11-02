package fr.cartmodels.network;

import fr.cartmodels.model.CartModelParser;
import fr.cartmodels.model.CartModelStore;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModelNetwork {
    private static final Logger LOGGER = LoggerFactory.getLogger("cartmodels");
    private static final int MAX_ENCODED_SIZE = ((CartModelParser.MAX_FILE_BYTES + 2) / 3) * 4;
    private static final ModelChunkAssembler UPLOADS = new ModelChunkAssembler();

    private ModelNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ModelPayloads.ModelUploadChunk.TYPE, ModelPayloads.ModelUploadChunk.STREAM_CODEC,
                ModelNetwork::receiveUploadOnServer);
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            registrar.playToClient(ModelPayloads.OpenImportScreen.TYPE,
                    ModelPayloads.OpenImportScreen.STREAM_CODEC, ModelNetwork::rejectUnexpectedClientPayload);
            registrar.playToClient(ModelPayloads.ImportResult.TYPE, ModelPayloads.ImportResult.STREAM_CODEC,
                    ModelNetwork::rejectUnexpectedClientPayload);
            registrar.playToClient(ModelPayloads.ModelSyncChunk.TYPE, ModelPayloads.ModelSyncChunk.STREAM_CODEC,
                    ModelNetwork::rejectUnexpectedClientPayload);
        }
    }

    public static void uploadModel(String modelName, String fileName, byte[] fileBytes) {
        String encoded = Base64.getEncoder().encodeToString(fileBytes);
        int chunks = (encoded.length() + ModelChunkAssembler.CHUNK_SIZE - 1) / ModelChunkAssembler.CHUNK_SIZE;
        String transferId = UUID.randomUUID().toString();
        for (int index = 0; index < chunks; index++) {
            int start = index * ModelChunkAssembler.CHUNK_SIZE;
            int end = Math.min(start + ModelChunkAssembler.CHUNK_SIZE, encoded.length());
            PacketDistributor.sendToServer(new ModelPayloads.ModelUploadChunk(transferId, modelName, fileName, index,
                    chunks, encoded.substring(start, end)));
        }
    }

    public static void sendAllModels(ServerPlayer player, CartModelStore store) {
        store.snapshot().forEach((name, json) -> sendModel(player, name, json));
    }

    public static void sendModel(ServerPlayer player, String name, String json) {
        sendChunks(player, name, "model.json", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendChunks(ServerPlayer player, String name, String fileName, byte[] bytes) {
        String encoded = Base64.getEncoder().encodeToString(bytes);
        int chunks = (encoded.length() + ModelChunkAssembler.CHUNK_SIZE - 1) / ModelChunkAssembler.CHUNK_SIZE;
        String transferId = UUID.randomUUID().toString();
        for (int index = 0; index < chunks; index++) {
            int start = index * ModelChunkAssembler.CHUNK_SIZE;
            int end = Math.min(start + ModelChunkAssembler.CHUNK_SIZE, encoded.length());
            PacketDistributor.sendToPlayer(player,
                    new ModelPayloads.ModelSyncChunk(transferId, name, fileName, index, chunks,
                            encoded.substring(start, end)));
        }
    }

    private static void receiveUploadOnServer(ModelPayloads.ModelUploadChunk payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player sender = context.player();
            if (!(sender instanceof ServerPlayer player) || !player.hasPermissions(2)) {
                if (sender instanceof ServerPlayer serverPlayer) {
                    PacketDistributor.sendToPlayer(serverPlayer,
                            new ModelPayloads.ImportResult(false, "Only operators can import models."));
                }
                return;
            }
            try {
                byte[] completed = UPLOADS.accept(payload.chunkData(), MAX_ENCODED_SIZE);
                if (completed == null) {
                    return;
                }
                if (!CartModelStore.isValidName(payload.modelName())) {
                    throw new IOException("Names must be 1–32 letters, numbers, underscores, or hyphens.");
                }
                String input = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(completed)).toString();
                String normalized = CartModelParser.parse(payload.fileName(), input);
                CartModelStore store = CartModelStore.get(player.server);
                boolean replacing = store.contains(payload.modelName());
                store.put(payload.modelName(), normalized);
                player.server.getPlayerList().getPlayers()
                        .forEach(onlinePlayer -> sendModel(onlinePlayer, payload.modelName(), normalized));
                PacketDistributor.sendToPlayer(player,
                        new ModelPayloads.ImportResult(true, replacing
                                ? "Replaced imported model '" + payload.modelName() + "'."
                                : "Imported model '" + payload.modelName() + "'."));
            } catch (IOException | IllegalArgumentException exception) {
                UPLOADS.clear(payload.transferId());
                LOGGER.warn("Rejected model import '{}' from {}", payload.modelName(), player.getGameProfile().getName(),
                        exception);
                PacketDistributor.sendToPlayer(player,
                        new ModelPayloads.ImportResult(false, "Import failed: " + exception.getMessage()));
            } catch (IllegalStateException exception) {
                UPLOADS.clear(payload.transferId());
                LOGGER.error("Could not persist imported model '{}'", payload.modelName(), exception);
                PacketDistributor.sendToPlayer(player,
                        new ModelPayloads.ImportResult(false, "Could not save model data: " + exception.getMessage()));
            }
        });
    }

    private static void rejectUnexpectedClientPayload(CustomPacketPayload payload, IPayloadContext context) {
        context.disconnect(Component.literal("Unexpected clientbound Cart Models payload."));
    }

}
