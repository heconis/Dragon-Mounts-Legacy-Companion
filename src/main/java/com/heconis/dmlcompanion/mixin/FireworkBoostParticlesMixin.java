package com.heconis.dmlcompanion.mixin;

import com.heconis.dmlcompanion.DragonFlight;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireworkRocketEntity.class)
abstract class FireworkBoostParticlesMixin {
    @Shadow
    public abstract ItemStack getItem();

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void dmlcompanion$skipBoostTrail(Level level, ParticleOptions particle, double x, double y, double z,
                                             double vx, double vy, double vz) {
        if (!DragonFlight.isBoostRocket(getItem())) {
            level.addParticle(particle, x, y, z, vx, vy, vz);
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
    private void dmlcompanion$skipBoostBurst(byte event, CallbackInfo callback) {
        if (event == 17 && DragonFlight.isBoostRocket(getItem())) {
            callback.cancel();
        }
    }
}
