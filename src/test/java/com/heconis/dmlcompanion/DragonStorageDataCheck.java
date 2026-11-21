package com.heconis.dmlcompanion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class DragonStorageDataCheck {
    public static void main(String[] args) throws IOException {
        verifyFriendship();
        verifyFlightResume();
        verifySharedRide();
        Path folder = Files.createTempDirectory(Path.of("."), "dragon-storage-check-");
        Path file = folder.resolve("dragons.dat");
        Path records = folder.resolve("dragons.dat.d");
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
            data.commit(id, new DragonStorageData.Stored(owner, snapshot, true, 14));
            data = new DragonStorageData(file);
            require(!data.allowsJoin(id, previousRevision, null), "The old entity cannot rejoin after storage");
            require(!data.allowsJoin(id, revision, null), "An uncommitted restoration cannot rejoin");
            var stored = (DragonStorageData.Stored) data.get(id);
            require(stored.owner().equals(owner), "The owner must survive reopening the journal");
            require(stored.snapshot().equals(snapshot), "The entire snapshot must survive reopening the journal");
            require(stored.minion(), "The minion preference must survive reopening the journal");
            verifyDragonNames(stored);
            var rings = new IssuedDragonRings();
            data.reconcileIssuedRings(rings);
            require(rings.contains(id) && rings.advanceFriendship(id) == 1, "A durable record must repair lost issuance");
            data.reconcileIssuedRings(rings);
            require(rings.friendshipTicks(id) == 1 && !rings.recordIssue(id), "Repair must retain progress and prevent reissue");
            verifyRecovery(data, file, id, stored);
            verifyLegacyStorage(folder.resolve("legacy.dat"), id, owner, snapshot);
            verifyIndependentRecords(file, records, id, owner, snapshot);
            data.commit(id, new DragonStorageData.Active(revision));
            data = new DragonStorageData(file);
            require(data.allowsJoin(id, revision, null), "The committed revision can rejoin");
            require(!data.allowsJoin(id, null, null), "The original entity cannot rejoin after restoration");
            require(!data.allowsJoin(id, previousRevision, null), "Earlier restorations cannot rejoin");
            verifySavedRevision(folder, region, id, revision);

            data.commit(id, DragonStorageData.Dead.INSTANCE);
            data = new DragonStorageData(file);
            require(!data.allowsJoin(id, revision, null), "A dead dragon cannot rejoin");

            var recordFile = records.resolve(id + ".dat");
            Files.delete(recordFile);
            Files.createDirectory(recordFile);
            Files.createFile(recordFile.resolve("blocker"));
            try {
                data.commit(id, new DragonStorageData.Stored(owner, snapshot, true, 14));
                throw new AssertionError("Replacing a nonempty directory must fail");
            } catch (IOException expected) {
                require(data.get(id) == DragonStorageData.Dead.INSTANCE,
                        "A failed write must leave the authoritative state intact");
            }
        } finally {
            try (var paths = Files.walk(folder)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        System.out.println("Dragon storage journal checks passed");
    }

    private static void verifySharedRide() {
        var ride = new DragonRiding.RideLink(UUID.randomUUID(), UUID.randomUUID(), "minecraft:the_nether");
        var saved = ride.save();
        require(ride.equals(DragonRiding.RideLink.read(saved)),
                "A rider's save must retain the same dragon, owner and dimension");
        saved.remove("Owner");
        require(DragonRiding.RideLink.read(saved) == null, "A ride without its original owner must not resume");
    }

    private static void verifyFlightResume() {
        var dragon = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        var flight = new DragonFlight.Flight(dragon, 100, DragonFlight.durationTicks(5));
        var saved = DragonFlight.FlightCheckpoint.read(flight.checkpoint(340, dimension).save());
        require(saved != null && saved.dragonId().equals(dragon) && saved.dimension().equals(dimension),
                "The resume record must retain the dragon and dimension");
        require(saved.mode() == DragonFlight.Mode.FLYING && saved.remaining() == 360,
                "The flight must save the time remaining at logout");
        var resumed = saved.resumeAt(10_000, DragonFlight.durationTicks(5));
        require(resumed.checkpoint(10_000, dimension).remaining() == 360, "Offline time must not count");
        require(resumed.checkpoint(10_360, dimension).mode() == DragonFlight.Mode.RECOVERY_GLIDE,
                "The original flight must still expire after resuming");

        var unlimited = new DragonFlight.FlightCheckpoint(dragon, dimension, DragonFlight.Mode.FLYING, -1, 30);
        var loaded = DragonFlight.FlightCheckpoint.read(unlimited.save());
        require(loaded != null && loaded.resumeAt(1_000, -1).checkpoint(1_010, dimension).boostCooldown() == 20,
                "A boost cooldown must pause offline and resume on login");
        require(loaded.resumeAt(1_000, -1).checkpoint(20_000, dimension).remaining() == -1,
                "Unlimited flight must remain unlimited after resuming");

        var playerData = new CompoundTag();
        String key = DragonMountsCompanion.MOD_ID + ":flight";
        playerData.put(key, saved.save());
        DragonFlight.updateLandingLock(playerData, false);
        playerData.remove(key); // Login with the ring removed invalidates resumption.
        var afterLogin = playerData.copy();
        DragonFlight.updateLandingLock(afterLogin, false);
        require(!DragonFlight.canStartNewFlight(afterLogin), "Re-equipping in midair must keep the landing lock");
        DragonFlight.updateLandingLock(afterLogin, true);
        require(DragonFlight.canStartNewFlight(afterLogin), "Landing must unlock a fresh flight");
    }

    private static void verifyFriendship() {
        int[] minutes = {0, 20, 60, 120, 200, 300, 420, 560, 720, 900};
        for (int level = 1; level <= minutes.length; level++) {
            int ticks = minutes[level - 1] * 60 * 20;
            require(DragonFriendship.ticksForLevel(level) == ticks, "Cumulative time must match level " + level);
            require(DragonFriendship.levelAt(ticks) == level, "The new level must begin at its threshold");
            if (level > 1) {
                require(DragonFriendship.levelAt(ticks - 1) == level - 1, "The previous level ends at the threshold");
                require(DragonFriendship.progressPercent(ticks - 1) == 99, "Progress must not round up early");
            }
            if (level < DragonFriendship.MAX_LEVEL) {
                require(DragonFriendship.progressPercent(ticks) == 0, "Progress must reset at the start of a level");
            }
        }
        require(DragonFriendship.progressPercent(12_000) == 50, "Progress must reflect the current level interval");
        require(DragonFriendship.progressPercent(DragonFriendship.MAX_TICKS) == 100, "The final level is complete");
        require(DragonFriendship.riddenFlightSpeedBonus(1) == 0, "Lv1 must keep the original riding speed");
        require(Math.abs(DragonFriendship.riddenFlightSpeedBonus(5) - 0.4444444444) < 1e-9,
                "Intermediate levels must increase riding speed gradually");
        require(DragonFriendship.riddenFlightSpeedBonus(10) == 1, "Lv10 must double the riding speed");
        require(DragonFlight.MIN_LEVEL == 5, "Flight must unlock at Lv5");
        require(DragonFlight.durationTicks(5) == 600 && DragonFlight.durationTicks(7) == 600,
                "Lv5 to Lv7 have thirty seconds of flight");
        require(DragonFlight.durationTicks(8) == 1200 && DragonFlight.durationTicks(9) == 1200,
                "Lv8 and Lv9 have sixty seconds of flight");
        require(DragonFlight.durationTicks(10) == -1, "Lv10 flight has no duration limit");

        var rings = new IssuedDragonRings();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        rings.recordIssue(first);
        rings.recordIssue(second);
        var legacy = rings.save(new CompoundTag());
        legacy.remove("FriendshipTicks");
        rings = IssuedDragonRings.load(legacy);
        require(rings.friendshipTicks(first) == 0, "Old rings must start at Lv1");
        rings.advanceFriendship(first);
        var persisted = rings.save(new CompoundTag());
        var reloaded = IssuedDragonRings.load(persisted);
        require(reloaded.friendshipTicks(first) == 1, "Friendship time must survive saving and reloading");
        require(reloaded.friendshipTicks(second) == 0, "A second dragon must have independent progress");
        require(reloaded.advanceFriendship(UUID.randomUUID()) == 0, "Unissued rings must not gain friendship");

        persisted.getCompound("FriendshipTicks").putInt(first.toString(), DragonFriendship.MAX_TICKS);
        reloaded = IssuedDragonRings.load(persisted);
        require(reloaded.advanceFriendship(first) == DragonFriendship.MAX_TICKS, "Lv10 must stop accumulating time");
    }

    private static void verifyDragonNames(DragonStorageData.Stored stored) {
        require("Ash".equals(stored.dragonName().getString()), "A stored name tag takes priority over the breed");

        var snapshot = stored.snapshot().copy();
        var unnamed = new DragonStorageData.Stored(stored.owner(), snapshot, true, stored.maxHealth());
        snapshot.remove("CustomName");
        require(unnamed.dragonName().getContents() instanceof TranslatableContents translated
                        && translated.getKey().equals("dragon_breed.dragonmounts.fire"),
                "Unnamed dragons must use the breed translation key");

        snapshot.putString("CustomName", "{");
        require(unnamed.dragonName().getContents() instanceof TranslatableContents translated
                        && translated.getKey().equals("dragon_breed.dragonmounts.fire"),
                "A malformed custom name must fall back to the breed");
    }

    private static void verifyRecovery(DragonStorageData data, Path file, UUID id,
                                       DragonStorageData.Stored stored) throws IOException {
        require(stored.hasMaxHealth() && stored.maxHealth() == 14, "Maximum health must survive reopening");
        var recovered = stored.recoverOneHealth(stored.maxHealth());
        require(recovered.snapshot().getFloat("Health") == 13.5f, "One interval must heal exactly one HP");
        require(stored.snapshot().getFloat("Health") == 12.5f, "Recovery must not mutate the authoritative snapshot");
        data.commit(id, recovered);
        var reopened = (DragonStorageData.Stored) new DragonStorageData(file).get(id);
        require(reopened.snapshot().equals(recovered.snapshot()), "Recovered health must survive reopening");
        var full = reopened.recoverOneHealth(reopened.maxHealth());
        require(full.snapshot().getFloat("Health") == 14, "Recovery must stop at maximum health");
        require(full.recoverOneHealth(full.maxHealth()) == full, "A full dragon needs no write");

        var legacy = new DragonStorageData.Stored(stored.owner(), stored.snapshot(), true, Float.NaN);
        require(legacy.recoverOneHealth(14).maxHealth() == 14, "A legacy snapshot can retain its resolved maximum");
        var inactive = new DragonStorageData.Stored(stored.owner(), stored.snapshot(), false, 14);
        require(inactive.recoverOneHealth(14) == inactive, "Stored dragons outside minion mode must not recover");
        var zeroHealth = stored.snapshot().copy();
        zeroHealth.putFloat("Health", 0);
        var invalid = new DragonStorageData.Stored(stored.owner(), zeroHealth, true, 14);
        require(invalid.recoverOneHealth(14) == invalid, "A zero-health snapshot must not be revived");
    }

    private static void verifyLegacyStorage(Path file, UUID id, UUID owner, CompoundTag snapshot) throws IOException {
        var legacyEntry = new CompoundTag();
        legacyEntry.putString("State", "stored");
        legacyEntry.putUUID("Owner", owner);
        legacyEntry.put("Snapshot", snapshot);
        var dragons = new CompoundTag();
        dragons.put(id.toString(), legacyEntry);
        var root = new CompoundTag();
        root.putInt("Format", 1);
        root.put("Dragons", dragons);
        NbtIo.writeCompressed(root, file.toFile());
        var restored = (DragonStorageData.Stored) new DragonStorageData(file).get(id);
        require(!restored.minion() && !restored.hasMaxHealth() && restored.snapshot().equals(snapshot),
                "Storage from 0.2.0 must load without activating a minion");
        new DragonStorageData(file).commit(id, DragonStorageData.Dead.INSTANCE);
        require(new DragonStorageData(file).get(id) == DragonStorageData.Dead.INSTANCE,
                "An individual commit must supersede the legacy snapshot after restart");
    }

    private static void verifyIndependentRecords(Path file, Path records, UUID id, UUID owner, CompoundTag snapshot)
            throws IOException {
        var data = new DragonStorageData(file);
        var other = UUID.randomUUID();
        data.commit(other, new DragonStorageData.Active(UUID.randomUUID()));
        var otherFile = records.resolve(other + ".dat");
        var sentinel = java.nio.file.attribute.FileTime.fromMillis(100000);
        Files.setLastModifiedTime(otherFile, sentinel);
        data.commit(id, new DragonStorageData.Stored(owner, snapshot, true, 14));
        require(Files.getLastModifiedTime(otherFile).equals(sentinel), "A commit must only write its own record");

        for (String field : new String[]{"Owner", "Revision"}) {
            var damaged = new CompoundTag();
            damaged.putString("State", field.equals("Owner") ? "stored" : "active");
            damaged.put("Snapshot", snapshot);
            NbtIo.writeCompressed(damaged, otherFile.toFile());
            var reopened = new DragonStorageData(file);
            try {
                reopened.get(other);
                throw new AssertionError("Missing " + field + " was accepted");
            } catch (IOException expected) {
                require(expected.getMessage().contains(field) && expected.getMessage().contains(other.toString()),
                        "The error must identify both the dragon and the missing field");
            }
            require(reopened.get(id) instanceof DragonStorageData.Stored, "Other dragons must stay accessible");
            var rings = new IssuedDragonRings();
            reopened.reconcileIssuedRings(rings);
            require(rings.contains(id) && !rings.contains(other), "Only valid records may recover membership");
        }
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
