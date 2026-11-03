package fr.cartmodels.block;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import fr.cartmodels.CartModels;
import fr.cartmodels.model.CartModelStore;
import fr.cartmodels.model.ModelBounds;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModelBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger("cartmodels");

    private String modelName = "";
    @Nullable
    private ModelBounds selectionBounds;

    public ModelBlockEntity(BlockPos pos, BlockState state) {
        this(CartModels.MODEL_BLOCK_ENTITY.get(), pos, state);
    }

    public ModelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public String getModelName() {
        return modelName;
    }

    @Nullable
    public ModelBounds getSelectionBounds() {
        return selectionBounds;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
        refreshSelectionBounds();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (selectionBounds == null) {
            refreshSelectionBounds();
        }
    }

    private void refreshSelectionBounds() {
        selectionBounds = null;
        if (level == null || level.isClientSide || level.getServer() == null || modelName.isBlank()) {
            return;
        }
        String stored = CartModelStore.get(level.getServer()).getModel(modelName);
        if (stored == null) {
            return;
        }
        try {
            JsonObject model = JsonParser.parseString(stored).getAsJsonObject();
            selectionBounds = ModelBounds.fromModel(model);
        } catch (IllegalStateException | JsonSyntaxException exception) {
            LOGGER.warn("Could not calculate selection bounds for model '{}'", modelName, exception);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("model_name", modelName);
        if (selectionBounds == null) {
            tag.remove("bounds_min_x");
            tag.remove("bounds_min_y");
            tag.remove("bounds_min_z");
            tag.remove("bounds_max_x");
            tag.remove("bounds_max_y");
            tag.remove("bounds_max_z");
        } else {
            tag.putDouble("bounds_min_x", selectionBounds.minX());
            tag.putDouble("bounds_min_y", selectionBounds.minY());
            tag.putDouble("bounds_min_z", selectionBounds.minZ());
            tag.putDouble("bounds_max_x", selectionBounds.maxX());
            tag.putDouble("bounds_max_y", selectionBounds.maxY());
            tag.putDouble("bounds_max_z", selectionBounds.maxZ());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        modelName = tag.getString("model_name");
        selectionBounds = tag.contains("bounds_min_x") && tag.contains("bounds_min_y")
                && tag.contains("bounds_min_z") && tag.contains("bounds_max_x")
                && tag.contains("bounds_max_y") && tag.contains("bounds_max_z")
                ? new ModelBounds(tag.getDouble("bounds_min_x"), tag.getDouble("bounds_min_y"),
                        tag.getDouble("bounds_min_z"), tag.getDouble("bounds_max_x"),
                        tag.getDouble("bounds_max_y"), tag.getDouble("bounds_max_z"))
                : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
