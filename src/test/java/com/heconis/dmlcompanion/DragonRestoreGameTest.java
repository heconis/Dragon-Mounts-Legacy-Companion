package com.heconis.dmlcompanion;

import com.github.kay9.dragonmounts.DMLRegistry;
import com.github.kay9.dragonmounts.dragon.TameableDragon;
import com.github.kay9.dragonmounts.dragon.breed.BreedRegistry;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@GameTestHolder(DragonMountsCompanion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DragonRestoreGameTest {
    @GameTest(templateNamespace = "minecraft", template = "igloo/top")
    public static void restoreWritesDragonChunk(GameTestHelper helper) throws IOException {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RingTester"));
        var anchor = helper.absolutePos(new BlockPos(2, 12, 2));
        player.moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, 0, 0);
        level.addNewPlayer(player);
        var dragon = helper.spawn(DMLRegistry.DRAGON.get(), 2, 12, 4);
        dragon.setBreed(BreedRegistry.get("dragonmounts:fire", level.registryAccess()));
        dragon.tame(player);
        var rings = IssuedDragonRings.get(player.server);
        var id = dragon.getUUID();
        rings.recordIssue(id);
        var ring = DragonMountsCompanion.DRAGON_RING.get().bind(dragon, player.getUUID());
        CuriosApi.getCuriosInventory(player).orElseThrow(IllegalStateException::new).setEquippedCurio("ring", 0, ring);
        var stored = DragonStorage.toggle(player);
        helper.assertTrue(stored.result() == DragonStorage.Result.MINION, "Storage returned " + stored.result());
        long start = System.nanoTime();
        var restored = DragonStorage.toggle(player);
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        helper.assertTrue(restored.result() == DragonStorage.Result.RESTORED, "Restore returned " + restored.result());
        var live = (TameableDragon) level.getEntity(id);
        var entry = (DragonStorageData.Active) DragonStorageData.get(player.server).get(id);
        var root = player.server.getWorldPath(LevelResource.ROOT);
        var entities = DimensionType.getStorageFolder(level.dimension(), root).resolve("entities");
        helper.assertTrue(live != null && DragonStorage.hasSavedRevision(entities, live.chunkPosition(), id, entry.revision()),
                "The committed revision must exist in vanilla's entity region");
        LogUtils.getLogger().info("GameTest dragon restore including disk verification took {} ms", elapsed);
        live.discard();
        level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        helper.succeed();
    }
}
