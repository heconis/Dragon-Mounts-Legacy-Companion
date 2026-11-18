package com.heconis.dmlcompanion;

import com.github.kay9.dragonmounts.DMLRegistry;
import com.github.kay9.dragonmounts.dragon.TameableDragon;
import com.mojang.logging.LogUtils;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class DragonStorage {
    static final String REVISION = DragonMountsCompanion.MOD_ID + ":storage_revision";
    private static final Logger LOGGER = LogUtils.getLogger();

    private DragonStorage() {}

    static Result toggle(ServerPlayer player) {
        var server = player.server;
        if (!server.isSameThread()) {
            throw new IllegalStateException("Dragon storage must run on the server thread");
        }
        var equipped = DragonMountsCompanion.DRAGON_RING.get().equippedDragon(player);
        if (equipped.isEmpty() || !player.isAlive()) {
            return Result.NO_RING;
        }
        var id = equipped.get();
        try {
            var data = DragonStorageData.get(server);
            var entry = data.get(id);
            // An existing storage record already proves the ring passed the issuance check.
            if (entry == null && !IssuedDragonRings.get(server).contains(id)) {
                return Result.UNBOUND;
            }
            if (entry == DragonStorageData.Dead.INSTANCE) {
                return Result.DEAD;
            }
            if (data.isRestoring(id)) {
                return Result.BUSY;
            }
            return entry instanceof DragonStorageData.Stored stored
                    ? restore(player, id, data, stored)
                    : minionize(player, id, data);
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Could not change storage state for dragon {}", id, failure);
            return Result.STORAGE_ERROR;
        }
    }

    static void onPlayerDeath(ServerPlayer player, UUID id) {
        try {
            var data = DragonStorageData.get(player.server);
            if (data.isRestoring(id)) {
                return;
            }
            var entry = data.get(id);
            // A stored dragon already has a snapshot. Keep its minion preference across respawn.
            if (entry instanceof DragonStorageData.Active
                    || entry == null && IssuedDragonRings.get(player.server).contains(id)) {
                minionize(player, id, data);
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Could not store dragon {} on player death", id, failure);
        }
    }

    private static Result minionize(ServerPlayer player, UUID id, DragonStorageData data) throws IOException {
        var loaded = findLoaded(player.server, id);
        if (loaded.isEmpty()) {
            return Result.NOT_LOADED;
        }
        if (loaded.size() != 1 || !(loaded.get(0) instanceof TameableDragon dragon)
                || !data.allowsJoin(id, revisionOf(dragon), null)) {
            return Result.CONFLICT;
        }
        if (!dragon.isTamedFor(player)) {
            return Result.UNBOUND;
        }
        if (!dragon.isAlive()) {
            return Result.DEAD;
        }
        if (dragon.isVehicle() || dragon.isPassenger() || dragon.isLeashed()) {
            return Result.ATTACHED;
        }
        if (dragon.level().noSave()) {
            LOGGER.warn("Storage refused for dragon {} because saving is disabled in {}", id, dragon.level().dimension().location());
            return Result.STORAGE_ERROR;
        }

        var snapshot = new CompoundTag();
        if (dragon.getBreed() == null || !dragon.save(snapshot)) {
            return Result.INVALID_DATA;
        }
        data.commit(id, new DragonStorageData.Stored(player.getUUID(), snapshot, true));
        dragon.discard();
        return Result.MINION;
    }

    private static Result restore(ServerPlayer player, UUID id, DragonStorageData data,
                                  DragonStorageData.Stored stored) throws IOException {
        if (!stored.owner().equals(player.getUUID())) {
            return Result.UNBOUND;
        }
        if (!findLoaded(player.server, id).isEmpty()) {
            return Result.CONFLICT;
        }

        var level = player.serverLevel();
        var dragon = loadDragon(level, player.position(), id, stored);
        if (dragon == null) {
            return Result.INVALID_DATA;
        }
        if (!placeInFront(player, dragon)) {
            return Result.NO_SPACE;
        }
        if (level.noSave()) {
            LOGGER.warn("Restoration refused for dragon {} because saving is disabled in {}", id, level.dimension().location());
            return Result.STORAGE_ERROR;
        }

        // Each attempt gets a new revision, including retries after a failed world save.
        var revision = UUID.randomUUID();
        dragon.getPersistentData().putUUID(REVISION, revision);
        data.beginRestore(dragon);
        boolean committed = false;
        try {
            if (!level.addFreshEntity(dragon) || level.getEntity(id) != dragon) {
                return Result.ADD_FAILED;
            }
            // Keep the snapshot authoritative until the restored entity has been flushed to its level.
            level.save(null, true, false);
            if (!dragon.isAlive() || level.getEntity(id) != dragon) {
                return Result.ADD_FAILED;
            }
            var worldRoot = player.server.getWorldPath(LevelResource.ROOT);
            var entityFolder = DimensionType.getStorageFolder(level.dimension(), worldRoot).resolve("entities");
            if (!hasSavedRevision(entityFolder, dragon.chunkPosition(), id, revision)) {
                LOGGER.warn("Restored dragon {} was not found in the saved entity chunk {}", id, dragon.chunkPosition());
                return Result.STORAGE_ERROR;
            }
            data.commit(id, new DragonStorageData.Active(revision));
            committed = true;
            return Result.RESTORED;
        } finally {
            data.endRestore(dragon);
            if (!committed) {
                dragon.discard();
            }
        }
    }

    static boolean hasSavedRevision(Path entityFolder, ChunkPos chunk, UUID id, UUID revision) throws IOException {
        var file = entityFolder.resolve("r." + chunk.getRegionX() + "." + chunk.getRegionZ() + ".mca");
        if (!Files.isRegularFile(file)) {
            return false;
        }
        // Vanilla's entity writer can log a failed write without throwing to the caller of level.save().
        try (var region = new RegionFile(file, entityFolder, false);
             var input = region.getChunkDataInputStream(chunk)) {
            if (input == null) {
                return false;
            }
            var entities = NbtIo.read(input).getList("Entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < entities.size(); i++) {
                var entity = entities.getCompound(i);
                var data = entity.getCompound("ForgeData");
                if (entity.hasUUID("UUID") && id.equals(entity.getUUID("UUID"))
                        && data.hasUUID(REVISION) && revision.equals(data.getUUID(REVISION))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static TameableDragon loadDragon(ServerLevel level, Vec3 anchor, UUID id, DragonStorageData.Stored stored) {
        var snapshot = stored.snapshot().copy();
        if (!DMLRegistry.DRAGON.getId().toString().equals(snapshot.getString("id"))
                || !snapshot.hasUUID("UUID") || !id.equals(snapshot.getUUID("UUID"))
                || !snapshot.contains("Health", Tag.TAG_ANY_NUMERIC)) {
            return null;
        }
        float health = snapshot.getFloat("Health");
        if (!Float.isFinite(health) || health <= 0) {
            return null;
        }
        var position = snapshot.getList("Pos", Tag.TAG_DOUBLE);
        if (position.size() != 3) {
            return null;
        }
        // Use a loaded anchor while DML reconstructs the entity and recalculates its dimensions.
        position.set(0, DoubleTag.valueOf(anchor.x));
        position.set(1, DoubleTag.valueOf(anchor.y));
        position.set(2, DoubleTag.valueOf(anchor.z));

        var dragon = DMLRegistry.DRAGON.get().create(level);
        if (dragon == null) {
            return null;
        }
        dragon.load(snapshot);
        if (dragon.getBreed() == null || !dragon.isTame() || !stored.owner().equals(dragon.getOwnerUUID())) {
            return null;
        }
        // DML recalculates youth attributes while reading NBT. Apply absolute health after those changes.
        dragon.setHealth(health);
        return dragon;
    }

    private static boolean placeInFront(ServerPlayer player, TameableDragon dragon) {
        var level = player.serverLevel();
        var forward = Vec3.directionFromRotation(0, player.getYRot());
        double distance = (player.getBbWidth() + dragon.getBbWidth()) / 2d + 1;
        var position = player.position().add(forward.scale(distance));
        dragon.moveTo(position.x, position.y, position.z, player.getYRot(), 0);
        dragon.setDeltaMovement(Vec3.ZERO);
        dragon.fallDistance = 0;

        var box = dragon.getBoundingBox();
        var border = level.getWorldBorder();
        if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()
                || box.minX < border.getMinX() || box.maxX > border.getMaxX()
                || box.minZ < border.getMinZ() || box.maxZ > border.getMaxZ()) {
            return false;
        }
        int minX = SectionPos.blockToSectionCoord(Mth.floor(box.minX));
        int minZ = SectionPos.blockToSectionCoord(Mth.floor(box.minZ));
        int maxX = SectionPos.blockToSectionCoord(Mth.floor(Math.nextDown(box.maxX)));
        int maxZ = SectionPos.blockToSectionCoord(Mth.floor(Math.nextDown(box.maxZ)));
        for (var chunk : ChunkPos.rangeClosed(new ChunkPos(minX, minZ), new ChunkPos(maxX, maxZ)).toList()) {
            if (!level.hasChunk(chunk.x, chunk.z) || !level.areEntitiesLoaded(chunk.toLong())) {
                return false;
            }
        }
        return level.noCollision(dragon) && !level.containsAnyLiquid(box)
                && level.getEntities(dragon, box, entity -> entity instanceof LivingEntity && entity.isAlive()).isEmpty();
    }

    private static List<Entity> findLoaded(MinecraftServer server, UUID id) {
        var found = new ArrayList<Entity>();
        for (var level : server.getAllLevels()) {
            var entity = level.getEntity(id);
            if (entity != null) {
                found.add(entity);
            }
        }
        return found;
    }

    static UUID revisionOf(TameableDragon dragon) {
        var data = dragon.getPersistentData();
        return data.hasUUID(REVISION) ? data.getUUID(REVISION) : null;
    }

    enum Result {
        MINION, RESTORED, NO_RING, UNBOUND, DEAD, BUSY,
        NOT_LOADED, CONFLICT, ATTACHED, INVALID_DATA, NO_SPACE, ADD_FAILED, STORAGE_ERROR
    }
}
