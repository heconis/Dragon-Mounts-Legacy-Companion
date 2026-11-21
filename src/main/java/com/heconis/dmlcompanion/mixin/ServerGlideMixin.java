package com.heconis.dmlcompanion.mixin;

import com.heconis.dmlcompanion.DragonGlide;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGlideMixin {
    @Shadow
    public ServerPlayer player;

    @Shadow
    private boolean clientIsFloating;

    @Inject(method = "tick", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;clientIsFloating:Z"))
    private void dmlcompanion$recognizeMinionGlide(CallbackInfo callback) {
        if (clientIsFloating && DragonGlide.isAuthorizedGlide(player)) {
            clientIsFloating = false;
        }
    }
}
