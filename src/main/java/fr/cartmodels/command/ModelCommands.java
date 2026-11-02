package fr.cartmodels.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.cartmodels.CartModels;
import fr.cartmodels.model.CartModelStore;
import fr.cartmodels.network.ModelPayloads;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;

public final class ModelCommands {
    private static final SuggestionProvider<net.minecraft.commands.CommandSourceStack> MODEL_SUGGESTIONS =
            (context, builder) -> {
                var server = context.getSource().getServer();
                return SharedSuggestionProvider.suggest(CartModelStore.get(server).snapshot().keySet(), builder);
            };

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("import-model")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player == null) {
                        context.getSource().sendFailure(Component.literal("This command must be run by a player."));
                        return 0;
                    }
                    PacketDistributor.sendToPlayer(player, new ModelPayloads.OpenImportScreen());
                    return 1;
                }));

        event.getDispatcher().register(Commands.literal("give-model")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(MODEL_SUGGESTIONS)
                        .executes(context -> giveModel(context.getSource().getPlayer(),
                                StringArgumentType.getString(context, "name"), context.getSource()))));
    }

    private static int giveModel(ServerPlayer player, String name,
                                 net.minecraft.commands.CommandSourceStack source) {
        if (player == null) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
        CartModelStore store = CartModelStore.get(player.server);
        if (!store.contains(name)) {
            source.sendFailure(Component.literal("No imported model named '" + name + "' exists."));
            return 0;
        }

        ItemStack stack = new ItemStack(CartModels.MODEL_BLOCK_ITEM.get());
        CompoundTag modelTag = new CompoundTag();
        modelTag.putString("model_name", name);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(modelTag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.literal("Gave model block '" + name + "'."), false);
        return 1;
    }
}
