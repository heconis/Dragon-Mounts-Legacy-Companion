package com.heconis.dmlcompanion.mixin;

import com.github.kay9.dragonmounts.dragon.TameableDragon;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TameableDragon.class)
abstract class DragonPassengerCameraMixin {
    @Redirect(method = {"addPassenger", "removePassenger"}, at = @At(value = "INVOKE",
            target = "Lcom/github/kay9/dragonmounts/dragon/TameableDragon;hasLocalDriver()Z", remap = false),
            require = 2)
    private boolean dmlcompanion$cameraFollowsDriverChanges(TameableDragon dragon, Entity passenger) {
        return passenger instanceof Player player && player.isLocalPlayer() && dragon.hasLocalDriver();
    }
}
