package com.heconis.dmlcompanion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

public final class DragonFlight {
    static final int MIN_LEVEL = 5;
    private static final int BOOST_COOLDOWN = 3 * 20;
    private static final String BOOST_ROCKET = DragonMountsCompanion.MOD_ID + ":boost_rocket";
    private static final String SAVED_FLIGHT = DragonMountsCompanion.MOD_ID + ":flight";
    private static final Map<ServerPlayer, Flight> FLIGHTS = new IdentityHashMap<>();

    private DragonFlight() {}

    static boolean canFly(Player player) {
        return DragonGlide.canUseAirMovement(player) && !player.hasEffect(MobEffects.LEVITATION)
                && !player.getItemBySlot(EquipmentSlot.CHEST).canElytraFly(player);
    }

    static int durationTicks(int level) {
        if (level >= 10) {
            return MobEffectInstance.INFINITE_DURATION;
        }
        return (level >= 8 ? 60 : 30) * 20;
    }

    static void pressJump(ServerPlayer player, JumpAction action) {
        var minion = DragonMinions.activeMinion(player);
        if (minion == null || minion.friendshipLevel() < MIN_LEVEL || !canFly(player)
                || DragonMountsCompanion.DRAGON_RING.get().equippedDragon(player)
                        .filter(minion.dragonId()::equals).isEmpty()) {
            return;
        }
        int now = player.server.getTickCount();
        var flight = FLIGHTS.get(player);
        if (flight != null && !flight.dragonId.equals(minion.dragonId())) {
            return;
        }
        switch (action) {
            case CANCEL_RECOVERY -> {
                if (flight != null && flight.mode == Mode.RECOVERY_GLIDE) {
                    flight.mode = Mode.LOCKED;
                    DragonGlide.clear(player);
                    minion.setAirMode(DragonMinion.AirMode.NONE);
                    saveProgress(player, flight);
                }
            }
            case BOOST -> {
                if (flight != null && flight.mode == Mode.FLYING && player.isFallFlying()
                        && !flight.expired(now) && now - flight.nextBoostTick >= 0) {
                    boost(player, flight, now);
                }
            }
            case START -> {
                if (flight == null && !player.isFallFlying()) {
                    flight = new Flight(minion.dragonId(), now, durationTicks(minion.friendshipLevel()));
                    FLIGHTS.put(player, flight);
                    saveProgress(player, flight);
                    player.startFallFlying();
                    showEffect(player, flight);
                    minion.setAirMode(DragonMinion.AirMode.FLIGHT);
                }
            }
        }
    }

