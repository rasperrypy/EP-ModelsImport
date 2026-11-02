package fr.cartmodels;

import fr.cartmodels.block.ModelBlock;
import fr.cartmodels.block.ModelBlockEntity;
import fr.cartmodels.command.ModelCommands;
import fr.cartmodels.entity.CartSeatEntity;
import fr.cartmodels.network.ModelNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

@Mod(CartModels.MOD_ID)
public final class CartModels {
    public static final String MOD_ID = "cartmodels";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);

    public static final DeferredBlock<ModelBlock> MODEL_BLOCK = BLOCKS.register("model_block",
            () -> new ModelBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                    .strength(1.0F)
                    .noOcclusion()
                    .noCollission()));
    public static final DeferredItem<BlockItem> MODEL_BLOCK_ITEM = ITEMS.register("model_block",
            () -> new BlockItem(MODEL_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ModelBlockEntity>> MODEL_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("model_block",
                    () -> BlockEntityType.Builder.of(ModelBlockEntity::new, MODEL_BLOCK.get()).build(null));
    public static final DeferredHolder<EntityType<?>, EntityType<CartSeatEntity>> CART_SEAT =
            ENTITY_TYPES.register("cart_seat", () -> EntityType.Builder
                    .<CartSeatEntity>of(CartSeatEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("cartmodels:cart_seat"));

    public CartModels(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(ModelNetwork::register);
        NeoForge.EVENT_BUS.register(new ModelCommands());
        NeoForge.EVENT_BUS.register(new ServerLifecycleEvents());
    }
}
