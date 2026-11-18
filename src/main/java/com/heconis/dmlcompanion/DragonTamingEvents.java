package com.heconis.dmlcompanion;

import com.github.kay9.dragonmounts.dragon.TameableDragon;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DragonMountsCompanion.MOD_ID)
public final class DragonTamingEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof TameableDragon dragon) || dragon.isTame()) {
            return;
        }

        var server = player.server;
        // EntityInteract runs before DML attempts to tame the dragon. Queue the check after it returns.
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (dragon.isTamedFor(player) && IssuedDragonRings.get(server).recordIssue(dragon.getUUID())) {
                var ring = DragonMountsCompanion.DRAGON_RING.get().bind(dragon, player.getUUID());
                player.getInventory().placeItemBackInInventory(ring);
            }
        }));
    }
}
