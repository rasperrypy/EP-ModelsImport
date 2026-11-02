package fr.cartmodels.client;

import fr.cartmodels.CartModels;
import fr.cartmodels.network.ModelPayloads;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = CartModels.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CartModels.MODEL_BLOCK_ENTITY.get(), ModelBlockEntityRenderer::new);
        event.registerEntityRenderer(CartModels.CART_SEAT.get(), CartSeatRenderer::new);
    }

    @SubscribeEvent
    public static void registerClientPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(ModelPayloads.OpenImportScreen.TYPE, ModelPayloads.OpenImportScreen.STREAM_CODEC,
                ClientModelNetwork::openImportScreen);
        registrar.playToClient(ModelPayloads.ImportResult.TYPE, ModelPayloads.ImportResult.STREAM_CODEC,
                ClientModelNetwork::showImportResult);
        registrar.playToClient(ModelPayloads.ModelSyncChunk.TYPE, ModelPayloads.ModelSyncChunk.STREAM_CODEC,
                ClientModelNetwork::receiveModel);
    }
}
