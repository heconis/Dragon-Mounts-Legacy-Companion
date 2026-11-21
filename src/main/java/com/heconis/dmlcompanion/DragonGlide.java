package com.heconis.dmlcompanion;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.IdentityHashMap;
import java.util.Map;

public final class DragonGlide {
    static final int INPUT_INTERVAL = 8;
    private static final int INPUT_TIMEOUT = 24;
    private static final double MAX_DESCENT = 0.08;
    private static final double MAX_HORIZONTAL_SPEED = 0.075;
    private static final double IMPROVED_HORIZONTAL_SPEED = 0.26;
    private static final double IMPROVED_HORIZONTAL_FACTOR = 1.025;
    private static final Map<ServerPlayer, Integer> LAST_INPUT = new IdentityHashMap<>();

    private DragonGlide() {}

    static void setHeld(ServerPlayer player, boolean held) {
        if (held && DragonMinions.hasActiveMinion(player)) {
            LAST_INPUT.put(player, player.server.getTickCount());
        } else {
            clear(player);
        }
    }

    static void clear(ServerPlayer player) {
        LAST_INPUT.remove(player);
    }

    static void clearServer(MinecraftServer server) {
        LAST_INPUT.keySet().removeIf(player -> player.server == server);
    }

    public static boolean isAuthorizedGlide(ServerPlayer player) {
        var minion = DragonMinions.activeMinion(player);
        if (minion == null || !minion.isGliding() || !canGlide(player) || player.getDeltaMovement().y > 0) {
            return false;
        }
        var lastInput = LAST_INPUT.get(player);
        boolean held = lastInput != null && player.server.getTickCount() - lastInput < INPUT_TIMEOUT;
        return (held || minion.isRecoveryGlide()) && DragonMountsCompanion.DRAGON_RING.get().equippedDragon(player)
                .filter(minion.dragonId()::equals).isPresent();
    }

    static void tick(ServerPlayer player, DragonMinion minion) {
        var flightMode = DragonFlight.tick(player, minion);
        if (flightMode != DragonMinion.AirMode.NONE) {
            minion.setAirMode(flightMode);
            if (flightMode == DragonMinion.AirMode.RECOVERY_GLIDE) {
                slowFall(player, minion.friendshipLevel());
            }
            return;
        }
        Integer lastInput = LAST_INPUT.get(player);
        if (lastInput != null && player.server.getTickCount() - lastInput >= INPUT_TIMEOUT) {
            clear(player);
            lastInput = null;
        }
        double verticalSpeed = player.getDeltaMovement().y;
        boolean gliding = lastInput != null && canGlide(player) && verticalSpeed <= 0
                && (verticalSpeed < 0 || player.fallDistance > 0 || minion.isGliding());
        minion.setAirMode(gliding ? DragonMinion.AirMode.GLIDE : DragonMinion.AirMode.NONE);
        if (gliding) {
            slowFall(player, minion.friendshipLevel());
        }
    }

    static boolean canGlide(Player player) {
        return canUseAirMovement(player) && !player.isFallFlying();
    }

    static boolean canUseAirMovement(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.onGround() && !player.getAbilities().flying
                && !player.isPassenger() && !player.onClimbable()
                && !player.isInWaterOrBubble() && !player.isInLava();
    }

    static void slowFall(Player player, int friendshipLevel) {
        var velocity = player.getDeltaMovement();
        double horizontalSpeed = velocity.horizontalDistance();
        boolean improved = friendshipLevel >= 4;
        double speedLimit = improved ? IMPROVED_HORIZONTAL_SPEED : MAX_HORIZONTAL_SPEED;
        double scale = improved ? IMPROVED_HORIZONTAL_FACTOR : 1;
        if (horizontalSpeed * scale > speedLimit) {
            scale = speedLimit / horizontalSpeed;
        }
        player.setDeltaMovement(velocity.x * scale, Math.max(velocity.y, -MAX_DESCENT), velocity.z * scale);
        player.resetFallDistance();
    }
}
