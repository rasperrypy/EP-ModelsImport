package fr.cartmodels.client;

import fr.cartmodels.model.CartModelParser;
import fr.cartmodels.network.ModelChunkAssembler;
import fr.cartmodels.network.ModelPayloads;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientModelNetwork {
    private static final Logger LOGGER = LoggerFactory.getLogger("cartmodels");
    private static final int MAX_ENCODED_SIZE = ((CartModelParser.MAX_FILE_BYTES + 2) / 3) * 4;
    private static final ModelChunkAssembler DOWNLOADS = new ModelChunkAssembler();

    private ClientModelNetwork() {
    }

    public static void openImportScreen(ModelPayloads.OpenImportScreen payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new CartModelImportScreen()));
    }

    public static void showImportResult(ModelPayloads.ImportResult payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof CartModelImportScreen screen) {
                screen.setStatus(payload.message(), payload.success());
            } else if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal(payload.message()), false);
            }
        });
    }

    public static void receiveModel(ModelPayloads.ModelSyncChunk payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                byte[] completed = DOWNLOADS.accept(payload.chunkData(), MAX_ENCODED_SIZE);
                if (completed != null) {
                    ModelRenderCache.put(payload.modelName(), new String(completed, StandardCharsets.UTF_8));
                }
            } catch (IOException exception) {
                DOWNLOADS.clear(payload.transferId());
                LOGGER.error("Could not receive imported model '{}'", payload.modelName(), exception);
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("Model sync failed: " + exception.getMessage()), false);
                }
            }
        });
    }
}
