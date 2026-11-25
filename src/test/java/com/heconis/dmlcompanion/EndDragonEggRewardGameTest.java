package com.heconis.dmlcompanion;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import noobanidus.mods.lootr.block.entities.LootrInventoryBlockEntity;
import noobanidus.mods.lootr.data.DataStorage;
import noobanidus.mods.lootr.util.ChestUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(DragonMountsCompanion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EndDragonEggRewardGameTest {
    @GameTest(templateNamespace = "minecraft", template = "igloo/top")
    public static void eggRewardFollowsDragonFight(GameTestHelper helper) {
        var level = helper.getLevel();
        var portal = helper.absolutePos(new BlockPos(2, 15, 2));
        var dragon = EntityType.ENDER_DRAGON.create(level);
        var fight = createFight(level, portal, dragon, false);
        fight.setDragonKilled(dragon);
        var reward = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, portal).below();

        if (!ModList.get().isLoaded("lootr")) {
            helper.assertTrue(level.getBlockState(reward).is(Blocks.DRAGON_EGG), "DML's regular egg must remain available");
            helper.succeed();
            return;
        }

        LootrCheck.verify(helper, level, portal, reward, fight);
        helper.succeed();
    }

    private static EndDragonFight createFight(ServerLevel level, BlockPos portal, EnderDragon dragon, boolean previouslyKilled) {
        var data = new EndDragonFight.Data(false, false, previouslyKilled, false, Optional.of(dragon.getUUID()),
                Optional.of(portal), Optional.of(List.of()));
        return new EndDragonFight(level, level.getSeed(), data, portal.atY(0));
    }

    private static final class LootrCheck {
        private static void verify(GameTestHelper helper, ServerLevel level, BlockPos portal, BlockPos reward, EndDragonFight fight) {
            var chest = (LootrInventoryBlockEntity) level.getBlockEntity(reward);
            helper.assertTrue(chest != null, "Expected a Lootr chest at " + reward + ", found " + level.getBlockState(reward));
            var chestId = chest.getTileId();
            var first = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RewardTester1"));
            var second = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RewardTester2"));
            var firstLoot = DataStorage.getInventory(level, chestId, ChestUtil.copyItemList(chest.getCustomInventory()), first, reward, chest);
            helper.assertTrue(firstLoot.getContainerSize() == 27 && firstLoot.getItem(13).is(Items.DRAGON_EGG)
                    && firstLoot.getItem(13).getCount() == 1, "The chest must have an egg in the center");
            for (int slot = 0; slot < 27; slot++) {
                helper.assertTrue(slot == 13 || firstLoot.getItem(slot).isEmpty(), "All other slots must be empty");
            }
            firstLoot.removeItem(13, 1);
            var secondLoot = DataStorage.getInventory(level, chestId, ChestUtil.copyItemList(chest.getCustomInventory()), second, reward, chest);
            helper.assertTrue(firstLoot.isEmpty() && secondLoot.getItem(13).is(Items.DRAGON_EGG),
                    "Claiming one egg must leave the other player's loot intact");

            fight.tryRespawn();
            helper.assertTrue(level.getBlockEntity(reward) == chest, "A failed attempt must keep the rewards");
            var crystals = new ArrayList<EndCrystal>();
            for (var direction : Direction.Plane.HORIZONTAL) {
                var pos = portal.above().relative(direction, 2);
                var crystal = EntityType.END_CRYSTAL.create(level);
                crystal.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                level.addFreshEntity(crystal);
                crystals.add(crystal);
            }
            fight.tryRespawn();
            helper.assertTrue(level.isEmptyBlock(reward), "Actual ritual start must remove the old chest");
            crystals.forEach(crystal -> crystal.discard());

            var nextDragon = EntityType.ENDER_DRAGON.create(level);
            createFight(level, portal, nextDragon, true).setDragonKilled(nextDragon);
            var next = (LootrInventoryBlockEntity) level.getBlockEntity(reward);
            helper.assertTrue(next != null && !chestId.equals(next.getTileId()), "The next victory must create a fresh chest");
            var nextLoot = DataStorage.getInventory(level, next.getTileId(), ChestUtil.copyItemList(next.getCustomInventory()), first, reward, next);
            helper.assertTrue(nextLoot.getItem(13).is(Items.DRAGON_EGG), "A previous claimant must receive another egg");
            EndDragonEggReward.remove(level);
        }
    }
}
