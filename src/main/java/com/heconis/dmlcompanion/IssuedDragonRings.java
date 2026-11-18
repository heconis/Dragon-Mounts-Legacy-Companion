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
    private final Set<UUID> issued = new HashSet<>();
    private final Map<UUID, String> names = new HashMap<>();

    static IssuedDragonRings get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(IssuedDragonRings::load, IssuedDragonRings::new, FILE_NAME);
    }

    private static IssuedDragonRings load(CompoundTag tag) {
        var data = new IssuedDragonRings();
        for (Tag dragonId : tag.getList(DRAGONS, Tag.TAG_INT_ARRAY)) {
            data.issued.add(NbtUtils.loadUUID(dragonId));
        }
        var names = tag.getCompound(NAMES);
        for (UUID dragonId : data.issued) {
            var name = names.getString(dragonId.toString());
            if (!name.isEmpty()) {
                data.names.put(dragonId, name);
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

    void rememberName(TameableDragon dragon) {
        if (contains(dragon.getUUID()) && dragon.getBreed() != null) {
            rememberName(dragon.getUUID(), Component.Serializer.toJson(dragon.getName()));
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
        return tag;
    }
}
