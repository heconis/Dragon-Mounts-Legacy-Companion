package com.heconis.dmlcompanion;

import com.github.kay9.dragonmounts.dragon.TameableDragon;
import com.google.gson.JsonParseException;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.Tag;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.UUID;

final class DragonRingItem extends Item implements ICurioItem {
    private static final String RING_SLOT = "ring";
    private static final String DRAGON_ID = "DragonId";
    private static final String OWNER_ID = "OwnerId";
    private static final String DRAGON_NAME = "DragonName";

    DragonRingItem() {
        super(new Properties().stacksTo(1));
    }

    ItemStack bind(TameableDragon dragon, UUID ownerId) {
        var stack = new ItemStack(this);
        var tag = stack.getOrCreateTag();
        tag.putUUID(DRAGON_ID, dragon.getUUID());
        tag.putUUID(OWNER_ID, ownerId);
        tag.putString(DRAGON_NAME, Component.Serializer.toJson(dragon.getName()));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        var tag = stack.getTag();
        if (tag != null && tag.contains(DRAGON_NAME, Tag.TAG_STRING)) {
            try {
                var dragonName = Component.Serializer.fromJson(tag.getString(DRAGON_NAME));
                if (dragonName != null) {
                    return Component.translatable(getDescriptionId() + ".bound", dragonName);
                }
            } catch (JsonParseException ignored) {
                return super.getName(stack);
            }
        }
        return super.getName(stack);
    }

    @Override
    public boolean canEquip(SlotContext slot, ItemStack stack) {
        if (!(slot.entity() instanceof Player player) || slot.cosmetic() || !RING_SLOT.equals(slot.identifier())) {
            return false;
        }

        var tag = stack.getTag();
        if (tag == null || !tag.hasUUID(DRAGON_ID) || !tag.hasUUID(OWNER_ID)
                || !player.getUUID().equals(tag.getUUID(OWNER_ID))) {
            return false;
        }

        return CuriosApi.getCuriosInventory(player).resolve()
                .map(inventory -> inventory.findCurios(this).stream().allMatch(result ->
                        slot.identifier().equals(result.slotContext().identifier())
                                && slot.index() == result.slotContext().index()))
                .orElse(false);
    }
}
