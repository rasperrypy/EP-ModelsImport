package fr.cartmodels;

import fr.cartmodels.model.CartModelStore;
import fr.cartmodels.network.ModelNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ServerLifecycleEvents {
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        CartModelStore.get(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModelNetwork.sendAllModels(player, CartModelStore.get(player.server));
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        CartModelStore.unload(event.getServer());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) {
            ModelNetwork.cleanupExpiredUploads();
        }
    }
}
