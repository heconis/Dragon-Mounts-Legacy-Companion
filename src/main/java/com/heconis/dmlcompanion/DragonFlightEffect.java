package com.heconis.dmlcompanion;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

import java.util.List;
import java.util.function.Consumer;

final class DragonFlightEffect extends MobEffect {
    DragonFlightEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x807594);
    }

    @Override
    public List<ItemStack> getCurativeItems() {
        return List.of();
    }

    @Override
    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        consumer.accept(new IClientMobEffectExtensions() {
            private final ResourceLocation elytra = ResourceLocation.withDefaultNamespace("textures/item/elytra.png");

            @Override
            public boolean renderGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphics graphics,
                                         int x, int y, float z, float alpha) {
                graphics.setColor(1, 1, 1, alpha);
                graphics.blit(elytra, x + 4, y + 4, 0, 0, 16, 16, 16, 16);
                graphics.setColor(1, 1, 1, 1);
                return true;
            }

            @Override
            public boolean renderInventoryIcon(MobEffectInstance instance, EffectRenderingInventoryScreen<?> screen,
                                               GuiGraphics graphics, int x, int y, int z) {
                graphics.blit(elytra, x + 1, y + 8, 0, 0, 16, 16, 16, 16);
                return true;
            }
        });
    }
}
