package com.heconis.dmlcompanion.mixin;

import com.heconis.dmlcompanion.EndDragonEggReward;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EndDragonFight.class)
abstract class EndDragonEggRewardMixin {
    @Shadow @Final private ServerLevel level;

    // DML controls whether the egg spawns. Swap the block at the point where the fight places it.
    @Redirect(method = "setDragonKilled", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean dmlcompanion$placeEggReward(ServerLevel level, BlockPos position, BlockState egg) {
        return EndDragonEggReward.place(level, position) || level.setBlockAndUpdate(position, egg);
    }

    @Inject(method = "respawnDragon", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/dimension/end/EndDragonFight;spawnExitPortal(Z)V"))
    private void dmlcompanion$removePreviousEggReward(List<EndCrystal> crystals, CallbackInfo callback) {
        EndDragonEggReward.remove(level);
    }
}
