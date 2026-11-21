package com.heconis.dmlcompanion;

import com.github.kay9.dragonmounts.dragon.TameableDragon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class IssuedDragonRings extends SavedData {
    private static final String FILE_NAME = DragonMountsCompanion.MOD_ID + "_issued_rings";
    private static final String DRAGONS = "IssuedDragons";
    private static final String NAMES = "DragonNames";
    private static final String HEALTH = "DragonHealth";
    private static final String FRIENDSHIP = "FriendshipTicks";
    private final Set<UUID> issued = new HashSet<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, DragonHealth> health = new HashMap<>();
    private final Map<UUID, Integer> friendship = new HashMap<>();

    static IssuedDragonRings get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(IssuedDragonRings::load, IssuedDragonRings::new, FILE_NAME);
    }

    static IssuedDragonRings load(CompoundTag tag) {
        var data = new IssuedDragonRings();
        for (Tag dragonId : tag.getList(DRAGONS, Tag.TAG_INT_ARRAY)) {
            data.issued.add(NbtUtils.loadUUID(dragonId));
        }
        var names = tag.getCompound(NAMES);
        var health = tag.getCompound(HEALTH);
        var friendship = tag.getCompound(FRIENDSHIP);
        for (UUID dragonId : data.issued) {
            var name = names.getString(dragonId.toString());
            if (!name.isEmpty()) {
                data.names.put(dragonId, name);
            }
            var lastHealth = DragonHealth.read(health.getCompound(dragonId.toString()));
            if (lastHealth != null) {
                data.health.put(dragonId, lastHealth);
            }
            int ticks = Math.min(friendship.getInt(dragonId.toString()), DragonFriendship.MAX_TICKS);
            if (ticks > 0) {
                data.friendship.put(dragonId, ticks);
            }
        }
        return data;
    }

    boolean recordIssue(UUID dragonId) {
        if (!issued.add(dragonId)) {
            return false;
        }
        setDirty();
        return true;
    }

    boolean contains(UUID dragonId) {
        return issued.contains(dragonId);
    }

    void observe(TameableDragon dragon) {
        if (contains(dragon.getUUID()) && dragon.getBreed() != null) {
            rememberName(dragon.getUUID(), Component.Serializer.toJson(dragon.getName()));
            var observation = DragonHealth.of(dragon);
            if (!observation.equals(health.put(dragon.getUUID(), observation))) {
                setDirty();
            }
        }
    }

    void rememberName(UUID dragonId, String name) {
        if (contains(dragonId) && !name.isEmpty() && !name.equals(names.get(dragonId))) {
            names.put(dragonId, name);
            setDirty();
        }
    }

    String nameOf(UUID dragonId) {
        return names.get(dragonId);
    }

    DragonHealth healthOf(UUID dragonId) {
        return health.get(dragonId);
    }

    int friendshipTicks(UUID dragonId) {
        return friendship.getOrDefault(dragonId, 0);
    }

    int advanceFriendship(UUID dragonId) {
        int ticks = friendshipTicks(dragonId);
        if (!contains(dragonId) || ticks >= DragonFriendship.MAX_TICKS) {
            return ticks;
        }
        friendship.put(dragonId, ++ticks);
        setDirty();
        return ticks;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        var dragonIds = new ListTag();
        for (UUID dragonId : issued) {
            dragonIds.add(NbtUtils.createUUID(dragonId));
        }
        tag.put(DRAGONS, dragonIds);
        var savedNames = new CompoundTag();
        names.forEach((id, name) -> savedNames.putString(id.toString(), name));
        tag.put(NAMES, savedNames);
        var savedHealth = new CompoundTag();
        health.forEach((id, observation) -> savedHealth.put(id.toString(), observation.save()));
        tag.put(HEALTH, savedHealth);
        var savedFriendship = new CompoundTag();
        friendship.forEach((id, ticks) -> savedFriendship.putInt(id.toString(), ticks));
        tag.put(FRIENDSHIP, savedFriendship);
        return tag;
    }
}
