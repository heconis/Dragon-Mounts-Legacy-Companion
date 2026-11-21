package com.heconis.dmlcompanion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.List;

public final class DragonBlessings {
    private static final List<Blessing> BLESSINGS = List.of(
            new Blessing(2, MobEffects.DIG_SPEED),
            new Blessing(3, MobEffects.MOVEMENT_SPEED),
            new Blessing(6, MobEffects.JUMP),
            new Blessing(7, MobEffects.DAMAGE_RESISTANCE),
            new Blessing(9, MobEffects.DAMAGE_BOOST));

    private DragonBlessings() {}

    static void update(ServerPlayer player, int level) {
        for (var blessing : BLESSINGS) {
            var effect = blessing.effect();
            var current = player.getEffect(effect);
            if (level < blessing.level()) {
                if (current instanceof BlessingEffect) {
                    player.removeEffect(effect);
                }
            } else if (current == null) {
                player.addEffect(new BlessingEffect(effect));
            }
        }
    }

    static void clear(ServerPlayer player) {
        update(player, 0);
    }

    public static void replaceBlessing(ServerPlayer player, MobEffectInstance incoming) {
        var effect = incoming.getEffect();
        if (!(incoming instanceof BlessingEffect) && player.getEffect(effect) instanceof BlessingEffect) {
            // An infinite vanilla effect would otherwise absorb a finite effect of the same strength.
            player.removeEffect(effect);
        }
    }

    private static final class BlessingEffect extends MobEffectInstance {
        BlessingEffect(MobEffect effect) {
            super(effect, INFINITE_DURATION, 0, true, false, true);
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            // An autosave must not turn a ring benefit into a permanent vanilla effect.
            var saved = super.save(tag);
            saved.putInt("Duration", 1);
            return saved;
        }
    }

    private record Blessing(int level, MobEffect effect) {}
}
