package com.heconis.dmlcompanion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = DragonMountsCompanion.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MinionSettingsScreen extends Screen {
    private final Screen parent;
    private MinionDisplay.Mode selected;

    private MinionSettingsScreen(Screen parent) {
        super(Component.translatable("screen.dmlcompanion.minion_settings"));
        this.parent = parent;
        selected = MinionDisplay.currentMode();
    }

    @SubscribeEvent
    public static void register(FMLClientSetupEvent event) {
        ModList.get().getModContainerById(DragonMountsCompanion.MOD_ID).orElseThrow()
                .registerExtensionPoint(ConfigScreenFactory.class,
                        () -> new ConfigScreenFactory(MinionSettingsScreen::new));
    }

    @Override
    protected void init() {
        var left = width / 2;
        var middle = height / 2;
        var optionWidth = Math.min(240, width - 24);
        addRenderableWidget(CycleButton.<MinionDisplay.Mode>builder(mode ->
                        Component.translatable("screen.dmlcompanion.display." + mode.name().toLowerCase(Locale.ROOT)))
                .withValues(MinionDisplay.Mode.FOLLOW, MinionDisplay.Mode.HEAD, MinionDisplay.Mode.HIDDEN)
                .withInitialValue(selected)
                .create(left - optionWidth / 2, middle - 16, optionWidth, 20,
                        Component.translatable("screen.dmlcompanion.display"), (button, mode) -> selected = mode));
        var actionWidth = (optionWidth - 8) / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> saveAndClose())
                .bounds(left - actionWidth - 4, middle + 20, actionWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + 4, middle + 20, actionWidth, 20).build());
    }

    private void saveAndClose() {
        if (MinionDisplay.MODE.get() != selected) {
            MinionDisplay.MODE.set(selected);
            MinionDisplay.MODE.save();
        }
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 42, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
