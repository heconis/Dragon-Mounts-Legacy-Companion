package com.heconis.dmlcompanion.mixin;

import com.github.kay9.dragonmounts.dragon.TameableDragon;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TameableDragon.class)
abstract class DragonFeedingMixin {
    @Redirect(method = "mobInteract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/HolderSet;contains(Lnet/minecraft/core/Holder;)Z"))
    private boolean dmlcompanion$useAmethystForTaming(HolderSet<Item> breedItems, Holder<Item> offeredItem) {
        return offeredItem.value() == Items.AMETHYST_SHARD;
    }

    @Inject(method = "isFoodItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void dmlcompanion$excludeFishFromHealing(ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        if (stack.is(ItemTags.FISHES)) {
            result.setReturnValue(false);
        }
    }

    @Inject(method = "isFood", at = @At("HEAD"), cancellable = true)
    private void dmlcompanion$disableBreedingFood(ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        result.setReturnValue(false);
    }

    @Inject(method = "canReproduce", at = @At("HEAD"), cancellable = true, remap = false)
    private void dmlcompanion$disableBreeding(CallbackInfoReturnable<Boolean> result) {
        result.setReturnValue(false);
    }
}
