package com.heconis.dmlcompanion;

import com.github.kay9.dragonmounts.dragon.TameableDragon;
import com.github.kay9.dragonmounts.dragon.breed.BreedRegistry;
import com.github.kay9.dragonmounts.dragon.breed.DragonBreed;
import com.google.gson.JsonParseException;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

final class DragonRingItem extends Item implements ICurioItem {
    private static final String RING_SLOT = "ring";
    private static final String DRAGON_ID = "DragonId";
    private static final String OWNER_ID = "OwnerId";
    private static final String DRAGON_NAME = "DragonName";
    private static final String DRAGON_INFO = "DragonInfo";
    private static final String BREED = "Breed";
    private static final String STATE = "State";
    private static final String COLOR = "Color";
    private static final String CURRENT_HP = "Current";
    private static final String MAX_HP = "Max";
    private static final String LAST_KNOWN = "LastKnown";
    private static final String FRIENDSHIP_LEVEL = "FriendshipLevel";
    private static final String FRIENDSHIP_PROGRESS = "FriendshipProgress";
    private static final ResourceLocation TOOLTIP_LEVEL_FONT =
            ResourceLocation.fromNamespaceAndPath(DragonMountsCompanion.MOD_ID, "tooltip_level");

    DragonRingItem() {
        super(new Properties().stacksTo(1));
    }

    Optional<UUID> equippedDragon(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve().flatMap(inventory -> {
            var rings = inventory.findCurios(this);
            if (rings.size() != 1) {
                return Optional.empty();
            }
            var ring = rings.get(0);
            var slot = ring.slotContext();
            return !slot.cosmetic() && RING_SLOT.equals(slot.identifier())
                    ? ownedDragon(ring.stack(), player) : Optional.empty();
        });
    }

    private Optional<UUID> ownedDragon(ItemStack stack, Player player) {
        var tag = stack.getTag();
        if (tag == null || !tag.hasUUID(DRAGON_ID) || !tag.hasUUID(OWNER_ID)
                || !player.getUUID().equals(tag.getUUID(OWNER_ID))) {
            return Optional.empty();
        }
        return Optional.of(tag.getUUID(DRAGON_ID));
    }

