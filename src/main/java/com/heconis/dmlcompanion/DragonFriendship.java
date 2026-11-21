package com.heconis.dmlcompanion;

import com.google.gson.JsonParseException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

final class DragonFriendship {
    static final int MAX_LEVEL = 10;
    private static final int STEP_TICKS = 20 * 60 * 20;
    static final int MAX_TICKS = ticksForLevel(MAX_LEVEL);

    private DragonFriendship() {}

    static int ticksForLevel(int level) {
        return STEP_TICKS * level * (level - 1) / 2;
    }

    static int levelAt(int ticks) {
        int level = 1;
        while (level < MAX_LEVEL && ticks >= ticksForLevel(level + 1)) {
            level++;
        }
        return level;
    }

    static int progressPercent(int ticks) {
        int level = levelAt(ticks);
        return level == MAX_LEVEL ? 100 : Math.max(0, ticks - ticksForLevel(level)) * 100 / (STEP_TICKS * level);
    }

    static int clampLevel(int level) {
        return Mth.clamp(level, 1, MAX_LEVEL);
    }

    static double riddenFlightSpeedBonus(int level) {
        return (clampLevel(level) - 1) / (double) (MAX_LEVEL - 1);
    }

    static Component abilityText(int level) {
        return Component.translatable("friendship.dmlcompanion.ability." + level);
    }

    static void notifyLevelUp(ServerPlayer player, String savedName, int level) {
        Component dragonName = null;
        if (savedName != null) {
            try {
                dragonName = Component.Serializer.fromJson(savedName);
            } catch (JsonParseException ignored) {
                // An invalid legacy display name should not prevent a level-up.
            }
        }
        if (dragonName == null) {
            dragonName = Component.translatable("entity.dmlcompanion.minion");
        }
        var message = Component.translatable("message.dmlcompanion.friendship.level_up", dragonName, level);
        switch (level) {
            case 2, 3, 4, 5, 6, 7, 8, 9, 10 -> message.append(" ").append(abilityText(level));
            default -> {}
        }
        player.displayClientMessage(message, true);
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.55f, 1.15f);
    }
}
