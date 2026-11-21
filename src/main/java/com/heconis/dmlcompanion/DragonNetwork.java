package com.heconis.dmlcompanion;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Locale;

final class DragonNetwork {
    private static final String PROTOCOL = "7";
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
        CHANNEL.messageBuilder(GlideInput.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((input, buffer) -> buffer.writeBoolean(input.held()))
                .decoder(buffer -> new GlideInput(buffer.readBoolean()))
                .consumerMainThread((input, context) -> {
                    var player = context.get().getSender();
                    if (player != null) {
                        DragonGlide.setHeld(player, input.held());
                    }
                })
                .add();
        CHANNEL.messageBuilder(FlightJump.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder((input, buffer) -> buffer.writeEnum(input.action()))
                .decoder(buffer -> new FlightJump(buffer.readEnum(DragonFlight.JumpAction.class)))
                .consumerMainThread((input, context) -> {
                    var player = context.get().getSender();
                    if (player != null) {
                        DragonFlight.pressJump(player, input.action());
                    }
                })
                .add();
    }

    static void sendToggle() {
        CHANNEL.sendToServer(new ToggleRequest());
    }

    static void sendGlideInput(boolean held) {
        CHANNEL.sendToServer(new GlideInput(held));
    }

    static void sendFlightJump(DragonFlight.JumpAction action) {
        CHANNEL.sendToServer(new FlightJump(action));
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
            playToggleEffects(outcome);
        }
        String key = "message.dmlcompanion." + result.name().toLowerCase(Locale.ROOT);
        var message = success ? Component.translatable(key, outcome.dragonName()) : Component.translatable(key);
        player.displayClientMessage(message, success);
    }

    private static void playToggleEffects(DragonStorage.Outcome outcome) {
        var level = outcome.level();
        var origin = outcome.position();
        boolean summoned = outcome.result() == DragonStorage.Result.RESTORED;
        level.sendParticles(summoned ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL,
                origin.x, origin.y + 0.5, origin.z, summoned ? 20 : 16, 0.55, 0.38, 0.55, 0.055);
        level.playSound(null, origin.x, origin.y, origin.z,
                summoned ? SoundEvents.ENDER_DRAGON_FLAP : SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, summoned ? 0.8f : 0.65f, summoned ? 1.1f : 1.35f);
    }

    private record ToggleRequest() {}
    private record GlideInput(boolean held) {}
    private record FlightJump(DragonFlight.JumpAction action) {}
}
