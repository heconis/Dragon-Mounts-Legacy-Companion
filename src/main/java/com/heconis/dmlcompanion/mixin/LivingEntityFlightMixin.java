package com.heconis.dmlcompanion.mixin;

import com.heconis.dmlcompanion.DragonBlessings;
import com.heconis.dmlcompanion.DragonFlight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityFlightMixin {
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private void dmlcompanion$replaceBlessingBeforeMerge(MobEffectInstance effect, Entity source,
                                                       CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof ServerPlayer player) {
            DragonBlessings.replaceBlessing(player, effect);
        }
    }

    @Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
    private void dmlcompanion$keepMinionFlight(CallbackInfo callback) {
        // Keep vanilla flight mechanics and bypass its chest-slot upkeep only during an approved minion flight.
        if ((Object) this instanceof ServerPlayer player && DragonFlight.keepsFlying(player)) {
            callback.cancel();
        }
    }
}
