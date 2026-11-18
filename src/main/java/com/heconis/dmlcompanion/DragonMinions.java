package com.heconis.dmlcompanion;

import com.github.kay9.dragonmounts.dragon.TameableDragon;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = DragonMountsCompanion.MOD_ID)
public final class DragonMinions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ServerPlayer, DragonMinion> ACTIVE = new IdentityHashMap<>();

    private DragonMinions() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && (ACTIVE.containsKey(player) || player.tickCount % 5 == 0)) {
            refresh(player);
        }
    }

    static void refresh(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) {
            remove(player);
            return;
        }
        var selected = DragonMountsCompanion.DRAGON_RING.get().equippedDragon(player);
        if (selected.isEmpty()) {
            remove(player);
            return;
        }
        var id = selected.get();
        try {
            var entry = DragonStorageData.get(player.server).get(id);
            if (!(entry instanceof DragonStorageData.Stored stored) || !stored.minion()
                    || !stored.owner().equals(player.getUUID())) {
                remove(player);
                return;
            }
            var existing = ACTIVE.get(player);
            if (existing != null && !existing.isRemoved() && existing.level() == player.level()
                    && id.equals(existing.dragonId()) && existing.distanceToSqr(player) < 24 * 24) {
                return;
            }
            remove(player);

            String breedId = stored.snapshot().getString(TameableDragon.NBT_BREED);
            if (breedId.isEmpty()) {
                return;
            }
            var breed = ResourceLocation.tryParse(breedId);
            var level = player.serverLevel();
            var minion = DragonMountsCompanion.MINION.get().create(level);
            if (breed != null && minion != null && minion.bind(player, id, breed) && level.addFreshEntity(minion)) {
                ACTIVE.put(player, minion);
            }
        } catch (IOException failure) {
            remove(player);
            if (player.tickCount % 200 == 0) {
                LOGGER.error("Could not read minion state for {}", id, failure);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var selected = DragonMountsCompanion.DRAGON_RING.get().equippedDragon(player);
            var server = player.server;
            server.tell(new TickTask(server.getTickCount(), () -> {
                if (!event.isCanceled()) {
                    remove(player);
                    selected.ifPresent(id -> DragonStorage.onPlayerDeath(player, id));
                }
            }));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            remove(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            remove(player);
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void onServerStop(ServerStoppedEvent event) {
        ACTIVE.keySet().removeIf(player -> player.server == event.getServer());
    }

    private static void remove(ServerPlayer player) {
        var minion = ACTIVE.remove(player);
        if (minion != null) {
            minion.discard();
        }
    }
}
