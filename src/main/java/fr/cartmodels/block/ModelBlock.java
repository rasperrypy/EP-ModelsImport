package fr.cartmodels.block;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.MapCodec;
import fr.cartmodels.CartModels;
import fr.cartmodels.entity.CartSeatEntity;
import fr.cartmodels.model.CartModelStore;
import fr.cartmodels.model.ModelSeat;
import java.io.IOException;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

public final class ModelBlock extends BaseEntityBlock {
    public static final MapCodec<ModelBlock> CODEC = simpleCodec(ModelBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape EMPTY_SHAPE = Shapes.empty();

    public ModelBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ModelBlockEntity modelBlockEntity)
                || modelBlockEntity.getModelName().isBlank()) {
            return InteractionResult.PASS;
        }
        String stored = CartModelStore.get(level.getServer()).getModel(modelBlockEntity.getModelName());
        if (stored == null) {
            player.displayClientMessage(Component.translatable("seat.cartmodels.model_missing"), true);
            return InteractionResult.CONSUME;
        }
        try {
            JsonObject model = JsonParser.parseString(stored).getAsJsonObject();
            int voxelSize = model.has("seatVoxelSize")
                    ? model.get("seatVoxelSize").getAsInt() : ModelSeat.LEGACY_VOXEL_SIZE;
            List<ModelSeat> seats = ModelSeat.read(model.get("seats"), voxelSize);
            if (seats.isEmpty()) {
                player.displayClientMessage(Component.translatable("seat.cartmodels.none"), true);
                return InteractionResult.CONSUME;
            }
            for (int index = 0; index < seats.size(); index++) {
                int seatIndex = index;
                ModelSeat seat = seats.get(index);
                double[] local = rotateSeat(seat, voxelSize, state.getValue(FACING));
                double seatX = pos.getX() + local[0];
                double seatY = pos.getY() + local[1];
                double seatZ = pos.getZ() + local[2];
                boolean occupied = level.getEntitiesOfClass(CartSeatEntity.class,
                        new net.minecraft.world.phys.AABB(seatX - 0.4, seatY - 0.4, seatZ - 0.4,
                                seatX + 0.4, seatY + 0.4, seatZ + 0.4),
                        anchorEntity -> anchorEntity.getAnchorPos().equals(pos)
                                && anchorEntity.getSeatIndex() == seatIndex
                                && !anchorEntity.getPassengers().isEmpty()).size() > 0;
                if (occupied) {
                    continue;
                }
                CartSeatEntity anchor = CartModels.CART_SEAT.get().create(level);
                if (anchor == null) {
                    throw new IllegalStateException("Could not create a seat anchor entity.");
                }
                anchor.setSeat(pos, index);
                anchor.setPos(pos.getX() + local[0], pos.getY() + local[1], pos.getZ() + local[2]);
                anchor.setYRot(state.getValue(FACING).toYRot());
                if (!level.addFreshEntity(anchor)) {
                    player.displayClientMessage(Component.translatable("seat.cartmodels.failed"), true);
                    return InteractionResult.CONSUME;
                }
                if (player.startRiding(anchor, true)) {
                    player.displayClientMessage(Component.translatable("seat.cartmodels.entered", index + 1), true);
                } else {
                    anchor.discard();
                    player.displayClientMessage(Component.translatable("seat.cartmodels.failed"), true);
                }
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.translatable("seat.cartmodels.full"), true);
        } catch (IOException | IllegalStateException | com.google.gson.JsonSyntaxException exception) {
            player.displayClientMessage(Component.translatable("seat.cartmodels.invalid"), true);
        }
        return InteractionResult.CONSUME;
    }

    private static double[] rotateSeat(ModelSeat seat, int voxelSize, Direction facing) {
        double scale = voxelSize / 16.0;
        double x = (seat.x() + 0.5) * scale;
        double y = (seat.y() + 1.0) * scale;
        double z = (seat.z() + 0.5) * scale;
        return switch (facing) {
            case EAST -> new double[]{1.0 - z, y, x};
            case SOUTH -> new double[]{1.0 - x, y, 1.0 - z};
            case WEST -> new double[]{z, y, 1.0 - x};
            default -> new double[]{x, y, z};
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return EMPTY_SHAPE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ModelBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ModelBlockEntity modelBlockEntity)) {
            return;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return;
        }
        CompoundTag tag = customData.copyTag();
        if (tag.contains("model_name")) {
            modelBlockEntity.setModelName(tag.getString("model_name"));
        }
    }
}
