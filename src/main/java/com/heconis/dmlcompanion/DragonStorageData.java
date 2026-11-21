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

    private final Path recordDirectory;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Map<UUID, IOException> unreadable = new HashMap<>();
    private final Map<UUID, TameableDragon> restoring = new HashMap<>();

    static synchronized DragonStorageData get(MinecraftServer server) throws IOException {
        var data = WORLDS.get(server);
        if (data == null) {
            data = new DragonStorageData(server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME));
            data.reconcileIssuedRings(IssuedDragonRings.get(server));
            WORLDS.put(server, data);
        }
        return data;
    }

    DragonStorageData(Path legacyFile) throws IOException {
        recordDirectory = legacyFile.resolveSibling(legacyFile.getFileName() + ".d");
        readLegacy(legacyFile);
        // Individual records take precedence over the original, read-only snapshot.
        try (var files = Files.newDirectoryStream(recordDirectory, "*.dat")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                UUID id = parseId(name.substring(0, name.length() - 4));
                try (var input = Files.newInputStream(file)) {
                    readEntry(id, NbtIo.readCompressed(input));
                } catch (IOException | RuntimeException failure) {
                    markUnreadable(id, new IOException("Could not read dragon " + id + " from " + file, failure));
                }
            }
        } catch (NoSuchFileException absent) {
            // No individual records have been committed yet.
        }
    }

    private void readLegacy(Path file) throws IOException {
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
        for (String key : dragons.getAllKeys()) {
            readEntry(parseId(key), dragons.getCompound(key));
        }
    }

    private static UUID parseId(String key) throws IOException {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Invalid dragon record ID: " + key, malformed);
        }
    }

    private void readEntry(UUID id, CompoundTag tag) {
        try {
            Entry entry = switch (tag.getString("State")) {
                case "stored" -> {
                    if (!tag.contains("Snapshot", Tag.TAG_COMPOUND)) {
                        throw new IOException("Missing stored dragon snapshot: " + id);
                    }
                    float maxHealth = tag.contains("MaxHealth", Tag.TAG_ANY_NUMERIC)
                            ? tag.getFloat("MaxHealth") : Float.NaN;
                    UUID owner = requireUuid(tag, "Owner", id);
                    var snapshot = tag.getCompound("Snapshot");
                    if (!id.equals(requireUuid(snapshot, "UUID", id)) || !owner.equals(requireUuid(snapshot, "Owner", id))) {
                        throw new IOException("Snapshot UUID or Owner mismatch for dragon " + id);
                    }
                    yield new Stored(owner, snapshot, tag.getBoolean("Minion"), maxHealth);
                }
                case "active" -> new Active(requireUuid(tag, "Revision", id));
                case "dead" -> Dead.INSTANCE;
                default -> throw new IOException("Unknown stored dragon state: " + id);
            };
            entries.put(id, entry);
            unreadable.remove(id);
        } catch (IOException failure) {
            markUnreadable(id, failure);
        }
    }

    private static UUID requireUuid(CompoundTag tag, String field, UUID id) throws IOException {
        if (!tag.hasUUID(field)) {
            throw new IOException("Missing or invalid " + field + " for dragon " + id);
        }
        return tag.getUUID(field);
    }

    private void markUnreadable(UUID id, IOException failure) {
        entries.remove(id);
        unreadable.put(id, failure);
    }

    void reconcileIssuedRings(IssuedDragonRings rings) {
        // Every journal entry is evidence of prior issuance. Rebuild membership, never grant a second item.
        entries.keySet().forEach(rings::recordIssue);
    }

    Entry get(UUID dragonId) throws IOException {
        var failure = unreadable.get(dragonId);
        if (failure != null) {
            throw failure;
        }
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

    boolean allowsJoin(UUID id, UUID revision, TameableDragon candidate) throws IOException {
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
        get(dragonId); // Do not overwrite an unreadable record.
        Files.createDirectories(recordDirectory);
        var file = recordDirectory.resolve(dragonId + ".dat");
        var temporary = Files.createTempFile(recordDirectory, dragonId.toString(), ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                NbtIo.writeCompressed(encode(entry), output);
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
            if (stored.hasMaxHealth()) {
                tag.putFloat("MaxHealth", stored.maxHealth());
            }
        } else if (entry instanceof Active active) {
            tag.putString("State", "active");
            tag.putUUID("Revision", active.revision());
        } else {
            tag.putString("State", "dead");
        }
        return tag;
    }

    sealed interface Entry permits Stored, Active, Dead {}

    record Stored(UUID owner, CompoundTag snapshot, boolean minion, float maxHealth) implements Entry {
        boolean hasMaxHealth() {
            return Float.isFinite(maxHealth) && maxHealth > 0;
        }

        Stored recoverOneHealth(float limit) {
            float current = snapshot.getFloat("Health");
            if (!minion || !Float.isFinite(limit) || limit <= 0 || !Float.isFinite(current) || current <= 0) {
                return this;
            }
            float recovered = current < limit ? Math.min(current + 1, limit) : current;
            if (recovered == current) {
                return hasMaxHealth() ? this : new Stored(owner, snapshot, true, limit);
            }
            var updated = snapshot.copy();
            updated.putFloat("Health", recovered);
            return new Stored(owner, updated, true, limit);
        }

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
