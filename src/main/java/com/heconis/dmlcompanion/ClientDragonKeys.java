package com.heconis.dmlcompanion;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
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
        private Input() {}

        @SubscribeEvent
        public static void onKey(InputEvent.Key event) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                sendIfBound(InputConstants.getKey(event.getKey(), event.getScanCode()));
            }
        }

        @SubscribeEvent
        public static void onMouse(InputEvent.MouseButton.Post event) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                sendIfBound(InputConstants.Type.MOUSE.getOrCreate(event.getButton()));
            }
        }

        private static void sendIfBound(InputConstants.Key key) {
            var game = Minecraft.getInstance();
            if (game.screen == null && game.player != null && game.getConnection() != null
                    && TOGGLE.isActiveAndMatches(key)) {
                DragonNetwork.sendToggle();
            }
        }
    }
}
