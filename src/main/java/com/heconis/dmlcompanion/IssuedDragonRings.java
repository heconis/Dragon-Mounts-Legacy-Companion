package com.heconis.dmlcompanion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class IssuedDragonRings extends SavedData {
    private static final String FILE_NAME = DragonMountsCompanion.MOD_ID + "_issued_rings";
    private static final String DRAGONS = "IssuedDragons";
    private final Set<UUID> issued = new HashSet<>();

    static IssuedDragonRings get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(IssuedDragonRings::load, IssuedDragonRings::new, FILE_NAME);
    }

    private static IssuedDragonRings load(CompoundTag tag) {
        var data = new IssuedDragonRings();
        for (Tag dragonId : tag.getList(DRAGONS, Tag.TAG_INT_ARRAY)) {
            data.issued.add(NbtUtils.loadUUID(dragonId));
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

    @Override
    public CompoundTag save(CompoundTag tag) {
        var dragonIds = new ListTag();
        for (UUID dragonId : issued) {
            dragonIds.add(NbtUtils.createUUID(dragonId));
        }
        tag.put(DRAGONS, dragonIds);
        return tag;
    }
}
