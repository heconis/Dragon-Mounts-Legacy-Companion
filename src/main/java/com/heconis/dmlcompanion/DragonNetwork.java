package com.heconis.dmlcompanion;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Locale;

final class DragonNetwork {
    private static final String PROTOCOL = "2";
    private static final int TOGGLE_COOLDOWN_TICKS = 20;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(DragonMountsCompanion.MOD_ID, "main"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);

    private DragonNetwork() {}

    static void register() {
        // The server resolves the ring and dragon from the sender's equipment.
        CHANNEL.messageBuilder(ToggleRequest.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((request, buffer) -> {})
                .decoder(buffer -> new ToggleRequest())
                .consumerMainThread((request, context) -> toggle(context.get().getSender()))
                .add();
    }

    static void sendToggle() {
        CHANNEL.sendToServer(new ToggleRequest());
    }

    private static void toggle(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        var ring = DragonMountsCompanion.DRAGON_RING.get();
        var cooldowns = player.getCooldowns();
        if (cooldowns.isOnCooldown(ring)) {
            return;
        }
        cooldowns.addCooldown(ring, TOGGLE_COOLDOWN_TICKS);

        var outcome = DragonStorage.toggle(player);
        var result = outcome.result();
        boolean success = result == DragonStorage.Result.MINION || result == DragonStorage.Result.RESTORED;
        if (success) {
            DragonMinions.refresh(player);
        }
        String key = "message.dmlcompanion." + result.name().toLowerCase(Locale.ROOT);
        var message = success ? Component.translatable(key, outcome.dragonName()) : Component.translatable(key);
        player.displayClientMessage(message, success);
    }

    private record ToggleRequest() {}
}
