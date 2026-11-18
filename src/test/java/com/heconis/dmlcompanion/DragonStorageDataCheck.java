package com.heconis.dmlcompanion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class DragonStorageDataCheck {
    public static void main(String[] args) throws IOException {
        Path folder = Files.createTempDirectory(Path.of("."), "dragon-storage-check-");
        Path file = folder.resolve("dragons.dat");
        Path writeBlocker = file.resolve("blocker");
        Path region = folder.resolve("r.-1.0.mca");
        try {
            var data = new DragonStorageData(file);
            var id = UUID.randomUUID();
            var owner = UUID.randomUUID();
            var previousRevision = UUID.randomUUID();
            var revision = UUID.randomUUID();
            require(data.allowsJoin(id, null, null), "Dragons from 0.1.0 can join");

            var snapshot = new CompoundTag();
            snapshot.putUUID("UUID", id);
            snapshot.putUUID("Owner", owner);
            snapshot.putString("id", "dragonmounts:dragon");
            snapshot.putString("Breed", "dragonmounts:fire");
            snapshot.putString("CustomName", "{\"text\":\"Ash\"}");
            snapshot.putFloat("Health", 12.5f);
            snapshot.putInt("Age", -18000);
            snapshot.putBoolean("Sitting", true);
            snapshot.putBoolean("Saddle", true);

            data.commit(id, new DragonStorageData.Active(previousRevision));
            data.commit(id, new DragonStorageData.Stored(owner, snapshot));
            data = new DragonStorageData(file);
            require(!data.allowsJoin(id, previousRevision, null), "The old entity cannot rejoin after storage");
            require(!data.allowsJoin(id, revision, null), "An uncommitted restoration cannot rejoin");
            var stored = (DragonStorageData.Stored) data.get(id);
            require(stored.owner().equals(owner), "The owner must survive reopening the journal");
            require(stored.snapshot().equals(snapshot), "The entire snapshot must survive reopening the journal");

            data.commit(id, new DragonStorageData.Active(revision));
            data = new DragonStorageData(file);
            require(data.allowsJoin(id, revision, null), "The committed revision can rejoin");
            require(!data.allowsJoin(id, null, null), "The original entity cannot rejoin after restoration");
            require(!data.allowsJoin(id, previousRevision, null), "Earlier restorations cannot rejoin");
            verifySavedRevision(folder, region, id, revision);

            data.commit(id, DragonStorageData.Dead.INSTANCE);
            data = new DragonStorageData(file);
            require(!data.allowsJoin(id, revision, null), "A dead dragon cannot rejoin");

            Files.delete(file);
            Files.createDirectory(file);
            Files.createFile(writeBlocker);
            try {
                data.commit(id, new DragonStorageData.Stored(owner, snapshot));
                throw new AssertionError("Replacing a nonempty directory must fail");
            } catch (IOException expected) {
                require(data.get(id) == DragonStorageData.Dead.INSTANCE,
                        "A failed write must leave the authoritative state intact");
            }
        } finally {
            if (Files.isDirectory(file)) {
                Files.deleteIfExists(writeBlocker);
            }
            Files.deleteIfExists(region);
            Files.deleteIfExists(file);
            Files.delete(folder);
        }
        System.out.println("Dragon storage journal checks passed");
    }

    private static void verifySavedRevision(Path folder, Path file, UUID id, UUID revision) throws IOException {
        var chunk = new ChunkPos(-1, 0);
        require(!DragonStorage.hasSavedRevision(folder, chunk, id, revision), "A missing region cannot commit");
        var dragon = new CompoundTag();
        dragon.putUUID("UUID", id);
        var data = new CompoundTag();
        data.putUUID(DragonStorage.REVISION, revision);
        dragon.put("ForgeData", data);
        var entities = new ListTag();
        entities.add(dragon);
        var chunkTag = new CompoundTag();
        chunkTag.put("Entities", entities);
        try (var region = new RegionFile(file, folder, true);
             var output = region.getChunkDataOutputStream(chunk)) {
            NbtIo.write(chunkTag, output);
        }
        require(DragonStorage.hasSavedRevision(folder, chunk, id, revision), "A matching disk snapshot can commit");
        require(!DragonStorage.hasSavedRevision(folder, chunk, id, UUID.randomUUID()),
                "An earlier disk snapshot cannot commit");
        require(!DragonStorage.hasSavedRevision(folder, chunk, UUID.randomUUID(), revision),
                "Another dragon cannot commit");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
