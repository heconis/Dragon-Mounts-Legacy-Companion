package com.heconis.dmlcompanion;

import com.github.kay9.dragonmounts.dragon.TameableDragon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = DragonMountsCompanion.MOD_ID)
public final class DragonRiding {
    private static final String SHARED_MOUNT = DragonMountsCompanion.MOD_ID + ":shared_mount";
    private static final String SAVED_RIDE = DragonMountsCompanion.MOD_ID + ":ride";
    private static final UUID FRIENDSHIP_FLIGHT_SPEED_ID = UUID.fromString("3b73cfd4-8488-4203-b4e9-b78b13255978");
    private static final int RESTORE_WAIT_TICKS = 15 * 20;
    private static final Map<ServerPlayer, PendingRide> PENDING = new IdentityHashMap<>();

    private DragonRiding() {}

    @SubscribeEvent
    public static void onDragonTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof TameableDragon dragon) || dragon.level().isClientSide) {
            return;
        }
        var speed = dragon.getAttribute(Attributes.FLYING_SPEED);
        if (speed == null) {
            return;
        }

        double bonus = 0;
        if (dragon.getControllingPassenger() instanceof ServerPlayer driver && dragon.isOwnedBy(driver)) {
            int ticks = IssuedDragonRings.get(driver.getServer()).friendshipTicks(dragon.getUUID());
            bonus = DragonFriendship.riddenFlightSpeedBonus(DragonFriendship.levelAt(ticks));
        }

        // DML uses this synced attribute for ridden flight and MOVEMENT_SPEED on the ground.
        var modifier = speed.getModifier(FRIENDSHIP_FLIGHT_SPEED_ID);
        if (modifier != null && modifier.getAmount() == bonus) {
            return;
        }
        if (modifier != null) {
            speed.removeModifier(modifier);
        }
        if (bonus > 0) {
            speed.addTransientModifier(new AttributeModifier(FRIENDSHIP_FLIGHT_SPEED_ID,
                    "DML Companion friendship flight speed", bonus, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        var player = event.getEntity();
        if (event.getTarget() instanceof TameableDragon dragon
                && player.getItemInHand(event.getHand()).isEmpty() && !player.isSecondaryUseActive()
                && !player.isPassenger() && canTakeSecondSeat(dragon, player)
                && (player.level().isClientSide || player.startRiding(dragon))) {
            event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
            event.setCanceled(true);
        }
    }

    public static boolean canTakeSecondSeat(TameableDragon dragon, Entity passenger) {
        if (!(passenger instanceof Player player) || !player.isAlive() || player.isSpectator() || !isRideable(dragon)
                || dragon.getPassengers().size() != 1 || !(dragon.getFirstPassenger() instanceof Player first)) {
            return false;
        }
        return dragon.isTamedFor(player) != dragon.isTamedFor(first);
    }

    private static boolean isRideable(TameableDragon dragon) {
        return dragon.isAlive() && dragon.isTame() && dragon.isSaddled() && !dragon.isHatchling()
                && !dragon.isOrderedToSit();
    }

    public static void markSharedMount(TameableDragon dragon) {
        if (!dragon.level().isClientSide) {
            dragon.getPersistentData().putBoolean(SHARED_MOUNT, true);
        }
    }

    public static boolean isSharedMount(TameableDragon dragon) {
        return dragon.getPersistentData().getBoolean(SHARED_MOUNT);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var data = player.getPersistentData();
            if (data.contains(SAVED_RIDE, Tag.TAG_COMPOUND)) {
                var link = RideLink.read(data.getCompound(SAVED_RIDE));
                if (link == null) {
                    data.remove(SAVED_RIDE);
                } else {
                    PENDING.put(player, new PendingRide(link, player.server.getTickCount()));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        var pending = PENDING.get(player);
        if (pending == null) {
            return;
        }
        var link = pending.link();
        var level = player.serverLevel();
        if (!player.isAlive() || player.isSpectator() || player.isPassenger()
                || !link.dimension().equals(level.dimension().location().toString())
                || player.server.getTickCount() - pending.startedAt() >= RESTORE_WAIT_TICKS) {
            clearPending(player);
            return;
        }
        if (level.getEntity(link.dragonId()) instanceof TameableDragon dragon) {
            if (!link.ownerId().equals(dragon.getOwnerUUID()) || !isRideable(dragon) || !isSharedMount(dragon)) {
                clearPending(player);
            } else if ((dragon.getPassengers().isEmpty() || canTakeSecondSeat(dragon, player))
                    && player.startRiding(dragon)) {
                clearPending(player);
            }
        }
    }

    private static void clearPending(ServerPlayer player) {
        PENDING.remove(player);
        player.getPersistentData().remove(SAVED_RIDE);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            saveRide(player);
            PENDING.remove(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        event.getServer().getPlayerList().getPlayers().forEach(DragonRiding::saveRide);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.keySet().removeIf(player -> player.server == event.getServer());
    }

    private static void saveRide(ServerPlayer player) {
        var data = player.getPersistentData();
        if (player.isAlive() && player.getVehicle() instanceof TameableDragon dragon && isSharedMount(dragon)
                && dragon.getOwnerUUID() != null && isRideable(dragon)) {
            var link = new RideLink(dragon.getUUID(), dragon.getOwnerUUID(), dragon.level().dimension().location().toString());
            data.put(SAVED_RIDE, link.save());
        } else if (!PENDING.containsKey(player)) {
            data.remove(SAVED_RIDE);
        }
    }

    private record PendingRide(RideLink link, int startedAt) {}

    record RideLink(UUID dragonId, UUID ownerId, String dimension) {
        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putUUID("Dragon", dragonId);
            tag.putUUID("Owner", ownerId);
            tag.putString("Dimension", dimension);
            return tag;
        }

        static RideLink read(CompoundTag tag) {
            if (!tag.hasUUID("Dragon") || !tag.hasUUID("Owner") || tag.getString("Dimension").isEmpty()) {
                return null;
            }
            return new RideLink(tag.getUUID("Dragon"), tag.getUUID("Owner"), tag.getString("Dimension"));
        }
    }
}
