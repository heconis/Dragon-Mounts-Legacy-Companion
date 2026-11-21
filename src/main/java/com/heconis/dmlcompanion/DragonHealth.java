package com.heconis.dmlcompanion;

import com.github.kay9.dragonmounts.dragon.TameableDragon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

record DragonHealth(String breed, float current, float max) {
    static DragonHealth of(TameableDragon dragon) {
        return new DragonHealth(dragon.getBreed().id(dragon.level().registryAccess()).toString(),
                dragon.getHealth(), dragon.getMaxHealth());
    }

    static DragonHealth read(CompoundTag tag) {
        float current = tag.getFloat("Current");
        float max = tag.getFloat("Max");
        if (tag.contains("Current", Tag.TAG_ANY_NUMERIC) && Float.isFinite(current) && current >= 0
                && Float.isFinite(max) && max > 0) {
            return new DragonHealth(tag.getString("Breed"), current, max);
        }
        return null;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Breed", breed);
        tag.putFloat("Current", current);
        tag.putFloat("Max", max);
        return tag;
    }
}
