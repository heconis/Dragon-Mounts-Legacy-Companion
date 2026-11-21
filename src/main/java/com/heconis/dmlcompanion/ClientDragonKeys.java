package com.heconis.dmlcompanion;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = DragonMountsCompanion.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientDragonKeys {
    private static final KeyMapping TOGGLE = new KeyMapping(
            "key.dmlcompanion.toggle_dragon", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.dmlcompanion");

    private ClientDragonKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
    }

    @Mod.EventBusSubscriber(modid = DragonMountsCompanion.MOD_ID, value = Dist.CLIENT)
    public static final class Input {
        private static final long GLIDE_HOLD_MILLIS = 200;
        private static LocalPlayer inputPlayer;
        private static boolean jumpHeld;
        private static long jumpPressedAt = -1;
        private static boolean shortFlightCandidate;
        private static boolean suppressGlideUntilRelease;
        private static int lastGlideInput;

        private Input() {}

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            var game = Minecraft.getInstance();
            var player = game.player;
            if (player != inputPlayer) {
                inputPlayer = player;
                jumpHeld = false;
                resetJump();
            }
            if (player == null || game.getConnection() == null) {
                return;
            }
            var minion = DragonPlayerLayer.minionFor(player);
            if (game.screen != null || !game.options.keyJump.isDown()) {
                resetJump();
            }
            boolean held = jumpPressedAt >= 0 && Util.getMillis() - jumpPressedAt >= GLIDE_HOLD_MILLIS
                    && !suppressGlideUntilRelease && minion != null && DragonGlide.canGlide(player);
            if (held != jumpHeld || held && player.tickCount - lastGlideInput >= DragonGlide.INPUT_INTERVAL) {
                DragonNetwork.sendGlideInput(held);
                jumpHeld = held;
                lastGlideInput = player.tickCount;
            }
            if (!suppressGlideUntilRelease && minion != null && minion.isGliding() && (held || minion.isRecoveryGlide())
                    && DragonGlide.canGlide(player) && player.getDeltaMovement().y <= 0) {
                DragonGlide.slowFall(player, minion.friendshipLevel());
            }
        }

        @SubscribeEvent
        public static void onKey(InputEvent.Key event) {
            handleKey(InputConstants.getKey(event.getKey(), event.getScanCode()), event.getAction());
        }

        @SubscribeEvent
        public static void onMouse(InputEvent.MouseButton.Post event) {
            handleKey(InputConstants.Type.MOUSE.getOrCreate(event.getButton()), event.getAction());
        }

        private static void handleKey(InputConstants.Key key, int action) {
            var game = Minecraft.getInstance();
            var player = game.player;
            if (game.screen != null || player == null || game.getConnection() == null) {
                return;
            }
            if (action == GLFW.GLFW_PRESS && TOGGLE.isActiveAndMatches(key)) {
                DragonNetwork.sendToggle();
            }
            if (player != inputPlayer || !game.options.keyJump.isActiveAndMatches(key)) {
                return;
            }

            if (action == GLFW.GLFW_PRESS) {
                resetJump();
                jumpPressedAt = Util.getMillis();
                var minion = DragonPlayerLayer.minionFor(player);
                if (minion != null && minion.friendshipLevel() >= DragonFlight.MIN_LEVEL
                        && DragonFlight.canFly(player)) {
                    if (minion.isFlyingWithOwner() || minion.isRecoveryGlide()) {
                        suppressGlideUntilRelease = true;
                        DragonNetwork.sendFlightJump(minion.isRecoveryGlide() ? DragonFlight.JumpAction.CANCEL_RECOVERY
                                : DragonFlight.JumpAction.BOOST);
                    } else {
                        shortFlightCandidate = !player.isFallFlying();
                    }
                }
            } else if (action == GLFW.GLFW_RELEASE) {
                if (jumpPressedAt >= 0 && shortFlightCandidate && Util.getMillis() - jumpPressedAt < GLIDE_HOLD_MILLIS
                        && DragonFlight.canFly(player) && !player.isFallFlying()) {
                    DragonNetwork.sendFlightJump(DragonFlight.JumpAction.START);
                }
                resetJump();
            }
        }

        private static void resetJump() {
            jumpPressedAt = -1;
            shortFlightCandidate = false;
            suppressGlideUntilRelease = false;
        }
    }
}
