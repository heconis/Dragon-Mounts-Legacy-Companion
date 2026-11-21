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
import java.util.UUID;

@Mod.EventBusSubscriber(modid = DragonMountsCompanion.MOD_ID)
public final class DragonMinions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int RECOVERY_DELAY = 30 * 20;
    private static final int RECOVERY_INTERVAL = 10 * 20;
    private static final Map<ServerPlayer, DragonMinion> ACTIVE = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Recovery> RECOVERY = new IdentityHashMap<>();

    private DragonMinions() {}

    static boolean hasActiveMinion(ServerPlayer player) {
        return activeMinion(player) != null;
    }

    static DragonMinion activeMinion(ServerPlayer player) {
        var minion = ACTIVE.get(player);
        return minion != null && !minion.isRemoved() && minion.level() == player.level() ? minion : null;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            refresh(player, true);
        }
    }

    static void refresh(ServerPlayer player) {
        refresh(player, false);
    }

    private static void refresh(ServerPlayer player, boolean countTime) {
        DragonFlight.updateLandingLock(player.getPersistentData(), player.onGround() || !player.isAlive());
        if (!player.isAlive() || player.isSpectator()) {
            clear(player);
            return;
        }
        var selected = DragonMountsCompanion.DRAGON_RING.get().equippedDragon(player);
        if (selected.isEmpty()) {
            clear(player);
            return;
        }
        var id = selected.get();
        try {
            var data = DragonStorageData.get(player.server);
            var entry = data.get(id);
            if (entry == DragonStorageData.Dead.INSTANCE
                    || entry instanceof DragonStorageData.Stored stored && !stored.owner().equals(player.getUUID())) {
                clear(player);
                return;
            }
            var rings = IssuedDragonRings.get(player.server);
            int previousTicks = rings.friendshipTicks(id);
            int ticks = countTime ? rings.advanceFriendship(id) : previousTicks;
            int level = DragonFriendship.levelAt(ticks);
            if (ticks != previousTicks && ticks == DragonFriendship.ticksForLevel(level)) {
                DragonFriendship.notifyLevelUp(player, rings.nameOf(id), level);
            }
            if (!(entry instanceof DragonStorageData.Stored stored) || !stored.minion()) {
                clear(player);
                return;
            }
            DragonBlessings.update(player, level);
            recover(player, id, data, stored);
            var existing = ACTIVE.get(player);
            if (existing != null && !existing.isRemoved() && existing.level() == player.level()
                    && id.equals(existing.dragonId()) && existing.distanceToSqr(player) < 24 * 24) {
                existing.setFriendshipLevel(level);
                DragonGlide.tick(player, existing);
                return;
            }
            remove(player);

            String breedId = stored.snapshot().getString(TameableDragon.NBT_BREED);
            var breed = breedId.isEmpty() ? null : ResourceLocation.tryParse(breedId);
            if (breed == null) {
                DragonFlight.interrupt(player);
                return;
            }
            var world = player.serverLevel();
            var minion = DragonMountsCompanion.MINION.get().create(world);
            if (minion != null && minion.bind(player, id, breed, stored.dragonName())
                    && world.addFreshEntity(minion)) {
                ACTIVE.put(player, minion);
                minion.setFriendshipLevel(level);
                DragonGlide.tick(player, minion);
            } else {
                DragonFlight.interrupt(player);
            }
        } catch (IOException failure) {
            clear(player);
            if (player.tickCount % 200 == 0) {
                LOGGER.error("Could not read minion state for {}", id, failure);
            }
        }
    }

    private static void recover(ServerPlayer player, UUID id, DragonStorageData data, DragonStorageData.Stored stored) {
        int now = player.server.getTickCount();
        var recovery = RECOVERY.get(player);
        if (recovery == null || !id.equals(recovery.dragonId())) {
            RECOVERY.put(player, new Recovery(id, now + RECOVERY_DELAY));
            return;
        }
        if (now - recovery.nextTick() < 0) {
            return;
        }
        RECOVERY.put(player, new Recovery(id, now + RECOVERY_INTERVAL));
        try {
            DragonStorage.recoverMinion(player, id, data, stored);
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Could not save recovery for dragon {}", id, failure);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var selected = DragonMountsCompanion.DRAGON_RING.get().equippedDragon(player);
            var server = player.server;
            server.tell(new TickTask(server.getTickCount(), () -> {
                if (!event.isCanceled()) {
                    DragonFlight.forget(player);
                    clear(player);
                    selected.ifPresent(id -> DragonStorage.onPlayerDeath(player, id));
                }
            }));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DragonFlight.pause(player);
            clearMinion(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DragonFlight.forget(player);
            DragonGlide.clear(player);
            remove(player);
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void onServerStop(ServerStoppedEvent event) {
        DragonFlight.clearServer(event.getServer());
        DragonGlide.clearServer(event.getServer());
        RECOVERY.keySet().stream().filter(player -> player.server == event.getServer()).forEach(DragonBlessings::clear);
        ACTIVE.keySet().removeIf(player -> player.server == event.getServer());
        RECOVERY.keySet().removeIf(player -> player.server == event.getServer());
    }

    private static void clear(ServerPlayer player) {
        DragonFlight.invalidate(player);
        clearMinion(player);
    }

    private static void clearMinion(ServerPlayer player) {
        DragonGlide.clear(player);
        DragonBlessings.clear(player);
        RECOVERY.remove(player);
        remove(player);
    }

    private static void remove(ServerPlayer player) {
        var minion = ACTIVE.remove(player);
        if (minion != null) {
            minion.discard();
        }
    }

    private record Recovery(UUID dragonId, int nextTick) {}
}
