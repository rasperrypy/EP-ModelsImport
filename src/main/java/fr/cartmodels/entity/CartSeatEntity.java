package fr.cartmodels.entity;

import fr.cartmodels.CartModels;
import fr.cartmodels.block.ModelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class CartSeatEntity extends Entity {
    private BlockPos anchorPos = BlockPos.ZERO;
    private int seatIndex = -1;

    public CartSeatEntity(EntityType<? extends CartSeatEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public void setSeat(BlockPos anchorPos, int seatIndex) {
        this.anchorPos = anchorPos.immutable();
        this.seatIndex = seatIndex;
        setNoGravity(true);
    }

    public BlockPos getAnchorPos() {
        return anchorPos;
    }

    public int getSeatIndex() {
        return seatIndex;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (getPassengers().isEmpty()) {
            discard();
            return;
        }
        if (level().hasChunkAt(anchorPos)
                && !level().getBlockState(anchorPos).is(CartModels.MODEL_BLOCK.get())) {
            while (!getPassengers().isEmpty()) {
                getPassengers().get(0).stopRiding();
            }
            discard();
        }
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        anchorPos = BlockPos.of(tag.getLong("anchor"));
        seatIndex = tag.getInt("seat");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("anchor", anchorPos.asLong());
        tag.putInt("seat", seatIndex);
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return position();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
