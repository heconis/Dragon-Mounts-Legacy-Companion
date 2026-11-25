package com.heconis.dmlcompanion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import noobanidus.mods.lootr.block.entities.LootrInventoryBlockEntity;
import noobanidus.mods.lootr.init.ModBlocks;

@Mod.EventBusSubscriber(modid = DragonMountsCompanion.MOD_ID)
public final class EndDragonEggReward {
    private static final String MARKER = DragonMountsCompanion.MOD_ID + ":end_dragon_reward";

    private EndDragonEggReward() {}

    public static boolean place(ServerLevel level, BlockPos pos) {
        if (!ModList.get().isLoaded("lootr") || !level.isEmptyBlock(pos) || !Lootr.place(level, pos)) {
            return false;
        }
        var record = RewardPosition.get(level);
        record.position = pos.immutable();
        record.setDirty();
        return true;
    }

    public static void remove(ServerLevel level) {
        if (!ModList.get().isLoaded("lootr")) {
            return;
        }
        var record = RewardPosition.get(level);
        if (record.position == null) {
            return;
        }
        if (isReward(level.getBlockEntity(record.position))) {
            level.removeBlock(record.position, false);
        }
        record.position = null;
        record.setDirty();
    }

    @SubscribeEvent
    public static void protectReward(BlockEvent.BreakEvent event) {
        if (isReward(event.getLevel().getBlockEntity(event.getPos()))) {
            event.setCanceled(true);
        }
    }

    private static boolean isReward(BlockEntity entity) {
        return entity != null && entity.getPersistentData().getBoolean(MARKER);
    }

    private static final class Lootr {
        private static boolean place(ServerLevel level, BlockPos pos) {
            if (!level.setBlockAndUpdate(pos, ModBlocks.INVENTORY.get().defaultBlockState())) {
                return false;
            }
            if (!(level.getBlockEntity(pos) instanceof LootrInventoryBlockEntity chest)) {
                level.removeBlock(pos, false);
                return false;
            }
            var contents = NonNullList.withSize(27, ItemStack.EMPTY);
            contents.set(13, new ItemStack(Items.DRAGON_EGG));
            chest.setCustomInventory(contents);
            chest.getPersistentData().putBoolean(MARKER, true);
            chest.setChanged();
            var state = chest.getBlockState();
            level.sendBlockUpdated(pos, state, state, 2);
            return true;
        }
    }

    private static final class RewardPosition extends SavedData {
        private BlockPos position;

        private static RewardPosition get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(RewardPosition::load, RewardPosition::new,
                    DragonMountsCompanion.MOD_ID + "_end_dragon_reward");
        }

        private static RewardPosition load(CompoundTag tag) {
            var record = new RewardPosition();
            if (tag.contains("Position", Tag.TAG_LONG)) {
                record.position = BlockPos.of(tag.getLong("Position"));
            }
            return record;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            if (position != null) {
                tag.putLong("Position", position.asLong());
            }
            return tag;
        }
    }
}
