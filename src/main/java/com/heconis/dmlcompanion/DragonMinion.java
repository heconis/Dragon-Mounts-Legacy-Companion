package com.heconis.dmlcompanion;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.Optional;
import java.util.UUID;

final class DragonMinion extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(DragonMinion.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> BREED =
            SynchedEntityData.defineId(DragonMinion.class, EntityDataSerializers.STRING);
    private UUID dragonId;
    private Vec3 lerpPosition = Vec3.ZERO;
    private float lerpYaw;
    private int lerpSteps;

    DragonMinion(EntityType<? extends DragonMinion> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    boolean bind(Player owner, UUID id, ResourceLocation breed) {
        dragonId = id;
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(BREED, breed.toString());
        setPos(owner.position());
        var target = followPosition(owner, 0);
        if (!canOccupy(target)) {
            target = owner.position().add(0, owner.getEyeHeight() + 0.25, 0);
        }
        if (!canOccupy(target)) {
            return false;
        }
        moveTo(target.x, target.y, target.z, owner.getYRot(), 0);
        return true;
    }

    UUID dragonId() {
        return dragonId;
    }

    Player owner() {
        return entityData.get(OWNER).map(level()::getPlayerByUUID).orElse(null);
    }

    String breedId() {
        return entityData.get(BREED);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER, Optional.empty());
        entityData.define(BREED, "");
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (lerpSteps > 0) {
                setPos(position().lerp(lerpPosition, 1d / lerpSteps));
                setYRot(getYRot() + Mth.wrapDegrees(lerpYaw - getYRot()) / lerpSteps);
                lerpSteps--;
            }
            return;
        }

        var player = owner();
        if (player == null || !player.isAlive() || player.isSpectator()) {
            discard();
            return;
        }
        var target = followPosition(player, 0.12 * Math.sin(tickCount * 0.14));
        var delta = target.subtract(position());
        if (delta.lengthSqr() > 64 && canOccupy(target)) {
            moveTo(target.x, target.y, target.z, player.getYRot(), 0);
        } else if (level().hasChunkAt(BlockPos.containing(target))) {
            var step = delta.scale(0.17);
            if (step.lengthSqr() > 0.55 * 0.55) {
                step = step.normalize().scale(0.55);
            }
            move(MoverType.SELF, step);
            setYRot(Mth.approachDegrees(getYRot(), player.getYRot(), 20));
        }
    }

    private static Vec3 followPosition(Player player, double bob) {
        var back = Vec3.directionFromRotation(0, player.getYRot()).scale(-1.8);
        return player.position().add(back.x, player.getEyeHeight() + 0.18 + bob, back.z);
    }

    private boolean canOccupy(Vec3 target) {
        return level().hasChunkAt(BlockPos.containing(target))
                && level().noCollision(this, getBoundingBox().move(target.subtract(position())));
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        if (teleport) {
            moveTo(x, y, z, yaw, pitch);
            setOldPosAndRot();
            lerpSteps = 0;
        } else {
            lerpPosition = new Vec3(x, y, z);
            lerpYaw = yaw;
            lerpSteps = Math.max(1, steps);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
