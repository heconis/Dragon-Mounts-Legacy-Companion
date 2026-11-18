package com.heconis.dmlcompanion;

import com.github.kay9.dragonmounts.dragon.TameableDragon;
import com.google.gson.JsonParseException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

final class DragonStorageData {
    private static final Map<MinecraftServer, DragonStorageData> WORLDS = new WeakHashMap<>();
    private static final String FILE_NAME = DragonMountsCompanion.MOD_ID + "_dragons.dat";

    private final Path file;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Map<UUID, TameableDragon> restoring = new HashMap<>();

    static synchronized DragonStorageData get(MinecraftServer server) throws IOException {
        var data = WORLDS.get(server);
        if (data == null) {
            data = new DragonStorageData(server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME));
            WORLDS.put(server, data);
        }
        return data;
    }

    DragonStorageData(Path file) throws IOException {
        this.file = file;
        CompoundTag root;
        try (var input = Files.newInputStream(file)) {
            root = NbtIo.readCompressed(input);
        } catch (NoSuchFileException absent) {
            return;
        } catch (RuntimeException malformed) {
            throw new IOException("Invalid dragon storage file " + file, malformed);
        }
        if (root == null || root.getInt("Format") != 1 || !root.contains("Dragons", Tag.TAG_COMPOUND)) {
            throw new IOException("Unknown dragon storage format in " + file);
        }
        var dragons = root.getCompound("Dragons");
        try {
            for (String id : dragons.getAllKeys()) {
                var tag = dragons.getCompound(id);
                Entry entry = switch (tag.getString("State")) {
                    case "stored" -> {
                        if (!tag.contains("Snapshot", Tag.TAG_COMPOUND)) {
                            throw new IOException("Missing stored dragon snapshot: " + id);
                        }
                        yield new Stored(tag.getUUID("Owner"), tag.getCompound("Snapshot"), tag.getBoolean("Minion"));
                    }
                    case "active" -> new Active(tag.getUUID("Revision"));
                    case "dead" -> Dead.INSTANCE;
                    default -> throw new IOException("Unknown stored dragon state: " + id);
                };
                entries.put(UUID.fromString(id), entry);
            }
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Invalid dragon storage entry in " + file, malformed);
        }
    }

    Entry get(UUID dragonId) {
        return entries.get(dragonId);
    }

    boolean isRestoring(UUID dragonId) {
        return restoring.containsKey(dragonId);
    }

    void beginRestore(TameableDragon dragon) {
        restoring.put(dragon.getUUID(), dragon);
    }

    void endRestore(TameableDragon dragon) {
        restoring.remove(dragon.getUUID(), dragon);
    }

    boolean allowsJoin(UUID id, UUID revision, TameableDragon candidate) {
        Entry entry = get(id);
        if (entry instanceof Stored) {
            // An old chunk save can still contain a pre-storage entity.
            return candidate != null && restoring.get(id) == candidate;
        }
        if (entry instanceof Active active) {
            return active.revision().equals(revision);
        }
        return entry == null && revision == null;
    }

    void commit(UUID dragonId, Entry entry) throws IOException {
        var dragons = new CompoundTag();
        entries.forEach((id, previous) -> dragons.put(id.toString(), encode(previous)));
        dragons.put(dragonId.toString(), encode(entry));
        var root = new CompoundTag();
        root.putInt("Format", 1);
        root.put("Dragons", dragons);

        Files.createDirectories(file.getParent());
        var temporary = Files.createTempFile(file.getParent(), FILE_NAME, ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                NbtIo.writeCompressed(root, output);
            }
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        entries.put(dragonId, entry);
    }

    private static CompoundTag encode(Entry entry) {
        var tag = new CompoundTag();
        if (entry instanceof Stored stored) {
            tag.putString("State", "stored");
            tag.putUUID("Owner", stored.owner());
            tag.put("Snapshot", stored.snapshot());
            tag.putBoolean("Minion", stored.minion());
        } else if (entry instanceof Active active) {
            tag.putString("State", "active");
            tag.putUUID("Revision", active.revision());
        } else {
            tag.putString("State", "dead");
        }
        return tag;
    }

    sealed interface Entry permits Stored, Active, Dead {}

    record Stored(UUID owner, CompoundTag snapshot, boolean minion) implements Entry {
        Component dragonName() {
            String customName = snapshot.getString("CustomName");
            if (!customName.isEmpty()) {
                try {
                    var name = Component.Serializer.fromJson(customName);
                    if (name != null) {
                        return name;
                    }
                } catch (JsonParseException ignored) {
                    // Fall back to the breed name if the custom name cannot be read.
                }
            }
            String breedId = snapshot.getString(TameableDragon.NBT_BREED);
            // DML maps namespace:path to dragon_breed.namespace.path.
            return breedId.isEmpty() ? null : Component.translatable("dragon_breed." + breedId.replace(':', '.'));
        }
    }

    record Active(UUID revision) implements Entry {}

    enum Dead implements Entry { INSTANCE }
}
