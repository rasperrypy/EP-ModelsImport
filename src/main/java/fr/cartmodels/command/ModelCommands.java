package fr.cartmodels.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.cartmodels.CartModels;
import fr.cartmodels.model.CartModelInfo;
import fr.cartmodels.model.CartModelStore;
import fr.cartmodels.network.ModelPayloads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;

public final class ModelCommands {
    private static final int MODELS_PER_PAGE = 20;
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

        var giveModelCommand = Commands.literal("give-model")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(MODEL_SUGGESTIONS)
                        .executes(context -> giveModel(context.getSource().getPlayer(),
                                StringArgumentType.getString(context, "name"), 1, context.getSource()))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                .executes(context -> giveModel(context.getSource().getPlayer(),
                                        StringArgumentType.getString(context, "name"),
                                        IntegerArgumentType.getInteger(context, "amount"),
                                        context.getSource()))));
        event.getDispatcher().register(giveModelCommand);

        event.getDispatcher().register(Commands.literal("list-models")
                .executes(context -> listModels(context.getSource(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> listModels(context.getSource(),
                                IntegerArgumentType.getInteger(context, "page")))));

        event.getDispatcher().register(Commands.literal("model-info")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(MODEL_SUGGESTIONS)
                        .executes(context -> showModelInfo(context.getSource(),
                                StringArgumentType.getString(context, "name")))));
    }

    private static int giveModel(ServerPlayer player, String name, int amount,
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

        ItemStack stack = new ItemStack(CartModels.MODEL_BLOCK_ITEM.get(), amount);
        CompoundTag modelTag = new CompoundTag();
        modelTag.putString("model_name", name);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(modelTag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.translatable("commands.cartmodels.give.success", amount, name), false);
        return 1;
    }

    private static int listModels(net.minecraft.commands.CommandSourceStack source, int requestedPage) {
        List<String> names = new ArrayList<>(CartModelStore.get(source.getServer()).snapshot().keySet());
        if (names.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.cartmodels.list.empty"), false);
            return 0;
        }

        int pageCount = (names.size() - 1) / MODELS_PER_PAGE + 1;
        if (requestedPage > pageCount) {
            source.sendFailure(Component.translatable("commands.cartmodels.list.invalid_page", pageCount));
            return 0;
        }
        int start = (requestedPage - 1) * MODELS_PER_PAGE;
        int end = Math.min(start + MODELS_PER_PAGE, names.size());
        source.sendSuccess(() -> Component.translatable(
                "commands.cartmodels.list.header", names.size(), requestedPage, pageCount), false);
        for (String name : names.subList(start, end)) {
            source.sendSuccess(() -> Component.literal("- " + name).withStyle(style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/give-model " + name))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("commands.cartmodels.list.hover", name)))), false);
        }
        return names.size();
    }

    private static int showModelInfo(net.minecraft.commands.CommandSourceStack source, String name) {
        String model = CartModelStore.get(source.getServer()).getModel(name);
        if (model == null) {
            source.sendFailure(Component.literal("No imported model named '" + name + "' exists."));
            return 0;
        }
        try {
            CartModelInfo info = CartModelInfo.inspect(model);
            source.sendSuccess(() -> Component.translatable("commands.cartmodels.info.header", name), false);
            source.sendSuccess(() -> Component.translatable("commands.cartmodels.info.cubes", info.cubeCount()), false);
            source.sendSuccess(() -> Component.translatable(
                    "commands.cartmodels.info.textures", info.embeddedTextureCount()), false);
            source.sendSuccess(() -> Component.translatable("commands.cartmodels.info.size", info.dataSizeBytes()), false);
            return 1;
        } catch (java.io.IOException exception) {
            source.sendFailure(Component.literal("Could not inspect model '" + name + "': " + exception.getMessage()));
            return 0;
        }
    }
}
