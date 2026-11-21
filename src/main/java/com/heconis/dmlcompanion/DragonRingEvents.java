package com.heconis.dmlcompanion;

import com.github.kay9.dragonmounts.dragon.TameableDragon;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DragonMountsCompanion.MOD_ID)
public final class DragonRingEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof TameableDragon dragon)) {
            return;
        }

        boolean alreadyTamed = dragon.isTame();
        if (alreadyTamed && !player.getItemInHand(event.getHand()).is(Items.NAME_TAG)) {
            return;
        }
        var server = player.server;
        // DML applies taming or the name tag after EntityInteract returns.
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (event.isCanceled()) {
                return;
            }
            var records = IssuedDragonRings.get(server);
            if (alreadyTamed) {
                records.observe(dragon);
            } else if (dragon.isTamedFor(player) && records.recordIssue(dragon.getUUID())) {
                records.observe(dragon);
                var ring = DragonMountsCompanion.DRAGON_RING.get().bind(dragon, player.getUUID());
                player.getInventory().placeItemBackInInventory(ring);
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.55f, 1.15f);
            }
        }));
    }

    @SubscribeEvent
    public static void onDragonTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof TameableDragon dragon && dragon.tickCount % 20 == 0
                && dragon.isTame() && dragon.level() instanceof ServerLevel level) {
            IssuedDragonRings.get(level.getServer()).observe(dragon);
        }
    }
}
