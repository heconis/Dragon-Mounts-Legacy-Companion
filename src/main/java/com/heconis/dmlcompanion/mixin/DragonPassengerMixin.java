package com.heconis.dmlcompanion.mixin;

import com.github.kay9.dragonmounts.dragon.TameableDragon;
import com.google.common.collect.ImmutableList;
import com.heconis.dmlcompanion.DragonRiding;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class DragonPassengerMixin {
    @Shadow
    private ImmutableList<Entity> passengers;

    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    private void dmlcompanion$allowSecondSeat(Entity passenger, CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof TameableDragon dragon && DragonRiding.canTakeSecondSeat(dragon, passenger)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "addPassenger", at = @At("RETURN"))
    private void dmlcompanion$keepOwnerInFront(Entity passenger, CallbackInfo callback) {
        if ((Object) this instanceof TameableDragon dragon && passengers.size() == 2
                && passengers.get(0) instanceof Player first && passengers.get(1) instanceof Player second) {
            if (!dragon.isTamedFor(first) && dragon.isTamedFor(second)) {
                passengers = ImmutableList.of(second, first);
            }
            DragonRiding.markSharedMount(dragon);
        }
    }

    @ModifyArg(method = "positionRider(Lnet/minecraft/world/entity/Entity;)V", index = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V"))
    private Entity.MoveFunction dmlcompanion$offsetRearSeat(Entity.MoveFunction move) {
        if ((Object) this instanceof TameableDragon dragon && dragon.isTame()) {
            return (rider, x, y, z) -> {
                if (rider instanceof Player player && !dragon.isTamedFor(player)) {
                    double distance = Math.max(0.75, 1.1 * dragon.getScale());
                    var rear = Vec3.directionFromRotation(0, dragon.yBodyRot).scale(-distance);
                    move.accept(rider, x + rear.x, y, z + rear.z);
                } else {
                    move.accept(rider, x, y, z);
                }
            };
        }
        return move;
    }

    @Inject(method = "hasExactlyOnePlayerPassenger", at = @At("HEAD"), cancellable = true)
    private void dmlcompanion$keepSharedMountInWorld(CallbackInfoReturnable<Boolean> callback) {
        // Vanilla otherwise moves the dragon into the last rider's player file.
        if ((Object) this instanceof TameableDragon dragon && DragonRiding.isSharedMount(dragon)) {
            callback.setReturnValue(false);
        }
    }
}
