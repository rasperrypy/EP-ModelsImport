package fr.cartmodels.entity;

import fr.cartmodels.CartModels;
import fr.cartmodels.block.ModelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CartSeatEntity extends Entity {
    private static final EntityDataAccessor<Boolean> LOCKED =
            SynchedEntityData.defineId(CartSeatEntity.class, EntityDataSerializers.BOOLEAN);

    private BlockPos anchorPos = BlockPos.ZERO;
    private int seatIndex = -1;
    private long lockUntilGameTime;

    public CartSeatEntity(EntityType<? extends CartSeatEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public void setSeat(BlockPos anchorPos, int seatIndex, int lockSeconds) {
        this.anchorPos = anchorPos.immutable();
        this.seatIndex = seatIndex;
        lockUntilGameTime = lockSeconds > 0 ? level().getGameTime() + lockSeconds * 20L : 0L;
        entityData.set(LOCKED, lockSeconds > 0);
        setNoGravity(true);
    }

    public BlockPos getAnchorPos() {
        return anchorPos;
    }

    public int getSeatIndex() {
        return seatIndex;
    }

    public boolean isSeatLocked() {
        return level().isClientSide ? entityData.get(LOCKED) : lockUntilGameTime > level().getGameTime();
    }

    public int getRemainingLockSeconds() {
        long remainingTicks = lockUntilGameTime - level().getGameTime();
        return remainingTicks <= 0 ? 0 : (int) ((remainingTicks + 19) / 20);
    }

    public void clearSeatLock() {
        lockUntilGameTime = 0L;
        entityData.set(LOCKED, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (lockUntilGameTime > 0 && !isSeatLocked()) {
            clearSeatLock();
            if (!getPassengers().isEmpty() && getPassengers().get(0) instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("seat.cartmodels.unlocked"), true);
            }
        }
        if (getPassengers().isEmpty()) {
            discard();
            return;
        }
        if (level().hasChunkAt(anchorPos)
                && !level().getBlockState(anchorPos).is(CartModels.MODEL_BLOCK.get())) {
            clearSeatLock();
            while (!getPassengers().isEmpty()) {
                getPassengers().get(0).stopRiding();
            }
            discard();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LOCKED, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        anchorPos = BlockPos.of(tag.getLong("anchor"));
        seatIndex = tag.getInt("seat");
        lockUntilGameTime = tag.getLong("lockUntil");
        entityData.set(LOCKED, lockUntilGameTime > level().getGameTime());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("anchor", anchorPos.asLong());
        tag.putInt("seat", seatIndex);
        tag.putLong("lockUntil", lockUntilGameTime);
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