    private static void boost(ServerPlayer player, Flight flight, int now) {
        var firework = new ItemStack(Items.FIREWORK_ROCKET);
        FireworkRocketItem.setDuration(firework, (byte) 1);
        firework.getOrCreateTag().putBoolean(BOOST_ROCKET, true);
        var world = player.serverLevel();
        var rocket = new FireworkRocketEntity(world, firework, player) {
            @Override
            public boolean shouldBeSaved() {
                return false;
            }
        };
        rocket.setSilent(true);
        if (world.addFreshEntity(rocket)) {
            flight.boostRocket = rocket;
            flight.nextBoostTick = now + BOOST_COOLDOWN;
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.85f, 1.2f);
        }
    }

    public static boolean isBoostRocket(ItemStack firework) {
        var tag = firework.getTag();
        return tag != null && tag.getBoolean(BOOST_ROCKET);
    }

    public static boolean keepsFlying(ServerPlayer player) {
        var flight = FLIGHTS.get(player);
        return flight != null && flight.mode == Mode.FLYING && player.isFallFlying()
                && canFly(player) && !flight.expired(player.server.getTickCount());
    }

    static DragonMinion.AirMode tick(ServerPlayer player, DragonMinion minion) {
        var flight = FLIGHTS.get(player);
        if (flight == null) {
            flight = resume(player, minion);
        }
        if (flight == null) {
            clearEffect(player);
            return DragonMinion.AirMode.NONE;
        }
        if (player.onGround() || !player.isAlive()) {
            forget(player);
            return DragonMinion.AirMode.NONE;
        }
        if (!flight.dragonId.equals(minion.dragonId()) || minion.friendshipLevel() < MIN_LEVEL || !canFly(player)) {
            interrupt(player);
            return DragonMinion.AirMode.NONE;
        }
        if (flight.mode == Mode.FLYING) {
            if (flight.expired(player.server.getTickCount())) {
                stopNativeFlight(player, flight);
                flight.mode = Mode.RECOVERY_GLIDE;
            } else if (!player.isFallFlying()) {
                interrupt(player);
            } else if (!player.hasEffect(DragonMountsCompanion.FLIGHT_EFFECT.get())) {
                showEffect(player, flight);
            }
        }
        saveProgress(player, flight);
        return switch (flight.mode) {
            case FLYING -> DragonMinion.AirMode.FLIGHT;
            case RECOVERY_GLIDE -> DragonMinion.AirMode.RECOVERY_GLIDE;
            case LOCKED -> DragonMinion.AirMode.NONE;
        };
    }

    static void interrupt(ServerPlayer player) {
        var flight = FLIGHTS.get(player);
        if (flight == null) {
            return;
        }
        if (player.onGround() || !player.isAlive()) {
            forget(player);
        } else {
            stopNativeFlight(player, flight);
            flight.mode = Mode.LOCKED;
            saveProgress(player, flight);
        }
    }

    static void invalidate(ServerPlayer player) {
        if (FLIGHTS.containsKey(player)) {
            interrupt(player);
        } else {
            player.getPersistentData().remove(SAVED_FLIGHT);
            clearEffect(player);
        }
    }

    static void pause(ServerPlayer player) {
        var flight = FLIGHTS.remove(player);
        if (flight != null) {
            if (player.isAlive() && !player.onGround()) {
                saveProgress(player, flight);
            } else {
                player.getPersistentData().remove(SAVED_FLIGHT);
            }
            stopNativeFlight(player, flight);
        } else {
            clearEffect(player);
        }
    }

    static void forget(ServerPlayer player) {
        player.getPersistentData().remove(SAVED_FLIGHT);
        var flight = FLIGHTS.remove(player);
        if (flight != null) {
            stopNativeFlight(player, flight);
        } else {
            clearEffect(player);
        }
    }

    static void clearServer(MinecraftServer server) {
        FLIGHTS.keySet().removeIf(player -> player.server == server);
    }

    private static void saveProgress(ServerPlayer player, Flight flight) {
        var checkpoint = flight.checkpoint(player.server.getTickCount(), player.level().dimension().location().toString());
        player.getPersistentData().put(SAVED_FLIGHT, checkpoint.save());
    }

    private static Flight resume(ServerPlayer player, DragonMinion minion) {
        var data = player.getPersistentData();
        if (!data.contains(SAVED_FLIGHT, Tag.TAG_COMPOUND)) {
            return null;
        }
        var saved = FlightCheckpoint.read(data.getCompound(SAVED_FLIGHT));
        data.remove(SAVED_FLIGHT);
        if (saved == null || !saved.dragonId().equals(minion.dragonId()) || minion.friendshipLevel() < MIN_LEVEL
                || !saved.dimension().equals(player.level().dimension().location().toString()) || !canFly(player)) {
            return null;
        }
        var flight = saved.resumeAt(player.server.getTickCount(), durationTicks(minion.friendshipLevel()));
        FLIGHTS.put(player, flight);
        if (flight.mode == Mode.FLYING) {
            player.startFallFlying();
            showEffect(player, flight);
        } else {
            clearEffect(player);
        }
        return flight;
    }

    private static void stopNativeFlight(ServerPlayer player, Flight flight) {
        clearEffect(player);
        if (flight.boostRocket != null) {
            flight.boostRocket.discard();
            flight.boostRocket = null;
        }
        if (flight.mode == Mode.FLYING && player.isFallFlying()) {
            player.stopFallFlying();
        }
    }

    private static void showEffect(ServerPlayer player, Flight flight) {
        int remaining = flight.remainingAt(player.server.getTickCount());
        player.forceAddEffect(new MobEffectInstance(DragonMountsCompanion.FLIGHT_EFFECT.get(),
                remaining, 0, false, false, true), null);
    }

    private static void clearEffect(ServerPlayer player) {
        var effect = DragonMountsCompanion.FLIGHT_EFFECT.get();
        if (player.hasEffect(effect)) {
            player.removeEffect(effect);
        }
    }

    enum JumpAction { START, BOOST, CANCEL_RECOVERY }

    enum Mode { FLYING, RECOVERY_GLIDE, LOCKED }

    static final class Flight {
        private final UUID dragonId;
        private final int startedAt;
        private final int duration;
        private int nextBoostTick;
        private FireworkRocketEntity boostRocket;
        private Mode mode = Mode.FLYING;

        Flight(UUID dragonId, int startedAt, int duration) {
            this.dragonId = dragonId;
            this.startedAt = startedAt;
            this.duration = duration;
            nextBoostTick = startedAt;
        }

        private boolean expired(int now) {
            return duration >= 0 && now - startedAt >= duration;
        }

        FlightCheckpoint checkpoint(int now, String dimension) {
            int remaining = remainingAt(now);
            Mode savedMode = mode == Mode.FLYING && remaining == 0 ? Mode.RECOVERY_GLIDE : mode;
            return new FlightCheckpoint(dragonId, dimension, savedMode, remaining, Math.max(0, nextBoostTick - now));
        }

        private int remainingAt(int now) {
            return duration < 0 ? MobEffectInstance.INFINITE_DURATION : Math.max(0, duration - (now - startedAt));
        }
    }

    record FlightCheckpoint(UUID dragonId, String dimension, Mode mode, int remaining, int boostCooldown) {
        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putUUID("Dragon", dragonId);
            tag.putString("Dimension", dimension);
            tag.putString("Mode", mode.name());
            tag.putInt("Remaining", remaining);
            tag.putInt("BoostCooldown", boostCooldown);
            return tag;
        }

        static FlightCheckpoint read(CompoundTag tag) {
            if (!tag.hasUUID("Dragon") || !tag.contains("Remaining", Tag.TAG_INT) || tag.getString("Dimension").isEmpty()) {
                return null;
            }
            int remaining = tag.getInt("Remaining");
            if (remaining < -1 || remaining > durationTicks(9)) {
                return null;
            }
            try {
                var mode = Mode.valueOf(tag.getString("Mode"));
                return new FlightCheckpoint(tag.getUUID("Dragon"), tag.getString("Dimension"), mode, remaining,
                        Math.max(0, Math.min(BOOST_COOLDOWN, tag.getInt("BoostCooldown"))));
            } catch (IllegalArgumentException invalidMode) {
                return null;
            }
        }

        Flight resumeAt(int now, int levelLimit) {
            int duration = levelLimit < 0 ? remaining : remaining < 0 ? levelLimit : Math.min(remaining, levelLimit);
            var flight = new Flight(dragonId, now, duration);
            flight.mode = mode == Mode.FLYING && duration == 0 ? Mode.RECOVERY_GLIDE : mode;
            flight.nextBoostTick = now + boostCooldown;
            return flight;
        }
    }
}