    ItemStack bind(TameableDragon dragon, UUID ownerId) {
        var stack = new ItemStack(this);
        var tag = stack.getOrCreateTag();
        tag.putUUID(DRAGON_ID, dragon.getUUID());
        tag.putUUID(OWNER_ID, ownerId);
        tag.putString(DRAGON_NAME, Component.Serializer.toJson(dragon.getName()));
        if (dragon.getBreed() != null) {
            var health = DragonHealth.of(dragon);
            tag.put(DRAGON_INFO, description(dragon.level(), "active", health.breed(), health.current(), health.max(), false));
        }
        return stack;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        // Curios also invokes inventoryTick for equipment and syncs changed ItemStack tags.
        if (!(holder instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        var tag = stack.getTag();
        if (tag == null || !tag.hasUUID(DRAGON_ID)) {
            return;
        }
        var id = tag.getUUID(DRAGON_ID);
        var records = IssuedDragonRings.get(player.server);
        if (!records.contains(id)) {
            return;
        }
        int ticks = records.friendshipTicks(id);
        int friendshipLevel = DragonFriendship.levelAt(ticks);
        int progress = DragonFriendship.progressPercent(ticks);
        if (tag.getInt(FRIENDSHIP_LEVEL) != friendshipLevel) {
            tag.putInt(FRIENDSHIP_LEVEL, friendshipLevel);
        }
        if (tag.getInt(FRIENDSHIP_PROGRESS) != progress) {
            tag.putInt(FRIENDSHIP_PROGRESS, progress);
        }
        CompoundTag info = null;
        try {
            var data = DragonStorageData.get(player.server);
            var entry = data.get(id);
            var health = records.healthOf(id);
            String breed = health == null ? "" : health.breed();
            float current = health == null ? Float.NaN : health.current();
            float max = health == null ? Float.NaN : health.max();
            if (entry instanceof DragonStorageData.Stored stored) {
                var name = stored.dragonName();
                if (name != null) {
                    records.rememberName(id, Component.Serializer.toJson(name));
                }
                var snapshot = stored.snapshot();
                String snapshotBreed = snapshot.getString(TameableDragon.NBT_BREED);
                if (!snapshotBreed.equals(breed)) {
                    max = Float.NaN;
                }
                if (stored.hasMaxHealth()) {
                    max = stored.maxHealth();
                }
                current = snapshot.contains("Health", Tag.TAG_ANY_NUMERIC) ? snapshot.getFloat("Health") : Float.NaN;
                info = description(level, stored.minion() ? "minion" : "stored", snapshotBreed, current, max, false);
            } else if (entry == DragonStorageData.Dead.INSTANCE) {
                info = description(level, "dead", breed, 0, max, false);
            } else {
                var loaded = DragonStorage.findLoaded(player.server, id);
                DragonHealth observed = loaded.size() == 1 && loaded.get(0) instanceof TameableDragon dragon
                        && dragon.isAlive() && dragon.getBreed() != null
                        && data.allowsJoin(id, DragonStorage.revisionOf(dragon), null) ? DragonHealth.of(dragon) : null;
                if (observed != null) {
                    breed = observed.breed();
                    current = observed.current();
                    max = observed.max();
                }
                info = description(level, "active", breed, current, max, observed == null);
            }
        } catch (IOException ignored) {
            // Keep the last synchronized information if authoritative storage is unreadable.
        }
        if (info != null && !info.equals(tag.getCompound(DRAGON_INFO))) {
            tag.put(DRAGON_INFO, info);
        }
        var latest = records.nameOf(id);
        if (latest != null && !latest.equals(tag.getString(DRAGON_NAME))) {
            tag.putString(DRAGON_NAME, latest);
        }
    }

    private static CompoundTag description(Level level, String state, String breed, float current, float max,
                                           boolean lastKnown) {
        var info = new CompoundTag();
        info.putString(STATE, state);
        if (!breed.isEmpty()) {
            info.putString(BREED, breed);
            var breedId = ResourceLocation.tryParse(breed);
            if (breedId != null) {
                var definition = BreedRegistry.get(breedId, level.registryAccess());
                if (definition != null) {
                    info.putInt(COLOR, definition.secondaryColor() & 0xFFFFFF);
                }
            }
        }
        if (Float.isFinite(current) && current >= 0) {
            info.putFloat(CURRENT_HP, current);
            info.putBoolean(LAST_KNOWN, lastKnown);
        }
        if (Float.isFinite(max) && max > 0) {
            info.putFloat(MAX_HP, max);
        }
        return info;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        var tag = stack.getTag();
        if (tag == null || !tag.hasUUID(DRAGON_ID)) {
            return;
        }
        var info = tag.getCompound(DRAGON_INFO);
        String breed = info.getString(BREED);
        var unknown = Component.translatable("tooltip.dmlcompanion.unknown");
        var breedName = breed.isEmpty() ? unknown : Component.translatable(DragonBreed.getTranslationKey(breed));
        tooltip.add(Component.translatable("tooltip.dmlcompanion.breed", breedName).withStyle(ChatFormatting.GRAY));
        var state = switch (info.getString(STATE)) {
            case "active" -> Component.translatable("tooltip.dmlcompanion.state.active");
            case "stored" -> Component.translatable("tooltip.dmlcompanion.state.stored");
            case "minion" -> Component.translatable("tooltip.dmlcompanion.state.minion");
            case "dead" -> Component.translatable("tooltip.dmlcompanion.state.dead");
            default -> unknown;
        };
        tooltip.add(Component.translatable("tooltip.dmlcompanion.state", state).withStyle(ChatFormatting.GRAY));
        Component health = unknown;
        if (info.contains(CURRENT_HP, Tag.TAG_FLOAT)) {
            var current = formatHealth(info.getFloat(CURRENT_HP));
            var max = info.contains(MAX_HP, Tag.TAG_FLOAT) ? formatHealth(info.getFloat(MAX_HP)) : unknown;
            health = Component.translatable("tooltip.dmlcompanion.hp.value", current, max);
            if (info.getBoolean(LAST_KNOWN)) {
                health = Component.translatable("tooltip.dmlcompanion.hp.last_known", health);
            }
        }
        tooltip.add(Component.translatable("tooltip.dmlcompanion.hp", health).withStyle(ChatFormatting.GRAY));
        int friendshipLevel = DragonFriendship.clampLevel(tag.getInt(FRIENDSHIP_LEVEL));
        var friendship = friendshipLevel == DragonFriendship.MAX_LEVEL
                ? Component.translatable("tooltip.dmlcompanion.friendship.max")
                : Component.translatable("tooltip.dmlcompanion.friendship.progress",
                        Mth.clamp(tag.getInt(FRIENDSHIP_PROGRESS), 0, 99));
        tooltip.add(friendship.withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dmlcompanion.friendship.blessings").withStyle(ChatFormatting.GRAY));
        for (int unlockedLevel = 1; unlockedLevel <= friendshipLevel; unlockedLevel++) {
            String padding = unlockedLevel < 10 ? "\u2007" : "";
            var label = Component.literal("Lv" + padding + unlockedLevel)
                    .withStyle(style -> style.withFont(TOOLTIP_LEVEL_FONT));
            var ability = Component.translatable("tooltip.dmlcompanion.friendship.ability." + unlockedLevel);
            tooltip.add(Component.translatable("tooltip.dmlcompanion.friendship.ability", label, ability)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static Component formatHealth(float value) {
        return Component.literal(value == (int) value
                ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.1f", value));
    }

    static int gemColor(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null) {
            return 0x69C9F3;
        }
        var info = tag.getCompound(DRAGON_INFO);
        if ("dragonmounts:fire".equals(info.getString(BREED))) {
            return 0xF44139;
        }
        if (!info.contains(COLOR, Tag.TAG_INT)) {
            return 0x69C9F3;
        }
        int color = info.getInt(COLOR);
        int red = ((color >> 16 & 255) * 3 + 255) / 4;
        int green = ((color >> 8 & 255) * 3 + 255) / 4;
        int blue = ((color & 255) * 3 + 255) / 4;
        return red << 16 | green << 8 | blue;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasState(stack, "minion") || super.isFoil(stack);
    }

    static boolean isDeadRing(ItemStack stack) {
        return hasState(stack, "dead");
    }

    private static boolean hasState(ItemStack stack, String state) {
        var tag = stack.getTag();
        return tag != null && state.equals(tag.getCompound(DRAGON_INFO).getString(STATE));
    }

    @Override
    public Component getName(ItemStack stack) {
        var tag = stack.getTag();
        Component name = super.getName(stack);
        if (tag == null || !tag.hasUUID(DRAGON_ID)) {
            return name;
        }
        if (tag.contains(DRAGON_NAME, Tag.TAG_STRING)) {
            try {
                var dragonName = Component.Serializer.fromJson(tag.getString(DRAGON_NAME));
                if (dragonName != null) {
                    name = Component.translatable(getDescriptionId() + ".bound", dragonName);
                }
            } catch (JsonParseException ignored) {
                // Keep the generic ring name if the saved name cannot be read.
            }
        }
        int friendshipLevel = DragonFriendship.clampLevel(tag.getInt(FRIENDSHIP_LEVEL));
        if (friendshipLevel == DragonFriendship.MAX_LEVEL) {
            return Component.translatable(getDescriptionId() + ".max", name).withStyle(ChatFormatting.YELLOW);
        }
        return Component.translatable(getDescriptionId() + ".level", name, friendshipLevel);
    }

    @Override
    public boolean canEquip(SlotContext slot, ItemStack stack) {
        if (!(slot.entity() instanceof Player player) || slot.cosmetic() || !RING_SLOT.equals(slot.identifier())) {
            return false;
        }

        if (ownedDragon(stack, player).isEmpty()) {
            return false;
        }

        return CuriosApi.getCuriosInventory(player).resolve()
                .map(inventory -> inventory.findCurios(this).stream().allMatch(result ->
                        slot.identifier().equals(result.slotContext().identifier())
                                && slot.index() == result.slotContext().index()))
                .orElse(false);
    }
}
